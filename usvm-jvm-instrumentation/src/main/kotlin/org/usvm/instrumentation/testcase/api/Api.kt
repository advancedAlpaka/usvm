package org.usvm.instrumentation.testcase.api

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.jacodb.impl.types.JcArrayTypeImpl

/**
 * Api for UTestExpression
 * Used for specifying scenario of target method execution
 */

sealed interface UTestInst

sealed interface UTestExpression: UTestInst {
    val type: JcType?
}

sealed class UTestMock(
    override val type: JcType,
    open val fields: Map<JcField, UTestExpression>,
    open val methods: Map<JcMethod, List<UTestExpression>>
): UTestExpression {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestMock) return false

        if (type != other.type) return false
        if (fields != other.fields) return false
        if (methods != other.methods) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + fields.hashCode()
        result = 31 * result + methods.hashCode()
        return result
    }
}

/**
 * Mock for specific object
 */
class UTestMockObject(
    override val type: JcType,
    override val fields: Map<JcField, UTestExpression>,
    override val methods: Map<JcMethod, List<UTestExpression>>
) : UTestMock(type, fields, methods)

/**
 * Mock for all objects of type
 */
class UTestGlobalMock(
    override val type: JcType,
    override val fields: Map<JcField, UTestExpression>,
    override val methods: Map<JcMethod, List<UTestExpression>>
) : UTestMock(type, fields, methods)


sealed interface UTestCall : UTestExpression {
    val instance: UTestExpression?
    val method: JcMethod?
    val args: List<UTestExpression>
}

class UTestMethodCall(
    override val instance: UTestExpression,
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall {
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestMethodCall) return false

        if (instance != other.instance) return false
        if (method != other.method) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = instance.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }
}

class UTestStaticMethodCall(
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall {
    override val instance: UTestExpression? = null
    override val type: JcType? = method.enclosingClass.classpath.findTypeOrNull(method.returnType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestStaticMethodCall) return false

        if (method != other.method) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }
}

class UTestConstructorCall(
    override val method: JcMethod,
    override val args: List<UTestExpression>
) : UTestCall {
    override val instance: UTestExpression? = null
    override val type: JcType = method.enclosingClass.toType()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestConstructorCall) return false

        if (method != other.method) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }
}

class UTestAllocateMemoryCall(
    val clazz: JcClassOrInterface
) : UTestCall {
    override val instance: UTestExpression? = null
    override val method: JcMethod? = null
    override val args: List<UTestExpression> = listOf()
    override val type: JcType = clazz.toType()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestAllocateMemoryCall) return false

        if (clazz != other.clazz) return false

        return true
    }

    override fun hashCode(): Int {
        return clazz.hashCode()
    }
}

sealed interface UTestStatement : UTestInst

class UTestSetFieldStatement(
    val instance: UTestExpression,
    val field: JcField,
    val value: UTestExpression
) : UTestStatement {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestSetFieldStatement) return false

        if (instance != other.instance) return false
        if (field.signature != other.field.signature) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = instance.hashCode()
        result = 31 * result + field.signature.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

class UTestSetStaticFieldStatement(
    val field: JcField,
    val value: UTestExpression
) : UTestStatement {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestSetStaticFieldStatement) return false

        if (field.signature != other.field.signature) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = field.signature.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}


class UTestBinaryConditionExpression(
    val conditionType: ConditionType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    val trueBranch: UTestExpression,
    val elseBranch: UTestExpression
) : UTestExpression {
    //TODO!! What if trueBranch and elseBranch have different types of the last instruction? Shouldn't we find their LCA?

    init {
        check(trueBranch.type == elseBranch.type){ "True and else branches should be equal" }
    }

    //Probably add functionality in jacodb?
    override val type: JcType? = trueBranch.type

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestBinaryConditionExpression) return false

        if (conditionType != other.conditionType) return false
        if (lhv != other.lhv) return false
        if (rhv != other.rhv) return false
        if (trueBranch != other.trueBranch) return false
        if (elseBranch != other.elseBranch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conditionType.hashCode()
        result = 31 * result + lhv.hashCode()
        result = 31 * result + rhv.hashCode()
        result = 31 * result + trueBranch.hashCode()
        result = 31 * result + elseBranch.hashCode()
        return result
    }
}

class UTestBinaryConditionStatement(
    val conditionType: ConditionType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    val trueBranch: List<UTestStatement>,
    val elseBranch: List<UTestStatement>
) : UTestStatement {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestBinaryConditionStatement) return false

        if (conditionType != other.conditionType) return false
        if (lhv != other.lhv) return false
        if (rhv != other.rhv) return false
        if (trueBranch != other.trueBranch) return false
        if (elseBranch != other.elseBranch) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conditionType.hashCode()
        result = 31 * result + lhv.hashCode()
        result = 31 * result + rhv.hashCode()
        result = 31 * result + trueBranch.hashCode()
        result = 31 * result + elseBranch.hashCode()
        return result
    }
}

