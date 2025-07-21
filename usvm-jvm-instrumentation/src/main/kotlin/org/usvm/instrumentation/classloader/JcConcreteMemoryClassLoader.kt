package org.usvm.instrumentation.classloader

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.approximation.ApproximationClassName
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.instrumentation.util.replace
import org.usvm.instrumentation.util.toByteArray
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.Collections
import java.util.Enumeration
import java.util.IdentityHashMap
import java.util.LinkedList
import java.util.Queue
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
// TODO: make this 'class'
object JcConcreteMemoryClassLoader : SecureClassLoader(getSystemClassLoader()) {
    lateinit var cp: JcClasspath

    private val File.isJar
        get() = this.extension == "jar"

    private val File.URL
        get() = this.toURI().toURL()

    private fun File.matchResource(locURI: URI, name: String): Boolean {
        check(name.isNotEmpty())
        val relativePath by lazy { locURI.relativize(this.toURI()).toString() }
        return this.name == name
                || relativePath == name
                || relativePath.endsWith(name)
    }

    private fun JarEntry.matchResource(name: String, single: Boolean): Boolean {
        check(name.isNotEmpty())
        val entryName = this.name
        return entryName == name
                || entryName.endsWith(name)
                || !single && entryName.contains(name)
    }

    private fun findResourcesInFolder(
        locFile: File,
        name: String,
        single: Boolean
    ): List<URL>? {
        check(locFile.isDirectory)
        val result = mutableListOf<URL>()

        val locURI = locFile.toURI()
        val queue: Queue<File> = LinkedList()
        var current: File? = locFile
        while (current != null) {
            if (current.matchResource(locURI, name)) {
                result.add(current.URL)
                if (single)
                    break
            }

            if (current.isDirectory)
                queue.addAll(current.listFiles()!!)

            current = queue.poll()
        }

        if (result.isNotEmpty())
            return result

        return null
    }

    private fun findResourcesInJar(locFile: File, name: String, single: Boolean): List<URL>? {
        val jar = JarFile(locFile)
        val jarPath = "jar:file:${locFile.absolutePath}!".replace("\\", "/")
        if (single) {
            for (current in jar.entries()) {
                if (current.matchResource(name, true))
                    return listOf(URI("$jarPath/${current.name}").toURL())
            }
        } else {
            val result = jar.entries().toList().mapNotNull {
                if (it.matchResource(name, false))
                    URI("$jarPath/${it.name}").toURL()
                else null
            }
            if (result.isNotEmpty())
                return result
        }

        return null
    }

    private fun tryGetResource(locFile: File, name: String): List<URL>? {
        check(locFile.isFile)
        return if (locFile.name == name) listOf(locFile.URL) else null
    }

    private fun internalFindResources(name: String?, single: Boolean): Enumeration<URL>? {
        if (name.isNullOrEmpty())
            return null

        val result = mutableListOf<URL>()
        for (loc in cp.locations) {
            val locFile = loc.jarOrFolder
            val resources =
                if (locFile.isJar) findResourcesInJar(locFile, name, single)
                else if (locFile.isDirectory) findResourcesInFolder(locFile, name, single)
                else tryGetResource(locFile, name)
            if (resources != null) {
                if (single)
                    return Collections.enumeration(resources)
                result += resources
            }
        }

        if (result.isNotEmpty())
            return Collections.enumeration(result)

        return null
    }

    override fun loadClass(name: String?): Class<*> {
        if (name == null)
            throw ClassNotFoundException()

        if (name == "jdk.vm.ci.meta.Assumptions")
            println("AHTUNG!")

        if (name == "ch.qos.logback.classic.spi.Configurator")
            println()

        val loaded = findLoadedClass(name)
        if (loaded != null)
            return loaded

        val jcClass = cp.findClassOrNull(name)
        return when {
            jcClass == null -> throw ClassNotFoundException()
            else -> defineClassRecursively(jcClass)
        }
    }

    private fun defineClass(name: String, code: ByteArray): Class<*> {
        return defineClass(name, ByteBuffer.wrap(code), null as CodeSource?)
    }

    override fun getResource(name: String?): URL? {
        try {
            return internalFindResources(name, true)?.nextElement()
        } catch (e: Throwable) {
            error("Failed getting resource ${e.message}")
        }
    }

    override fun getResources(name: String?): Enumeration<URL> {
        try {
            return internalFindResources(name, false) ?: Collections.emptyEnumeration()
        } catch (e: Throwable) {
            error("Failed getting resources ${e.message}")
        }
    }

    private fun defineClassRecursively(jcClass: JcClassOrInterface): Class<*> =
        defineClassRecursively(jcClass, hashSetOf())
            ?: error("Can't define class $jcClass")

    private fun getBytecode(jcClass: JcClassOrInterface): ByteArray {
        val instrumentedMethods = jcClass.declaredMethods

        if (instrumentedMethods.isEmpty())
            return jcClass.bytecode()

        return jcClass.withAsmNode { asmNode ->
            for (method in instrumentedMethods) {
                val isApproximated = method is JcEnrichedVirtualMethod
                        || Approximations.findOriginalByApproximationOrNull(ApproximationClassName(jcClass.name)) != null
                if (isApproximated && asmNode.methods.none { it.name == method.name && it.desc == method.description })
                    continue

                if (isApproximated)
                    error("JcConcreteMemoryClassLoader.getBytecode: unable to find original method $method")

                val rawInstList = method.rawInstList

                val newMethodNode = MethodNodeBuilder(method, rawInstList).build()
                val oldMethodNode = asmNode.methods.find { it.name == method.name && it.desc == method.description }
                asmNode.methods.replace(oldMethodNode, newMethodNode)
            }

            asmNode.toByteArray(jcClass.classpath)
        }
    }

    private fun defineClassRecursively(
        jcClass: JcClassOrInterface,
        visited: MutableSet<JcClassOrInterface>
    ): Class<*>? {
        val className = jcClass.name
        val loaded = findLoadedClass(className)
        if (loaded != null)
            return loaded

        if (!visited.add(jcClass))
            return null

        if (jcClass.declaration.location.isRuntime || jcClass is JcUnknownClass)
            return super.loadClass(className)

        val notVisitedSupers = jcClass.allSuperHierarchySequence.filterNot { it in visited }
        notVisitedSupers.forEach { defineClassRecursively(it, visited) }

        val bytecode = getBytecode(jcClass)
        val loadedClass = defineClass(className, bytecode)

        return loadedClass
    }
}
