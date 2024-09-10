package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcRawFieldRef
import org.usvm.instrumentation.collector.trace.ConcolicCollector
import org.usvm.instrumentation.util.toJcClassOrInterface

object JcConcolicTracer : Tracer<ConcolicTrace>() {
    override fun getTrace(): ConcolicTrace {
        val coveredInstructionsIds = coveredInstructionsIds()

        val symbolicInstructionsTrace = List(coveredInstructionsIds.size) { idx ->
            val traceFrame = ConcolicCollector.symbolicInstructionsTrace[idx]
            InstructionInfo(
                decode(coveredInstructionsIds[idx]),
                traceFrame.concreteArguments.take(traceFrame.argumentsPointer + 1)
                    .associate { it.index to it.value }
            )
        }
        return ConcolicTrace(symbolicInstructionsTrace)
    }

    override fun coveredInstructionsIds(): List<Long> {
        val traceFromTraceCollector = mutableListOf<Long>()
        for (i in 0 .. ConcolicCollector.tracePointer) {
            traceFromTraceCollector.add(ConcolicCollector.symbolicInstructionsTrace[i].jcInstructionId)
        }
        return List(traceFromTraceCollector.size) { idx -> traceFromTraceCollector[idx] }
    }

    private val staticFields = mutableMapOf<String, Int>()
    private var currentStaticFieldId = 0
    fun encodeField(fieldRef: JcRawFieldRef, jcClasspath: JcClasspath): Int {
        if (fieldRef.instance == null) {
            return staticFields.getOrPut(fieldRef.toString()) { currentStaticFieldId++ }
        }

        var jcClass = fieldRef.declaringClass.toJcClassOrInterface(jcClasspath)
            ?: error("Can't find class in classpath")
        var fieldIndex = -1
        while (true) {
            for (field in jcClass.declaredFields) {
                fieldIndex++
                if (field.name == fieldRef.fieldName) {
                    return fieldIndex
                }
            }
            jcClass = jcClass.superClass ?: error("Field `$fieldRef` not found")
        }
    }

    override fun reset() {
        ConcolicCollector.tracePointer = -1
        ConcolicCollector.symbolicInstructionsTrace = arrayOfNulls(32)
        ConcolicCollector.stackPointer = -1
        ConcolicCollector.thisFlagsStack = ByteArray(32)
        ConcolicCollector.argumentsFlagsStack = arrayOfNulls(32)
        ConcolicCollector.localVariablesFlagsStack = arrayOfNulls(32)
        ConcolicCollector.heapFlags = ConcolicCollector.IdentityHashMap()
        ConcolicCollector.staticFieldsFlags = ByteArray(32)
    }
}

data class ConcolicTrace(
    val symbolicInstructionsTrace: List<InstructionInfo>
) : Trace(symbolicInstructionsTrace.map { it.instruction })

data class InstructionInfo(
    val instruction: JcInst,
    val concreteArguments: Map<Int, Any>
)