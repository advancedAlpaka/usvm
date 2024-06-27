package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.usvm.instrumentation.collector.trace.ConcolicCollector

class JcRuntimeConcolicInstrumenter(
    override val jcClasspath: JcClasspath
) : JcRuntimeTraceInstrumenter(jcClasspath) {

    override val instrumentConstructors = true

    private val concolicInfoHelper = TraceHelper(jcClasspath, ConcolicCollector::class.java)

    override fun processInstruction(
        encodedInst: Long,
        rawJcInstruction: JcRawInst,
        instrumentedInstructionsList: JcMutableInstList<JcRawInst>
    ) {
        when (rawJcInstruction) {
            is JcRawAssignInst -> {
                val rhv = rawJcInstruction.rhv
                if (rhv is JcRawCallExpr) {
                    instrumentedInstructionsList.insertBefore(
                        rawJcInstruction,
                        concolicInfoHelper.createInitializeNewCallStackFrameMethodCall(rhv.args.size)
                    )
                }

                val processOperandsInstructions = getProcessOperandsInstructions(encodedInst, listOf(rhv))

                if (processOperandsInstructions.isNotEmpty()) {
                    instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)

                    val assignFlagsInstruction = when (val lhv = rawJcInstruction.lhv) {
                        is JcRawLocalVar -> concolicInfoHelper.createAssignToLocalVariableMethodCall(lhv.index)
                        is JcRawArgument -> concolicInfoHelper.createAssignToArgumentMethodCall(lhv.index)
                        is JcRawArrayAccess -> concolicInfoHelper.createAssignToArrayMethodCall(lhv)
                        is JcRawFieldRef -> concolicInfoHelper.createAssignToFieldMethodCall(lhv)
                        is JcRawConstant, is JcRawThis ->
                            throw IllegalStateException("Variable expected as lhv of assign instruction")
                    }

                    instrumentedInstructionsList.insertBefore(rawJcInstruction, assignFlagsInstruction)
                }
            }

            is JcRawCallInst -> {
                instrumentedInstructionsList.insertBefore(
                    rawJcInstruction,
                    concolicInfoHelper.createInitializeNewCallStackFrameMethodCall(rawJcInstruction.callExpr.args.size)
                )

                val processOperandsInstructions = getProcessOperandsInstructions(
                    encodedInst, rawJcInstruction.operands
                )

                instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)
            }

            is JcRawReturnInst -> {
                val processOperandsInstructions = getProcessOperandsInstructions(encodedInst, rawJcInstruction.operands)

                instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)
                instrumentedInstructionsList.insertBefore(
                    rawJcInstruction, concolicInfoHelper.createOnExitFunctionMethodCall()
                )
            }

            is JcRawIfInst,
            is JcRawSwitchInst,
            is JcRawThrowInst,
            is JcRawCatchInst,
            is JcRawEnterMonitorInst,
            is JcRawExitMonitorInst -> {
                val processOperandsInstructions = getProcessOperandsInstructions(encodedInst, rawJcInstruction.operands)
                instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)
            }

            is JcRawGotoInst,
            is JcRawLineNumberInst,
            is JcRawLabelInst -> {}
        }
    }

    override fun atMethodStart(jcMethod: JcMethod, instrumentedInstructionsList: JcMutableInstList<JcRawInst>) {
        val localVariablesSize = jcMethod.rawInstList.asSequence()
            .flatMap { it.operands }.filterIsInstance<JcRawLocalVar>()
            .maxOfOrNull { it.index } ?: -1

        val onEnterFunctionMethodCall = concolicInfoHelper.createOnEnterFunctionMethodCall(localVariablesSize + 1)

        instrumentedInstructionsList.insertBefore(instrumentedInstructionsList.first(), onEnterFunctionMethodCall)
    }

    private fun getProcessOperandsInstructions(encodedInst: Long, operands: List<JcRawExpr>): List<JcRawInst> {
        var concreteArgumentIndex = 0

        fun processExpression(encodedInst: Long, expr: JcRawExpr,
                              isCallReceiver: Boolean = false, callParameterIndex: Int = -1): List<JcRawInst> {
            return when(expr) {
                is JcRawBinaryExpr -> processExpression(encodedInst, expr.lhv) +
                        processExpression(encodedInst, expr.rhv)
                is JcRawArrayAccess -> processExpression(encodedInst, expr.array) +
                        processExpression(encodedInst, expr.index) +
                        concolicInfoHelper.createProcessArrayAccessMethodCall(encodedInst, expr,
                            concreteArgumentIndex++, isCallReceiver, callParameterIndex)

                is JcRawCallExpr -> {
                    val argumentsInstructions = expr.args.withIndex()
                        .flatMap { (index, arg) -> processExpression(encodedInst, arg, callParameterIndex = index) }

                    if (expr is JcRawInstanceExpr && expr !is JcRawSpecialCallExpr) {
                        return processExpression(encodedInst, expr.instance, true) + argumentsInstructions
                    }

                    return argumentsInstructions
                }

                is JcRawNewArrayExpr -> expr.dimensions.flatMap { processExpression(encodedInst, it) }

                is JcRawCastExpr -> processExpression(encodedInst, expr.operand)
                is JcRawInstanceOfExpr -> processExpression(encodedInst, expr.operand)
                is JcRawLengthExpr -> processExpression(encodedInst, expr.array)
                is JcRawNegExpr -> processExpression(encodedInst, expr.operand)

                is JcRawFieldRef ->
                    listOf(concolicInfoHelper.createProcessFieldMethodCall(encodedInst, expr,
                        concreteArgumentIndex++, isCallReceiver, callParameterIndex))
                is JcRawArgument ->
                    listOf(concolicInfoHelper.createProcessArgumentMethodCall(encodedInst, expr.index, expr,
                        concreteArgumentIndex++, isCallReceiver, callParameterIndex))
                is JcRawLocalVar ->
                    listOf(concolicInfoHelper.createProcessLocalVariableMethodCall(encodedInst, expr.index, expr,
                        concreteArgumentIndex++, isCallReceiver, callParameterIndex))
                is JcRawThis ->
                    listOf(concolicInfoHelper.createProcessThisMethodCall(encodedInst, expr,
                        concreteArgumentIndex++, isCallReceiver, callParameterIndex))

                is JcRawConstant, is JcRawNewExpr -> emptyList<JcRawInst>().also { concreteArgumentIndex++ }
            }
        }

        return operands.flatMap { processExpression(encodedInst, it) }
    }

    // TODO: use index property from new version of JacoDB
    private val JcRawLocalVar.index: Int
        get() = name.drop(1).toInt()
}