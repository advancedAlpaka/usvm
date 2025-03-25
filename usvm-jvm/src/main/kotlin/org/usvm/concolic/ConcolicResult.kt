package org.usvm.concolic

import org.jacodb.api.cfg.JcInst
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

data class ConcolicResult(
    val concreteRuns: List<ConcreteRun>,
    val symbolicExecutions: List<SymbolicExecutionTrace>
)

data class ConcreteRun(
    val test: UTest,
    val result: UTestExecutionResult
)

data class SymbolicExecutionTrace(
    val trace: List<JcInst>,
    val concreteValue: List<Map<Int, UTestValueDescriptor>>
) {
    fun startsWith(anotherTrace: List<JcInst>): Boolean {
        return anotherTrace.size <= trace.size && anotherTrace.withIndex().all { trace[it.index] == it.value }
    }
}