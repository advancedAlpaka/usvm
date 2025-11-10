package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.usvm.instrumentation.collector.trace.ConcolicCollector
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.util.toJcClassOrInterface

object JcConcolicTracer : Tracer<ConcolicTrace>() {
    override fun getTrace(): ConcolicTrace {
        val coveredInstructionsIds = coveredInstructionsIds()

        val symbolicInstructionsTrace = List(coveredInstructionsIds.size) { idx ->
            val traceFrame = ConcolicCollector.symbolicInstructionsTrace[idx]
            InstructionInfo(
                decode(coveredInstructionsIds[idx]),
                traceFrame.concreteArguments.take(traceFrame.argumentsPointer + 1)
                    .map { it.index to it.value }
            )
        }
        return ConcolicTrace(symbolicInstructionsTrace)
    }

    fun convertInfo(frame: ConcolicCollector.InstructionInfo?) =
        frame?.let {
            InstructionInfo(
                decode(frame.jcInstructionId),
                frame.concreteArguments.take(frame.argumentsPointer + 1).map { it.index to it.value }
            )
        }

    override fun coveredInstructionsIds(): List<Long> {
        val traceFromTraceCollector = mutableListOf<Long>()
        for (i in 0..ConcolicCollector.tracePointer) {
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
    val concreteArguments: List<Pair<Int, Any>>
) {
    fun getConcreteValues(
        descriptorBuilder: Value2DescriptorConverter
    ): Map<Int, UTestValueDescriptor> {

        return concreteArguments.associate { (k, v) -> k to
                    descriptorBuilder.buildDescriptorResultFromAny(v, null).getOrThrow()
            }
    }
}