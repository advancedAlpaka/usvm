package org.usvm.instrumentation.instrumentation

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.instrumentation.util.enclosingClass
import org.usvm.instrumentation.util.enclosingMethod


open class Trace(val trace: List<JcInst>)

abstract class Tracer<T : Trace> {

    abstract fun getTrace(): T

    abstract fun coveredInstructionsIds(): List<Long>

    abstract fun reset()

    class EncodedClass(val id: Long) {
        val encodedMethods = hashMapOf<JcMethod, EncodedMethod>()
        val encodedFields = hashMapOf<JcField, EncodedField>()
        var currentFieldIndex = 0L
    }

    class EncodedMethod(val id: Long) {
        val encodedInstructions = hashMapOf<JcInst, EncodedInst>()
    }

    class EncodedField(val id: Long)

    class EncodedInst(val id: Long)

    private val encodedJcInstructions = hashMapOf<Long, JcInst>()
    protected val encodedClasses = hashMapOf<JcClassOrInterface, EncodedClass>()
    protected var currentClassIndex = 0L

    fun getEncodedClasses() = encodedClasses.entries.associate { it.key to it.value.id }

    fun encode(jcInst: JcInst): Long {
        val jcClass = jcInst.enclosingClass
        val jcMethod = jcInst.enclosingMethod
        val encodedClass = encodeClass(jcClass)
        val encodedMethod = encodeMethod(jcClass, jcMethod)
        val encodedInst =
            encodedMethod.encodedInstructions.getOrPut(jcInst) {
                EncodedInst(jcInst.location.index.toLong())
            }
        val instId = encodeTraceId(encodedClass.id, encodedMethod.id, encodedInst.id)
        encodedJcInstructions[instId] = jcInst
        return instId
    }

    fun encode(jcMethod: JcMethod): Long {
        val jcClass = jcMethod.enclosingClass
        val encodedClass = encodeClass(jcClass)
        val encodedMethod = encodeMethod(jcClass, jcMethod)
        return encodeTraceId(encodedClass.id, encodedMethod.id, 0L)
    }

    protected fun decode(jcInstructionId: Long): JcInst =
        encodedJcInstructions[jcInstructionId] ?: error("Can't decode inst")

    protected fun encodeClass(jcClass: JcClassOrInterface) =
        encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }

    private fun encodeMethod(jcClass: JcClassOrInterface, jcMethod: JcMethod): EncodedMethod {
        val encodedClass = encodeClass(jcClass)
        val methodIndex = jcClass.declaredMethods
            .indexOf(jcMethod)
            .also { if (it == -1) error("Encoding error") }
        return encodedClass.encodedMethods.getOrPut(jcMethod) { EncodedMethod(methodIndex.toLong()) }
    }

    /**
     *  0000 0000 0000 0000 0000 0000 0000 0000
     * |  class id    |methodId|    instId    |
     */
    private fun encodeTraceId(classId: Long, methodId: Long, instId: Long): Long {
        return (classId shl Byte.SIZE_BITS * 5) or (methodId shl Byte.SIZE_BITS * 3) or instId
    }
}