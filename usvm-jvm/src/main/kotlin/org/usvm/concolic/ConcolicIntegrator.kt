package org.usvm.concolic

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcChar
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcRawBool
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcShort
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.jacodb.impl.cfg.JcRawByte
import org.usvm.*
import org.usvm.instrumentation.classloader.MetaClassLoader
import org.usvm.instrumentation.instrumentation.InstructionInfo
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.util.getTypename
import org.usvm.machine.*
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.returnValue
import org.usvm.utils.ensureSat

object ConcolicIntegrator {
    lateinit var cp : JcClasspath
    lateinit var interpreter: JcInterpreter
    lateinit var state: JcState
    lateinit var ctx: JcContext
    lateinit var classLoader: MetaClassLoader
    lateinit var initStateDescriptorBuilder: Value2DescriptorConverter

    fun init(method: JcMethod, classLoader: MetaClassLoader) {
        ConcolicIntegrator.classLoader = classLoader
        cp = classLoader.jcClasspath
        initStateDescriptorBuilder = Value2DescriptorConverter(
            workerClassLoader = ConcolicIntegrator.classLoader,
            previousState = null
        )
        val options = UMachineOptions()
        val jcMachineOptions = JcMachineOptions()
        val applicationGraph = JcApplicationGraph(cp)

        val typeSystem = JcTypeSystem(cp, options.typeOperationsTimeout)
        val components = JcComponents(typeSystem, options)
        ctx = JcContext(cp, components)

        interpreter = JcInterpreter(ctx, applicationGraph, jcMachineOptions)
        state = interpreter.getInitialState(method)
    }


    fun mkReturnValue(value: TypeName) = when(value) {
        cp.boolean.getTypename() -> ctx.mkBool(false) // JcBool(false, cp.boolean)
        cp.byte.getTypename() ->  ctx.mkBv(0.toByte()) //JcByte(0, cp.byte)
        cp.short.getTypename() -> ctx.mkBv(0.toShort()) //JcShort(0, cp.short)
        cp.int.getTypename() -> ctx.mkBv(0)// JcInt(0, cp.int)
        cp.long.getTypename() -> ctx.mkBv(0.toLong()) //JcLong(0L, cp.long)
        cp.float.getTypename() -> ctx.mkFp32NaN() // JcFloat(0f, cp.float)
        cp.double.getTypename() -> ctx.mkFp64NaN() // JcDouble(0.0, cp.double)
        cp.char.getTypename() -> ctx.mkBv(0, 16u) // JcChar(0.toChar(), cp.char) // TOD
        else -> ctx.mkNullRef()
    }

/*    fun mkReturnInst(method: JcMethod): JcReturnInst {
        val returnValue = mkReturnValue(method.returnType)
        return JcReturnInst(
            JcInstLocationImpl(
                method,
                method.instList.size,
                method.instList.lastOrNull()?.location?.lineNumber ?: -1
            ),
            returnValue
        )
    }*/

    var stepAction: (InstructionInfo?) -> Unit = { info: InstructionInfo? ->
        info?.let {
            var fixedStack = false
            while(info.instruction.location.method != state.lastEnteredMethod) {
                fixedStack = true
                //state.pathNode += mkReturnInst(state.lastEnteredMethod)
                state.returnValue(mkReturnValue(state.lastEnteredMethod.returnType))
                interpreter.step(state)
            }
            if(fixedStack)
                state.pathNode = state.pathNode.parent!!
            state.pathNode += info.instruction
//          if(info.instruction !is JcReturnInst) {
            interpreter.step(state)
            if(state.lastStmt is JcConcreteMethodCallInst)
                interpreter.step(state)
            state.pathNode = state.pathNode.parent!!
//          }
        }
    }