class UTestArithmeticExpression(
    val operationType: ArithmeticOperationType,
    val lhv: UTestExpression,
    val rhv: UTestExpression,
    override val type: JcType
) : UTestExpression {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestArithmeticExpression) return false

        if (operationType != other.operationType) return false
        if (lhv != other.lhv) return false
        if (rhv != other.rhv) return false

        return true
    }

    override fun hashCode(): Int {
        var result = operationType.hashCode()
        result = 31 * result + lhv.hashCode()
        result = 31 * result + rhv.hashCode()
        return result
    }
}

class UTestGetStaticFieldExpression(
    val field: JcField
) : UTestExpression {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestGetStaticFieldExpression) return false

        if (field.signature != other.field.signature) return false

        return true
    }

    override fun hashCode(): Int {
        return field.signature.hashCode()
    }
}

sealed class UTestConstExpression<T> : UTestExpression {
    abstract val value: T

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestConstExpression<*>) return false

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }
}

class UTestBooleanExpression(
    override val value: Boolean,
    override val type: JcType
) : UTestConstExpression<Boolean>()

class UTestByteExpression(
    override val value: Byte,
    override val type: JcType
) : UTestConstExpression<Byte>()

class UTestShortExpression(
    override val value: Short,
    override val type: JcType
) : UTestConstExpression<Short>()

class UTestIntExpression(
    override val value: Int,
    override val type: JcType
) : UTestConstExpression<Int>()

class UTestLongExpression(
    override val value: Long,
    override val type: JcType
) : UTestConstExpression<Long>()

class UTestFloatExpression(
    override val value: Float,
    override val type: JcType
) : UTestConstExpression<Float>()

class UTestDoubleExpression(
    override val value: Double,
    override val type: JcType
) : UTestConstExpression<Double>()

class UTestCharExpression(
    override val value: Char,
    override val type: JcType
) : UTestConstExpression<Char>()

class UTestStringExpression(
    override val value: String,
    override val type: JcType
) : UTestConstExpression<String>()

class UTestNullExpression(
    override val type: JcType
) : UTestConstExpression<Any?>() {
    override val value = null
}

class UTestGetFieldExpression(
    val instance: UTestExpression,
    val field: JcField
) : UTestExpression {
    override val type: JcType? = field.enclosingClass.classpath.findTypeOrNull(field.type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestGetFieldExpression) return false

        if (instance != other.instance) return false
        if (field.signature != other.field.signature) return false

        return true
    }

    override fun hashCode(): Int {
        var result = instance.hashCode()
        result = 31 * result + field.signature.hashCode()
        return result
    }
}

class UTestArrayLengthExpression(
    val arrayInstance: UTestExpression
) : UTestExpression {
    override val type: JcType? = arrayInstance.type?.classpath?.int

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestArrayLengthExpression) return false

        if (arrayInstance != other.arrayInstance) return false

        return true
    }

    override fun hashCode(): Int {
        return arrayInstance.hashCode()
    }
}

class UTestArrayGetExpression(
    val arrayInstance: UTestExpression,
    val index: UTestExpression
) : UTestExpression {
    override val type: JcType? = (arrayInstance.type as? JcArrayType)?.elementType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestArrayGetExpression) return false

        if (arrayInstance != other.arrayInstance) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrayInstance.hashCode()
        result = 31 * result + index.hashCode()
        return result
    }
}

class UTestArraySetStatement(
    val arrayInstance: UTestExpression,
    val index: UTestExpression,
    val setValueExpression: UTestExpression
) : UTestStatement {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestArraySetStatement) return false

        if (arrayInstance != other.arrayInstance) return false
        if (index != other.index) return false
        if (setValueExpression != other.setValueExpression) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrayInstance.hashCode()
        result = 31 * result + index.hashCode()
        result = 31 * result + setValueExpression.hashCode()
        return result
    }
}

class UTestCreateArrayExpression(
    val elementType: JcType,
    val size: UTestExpression
) : UTestExpression {
    override val type: JcType = JcArrayTypeImpl(elementType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestCreateArrayExpression) return false

        if (elementType != other.elementType) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elementType.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}

class UTestCastExpression(
    val expr: UTestExpression,
    override val type: JcType
) : UTestExpression {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestCastExpression) return false

        if (expr != other.expr) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = expr.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

class UTestClassExpression(
    override val type: JcType
): UTestExpression {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UTestClassExpression) return false

        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}


enum class ConditionType {
    EQ, NEQ, GEQ, GT
}

enum class ArithmeticOperationType {

    //Arithmetic
    PLUS, SUB, MUL, DIV, REM,

    //Relational
    EQ, NEQ, GT, GEQ, LT, LEQ,

    //Bitwise
    OR, AND, XOR
}
