package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.isEnum
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.util.isSameSignature
import org.usvm.instrumentation.util.replace

/**
 * Class for runtime instrumentation for jcdb instructions
 * Collecting trace and information about static access
 */
open class JcRuntimeTraceInstrumenter(
    override val jcClasspath: JcClasspath,
    private val tracer: Tracer<*>
) : JcInstrumenter, AbstractFullRawExprSetCollector() {

    protected open val instrumentConstructors = false

    private val rawStaticsGet = hashSetOf<JcRawFieldRef>()
    private val rawStaticsSet = hashSetOf<JcRawFieldRef>()

    private val traceHelper by lazy { TraceHelper(jcClasspath, TraceCollector::class.java) }
    private val coveredInstructionMethodName = "jcInstructionCovered"
    private val staticFieldAccessedMethodName = "jcStaticFieldAccessed"

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
        val lhv = inst.lhv
        if (lhv is JcRawFieldRef && lhv.instance == null) {
            rawStaticsSet.add(lhv)
        }
        inst.rhv.accept(this)
    }

    private fun getStaticFieldRefs(inst: JcRawInst) {
        rawStaticsGet.clear()
        rawStaticsSet.clear()
        inst.accept(this)
    }

    override fun ifMatches(expr: JcRawExpr) {
        if (expr is JcRawFieldRef && expr.instance == null) rawStaticsGet.add(expr)
    }

    open fun processInstruction(
        encodedInst: Long,
        rawJcInstruction: JcRawInst,
        instrumentedInstructionsList: JcMutableInstList<JcRawInst>
    ) {
        val invocation = traceHelper.createTraceMethodCall(encodedInst, coveredInstructionMethodName)
        instrumentedInstructionsList.insertBefore(rawJcInstruction, invocation)

        if (tracer is JcInstructionTracer) {
            getStaticFieldRefs(rawJcInstruction)
            rawStaticsSet.forEach { jcRawFieldRef ->
                val encodedRef = tracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.SET, jcClasspath
                )
                val traceMethodCall = traceHelper.createTraceMethodCall(encodedRef, staticFieldAccessedMethodName)
                instrumentedInstructionsList.insertBefore(rawJcInstruction, traceMethodCall)
            }
            rawStaticsGet.forEach { jcRawFieldRef ->
                val encodedRef = tracer.encodeStaticFieldAccess(
                    jcRawFieldRef, StaticFieldAccessType.GET, jcClasspath
                )
                val traceMethodCall = traceHelper.createTraceMethodCall(encodedRef, staticFieldAccessedMethodName)
                instrumentedInstructionsList.insertBefore(rawJcInstruction, traceMethodCall)
            }
        }
    }

    open fun atMethodStart(jcMethod: JcMethod, instrumentedInstructionsList: JcMutableInstList<JcRawInst>) {}

    private fun instrumentMethod(jcMethod: JcMethod): MethodNode {
        val rawJcInstructionsList = jcMethod.rawInstList.filter { it !is JcRawLabelInst && it !is JcRawLineNumberInst }
        val jcInstructionsList = jcMethod.instList
        val instrumentedJcInstructionsList = jcMethod.rawInstList.toMutableList()
        atMethodStart(jcMethod, instrumentedJcInstructionsList)
        for (i in jcInstructionsList.indices) {
            val encodedInst = tracer.encode(jcInstructionsList[i])
            processInstruction(encodedInst, rawJcInstructionsList[i], instrumentedJcInstructionsList)
        }
        return MethodNodeBuilder(jcMethod, instrumentedJcInstructionsList).build()
    }

    override fun instrumentClass(classNode: ClassNode): ClassNode {
        val className = classNode.name.replace('/', '.')
        val jcClass = jcClasspath.findClassOrNull(className) ?: return classNode
        val asmMethods = classNode.methods
        val methodsToInstrument = if (jcClass.isEnum) {
            jcClass.declaredMethods.filterNot { !instrumentConstructors && it.isConstructor || it.isClassInitializer ||
                    it.name == "values" || it.name == "valueOf" }
        } else {
            jcClass.declaredMethods.filterNot { !instrumentConstructors && it.isConstructor || it.isClassInitializer }
        }
        //Copy of clinit method to be able to rollback statics between executions!
        //We are not able to call <clinit> method directly with reflection
        asmMethods.find { it.name == "<clinit>" }?.let { clinitNode ->
            val clinitCopy = MethodNode(9, GENERATED_CLINIT_NAME, "()V", null, emptyArray())
            clinitNode.instructions.forEach { clinitCopy.instructions.add(it) }
            asmMethods.add(0, clinitCopy)
        }
        methodsToInstrument.forEach { jcMethod ->
            val asmMethod = asmMethods.find { jcMethod.asmNode().isSameSignature(it) } ?: return@forEach
            val tracedMethod = instrumentMethod(jcMethod)
            asmMethods.replace(asmMethod, tracedMethod)
        }
        return classNode
    }

    companion object {
        const val GENERATED_CLINIT_NAME = "generatedClinit0"
    }

}