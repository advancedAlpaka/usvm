package org.usvm.instrumentation.instrumentation

import org.jacodb.api.*
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcMutableInstList
import org.jacodb.api.jvm.cfg.*
import org.jacodb.api.jvm.cfg.JcRawCallExpr
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.ext.*
import org.jacodb.impl.cfg.*
import org.jacodb.impl.cfg.util.isPrimitive
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.instrumentation.collector.trace.ConcolicCollector
import org.usvm.instrumentation.util.getTypename

class JcRuntimeConcolicInstrumenter(
    override val jcClasspath: JcClasspath
) : JcRuntimeTraceInstrumenter(jcClasspath, JcConcolicTracer) {

    override val instrumentConstructors = true

    private val concolicInfoHelper = TraceHelper(jcClasspath, ConcolicCollector::class.java)

    private lateinit var owner: JcMethod

    private val byte = jcClasspath.byte.getTypename()
    private val int = jcClasspath.int.getTypename()
    private val long = jcClasspath.long.getTypename()
    private val boolean = jcClasspath.boolean.getTypename()
    private val objectType = jcClasspath.objectType.getTypename()
    private val throwable = TypeNameImpl.fromTypeName(java.lang.Throwable::class.java.name)
    private val void = jcClasspath.void.getTypename()

    private val byteArray = with(jcClasspath) { arrayTypeOf(byte).getTypename() }
    private val arrayOfByteArray = with(jcClasspath) { arrayTypeOf(arrayTypeOf(byte)).getTypename() }
    private val heapObjectDescriptor = TypeNameImpl.fromTypeName(ConcolicCollector.HeapObjectDescriptor::class.java.name)
    private val identityHashMap = TypeNameImpl.fromTypeName(ConcolicCollector.IdentityHashMap::class.java.name)
    private val instructionInfo = TypeNameImpl.fromTypeName(ConcolicCollector.InstructionInfo::class.java.name)
    private val instructionInfoArray = TypeNameImpl.fromTypeName("${instructionInfo.typeName}[]")

    private val stackPointer = concolicInfoHelper.createStaticFieldRef("stackPointer", int)
    private val argumentsFlagsStack = concolicInfoHelper.createStaticFieldRef("argumentsFlagsStack", arrayOfByteArray)
    private val localVariablesFlagsStack = concolicInfoHelper.createStaticFieldRef("localVariablesFlagsStack", arrayOfByteArray)
    private val thisFlagsStack = concolicInfoHelper.createStaticFieldRef("thisFlagsStack", byteArray)
    private val heapFlags = concolicInfoHelper.createStaticFieldRef("heapFlags", identityHashMap)
    private val staticFieldsFlags = concolicInfoHelper.createStaticFieldRef("staticFieldsFlags", byteArray)
    private val tracePointer = concolicInfoHelper.createStaticFieldRef("tracePointer", int)
    private val symbolicInstructionsTrace = concolicInfoHelper.createStaticFieldRef("symbolicInstructionsTrace", instructionInfoArray)

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
                        createInitializeNewCallStackFrameInstructions(rhv.args.size)
                    )
                }

                val operandsProcessor = OperandsProcessor(encodedInst)
                val processOperandsInstructions = operandsProcessor.getProcessOperandsInstructions(rawJcInstruction, listOf(rhv)) { flagsBuffer ->
                    when (val lhv = rawJcInstruction.lhv) {
                        is JcRawLocalVar -> add(
                            JcRawAssignInst(
                                owner,
                                JcRawArrayAccess(
                                    JcRawArrayAccess(localVariablesFlagsStack, stackPointer, byteArray),
                                    JcRawInt(lhv.index), byte
                                ),
                                flagsBuffer
                            )
                        )

                        is JcRawArgument -> add(
                            JcRawAssignInst(
                                owner,
                                JcRawArrayAccess(
                                    JcRawArrayAccess(argumentsFlagsStack, stackPointer, byteArray),
                                    JcRawInt(lhv.index), byte
                                ),
                                flagsBuffer
                            )
                        )

                        is JcRawArrayAccess ->
                            addAll(createAssignToHeapObjectDescriptorInstructions(lhv.array, lhv.index, flagsBuffer))

                        is JcRawFieldRef -> {
                            val instance = lhv.instance
                            if (instance == null) {
                                val lengthLocalVar = newLocalVar(int, JcRawLengthExpr(int, staticFieldsFlags))

                                `if`(JcRawGeExpr(boolean, lhv.fieldId, lengthLocalVar)) {
                                    add(concolicInfoHelper.createResizeStaticFieldsFlagsMethodCall())
                                }
                                add(JcRawAssignInst(
                                    owner,
                                    JcRawArrayAccess(staticFieldsFlags, lhv.fieldId, byte),
                                    flagsBuffer
                                ))
                            } else {
                                addAll(createAssignToHeapObjectDescriptorInstructions(instance, lhv.fieldId, flagsBuffer))
                            }
                        }

                        is JcRawConstant,
                        is JcRawThis ->
                            throw IllegalStateException("Variable expected as lhv of assign instruction")
                    }
                }

                instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)
            }

            is JcRawCallInst -> {
                instrumentedInstructionsList.insertBefore(
                    rawJcInstruction,
                    createInitializeNewCallStackFrameInstructions(rawJcInstruction.callExpr.args.size)
                )

                val processOperandsInstructions =
                    OperandsProcessor(encodedInst).getProcessOperandsInstructions(rawJcInstruction)

                instrumentedInstructionsList.insertBefore(rawJcInstruction, processOperandsInstructions)
            }

            is JcRawReturnInst -> {
                val processOperandsInstructions =
                    OperandsProcessor(encodedInst).getProcessOperandsInstructions(rawJcInstruction)

                instrumentedInstructionsList.insertBefore(rawJcInstruction,  processOperandsInstructions)
                instrumentedInstructionsList.insertBefore(rawJcInstruction, getOnExitFunctionInstructions())
            }

            is JcRawIfInst,
            is JcRawSwitchInst,
            is JcRawThrowInst,
            is JcRawEnterMonitorInst,
            is JcRawExitMonitorInst -> {
                val processOperandsInstructions =
                    OperandsProcessor(encodedInst).getProcessOperandsInstructions(rawJcInstruction)
                instrumentedInstructionsList.insertBefore(rawJcInstruction,  processOperandsInstructions)
            }

            is JcRawCatchInst,
            is JcRawGotoInst,
            is JcRawLineNumberInst,
            is JcRawLabelInst -> {}
        }
    }

    private fun getOnExitFunctionInstructions() = buildList {
        add(JcRawAssignInst(owner,
            JcRawArrayAccess(argumentsFlagsStack, stackPointer, byteArray),
            JcRawNull()
        ))

        add(JcRawAssignInst(owner,
            JcRawArrayAccess(localVariablesFlagsStack, stackPointer, byteArray),
            JcRawNull()
        ))

        add(JcRawAssignInst(owner,
            stackPointer,
            JcRawSubExpr(int, stackPointer, JcRawInt(1))
        ))
    }

    private fun createAssignToHeapObjectDescriptorInstructions(
        receiver: JcRawValue,
        inhabitantId: JcRawValue,
        flags: JcRawValue
    ): List<JcRawInst> = buildList {
        val objectDescriptorLocalVar = newLocalVar(objectType, JcRawVirtualCallExpr(
            identityHashMap,
            "get",
            listOf(objectType),
            objectType,
            heapFlags,
            listOf(receiver)
        ))

        val newObjectDescriptorLocalVar = newLocalVar(heapObjectDescriptor)
        val endLabel = newLabelName()

        `if`(JcRawEqExpr(boolean, objectDescriptorLocalVar, JcRawNull())) {
            `if`(JcRawEqExpr(boolean, flags, JcRawByte(0))) {
                add(JcRawGotoInst(owner, JcRawLabelRef(endLabel)))
            }

            add(JcRawAssignInst(owner,
                newObjectDescriptorLocalVar,
                JcRawNewExpr(heapObjectDescriptor)
            ))

            add(JcRawCallInst(owner, JcRawSpecialCallExpr(
                heapObjectDescriptor,
                "<init>",
                listOf(),
                void,
                newObjectDescriptorLocalVar,
                listOf()
            )))
        } `else` {
            add(JcRawAssignInst(owner,
                newObjectDescriptorLocalVar,
                JcRawCastExpr(heapObjectDescriptor, objectDescriptorLocalVar)
            ))
        }

        add(JcRawCallInst(owner, JcRawVirtualCallExpr(
            heapObjectDescriptor,
            "addFlags",
            listOf(int, byte),
            void,
            newObjectDescriptorLocalVar,
            listOf(inhabitantId, flags)
        )))

        add(JcRawCallInst(owner, JcRawVirtualCallExpr(
            identityHashMap,
            "put",
            listOf(objectType, objectType),
            void,
            heapFlags,
            listOf(receiver, newObjectDescriptorLocalVar)
        )))

        add(JcRawLabelInst(owner, endLabel))
    }

    override fun atMethodStart(jcMethod: JcMethod, instrumentedInstructionsList: JcMutableInstList<JcRawInst>) {
        val instructions = jcMethod.rawInstList.asSequence()

        owner = jcMethod

        localVariablesNum = instructions
            .flatMap { it.operands }
            .filterIsInstance<JcRawLocalVar>()
            .maxOfOrNull { it.index }
            ?.let { it + 1 } ?: 0

        labelsNum = instructions
            .filterIsInstance<JcRawLabelInst>()
            .maxOfOrNull { it.index }
            ?.let { it + 1 } ?: 0

        val incrementStackPointer = JcRawAssignInst(owner,
            stackPointer,
            JcRawAddExpr(int, stackPointer, JcRawInt(1))
        )

        val firstMethodInstruction = instrumentedInstructionsList.first()
        instrumentedInstructionsList.insertBefore(firstMethodInstruction, incrementStackPointer)

        if (localVariablesNum != 0) {
            val variablesStackFrameAllocation = JcRawAssignInst(owner,
                JcRawArrayAccess(localVariablesFlagsStack, stackPointer, byteArray),
                JcRawNewArrayExpr(byte, JcRawInt(localVariablesNum))
            )

            instrumentedInstructionsList.insertBefore(firstMethodInstruction, variablesStackFrameAllocation)
        }

        val startLabel = if (jcMethod.isConstructor) "#1" else "#0"
        val finallyBlockEntry = JcRawCatchEntry(
            throwable,
            JcRawLabelRef(startLabel),
            JcRawLabelRef("#${labelsNum}")
        )
        val handlerLabel = newLabelName()
        val catchVar = creatLocalVar(throwable)

        val exceptionsHandler = buildList {
            add(JcRawLabelInst(owner, handlerLabel))
            add(JcRawCatchInst(owner, catchVar, JcRawLabelRef(handlerLabel), listOf(finallyBlockEntry)))
            addAll(getOnExitFunctionInstructions())
            add(JcRawThrowInst(owner, catchVar))
        }

        instrumentedInstructionsList.insertAfter(instrumentedInstructionsList.last(), exceptionsHandler)
    }

    private inner class OperandsProcessor(private val encodedInst: Long) {

        private var concreteArgumentIndex = 0
        private var expressionFlagsBuffer: JcRawLocalVar? =  null
        private lateinit var instructionInfoLocalVar: JcRawLocalVar

        fun getProcessOperandsInstructions(
            inst: JcRawInst,
            operands: List<JcRawExpr> = inst.operands,
            flagsBufferCallback: (MutableList<JcRawInst>.(JcRawValue) -> Unit)? = null
        ): List<JcRawInst> = buildList {
            instructionInfoLocalVar = newLocalVar(instructionInfo, JcRawNewExpr(instructionInfo))
            add(JcRawCallInst(owner,
                JcRawSpecialCallExpr(
                    instructionInfo,
                    "<init>",
                    listOf(long),
                    void,
                    instructionInfoLocalVar,
                    listOf(JcRawLong(encodedInst))
                )
            ))

            operands.forEach { processExpression(it) }

            if (expressionFlagsBuffer == null) {
                return listOf()
            }

            val bitCheckLocalVar = newLocalVar(byte,
                JcRawAndExpr(byte, expressionFlagsBuffer!!, JcRawByte(1))
            )
            val lengthLocalVar = newLocalVar(int)

            `if`(JcRawNeqExpr(boolean, bitCheckLocalVar, JcRawByte(0))) {
                add(JcRawAssignInst(owner,
                    tracePointer,
                    JcRawAddExpr(int, tracePointer, JcRawInt(1))
                ))
                add(JcRawAssignInst(owner,
                    lengthLocalVar,
                    JcRawLengthExpr(int, symbolicInstructionsTrace)
                ))
                `if`(JcRawGeExpr(boolean, tracePointer, lengthLocalVar)) {
                    add(concolicInfoHelper.createResizeSymbolicInstructionsTraceMethodCall())
                }
                add(JcRawAssignInst(owner,
                    JcRawArrayAccess(symbolicInstructionsTrace, tracePointer, instructionInfo),
                    instructionInfoLocalVar
                ))
                if (inst is JcRawIfInst) {
                    val castedExpr = newLocalVar(objectType)
                    `if`(inst.condition) {
                        add(JcRawAssignInst(owner, castedExpr,
                            concolicInfoHelper.createBoxValueCall(JcRawBool(true))!!))
                    } `else` {
                        add(JcRawAssignInst(owner, castedExpr,
                            concolicInfoHelper.createBoxValueCall(JcRawBool(false))!!))
                    }
                    add(
                        JcRawCallInst(
                            owner, JcRawVirtualCallExpr(
                                instructionInfo,
                                "addConcreteArgument",
                                listOf(int, objectType),
                                void,
                                instructionInfoLocalVar,
                                listOf(JcRawInt(-1), castedExpr)
                            )
                        )
                    )
                } else if (inst is JcRawSwitchInst) {
                    val saveBranchSwitch = JcRawSwitchInst(owner, inst.key,
                        inst.branches.mapValues { JcRawLabelRef(newLabelName()) }, JcRawLabelRef(newLabelName()))
                    val castedExpr = newLocalVar(objectType)
                    add(saveBranchSwitch)
                    val exitLabel = newLabelName()
                    for ((i, label) in (saveBranchSwitch.branches.values + saveBranchSwitch.default).withIndex()) {
                        add(JcRawLabelInst(owner, label.name))
                        add(JcRawAssignInst(owner, castedExpr,
                            concolicInfoHelper.createBoxValueCall(JcRawInt(i))!!))
                        add(JcRawGotoInst(owner, JcRawLabelRef(exitLabel)))
                    }
                    add(JcRawLabelInst(owner, exitLabel))
                    add(
                        JcRawCallInst(
                            owner, JcRawVirtualCallExpr(
                                instructionInfo,
                                "addConcreteArgument",
                                listOf(int, objectType),
                                void,
                                instructionInfoLocalVar,
                                listOf(JcRawInt(-1), castedExpr)
                            )
                        )
                    )
                }
            }

            if (flagsBufferCallback != null) {
                expressionFlagsBuffer?.let { flagsBufferCallback(it) }
            }
        }

        private fun MutableList<JcRawInst>.processExpression(
            expr: JcRawExpr, isCallReceiver: Boolean = false, callParameterIndex: Int = -1
        ) {
            when (expr) {
                is JcRawBinaryExpr -> {
                    processExpression(expr.lhv)
                    processExpression(expr.rhv)
                }

                is JcRawArrayAccess -> {
                    processExpression(expr.array)
                    processExpression(expr.index)

                    val flags = newLocalVar(byte)
                    getObjectInhabitantFlags(expr.array, expr.index, flags)

                    addProcessOperandInstructions(expr, flags, isCallReceiver, callParameterIndex)
                }

                is JcRawCallExpr -> {
                    if (expr is JcRawInstanceExpr && expr !is JcRawSpecialCallExpr) {
                        processExpression(expr.instance, true)
                    }

                    expr.args.withIndex()
                        .forEach { (index, arg) -> processExpression(arg, callParameterIndex = index) }
                }

                is JcRawNewArrayExpr -> expr.dimensions.forEach { processExpression(it) }

                is JcRawCastExpr -> processExpression(expr.operand)
                is JcRawInstanceOfExpr -> processExpression(expr.operand)
                is JcRawLengthExpr -> processExpression(expr.array)
                is JcRawNegExpr -> processExpression(expr.operand)

                is JcRawFieldRef -> {
                    val flags = newLocalVar(byte)
                    val instance = expr.instance

                    if (instance == null) {
                        val lengthLocalVar = newLocalVar(int, JcRawLengthExpr(int, staticFieldsFlags))

                        `if`(JcRawLtExpr(boolean, expr.fieldId, lengthLocalVar)) {
                            add(JcRawAssignInst(owner,
                                flags,
                                JcRawArrayAccess(staticFieldsFlags, expr.fieldId, byte)
                            ))
                        } `else` {
                            add(JcRawAssignInst(owner, flags, JcRawByte(0)))
                        }
                    } else {
                        processExpression(instance)
                        getObjectInhabitantFlags(instance, expr.fieldId, flags)
                    }

                    addProcessOperandInstructions(expr, flags, isCallReceiver, callParameterIndex)
                }

                is JcRawArgument -> {
                    val flags = newLocalVar(byte,
                        JcRawArrayAccess(
                            JcRawArrayAccess(argumentsFlagsStack, stackPointer, byteArray),
                            JcRawInt(expr.index), byte
                        )
                    )

                    addProcessOperandInstructions(expr, flags, isCallReceiver, callParameterIndex)
                }

                is JcRawLocalVar -> {
                    val flags = newLocalVar(byte,
                        JcRawArrayAccess(
                            JcRawArrayAccess(localVariablesFlagsStack, stackPointer, byteArray),
                            JcRawInt(expr.index), byte
                        )
                    )

                    addProcessOperandInstructions(expr, flags, isCallReceiver, callParameterIndex)
                }

                is JcRawThis -> {
                    val flags = newLocalVar(byte, JcRawArrayAccess(thisFlagsStack, stackPointer, byte))

                    addProcessOperandInstructions(expr, flags, isCallReceiver, callParameterIndex)
                }

                is JcRawConstant, is JcRawNewExpr -> { concreteArgumentIndex++ }
            }
        }

        private fun MutableList<JcRawInst>.getObjectInhabitantFlags(
            instance: JcRawValue,
            inhabitantId: JcRawValue,
            flags: JcRawValue
        ) {
            val objectDescriptorLocalVar = newLocalVar(objectType, JcRawVirtualCallExpr(
                identityHashMap,
                "get",
                listOf(objectType),
                objectType,
                heapFlags,
                listOf(instance)
            ))

            `if`(JcRawNeqExpr(boolean, objectDescriptorLocalVar, JcRawNull())) {
                val castedDescriptor = newLocalVar(
                    heapObjectDescriptor,
                    JcRawCastExpr(heapObjectDescriptor, objectDescriptorLocalVar)
                )
                add(JcRawAssignInst(owner,
                    flags,
                    JcRawVirtualCallExpr(
                        heapObjectDescriptor,
                        "getFlags",
                        listOf(int),
                        byte,
                        castedDescriptor,
                        listOf(inhabitantId)
                    )
                ))
            } `else` {
                add(JcRawAssignInst(owner, flags, expressionFlagsBuffer!!))
            }
        }

        private fun MutableList<JcRawInst>.addProcessOperandInstructions(
            expr: JcRawValue, flags: JcRawValue, isCallReceiver: Boolean, callParameterIndex: Int
        ) {
            val newBuffer = newLocalVar(byte,
                expressionFlagsBuffer?.let { JcRawOrExpr(byte, it, flags) } ?: flags
            )
            expressionFlagsBuffer = newBuffer

            val bitCheckLocalVar = newLocalVar(byte, JcRawAndExpr(byte, flags, JcRawByte(1)))

            `if`(JcRawEqExpr(boolean, bitCheckLocalVar, JcRawByte(0))) {
                val castedExpr = newLocalVar(objectType)

                if (expr.typeName.isPrimitive) {
                    add(concolicInfoHelper.createBoxValueCall(expr)
                        ?.let { JcRawAssignInst(owner, castedExpr, it) }!!
                    )
                }

                add(JcRawCallInst(
                    owner, JcRawVirtualCallExpr(
                        instructionInfo,
                        "addConcreteArgument",
                        listOf(int, objectType),
                        void,
                        instructionInfoLocalVar,
                        listOf(JcRawInt(concreteArgumentIndex++),
                            if (expr.typeName.isPrimitive) castedExpr else expr)
                    )
                ))
            }

            if (isCallReceiver || callParameterIndex != -1) {
                val nextStackPointer = newLocalVar(int, JcRawAddExpr(int, stackPointer, JcRawInt(1)))

                if (isCallReceiver) {
                    add(JcRawAssignInst(owner,
                        JcRawArrayAccess(thisFlagsStack, nextStackPointer, byte),
                        flags
                    ))
                } else {
                    add(JcRawAssignInst(owner,
                        JcRawArrayAccess(
                            JcRawArrayAccess(argumentsFlagsStack, nextStackPointer, byteArray),
                            JcRawInt(callParameterIndex), byte
                        ), flags
                    ))
                }
            }
        }
    }

    private fun createInitializeNewCallStackFrameInstructions(argumentsNum: Int): List<JcRawInst> {
        if (argumentsNum == 0) {
            return emptyList()
        }

        return buildList {
            val stackLengthLocalVar = newLocalVar(int, JcRawLengthExpr(int, thisFlagsStack))
            val incrementedStackPointerLocalVar = newLocalVar(int, JcRawAddExpr(int, stackPointer, JcRawInt(1)))

            `if`(JcRawGeExpr(boolean, incrementedStackPointerLocalVar, stackLengthLocalVar)) {
                add(concolicInfoHelper.createResizeFlagsStacksMethodCall())
            }
            add(JcRawAssignInst(owner,
                JcRawArrayAccess(argumentsFlagsStack, incrementedStackPointerLocalVar, byteArray),
                JcRawNewArrayExpr(byte, JcRawInt(argumentsNum))
            ))
        }
    }

    private fun MutableList<JcRawInst>.`if`(
        condition: JcRawConditionExpr,
        then: MutableList<JcRawInst>.() -> Unit
    ): MutableList<JcRawInst> {
        val firstLabelName = newLabelName()
        val secondLabelName = newLabelName()

        add(JcRawIfInst(
            owner, condition, JcRawLabelRef(firstLabelName), JcRawLabelRef(secondLabelName)
        ))

        add(JcRawLabelInst(owner, firstLabelName))
        then()

        add(JcRawLabelInst(owner, secondLabelName))

        return this
    }

    private infix fun MutableList<JcRawInst>.`else`(`else`: MutableList<JcRawInst>.() -> Unit) {
        val newLabelName = newLabelName()

        add(lastIndex, JcRawGotoInst(owner, JcRawLabelRef(newLabelName)))
        `else`()
        add(JcRawLabelInst(owner, newLabelName))
    }


    private var localVariablesNum = 0
    private fun MutableList<JcRawInst>.newLocalVar(type: TypeName, initialValue: JcRawExpr? = null): JcRawLocalVar {
        val localVar = creatLocalVar(type)
        if (initialValue != null) {
            add(JcRawAssignInst(owner, localVar, initialValue))
        }
        return localVar
    }
    private fun creatLocalVar(type: TypeName) = localVariablesNum++.let { JcRawLocalVar(it, "%${it}", type) }

    private var labelsNum = 0
    private fun newLabelName() = "#${labelsNum++}"

    // TODO: use index property from new version of JacoDB
    private val JcRawLocalVar.index: Int
        get() = name.drop(1).toInt()

    private val JcRawLabelInst.index: Int
        get() = name.drop(1).toInt()

    private val JcRawFieldRef.fieldId: JcRawInt
        get() = JcRawInt(JcConcolicTracer.encodeField(this, jcClasspath))
}