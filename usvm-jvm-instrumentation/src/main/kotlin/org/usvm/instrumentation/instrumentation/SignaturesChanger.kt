package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.*
import org.jacodb.api.jvm.ext.jcdbName
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class SignaturesChanger {

    companion object {
        val mappings = this.javaClass.classLoader.getResource("mappings")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .map { it.dropLast(1) /* ':' */ }
            .associate { it.split(" -> ").run { get(0) to get(1) } }

        val reverseMappings = mappings.map { (k, v) -> v to k }.toMap()
    }

    private val typeNamesCache = mutableMapOf<TypeName, TypeName>()
    private fun changeSignature(signature: TypeName): TypeName {
        typeNamesCache[signature]?.let { return it }
        if (!needToChange(signature.typeName, isJvmName = false, isReverseMode = false)) {
            return signature
        }

        return TypeNameImpl.fromTypeName(changeSignature(signature.typeName)!!)
            .also { typeNamesCache[signature] = it }
    }

    internal fun changeSignature(signature: String?, isReverseMode: Boolean = false): String? {
        val isJvmName = signature?.contains("/") ?: return null
        if (!needToChange(signature, isJvmName, isReverseMode)) {
            return signature
        }
        var updatedSignature = (if (isJvmName) signature.jcdbName() else signature)
        updatedSignature = (if (isReverseMode) reverseMappings[updatedSignature] else mappings[updatedSignature])
            ?: updatedSignature
        return if (isJvmName) updatedSignature.jvmName() else updatedSignature
    }

    private fun needToChange(signature: String?, isJvmName: Boolean, isReverseMode: Boolean): Boolean {
        if (signature == null) {
            return false
        }
        val normalizedSignature = if (isJvmName) signature.jcdbName() else signature

        return isReverseMode && reverseMappings.containsKey(normalizedSignature) ||
                !isReverseMode && mappings.containsKey(normalizedSignature)
    }

    fun instrumentMethod(jcMethod: JcMethod): Pair<JcMethod, List<JcRawInst>> {
        val processedJcInstructionsList = jcMethod.rawInstList.toMutableList().map { process(it) }
        return process(jcMethod) to processedJcInstructionsList
    }

    private val instructionsCache = mutableMapOf<JcRawInst, JcRawInst>()
    private fun process(inst: JcRawInst): JcRawInst {
        instructionsCache[inst]?.let { return it }
        return when (inst) {
            is JcRawAssignInst -> JcRawAssignInst(
                process(inst.owner),
                process(inst.lhv) as JcRawValue,
                process(inst.rhv) as JcRawExpr
            )

            is JcRawGotoInst -> JcRawGotoInst(
                process(inst.owner),
                inst.target
            )

            is JcRawIfInst -> JcRawIfInst(
                process(inst.owner),
                process(inst.condition) as JcRawConditionExpr,
                inst.trueBranch,
                inst.falseBranch
            )

            is JcRawSwitchInst -> JcRawSwitchInst(
                process(inst.owner),
                process(inst.key) as JcRawValue,
                inst.branches,
                inst.default
            )

            is JcRawCallInst -> JcRawCallInst(
                process(inst.owner),
                process(inst.callExpr) as JcRawCallExpr
            )

            is JcRawCatchInst -> JcRawCatchInst(
                process(inst.owner),
                process(inst.throwable) as JcRawValue,
                inst.handler,
                inst.entries.map { process(it) })

            is JcRawEnterMonitorInst -> JcRawEnterMonitorInst(
                process(inst.owner),
                process(inst.monitor) as JcRawSimpleValue
            )

            is JcRawExitMonitorInst -> JcRawExitMonitorInst(
                process(inst.owner),
                process(inst.monitor) as JcRawSimpleValue
            )

            is JcRawLabelInst -> JcRawLabelInst(
                process(inst.owner),
                inst.name
            )

            is JcRawLineNumberInst -> JcRawLineNumberInst(
                process(inst.owner),
                inst.lineNumber,
                inst.start
            )

            is JcRawReturnInst -> JcRawReturnInst(
                process(inst.owner),
                process(inst.returnValue) as? JcRawValue?,
            )

            is JcRawThrowInst -> JcRawThrowInst(
                process(inst.owner),
                process(inst.throwable) as JcRawValue,
            )
        }.also { instructionsCache[inst] = it }
    }

    private val expressionsCache = mutableMapOf<JcRawExpr?, JcRawExpr?>()
    private fun process(expr: JcRawExpr?): JcRawExpr? {
        expressionsCache[expr]?.let { return it }
        return when (expr) {
            null -> null

            is JcRawArrayAccess -> JcRawArrayAccess(
                process(expr.array) as JcRawValue,
                process(expr.index) as JcRawValue,
                changeSignature(expr.typeName)
            )

            is JcRawFieldRef -> JcRawFieldRef(
                process(expr.instance) as? JcRawValue?,
                changeSignature(expr.declaringClass),
                expr.fieldName,
                changeSignature(expr.typeName)
            )

            is JcRawBool -> JcRawBool(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawByte -> JcRawByte(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawChar -> JcRawChar(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawClassConstant -> JcRawClassConstant(
                changeSignature(expr.className),
                changeSignature(expr.typeName)
            )

            is JcRawDouble -> JcRawDouble(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawFloat -> JcRawFloat(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawInt -> JcRawInt(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawLong -> JcRawLong(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawMethodConstant -> JcRawMethodConstant(
                changeSignature(expr.declaringClass),
                expr.name,
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                changeSignature(expr.typeName)
            )

            is JcRawMethodType -> JcRawMethodType(
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                changeSignature(expr.typeName)
            )

            is JcRawNullConstant -> JcRawNullConstant(
                changeSignature(expr.typeName)
            )

            is JcRawShort -> JcRawShort(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawStringConstant -> JcRawStringConstant(
                expr.value,
                changeSignature(expr.typeName)
            )

            is JcRawArgument -> JcRawArgument(
                expr.index,
                expr.name,
                changeSignature(expr.typeName)
            )

            is JcRawLocalVar -> JcRawLocalVar(
                expr.index,
                expr.name,
                changeSignature(expr.typeName)
            )

            is JcRawThis -> JcRawThis(
                changeSignature(expr.typeName)
            )

            is JcRawDynamicCallExpr -> JcRawDynamicCallExpr(
                process(expr.bsm),
                expr.bsmArgs.map { process(it) },
                expr.callSiteMethodName,
                expr.callSiteArgTypes.map { changeSignature(it) },
                changeSignature(expr.callSiteReturnType),
                expr.callSiteArgs.map { process(it) as JcRawValue }
            )

            is JcRawInterfaceCallExpr -> JcRawInterfaceCallExpr(
                changeSignature(expr.declaringClass),
                expr.methodName,
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                process(expr.instance) as JcRawValue,
                expr.args.map { process(it) as JcRawValue }
            )

            is JcRawSpecialCallExpr -> JcRawSpecialCallExpr(
                changeSignature(expr.declaringClass),
                expr.methodName,
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                process(expr.instance) as JcRawValue,
                expr.args.map { process(it) as JcRawValue }
            )

            is JcRawVirtualCallExpr -> JcRawVirtualCallExpr(
                changeSignature(expr.declaringClass),
                expr.methodName,
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                process(expr.instance) as JcRawValue,
                expr.args.map { process(it) as JcRawValue }
            )

            is JcRawStaticCallExpr -> JcRawStaticCallExpr(
                changeSignature(expr.declaringClass),
                expr.methodName,
                expr.argumentTypes.map { changeSignature(it) },
                changeSignature(expr.returnType),
                expr.args.map { process(it) as JcRawValue }
            )

            is JcRawCastExpr -> JcRawCastExpr(
                changeSignature(expr.typeName),
                process(expr.operand) as JcRawValue
            )

            is JcRawInstanceOfExpr -> JcRawInstanceOfExpr(
                changeSignature(expr.typeName),
                process(expr.operand) as JcRawValue,
                changeSignature(expr.targetType)
            )

            is JcRawLengthExpr -> JcRawLengthExpr(
                changeSignature(expr.typeName),
                process(expr.array) as JcRawValue
            )

            is JcRawNegExpr -> JcRawNegExpr(
                changeSignature(expr.typeName),
                process(expr.operand) as JcRawValue
            )

            is JcRawNewArrayExpr -> JcRawNewArrayExpr(changeSignature(expr.typeName),
                expr.dimensions.map { process(it) as JcRawValue })

            is JcRawNewExpr -> JcRawNewExpr(changeSignature(expr.typeName))

            is JcRawAddExpr -> JcRawAddExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawAndExpr -> JcRawAndExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawCmpExpr -> JcRawCmpExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawCmpgExpr -> JcRawCmpgExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawCmplExpr -> JcRawCmplExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawDivExpr -> JcRawDivExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawEqExpr -> JcRawEqExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawGeExpr -> JcRawGeExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawGtExpr -> JcRawGtExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawLeExpr -> JcRawLeExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawLtExpr -> JcRawLtExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawMulExpr -> JcRawMulExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawNeqExpr -> JcRawNeqExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawOrExpr -> JcRawOrExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawRemExpr -> JcRawRemExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawShlExpr -> JcRawShlExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawShrExpr -> JcRawShrExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawSubExpr -> JcRawSubExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawUshrExpr -> JcRawUshrExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawXorExpr -> JcRawXorExpr(
                changeSignature(expr.typeName),
                process(expr.lhv) as JcRawValue,
                process(expr.rhv) as JcRawValue
            )

            is JcRawBinaryExpr -> error("Unexpected state")
        }.also { expressionsCache[expr] = it }
    }

    private fun process(entry: JcRawCatchEntry): JcRawCatchEntry {
        return JcRawCatchEntry(
            changeSignature(entry.acceptedThrowable),
            entry.startInclusive,
            entry.endExclusive
        )
    }

    private val cashedMethods = mutableMapOf<JcMethod, JcMethod>()
    private fun process(jcMethod: JcMethod): JcMethod {
        cashedMethods[jcMethod]?.let { return it }

        val featuresChain = JcMethodImpl::class.declaredMemberProperties
            .find { it.name == "featuresChain" }
        featuresChain!!.isAccessible = true
        val featuresChainValue = featuresChain.get(jcMethod as JcMethodImpl)

        val methodInfo = JcMethodImpl::class.declaredMemberProperties
            .find { it.name == "methodInfo" }
        methodInfo!!.isAccessible = true
        val methodInfoValue = methodInfo.get(jcMethod)

        return JcMethodImpl(
            processMethodInfo(methodInfoValue as MethodInfo),
            featuresChainValue as JcFeaturesChain,
            jcMethod.enclosingClass
        ).also { cashedMethods[jcMethod] = it }
    }

    private fun processMethodInfo(methodInfo: MethodInfo): MethodInfo {
        return MethodInfo(
            methodInfo.name,
            changeDescriptorSignature(methodInfo.desc),
            methodInfo.signature,
            methodInfo.access,
            methodInfo.annotations,
            methodInfo.exceptions.map { changeSignature(it)!! },
            methodInfo.parametersInfo.map { process(it) },
        )
    }

    private fun changeDescriptorSignature(desc: String): String {
        return """L.+;""".toRegex().replace(desc) { matchResult ->
            changeSignature(matchResult.value)!!
        }
    }

    private fun process(param: ParameterInfo): ParameterInfo {
        return ParameterInfo(
            changeSignature(param.type)!!,
            param.index,
            param.access,
            param.name,
            param.annotations
        )
    }

    private fun process(bsmHandle: BsmHandle): BsmHandle {
        return BsmHandle(
            bsmHandle.tag,
            changeSignature(bsmHandle.declaringClass),
            bsmHandle.name,
            bsmHandle.argTypes.map { changeSignature(it) },
            changeSignature(bsmHandle.returnType),
            bsmHandle.isInterface
        )
    }

    private fun process(bsmArg: BsmArg): BsmArg {
        return when (bsmArg) {
            is BsmIntArg -> bsmArg
            is BsmDoubleArg -> bsmArg
            is BsmFloatArg -> bsmArg
            is BsmHandle -> process(bsmArg)
            is BsmLongArg -> bsmArg
            is BsmMethodTypeArg -> BsmMethodTypeArg(
                bsmArg.argumentTypes.map { changeSignature(it) },
                changeSignature(bsmArg.returnType)
            )

            is BsmStringArg -> bsmArg
            is BsmTypeArg -> BsmTypeArg(
                changeSignature(bsmArg.typeName)
            )
        }
    }
}
