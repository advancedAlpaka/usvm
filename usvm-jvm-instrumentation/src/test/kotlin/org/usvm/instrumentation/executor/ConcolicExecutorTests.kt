package org.usvm.instrumentation.executor

import example.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.jacodb
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.concolic.ConcolicResult
import org.usvm.concolic.ConcreteRun
import org.usvm.concolic.JvmConcolicRunner
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.*
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import java.io.File
import kotlin.test.assertEquals

val logger = KotlinLogging.logger { }

class ConcolicExecutorTests {

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
    fun `lol test`() {
        val jcClass = jcClasspath.findClass<ConcolicTests>()
        val jcMethod = jcClass.declaredMethods.find { it.name == "lol" }!!

        val result = JvmConcolicRunner(testJarPath, jcMethod).use { it.runAnalysis() }
        result.printStatistics()

        result.hasIntResult(0)
        result.hasIntResult(5)

        // TODO("Must be 3, not 4, investigate")
        assertEquals(4, result.concreteRuns.size)
        assertEquals(2, result.symbolicExecutions.size)
    }

    @Test
    fun `lolD test`() {
        val jcClass = jcClasspath.findClass<ConcolicTests>()
        val jcMethod = jcClass.declaredMethods.find { it.name == "lolD" }!!

         val result = JvmConcolicRunner(testJarPath, jcMethod).use { it.runAnalysis() }
        result.printStatistics()

        result.hasIntResult(0)
        result.hasIntResult(5)
        result.hasExceptionResult("Cannot read field \"d\" because \"<parameter1>\" is null")

        // TODO("Must be 5, not 6, investigate")
        assertEquals(6, result.concreteRuns.size)
        assertEquals(2, result.symbolicExecutions.size)
    }

    @Test
    fun `lolol test`() {
        val jcClass = jcClasspath.findClass<ConcolicTests>()
        val jcMethod = jcClass.declaredMethods.find { it.name == "lolol" }!!

        val result = JvmConcolicRunner(testJarPath, jcMethod).use { it.runAnalysis() }
        result.printStatistics()

        result.hasIntResult(3)
        result.hasIntResult(4)
        result.hasIntResult(2)
        result.hasIntResult(31)

        assertEquals(5, result.concreteRuns.size)
        assertEquals(2, result.symbolicExecutions.size)
    }

    private fun ConcolicResult.hasIntResult(value: Int) {
        assert(concreteRuns.any {
            val result = it.result as? UTestExecutionSuccessResult
            val resultValue = result?.result as? UTestConstantDescriptor.Int
            resultValue?.value == value
        })
    }

    private fun ConcolicResult.hasExceptionResult(message: String) {
        assert(concreteRuns.any {
            val result = it.result as? UTestExecutionExceptionResult
            result?.cause?.message?.contains(message) == true
        })
    }

    private fun ConcolicResult.printStatistics() {
        println(
            buildString {
                appendLine()
                append(concreteRuns.size)
                appendLine(" concrete runs: ")
                concreteRuns.forEach {
                    append("    ")
                    append('(')
                    append(it.test.callMethodExpression.args.joinToString(separator = ", ") { arg -> it.stringify(arg) })
                    append(')')
                    append(" => ")
                    when (val result = it.result) {
                        is UTestExecutionSuccessResult -> {
                            append(stringify(result.result))
                        }
                        is UTestExecutionExceptionResult -> append("Exception: ${result.cause.message}")
                        is UTestExecutionFailedResult -> TODO()
                        is UTestExecutionInitFailedResult -> TODO()
                        is UTestExecutionTimedOutResult -> append("timeout")
                    }
                    append(System.lineSeparator())
                }

                append("with ")
                append(symbolicExecutions.size)
                appendLine(" symbolic executions")
            }
        )
    }

    private fun ConcreteRun.stringify(testExpr: UTestExpression): String {
        return when (testExpr) {
            is UTestIntExpression -> testExpr.value.toString()
            is UTestAllocateMemoryCall -> "new ${testExpr.clazz.simpleName}(${test.initStatements
                .filterIsInstance<UTestSetFieldStatement>()
                .filter { f -> f.instance == testExpr }
                .joinToString(separator = ",") { f -> "${f.field.name} = ${stringify(f.value)}" }})"
            is UTestNullExpression -> "null"
            is UTestArithmeticExpression -> TODO()
            is UTestArrayGetExpression -> TODO()
            is UTestArrayLengthExpression -> TODO()
            is UTestBinaryConditionExpression -> TODO()
            is UTestConstructorCall -> TODO()
            is UTestMethodCall -> TODO()
            is UTestStaticMethodCall -> TODO()
            is UTestCastExpression -> TODO()
            is UTestClassExpression -> TODO()
            is UTestBooleanExpression -> TODO()
            is UTestByteExpression -> TODO()
            is UTestCharExpression -> TODO()
            is UTestDoubleExpression -> TODO()
            is UTestFloatExpression -> TODO()
            is UTestLongExpression -> TODO()
            is UTestShortExpression -> TODO()
            is UTestStringExpression -> TODO()
            is UTestCreateArrayExpression -> "[${testExpr.elementType.typeName}:${(testExpr.size as UTestIntExpression).value}]"
            is UTestGetFieldExpression -> TODO()
            is UTestGetStaticFieldExpression -> TODO()
            is UTestGlobalMock -> TODO()
            is UTestMockObject -> TODO()
        }
    }

    private fun stringify(valueDescriptor: UTestValueDescriptor?): String {
        return when (valueDescriptor) {
            is UTestConstantDescriptor.Int -> valueDescriptor.value.toString()
            is UTestArrayDescriptor -> "[${valueDescriptor.value.joinToString { stringify(it) }}]"
            is UTestClassDescriptor -> TODO()
            is UTestConstantDescriptor.Boolean -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Byte -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Char -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Double -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Float -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Long -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.Null -> "null"
            is UTestConstantDescriptor.Short -> valueDescriptor.value.toString()
            is UTestConstantDescriptor.String -> valueDescriptor.value
            is UTestCyclicReferenceDescriptor -> TODO()
            is UTestEnumValueDescriptor -> TODO()
            is UTestExceptionDescriptor -> TODO()
            is UTestObjectDescriptor -> "${valueDescriptor.type.typeName}{${valueDescriptor.fields.toList().joinToString { 
                "${it.first.name} = ${stringify(it.second)}" 
            }}}"
            null -> "null"
        }
    }
}