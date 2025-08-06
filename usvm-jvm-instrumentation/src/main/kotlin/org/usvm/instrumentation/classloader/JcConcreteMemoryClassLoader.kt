package org.usvm.instrumentation.classloader

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.usvm.instrumentation.ConcolicHelper
import org.usvm.instrumentation.instrumentation.JcInstrumenterFactory
import org.usvm.instrumentation.instrumentation.JcRuntimeConcolicInstrumenterFactory
import org.usvm.instrumentation.util.*
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSource
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Loads known classes using [ClassLoader.getSystemClassLoader], or defines them using bytecode from jacodb if they are unknown.
 */
// TODO: make this 'class'
object JcConcreteMemoryClassLoader : MetaClassLoader(getSystemClassLoader()) {
    lateinit var cp: JcClasspath
    private val instrumenterCache = HashMap<String, ByteArray>()
    private val instrumenterFactoryInstance = JcRuntimeConcolicInstrumenterFactory()

    override val jcClasspath: JcClasspath
        get() = cp

    override fun redefineClass(jClass: Class<*>, asmBody: ClassNode) {

    }

    override fun defineClass(name: String, classNode: ClassNode) =
        defineClass(name, classNode.toByteArray(cp))

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

    private val beforeIfAction: java.util.function.Function<String, Void?> =
        java.util.function.Function { obj: String ->
            println("!!!Hi from beforeIfAction!!!")
            println("I am executing in ${Thread.currentThread().name}")
            println("Info from executor: $obj")
            return@Function null
        }

    private fun initConcolicHelper(type: Class<*>) {
        check(type.typeName == ConcolicHelper::class.java.typeName)

        type.declaredFields.first().get(null)
        // Initializing static fields
        for (field in type.staticFields) {
            when (field.name) {
                ConcolicHelper::beforeIfAction.javaName -> field.setStaticFieldValue(beforeIfAction)
            }
        }
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

        if (name.startsWith("org.usvm.instrumentation.collector.trace.")) return super.loadClass(name)

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
        if(ConcolicHelper::class.java.typeName == jcClass.name)
            return jcClass.bytecode()

        val instrumenter = instrumenterFactoryInstance.create(cp)

        return instrumenterCache.getOrPut(jcClass.name) {
            jcClass.withAsmNode { asmNode ->
                if (asmNode.version < Opcodes.V1_8)
                    return@withAsmNode asmNode.toByteArray(cp)

                val instrumentedClassNode = instrumenter.instrumentClass(asmNode)
                return@withAsmNode instrumentedClassNode.toByteArray(cp, checkClass = true)
            }
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
        if (loadedClass.typeName == ConcolicHelper::class.java.typeName)
            initConcolicHelper(loadedClass)

        return loadedClass
    }
}
