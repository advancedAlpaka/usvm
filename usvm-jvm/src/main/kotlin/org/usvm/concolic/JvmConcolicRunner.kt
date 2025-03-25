package org.usvm.concolic

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.*
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.JcUnknownField
import org.jacodb.impl.jacodb
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.api.util.JcTestStateResolver
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.instrumentation.JcRuntimeConcolicInstrumenterFactory
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.util.*
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcState
import org.usvm.ps.BfsPathSelector
import org.usvm.util.MemoryScope
import java.io.File

class JvmConcolicRunner(jarPaths: List<String>, private val method: JcMethod) : AutoCloseable {

    private var concreteExecutor: UTestConcreteExecutor
    private var classpath: JcClasspath
    private var applicationGraph: JcApplicationGraph

    init {
        runBlocking {
            val jarFilePaths = jarPaths.map { File(it) }
            val db = jacodb {
                loadByteCode(jarFilePaths)
                installFeatures(InMemoryHierarchy)
                jre = File(InstrumentationModuleConstants.pathToJava)
            }
            classpath = db.classpath(jarFilePaths)
            applicationGraph = JcApplicationGraph(classpath)
            concreteExecutor = UTestConcreteExecutor(
                JcRuntimeConcolicInstrumenterFactory::class,
                jarPaths + InstrumentationModuleConstants.pathToUsvmCollectorsJar,
                classpath,
                InstrumentationModuleConstants.testExecutionTimeout
            )
        }
    }

    private val concreteRunsSelector = BfsPathSelector<JcState>()
    fun runAnalysis(): ConcolicResult = runBlocking {
        val concreteRuns = mutableListOf<ConcreteRun>()
        val symbolicExecutions = mutableListOf<SymbolicExecutionTrace>()

        concreteRunsSelector.add(executeConcolic(generateUTest()!!, concreteRuns, symbolicExecutions))
        while (!concreteRunsSelector.isEmpty()) {
            val nextRunState = concreteRunsSelector.peek().also { concreteRunsSelector.remove(it) }
            val uTest = generateUTest(nextRunState) ?: continue
            val newStates = executeConcolic(uTest, concreteRuns, symbolicExecutions)
            concreteRunsSelector.add(newStates)
        }

        ConcolicResult(concreteRuns, symbolicExecutions)
    }

    private suspend fun executeConcolic(
        test: UTest,
        concreteRuns: MutableList<ConcreteRun>,
        symbolicExecutions: MutableList<SymbolicExecutionTrace>
    ): List<JcState> {
        val concreteResult = executeConcrete(test)
        concreteRuns.add(ConcreteRun(test, concreteResult))

        val (trace, concreteValues) = when (concreteResult) {
            is UTestExecutionSuccessResult -> {
                concreteResult.trace to concreteResult.concreteValues
            }
            is UTestExecutionExceptionResult -> {
                concreteResult.trace to concreteResult.concreteValues
            }
            is UTestExecutionInitFailedResult -> {
                concreteResult.trace to concreteResult.concreteValues
            }
            else -> return emptyList()
        }

        return if (!symbolicExecutions.any { it.startsWith(trace!!) }) {
            symbolicExecutions.add(SymbolicExecutionTrace(trace!!, concreteValues!!))
            executeSymbolic(trace, concreteValues)
        } else {
            emptyList()
        }
    }

    private suspend fun executeConcrete(uTest: UTest): UTestExecutionResult {
        concreteExecutor.ensureRunnerAlive()
        return concreteExecutor.executeAsync(uTest)
    }

    private fun executeSymbolic(
        concolicTrace: List<JcInst>,
        concreteValues: List<Map<Int, UTestValueDescriptor>>
    ): List<JcState> {
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.DFS),
            stopOnTargetsReached = true,
        )
        val machine = JcMachine(classpath, options,
            concolicTrace = concolicTrace.toMutableList(), concreteValues = concreteValues.toMutableList())

        return machine.use { it.analyze(method, listOf(object : JcTarget(concolicTrace.last()) {})) }
    }

    private val alreadyRanStates = mutableSetOf<JcState>()
    private val alreadyGeneratedTests = mutableSetOf<UTest>()
    private fun generateUTest(state: JcState? = null): UTest? {
        if (state != null && !alreadyRanStates.add(state)) {
            return null
        }

        if (method.isStatic) {
            val allocateArgumentsFlagsStack = UTestArraySetStatement(
                UTestGetStaticFieldExpression(
                    JcUnknownField(
                        JcUnknownClass(classpath, "org.usvm.instrumentation.collector.trace.ConcolicCollector"),
                        "argumentsFlagsStack",
                        classpath.arrayTypeOf(classpath.arrayTypeOf(classpath.byte)).getTypename()
                    )
                ),
                UTestIntExpression(0, classpath.int),
                UTestCreateArrayExpression(classpath.byte,
                    UTestIntExpression(
                        method.parameters.size,
                        classpath.int
                    )
                )
            )

            val initializeArgumentsFlagsStack = (0..<method.parameters.size).map {
                UTestArraySetStatement(
                    UTestArrayGetExpression(
                        UTestGetStaticFieldExpression(
                            JcUnknownField(
                                JcUnknownClass(classpath, "org.usvm.instrumentation.collector.trace.ConcolicCollector"),
                                "argumentsFlagsStack",
                                classpath.arrayTypeOf(classpath.arrayTypeOf(classpath.byte)).getTypename()
                            )
                        ),
                        UTestIntExpression(0, classpath.int)
                    ),
                    UTestIntExpression(it, classpath.int),
                    UTestByteExpression(1, classpath.byte)
                )
            }

            val concolicCollectorInitialization = listOf(allocateArgumentsFlagsStack) + initializeArgumentsFlagsStack
            val uTest = if (state != null) {
                val generatedTest = MemoryScope(state.ctx, state.models.first(), state.memory,
                    with(applicationGraph) { method.typed })
                    .withMode(JcTestStateResolver.ResolveMode.MODEL) { (this as MemoryScope).createUTest() }
                UTest(
                    concolicCollectorInitialization + generatedTest.initStatements,
                    generatedTest.callMethodExpression
                )
            } else {
                val args = generateInitialArgs()
                UTest(concolicCollectorInitialization, UTestStaticMethodCall(method, args))
            }

            return if (alreadyGeneratedTests.add(uTest)) uTest else null
        }
        TODO("support instance methods")
    }

    private fun generateInitialArgs(): List<UTestExpression> {
        // TODO receiver for instance methods
        return method.parameters.mapIndexed { _, param ->
            when (val jcType = param.type.toJcType(classpath)!!) {
                classpath.int -> UTestIntExpression(0, jcType)
                classpath.long -> UTestLongExpression(0, jcType)
                classpath.float -> UTestFloatExpression(0.0f, jcType)
                classpath.double -> UTestDoubleExpression(0.0, jcType)
                classpath.short -> UTestShortExpression(0, jcType)
                classpath.byte -> UTestByteExpression(0, jcType)
                classpath.char -> UTestCharExpression('0', jcType)
                else -> UTestAllocateMemoryCall(jcType.toJcClass()!!)
            }
        }
    }

    override fun close() {
        concreteExecutor.close()
    }
}