    var chooseBranchAction : (InstructionInfo?) -> Unit = { info: InstructionInfo? ->
        info?.let {
            when(info.instruction) {
                is JcIfInst -> {
                    var fixedStack = false
                    while(info.instruction.location.method != state.lastEnteredMethod) {
                        fixedStack = true
                        //state.pathNode += mkReturnInst(state.lastEnteredMethod)
                        state.returnValue(mkReturnValue(state.lastEnteredMethod.returnType))
                        interpreter.step(state)
                    }
                    if(fixedStack)
                        state.pathNode = state.pathNode.parent!!
                    //--------------------------------------------------------------------------
                    val ifInst = info.instruction as JcIfInst
                    println("We met if branching")
                    println("Condition was ${ifInst.condition}")
                    println("Concrete arguments were ${info.concreteArguments}")
                    val resultStateDescriptorBuilder =
                        Value2DescriptorConverter(classLoader, initStateDescriptorBuilder)
                    /*if(!maybeEvaluated.isTrue && !maybeEvaluated.isFalse)
                        println("It's problem, condition didn't evaluate")
                    else {
                        println("It was evaluated to ${maybeEvaluated.isTrue}")
                    }*/
                    val evaluated = (info.getConcreteValues(resultStateDescriptorBuilder)[-1] as? UTestConstantDescriptor.Boolean)?.value
                    if(evaluated == null){
                        println("PIZDA 3")
                        return@let
                    }
                    println("Condition evaluated to $evaluated")
                    println("We will try to compute model for different branch")
                    val constraints = state.pathConstraints.clone()
                    val scope = StepScope(state, interpreter.forkBlackList)
                    val exprResolver = interpreter.exprResolverWithScope(scope, info.instruction)
                    val expr = (exprResolver.resolveJcExpr(ifInst.condition)!!.asExpr(ctx.boolSort))
                    constraints.plusAssign(if(evaluated) ctx.mkNot(expr) else expr)
                    val solver = ctx.solver<JcType>()
                    val model = solver.check(constraints).ensureSat().model
                    println("We computed next model: ")
                    println(model)
                    // convert JcConditionExpr to UBoolExpr
                    state.pathNode += ifInst
                    val res = interpreter.step(state)
                    val weAreInTrueBranch = state.currentStatement.location.index == ifInst.trueBranch.index
                    if(evaluated xor weAreInTrueBranch)
                        state = res.forkedStates.single()
                    state.pathNode = state.pathNode.parent!!
                }
                is JcSwitchInst -> {
                    var fixedStack = false
                    while(info.instruction.location.method != state.lastEnteredMethod) {
                        fixedStack = true
                        //state.pathNode += mkReturnInst(state.lastEnteredMethod)
                        state.returnValue(mkReturnValue(state.lastEnteredMethod.returnType))
                        interpreter.step(state)
                    }
                    if(fixedStack)
                        state.pathNode = state.pathNode.parent!!
                    //--------------------------------------------------------------------------
                    val switchInst = info.instruction as JcSwitchInst
                    val resultStateDescriptorBuilder =
                        Value2DescriptorConverter(classLoader, initStateDescriptorBuilder)
                    val chosenBranchInd = (info.getConcreteValues(resultStateDescriptorBuilder)[-1] as? UTestConstantDescriptor.Int)?.value!!
                    val (evaluatedKey,  chosenBranch) = switchInst.branches.toList().getOrNull(chosenBranchInd) ?: (null to switchInst.default)
                    println("We met switch branching")
                    println("Key was ${switchInst.key}")
                    println("Concrete arguments were ${info.concreteArguments}")
                    println("Chosen branch was with key ${evaluatedKey ?: "default"}")
                    state.pathNode += switchInst
                    val res = interpreter.step(state)
                    if(chosenBranch.index != state.currentStatement.location.index)
                        state = res.forkedStates.find { it.currentStatement.location.index == chosenBranch.index }!!
                    state.pathNode = state.pathNode.parent!!
                }
                else -> {
                    println()
                }
            }



        }
    }
}