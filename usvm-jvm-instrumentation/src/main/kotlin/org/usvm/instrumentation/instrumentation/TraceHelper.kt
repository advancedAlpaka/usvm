package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.cfg.*
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.util.get
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

    fun createResizeFlagsStacksMethodCall() =
        createStaticCall("resizeFlagsStacks")

    fun createResizeSymbolicInstructionsTraceMethodCall() =
        createStaticCall("resizeSymbolicInstructionsTrace")

    fun createResizeStaticFieldsFlagsMethodCall() =
        createStaticCall("resizeStaticFieldsFlags")

    fun createBoxValueCall(value: JcRawValue): JcRawStaticCallExpr? {
        val (javaClass, typeName) = when (value.typeName.typeName) {
            PredefinedPrimitives.Byte -> java.lang.Byte::class.java to jcClasspath.byte.getTypename()

            PredefinedPrimitives.Char -> java.lang.Character::class.java to jcClasspath.char.getTypename()

            PredefinedPrimitives.Short -> java.lang.Short::class.java to jcClasspath.short.getTypename()

            PredefinedPrimitives.Int -> java.lang.Integer::class.java to jcClasspath.int.getTypename()

            PredefinedPrimitives.Long -> java.lang.Long::class.java to jcClasspath.long.getTypename()

            PredefinedPrimitives.Float -> java.lang.Float::class.java to jcClasspath.float.getTypename()

            PredefinedPrimitives.Double -> java.lang.Double::class.java to jcClasspath.double.getTypename()

            PredefinedPrimitives.Boolean -> java.lang.Boolean::class.java to jcClasspath.boolean.getTypename()

            else -> return null
        }

        val javaClassTypename = jcClasspath[javaClass]!!.typename
        return JcRawStaticCallExpr(
            javaClassTypename,
            "valueOf",
            listOf(typeName),
            javaClassTypename,
            listOf(value)
        )
    }

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

    fun createStaticFieldRef(fieldName: String, fieldType: TypeName): JcRawFieldRef {
        return JcRawFieldRef(null, jcVirtualGlobalObjectClass.typename, fieldName, fieldType)
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
}