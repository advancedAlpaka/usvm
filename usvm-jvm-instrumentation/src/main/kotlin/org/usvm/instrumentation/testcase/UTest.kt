package org.usvm.instrumentation.testcase

import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestInst

// TODO it is not a UTest, it is JcTest
class UTest(
    val initStatements: List<UTestInst>,
    val callMethodExpression: UTestCall
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTest) return false

        if (initStatements != other.initStatements) return false
        if (callMethodExpression != other.callMethodExpression) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initStatements.hashCode()
        result = 31 * result + callMethodExpression.hashCode()
        return result
    }
}