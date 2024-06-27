package org.usvm.instrumentation.instrumentation

import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcRawFieldRef
import org.usvm.instrumentation.util.toJcClassOrInterface

object JcConcolicTracer : Tracer<ConcolicTrace> {
    override fun getTrace(): ConcolicTrace {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
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
}

data class ConcolicTrace(
    val symbolicInstructionsTrace: List<InstructionInfo>
)

data class InstructionInfo(
    val instruction: JcInst,
    val concreteArguments: Map<Int, Any>
)