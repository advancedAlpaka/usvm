package org.usvm.instrumentation.rd

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.classloader.JcConcreteMemoryClassLoader
import org.usvm.instrumentation.classloader.MetaClassLoader
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.instrumentation.*
import org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.*
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.URLClassPathLoader
import java.lang.Exception

class UTestExecutor(
    private val jcClasspath: JcClasspath,
    private val tracer: Tracer<*>
) {

    private var metaClassLoader : MetaClassLoader = createMetaClassLoader()
    private var initStateDescriptorBuilder = Value2DescriptorConverter(
        workerClassLoader = metaClassLoader,
        previousState = null
    )
    private var staticDescriptorsBuilder = StaticDescriptorsBuilder(
        workerClassLoader = metaClassLoader,
        initialValue2DescriptorConverter = initStateDescriptorBuilder
    )
    private var mockHelper = MockHelper(
        jcClasspath = jcClasspath,
        classLoader = metaClassLoader
    )

    private fun createMetaClassLoader() : MetaClassLoader {
        JcConcreteMemoryClassLoader.cp = jcClasspath
        return JcConcreteMemoryClassLoader
    }

    private fun reset() {
        initStateDescriptorBuilder = Value2DescriptorConverter(
            workerClassLoader = metaClassLoader,
            previousState = null
        )
        staticDescriptorsBuilder.setClassLoader(metaClassLoader)
        staticDescriptorsBuilder.setInitialValue2DescriptorConverter(initStateDescriptorBuilder)
        tracer.reset()
        MockCollector.mocks.clear()
    }

    fun executeUTest(uTest: UTest): UTestExecutionResult {
        reset()
        val callMethodExpr = uTest.callMethodExpression

        val executor = UTestExpressionExecutor(metaClassLoader, mockHelper)
        val initStmts = (uTest.initStatements + listOf(callMethodExpr.instance) + callMethodExpr.args).filterNotNull()
        executor.executeUTestInsts(initStmts)
            ?.onFailure {
                return UTestExecutionInitFailedResult(
                    cause = buildExceptionDescriptor(
                        builder = initStateDescriptorBuilder,
                        exception = it,
                        raisedByUserCode = false
                    ),
                    trace = tracer.getTrace().trace,
                    concreteValues = null
                )
            }
        val initExecutionState = buildExecutionState(
            callMethodExpr = callMethodExpr,
            executor = executor,
            descriptorBuilder = initStateDescriptorBuilder,
        )

        val methodInvocationResult =
            executor.executeUTestInst(callMethodExpr)
        val resultStateDescriptorBuilder =
            Value2DescriptorConverter(metaClassLoader, initStateDescriptorBuilder)
        val unpackedInvocationResult =
            when {
                methodInvocationResult.isFailure -> methodInvocationResult.exceptionOrNull()
                else -> methodInvocationResult.getOrNull()
            }

        val trace = tracer.getTrace()
        if (unpackedInvocationResult is Throwable) {
            val resultExecutionState =
                buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder)
            return UTestExecutionExceptionResult(
                cause = buildExceptionDescriptor(
                    builder = resultStateDescriptorBuilder,
                    exception = unpackedInvocationResult,
                    raisedByUserCode = methodInvocationResult.isSuccess
                ),
                trace = tracer.getTrace().trace,
                concreteValues = trace.getConcreteValues(resultStateDescriptorBuilder),
                initialState = initExecutionState,
                resultState = resultExecutionState
            )
        }

        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder.buildDescriptorResultFromAny(unpackedInvocationResult, callMethodExpr.type)
                .getOrNull()
        val resultExecutionState =
            buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder)

        initExecutionState.statics.keys.forEach { initExecutionState.statics.remove(it) }
        if (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy == StaticsRollbackStrategy.ROLLBACK) {
            staticDescriptorsBuilder.rollBackStatics()
        }

        return UTestExecutionSuccessResult(
            trace.trace, trace.getConcreteValues(resultStateDescriptorBuilder),
            methodInvocationResultDescriptor, initExecutionState, resultExecutionState
        )
    }

    private fun Trace.getConcreteValues(
        descriptorBuilder: Value2DescriptorConverter
    ): List<Map<Int, UTestValueDescriptor>>? {
        if (this !is ConcolicTrace) return null

        return symbolicInstructionsTrace.map {
            it.concreteArguments.entries.associate { (k, v) -> k to
                    descriptorBuilder.buildDescriptorResultFromAny(v, null).getOrThrow()
            }
        }
    }

    private fun buildExceptionDescriptor(
        builder: Value2DescriptorConverter,
        exception: Throwable,
        raisedByUserCode: Boolean
    ): UTestExceptionDescriptor {
        val descriptor =
            builder.buildDescriptorResultFromAny(any = exception, type = null).getOrNull() as? UTestExceptionDescriptor
        return descriptor
            ?.also { it.raisedByUserCode = raisedByUserCode }
            ?: UTestExceptionDescriptor(
                type = jcClasspath.findClassOrNull(exception::class.java.name)?.toType()
                    ?: jcClasspath.findClass<Exception>().toType(),
                message = exception.message ?: "message_is_null",
                stackTrace = listOf(),
                raisedByUserCode = raisedByUserCode
            )
    }

    private fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
     ): UTestExecutionState = with(descriptorBuilder) {
        uTestExecutorCache.addAll(executor.objectToInstructionsCache)
        val instanceDescriptor = callMethodExpr.instance?.let {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val argsDescriptors = callMethodExpr.args.map {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val isInit = previousState == null
        val statics = if (isInit) {
            val descriptorsForInitializedStatics =
                staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(emptySet(), descriptorBuilder).getOrThrow()
            staticDescriptorsBuilder.builtInitialDescriptors.plus(descriptorsForInitializedStatics)
                .filter { it.value != null }
                .mapValues { it.value!! }
        } else {
            staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(emptySet(), descriptorBuilder).getOrThrow()
        }
        return UTestExecutionState(instanceDescriptor, argsDescriptors, statics.toMutableMap())
    }
}