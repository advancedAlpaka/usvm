package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.*
import org.usvm.instrumentation.collector.trace.ConcolicCollector

class JcRuntimeConcolicInstrumenter(
    override val jcClasspath: JcClasspath
) : JcRuntimeTraceInstrumenter(jcClasspath) {

    private val concolicInfoHelper = TraceHelper(jcClasspath, ConcolicCollector::class.java)

    override fun processInstruction(
        encodedInst: Long,
        rawJcInstruction: JcRawInst,
        instrumentedInstructionsList: JcMutableInstList<JcRawInst>
    ) {
        when (rawJcInstruction) {
            is JcRawAssignInst -> {
                val instructionsForExpressionFlags = resolveExpressionFlags(encodedInst, rawJcInstruction.rhv)
                instrumentedInstructionsList.insertBefore(rawJcInstruction, instructionsForExpressionFlags)

                val assignFlagsInstruction = when (val lhv = rawJcInstruction.lhv) {
                    is JcRawLocalVar -> concolicInfoHelper.createAssignFlagsToLocalVariableMethodCall(lhv.index)
                    is JcRawArgument -> concolicInfoHelper.createAssignFlagsToArgumentMethodCall(lhv.index)
                    is JcRawArrayAccess -> TODO()
                    is JcRawFieldRef -> concolicInfoHelper.createAssignFlagsToFieldMethodCall(lhv)
                    is JcRawConstant, is JcRawThis ->
                        throw IllegalStateException("Variable expected as lhv of assign instruction")
                }

                instrumentedInstructionsList.insertBefore(rawJcInstruction, assignFlagsInstruction)

                if (rawJcInstruction.rhv is JcRawCallExpr) {
                    instrumentedInstructionsList.insertBefore(
                        rawJcInstruction,
                        concolicInfoHelper.createOnEnterCallMethodCall()
                    )
                }
            }
            is JcRawReturnInst -> {
                // TODO also process instruction operands
                val onExitCall = concolicInfoHelper.createOnExitCallMethodCall()
                instrumentedInstructionsList.insertBefore(rawJcInstruction, onExitCall)
            }
            else -> {
                // TODO
            }
        }
    }

    private fun resolveExpressionFlags(encodedInst: Long, expr: JcRawExpr,
                                       isThisArgument: Boolean = false, parameterIndex: Int = -1): List<JcRawInst> {
        return when(expr) {
            is JcRawBinaryExpr -> resolveExpressionFlags(encodedInst, expr.lhv) +
                        resolveExpressionFlags(encodedInst, expr.rhv)
            is JcRawArrayAccess -> resolveExpressionFlags(encodedInst, expr.array) +
                    resolveExpressionFlags(encodedInst, expr.index)

            is JcRawInstanceExpr -> resolveExpressionFlags(encodedInst, expr.instance, true) +
                    expr.args.withIndex()
                        .flatMap { (index, arg) -> resolveExpressionFlags(encodedInst, arg, parameterIndex = index) }
            is JcRawCallExpr -> expr.args.withIndex()
                .flatMap { (index, arg) -> resolveExpressionFlags(encodedInst, arg, parameterIndex = index) }
            is JcRawNewArrayExpr -> expr.dimensions.flatMap { resolveExpressionFlags(encodedInst, it) }

            is JcRawCastExpr -> resolveExpressionFlags(encodedInst, expr.operand)
            is JcRawInstanceOfExpr -> resolveExpressionFlags(encodedInst, expr.operand)
            is JcRawLengthExpr -> resolveExpressionFlags(encodedInst, expr.array)
            is JcRawNegExpr -> resolveExpressionFlags(encodedInst, expr.operand)

            is JcRawFieldRef ->
                listOf(concolicInfoHelper.createApplyFlagsFromFieldMethodCall(encodedInst, expr,
                    isThisArgument, parameterIndex))
            is JcRawArgument ->
                listOf(concolicInfoHelper.createApplyFlagsFromArgumentMethodCall(encodedInst, expr.index,
                    isThisArgument, parameterIndex))
            is JcRawLocalVar ->
                listOf(concolicInfoHelper.createApplyFlagsFromLocalVariableMethodCall(encodedInst, expr.index,
                    isThisArgument, parameterIndex))
            is JcRawThis ->
                listOf(concolicInfoHelper.createApplyFlagsFromThisMethodCall(encodedInst, isThisArgument,
                    parameterIndex))

            is JcRawConstant -> emptyList()
            is JcRawNewExpr -> emptyList()
        }
    }

    // TODO: use index property from new version of JacoDB
    private val JcRawLocalVar.index: Int
        get() = name.drop(1).toInt()
}