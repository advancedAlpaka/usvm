package org.usvm.instrumentation.instrumentation

import org.jacodb.analysis.library.analyzers.thisInstance
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.instrumentation.collector.trace.ConcolicCollector
import org.usvm.instrumentation.util.getTypename

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
                    is JcRawLocalVar -> createAssignFlagsToLocalVariableCall(lhv)
                    is JcRawArgument -> createAssignFlagsToArgumentCall(lhv)
                    is JcRawArrayAccess -> TODO()
                    is JcRawFieldRef -> TODO()
                    is JcRawConstant, is JcRawThis ->
                        throw IllegalStateException("Variable expected as lhv of assign instruction")
                }

                // TODO: remove listOf call after JacoDB update
                instrumentedInstructionsList.insertAfter(rawJcInstruction, listOf(assignFlagsInstruction))
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

    override fun atMethodStart(jcMethod: JcMethod, instrumentedInstructionsList: JcMutableInstList<JcRawInst>) {
        val instructions = mutableListOf(concolicInfoHelper.createOnEnterCallMethodCall())

        if (!jcMethod.isStatic) {
            val assignFlagsToThisCall = concolicInfoHelper.createAssignFlagsToThisMethodCall(
                JcRawThis(jcMethod.thisInstance.type.getTypename())
            )
            instructions.add(assignFlagsToThisCall)
        }

        for ((index, parameter) in jcMethod.parameters.withIndex()) {
            val assignFlagsToArgumentCall =
                createAssignFlagsToArgumentCall(JcRawArgument.of(index, parameter.name, parameter.type))
            instructions.add(assignFlagsToArgumentCall)
        }

        instrumentedInstructionsList.insertBefore(instrumentedInstructionsList.first(), instructions)
    }

    private fun resolveExpressionFlags(encodedInst: Long, expr: JcRawExpr): List<JcRawInst> {
        return when(expr) {
            is JcRawBinaryExpr -> resolveExpressionFlags(encodedInst, expr.lhv) +
                        resolveExpressionFlags(encodedInst, expr.rhv)
            is JcRawArrayAccess -> resolveExpressionFlags(encodedInst, expr.array) +
                    resolveExpressionFlags(encodedInst, expr.index)

            is JcRawCallExpr -> expr.operands.flatMap { resolveExpressionFlags(encodedInst, it) }
            is JcRawNewArrayExpr -> expr.dimensions.flatMap { resolveExpressionFlags(encodedInst, it) }

            is JcRawCastExpr -> resolveExpressionFlags(encodedInst, expr.operand)
            is JcRawInstanceOfExpr -> resolveExpressionFlags(encodedInst, expr.operand)
            is JcRawLengthExpr -> resolveExpressionFlags(encodedInst, expr.array)
            is JcRawNegExpr -> resolveExpressionFlags(encodedInst, expr.operand)

            is JcRawFieldRef -> TODO()

            is JcRawArgument ->
                listOf(concolicInfoHelper.createApplyFlagsFromArgumentMethodCall(encodedInst, expr.index))
            is JcRawLocalVar ->
                listOf(concolicInfoHelper.createApplyFlagsFromLocalVariableMethodCall(encodedInst, expr.index))
            is JcRawThis ->
                listOf(concolicInfoHelper.createApplyFlagsFromThisMethodCall(encodedInst))

            is JcRawConstant -> emptyList()
            is JcRawNewExpr -> emptyList()
        }
    }

    private fun createAssignFlagsToLocalVariableCall(variable: JcRawLocalVar): JcRawCallInst {
        return if (variable.typeName.isPrimitive)
            concolicInfoHelper.createAssignFlagsToPrimitiveLocalVariableMethodCall(variable.index)
        else
            concolicInfoHelper.createAssignFlagsToReferenceLocalVariableMethodCall(variable.index, variable)
    }

    private fun createAssignFlagsToArgumentCall(parameter: JcRawArgument): JcRawCallInst {
        return if (parameter.typeName.isPrimitive)
            concolicInfoHelper.createAssignFlagsToPrimitiveArgumentMethodCall(parameter.index)
        else
            concolicInfoHelper.createAssignFlagsToReferenceArgumentMethodCall(parameter.index, parameter)
    }

    // TODO: use index property from new version of JacoDB
    private val JcRawLocalVar.index: Int
        get() = name.drop(1).toInt()
}