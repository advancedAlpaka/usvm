package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.cfg.*
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.typename
import java.lang.reflect.Method

class TraceHelper(
    private val jcClasspath: JcClasspath,
    globalObjectJClass: Class<*>
) {

    private val jcVirtualGlobalObjectClass =
        JcVirtualClassImpl(
            name = globalObjectJClass.name,
            access = globalObjectJClass.modifiers,
            initialFields = listOf(),
            initialMethods = globalObjectJClass.declaredMethods.map { createJcVirtualMethod(it) }
        ).also { it.classpath = jcClasspath }

    //We need virtual method to insert it invocation in instrumented instruction list
    private fun createJcVirtualMethod(jMethod: Method): JcVirtualMethod = JcVirtualMethodImpl(
        name = jMethod.name,
        access = jMethod.modifiers,
        returnType = TypeNameImpl(jMethod.returnType.name),
        parameters = createJcVirtualMethodParams(jMethod),
        description = ""
    )

    private fun createJcVirtualMethodParams(jMethod: Method): List<JcVirtualParameter> =
        jMethod.parameters.mapIndexed { i, p -> JcVirtualParameter(i, TypeNameImpl(p.type.typeName)) }

    /**
     * This method create instrumenting method call to insert it in instruction list
     * @param jcInstId --- Encoded instruction (see JcInstructionTracer.encode)
     * @param traceMethodName --- jacodb method name for instrumenting
     */
    fun createTraceMethodCall(jcInstId: Long, traceMethodName: String): JcRawCallInst {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        return JcRawCallInst(jcTraceMethod, createStaticExprWithLongArg(jcInstId, jcTraceMethod))
    }

    fun createMockCollectorCall(traceMethodName: String, id: Long, jcThisReference: JcRawValue): JcRawStaticCallExpr {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == traceMethodName }!!
        val jcRawLong = JcRawLong(id)
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(jcClasspath.long.getTypename(), jcClasspath.objectType.getTypename()),
            returnType = jcTraceMethod.returnType,
            args = listOf(jcRawLong, jcThisReference)
        )
    }

    fun createMockCollectorIsInExecutionCall(): JcRawStaticCallExpr {
        val jcTraceMethod = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == "isInExecution" }!!
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(),
            returnType = jcTraceMethod.returnType,
            args = listOf()
        )
    }

    fun createInitializeNewCallStackFrameMethodCall(argumentsNum: Int) =
        createStaticCall("initializeNewCallStackFrame", JcRawInt(argumentsNum))

    fun createOnEnterFunctionMethodCall(localVariablesNum: Int) =
        createStaticCall("onEnterFunction", JcRawInt(localVariablesNum))

    fun createOnExitFunctionMethodCall() = createStaticCall("onExitFunction")

    fun createProcessLocalVariableMethodCall(
        jcInstId: Long, variableIndex: Int, variable: JcRawLocalVar,
        concreteArgumentIndex: Int, isCallReceiver: Boolean, callParameterIndex: Int
    ): JcRawCallInst {
        val arguments = arrayOf(
            JcRawLong(jcInstId), JcRawInt(variableIndex), variable,
            JcRawInt(concreteArgumentIndex), JcRawBool(isCallReceiver), JcRawInt(callParameterIndex)
        )

        val methodName = when (variable.typeName.typeName) {
            PredefinedPrimitives.Byte -> "processByteLocalVariable"
            PredefinedPrimitives.Short -> "processShortLocalVariable"
            PredefinedPrimitives.Int -> "processIntLocalVariable"
            PredefinedPrimitives.Long -> "processLongLocalVariable"
            PredefinedPrimitives.Float -> "processFloatLocalVariable"
            PredefinedPrimitives.Double -> "processDoubleLocalVariable"
            PredefinedPrimitives.Char -> "processCharLocalVariable"
            PredefinedPrimitives.Boolean -> "processBooleanLocalVariable"
            else -> "processLocalVariable"
        }
        return createStaticCall(methodName, *arguments)
    }

    fun createProcessArgumentMethodCall(
        jcInstId: Long, argumentIndex: Int, argument: JcRawArgument,
        concreteArgumentIndex: Int, isCallReceiver: Boolean, callParameterIndex: Int
    ): JcRawCallInst {
        val arguments = arrayOf(
            JcRawLong(jcInstId), JcRawInt(argumentIndex), argument,
            JcRawInt(concreteArgumentIndex), JcRawBool(isCallReceiver), JcRawInt(callParameterIndex)
        )

        val methodName = when (argument.typeName.typeName) {
            PredefinedPrimitives.Byte -> "processByteArgument"
            PredefinedPrimitives.Short -> "processShortArgument"
            PredefinedPrimitives.Int -> "processIntArgument"
            PredefinedPrimitives.Long -> "processLongArgument"
            PredefinedPrimitives.Float -> "processFloatArgument"
            PredefinedPrimitives.Double -> "processDoubleArgument"
            PredefinedPrimitives.Char -> "processCharArgument"
            PredefinedPrimitives.Boolean -> "processBooleanArgument"
            else -> "processArgument"
        }
        return createStaticCall(methodName, *arguments)
    }

    fun createProcessThisMethodCall(
        jcInstId: Long, thisValue: JcRawThis,
        concreteArgumentIndex: Int, isCallReceiver: Boolean, callParameterIndex: Int
    ) = createStaticCall(
        "processThis", JcRawLong(jcInstId), thisValue,
        JcRawInt(concreteArgumentIndex), JcRawBool(isCallReceiver), JcRawInt(callParameterIndex)
    )

    fun createProcessFieldMethodCall(
        jcInstId: Long, fieldRef: JcRawFieldRef,
        concreteArgumentIndex: Int, isCallReceiver: Boolean, callParameterIndex: Int
    ): JcRawCallInst {
        val arguments = arrayOf(
            JcRawLong(jcInstId), fieldRef.instance ?: JcRawNull(), fieldRef.fieldId, fieldRef,
            JcRawInt(concreteArgumentIndex), JcRawBool(isCallReceiver), JcRawInt(callParameterIndex)
        )

        val methodName = when (fieldRef.typeName.typeName) {
            PredefinedPrimitives.Byte -> "processByteField"
            PredefinedPrimitives.Short -> "processShortField"
            PredefinedPrimitives.Int -> "processIntField"
            PredefinedPrimitives.Long -> "processLongField"
            PredefinedPrimitives.Float -> "processFloatField"
            PredefinedPrimitives.Double -> "processDoubleField"
            PredefinedPrimitives.Char -> "processCharField"
            PredefinedPrimitives.Boolean -> "processBooleanField"
            else -> "processField"
        }
        return createStaticCall(methodName, *arguments)
    }

    fun createProcessArrayAccessMethodCall(
        jcInstId: Long, arrayAccess: JcRawArrayAccess,
        concreteArgumentIndex: Int, isCallReceiver: Boolean, callParameterIndex: Int
    ): JcRawCallInst {
        val arguments = arrayOf(
            JcRawLong(jcInstId), arrayAccess.array, arrayAccess.index, arrayAccess,
            JcRawInt(concreteArgumentIndex), JcRawBool(isCallReceiver), JcRawInt(callParameterIndex)
        )

        val methodName = when (arrayAccess.typeName.typeName) {
            PredefinedPrimitives.Byte -> "processByteArrayAccess"
            PredefinedPrimitives.Short -> "processShortArrayAccess"
            PredefinedPrimitives.Int -> "processIntArrayAccess"
            PredefinedPrimitives.Long -> "processLongArrayAccess"
            PredefinedPrimitives.Float -> "processFloatArrayAccess"
            PredefinedPrimitives.Double -> "processDoubleArrayAccess"
            PredefinedPrimitives.Char -> "processCharArrayAccess"
            PredefinedPrimitives.Boolean -> "processBooleanArrayAccess"
            else -> "processArrayAccess"
        }
        return createStaticCall(methodName, *arguments)
    }

    fun createAssignToLocalVariableMethodCall(variableIndex: Int) =
        createStaticCall("assignToLocalVariable", JcRawInt(variableIndex))

    fun createAssignToArgumentMethodCall(argumentIndex: Int) =
        createStaticCall("assignToArgument", JcRawInt(argumentIndex))

    fun createAssignToFieldMethodCall(fieldRef: JcRawFieldRef) =
        createStaticCall("assignToField", fieldRef.instance ?: JcRawNull(), fieldRef.fieldId)

    fun createAssignToArrayMethodCall(arrayAccess: JcRawArrayAccess) =
        createStaticCall("assignToArray", arrayAccess.array, arrayAccess.index)

    fun createStaticExprWithLongArg(arg: Long, jcTraceMethod: JcVirtualMethod): JcRawStaticCallExpr {
        val argAsJcConst = JcRawLong(arg)
        return JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = jcTraceMethod.name,
            argumentTypes = listOf(jcClasspath.long.getTypename()),
            returnType = jcTraceMethod.returnType,
            args = listOf(argAsJcConst)
        )
    }

    private fun createStaticCall(methodName: String, vararg args: JcRawValue): JcRawCallInst {
        val method = jcVirtualGlobalObjectClass.declaredMethods.find { it.name == methodName }!!
        val callExpr = JcRawStaticCallExpr(
            declaringClass = jcVirtualGlobalObjectClass.typename,
            methodName = method.name,
            argumentTypes = method.parameters.map { it.type },
            returnType = method.returnType,
            args = args.toList()
        )

        return JcRawCallInst(method, callExpr)
    }

    private val JcRawFieldRef.fieldId: JcRawInt
        get() = JcRawInt(JcConcolicTracer.encodeField(this, jcClasspath))
}