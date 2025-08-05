package org.usvm.instrumentation.executor

import example.ConcolicTests
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.concolic.JvmConcolicRunner
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import kotlin.use

class UTestExecutorTests {
    companion object {
        lateinit var testJarPath: List<String>
        lateinit var jcClasspath: JcClasspath

        @BeforeAll
        @JvmStatic
        fun init() = runBlocking {
            testJarPath = listOf("build/libs/usvm-jvm-instrumentation-test.jar", "build/libs/usvm-jvm-instrumentation-collectors.jar")
            val cp = testJarPath.map { File(it) }
            val db = jacodb {
                loadByteCode(cp)
                installFeatures(InMemoryHierarchy)
                jre = File(InstrumentationModuleConstants.pathToJava)
            }
            jcClasspath = db.classpath(cp)
        }
    }

    @Test
    fun `if test`() {
        val jcClass = jcClasspath.findClass<ConcolicTests>()
        val jcMethod = jcClass.declaredMethods.find { it.name == "lolol" }!!

        val result = JvmConcolicRunner(testJarPath, jcMethod).use { it.runAnalysis() }

        println("Hi from end!")
    }
}