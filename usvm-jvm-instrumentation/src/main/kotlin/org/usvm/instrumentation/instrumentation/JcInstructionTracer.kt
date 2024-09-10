package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcRawFieldRef
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer.StaticFieldAccessType
import org.usvm.instrumentation.util.toJcClassOrInterface

//Jacodb instructions tracer
object JcInstructionTracer : Tracer<TraceWithStatics>() {

    // We are instrumenting statics access to build descriptors only for accessed while execution statics
    enum class StaticFieldAccessType {
        GET, SET
    }

    override fun getTrace(): TraceWithStatics {
        val traceFromTraceCollector =
            TraceCollector.trace.allValues
        val trace = List(traceFromTraceCollector.size) { idx ->
            decode(traceFromTraceCollector[idx])
        }
        val statics = List(TraceCollector.statics.size) { idx ->
            decodeStatic(TraceCollector.statics.arr[idx])
        }
        return TraceWithStatics(trace, statics)
    }

    override fun coveredInstructionsIds(): List<Long> {
        val traceFromTraceCollector =
            TraceCollector.trace.allValues
        return List(traceFromTraceCollector.size) { idx -> traceFromTraceCollector[idx] }
    }

    private val encodedJcStaticFieldRef = hashMapOf<Long, Pair<JcField, StaticFieldAccessType>>()

    fun encodeField(jcClass: JcClassOrInterface, jcField: JcField): EncodedField {
        val encodedClass = encodeClass(jcClass)
        return encodedClass.encodedFields.getOrPut(jcField) { EncodedField(encodedClass.currentFieldIndex++) }
    }

    /**
     *  0000 0000 0000 0000 0000 0000 0000 0000
     * |  class id    |fieldId | accessTypeId |
     */
    private fun encodeStaticFieldAccessId(classId: Long, fieldId: Long, accessTypeId: Long): Long {
        return (classId shl Byte.SIZE_BITS * 5) or (fieldId shl Byte.SIZE_BITS * 3) or accessTypeId
    }

    fun encodeStaticFieldAccess(
        jcRawFieldRef: JcRawFieldRef,
        accessType: StaticFieldAccessType,
        jcClasspath: JcClasspath
    ): Long {
        var jcClass =
            jcRawFieldRef.declaringClass.toJcClassOrInterface(jcClasspath) ?: error("Can't find class in classpath")
        while (true) {
            val encodedClass = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
            val indexedJcField =
                jcClass.declaredFields.withIndex()
                    .find { it.value.isStatic && it.value.name == jcRawFieldRef.fieldName }
            if (indexedJcField == null) {
                // static fields can be accessed via subclass of declaring class
                jcClass = jcClass.superClass
                    ?: error("Field `${jcRawFieldRef.declaringClass.typeName}.${jcRawFieldRef.fieldName}` not found")
                continue
            }
            val accessTypeId = accessType.ordinal.toLong()
            val instId = encodeStaticFieldAccessId(encodedClass.id, indexedJcField.index.toLong(), accessTypeId)
            encodedJcStaticFieldRef[instId] = indexedJcField.value to accessType
            return instId
        }
    }

    private fun decodeStatic(jcStaticId: Long): Pair<JcField, StaticFieldAccessType> =
        encodedJcStaticFieldRef[jcStaticId] ?: error("Can't decode inst")

    override fun reset() {
        TraceCollector.trace.clear()
        TraceCollector.statics.clear()
    }

}

class TraceWithStatics(trace: List<JcInst>, val statics: List<Pair<JcField, StaticFieldAccessType>>) : Trace(trace)