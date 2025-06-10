package org.usvm.util


import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.usvm.api.util.JcTestStateResolver
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.machine.JcContext
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase

/**
 * An actual class for resolving objects from [UExpr]s.
 *
 * @param model a model to which compose expressions.
 * @param finalStateMemory a read-only memory to read [ULValue]s from.
 */
class MemoryScope(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcTestStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {

    override val decoderApi = JcTestExecutorDecoderApi(ctx)

    fun createUTest(): UTest {
        val thisInstance = resolveThisInstance()
        val parameters = resolveParameters()

        resolveStatics()

        val initStmts = decoderApi.initializerInstructions()

        val callExpr = if (method.isStatic) {
            UTestStaticMethodCall(method.method, parameters)
        } else {
            UTestMethodCall(thisInstance, method.method, parameters)
        }

        return UTest(initStmts, callExpr)
    }

    override fun allocateClassInstance(type: JcClassType): UTestExpression =
        UTestAllocateMemoryCall(type.jcClass)

    // todo: looks incorrect
    override fun allocateString(value: UTestExpression): UTestExpression = value
}