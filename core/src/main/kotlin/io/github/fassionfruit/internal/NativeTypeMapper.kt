package io.github.fassionfruit.internal

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.lang.reflect.Method

internal object NativeTypeMapper {
    fun createDescriptor(method: Method): FunctionDescriptor {
        val returnType = method.returnType
        val parameterLayouts = method.parameterTypes.map(::map).toTypedArray()

        return if (returnType == Void.TYPE) {
            FunctionDescriptor.ofVoid(*parameterLayouts)
        } else {
            FunctionDescriptor.of(
                map(returnType),
                *parameterLayouts
            )
        }
    }

    private fun map(type: Class<*>): MemoryLayout {
        return when (type) {
            java.lang.Byte.TYPE -> ValueLayout.JAVA_BYTE
            java.lang.Short.TYPE -> ValueLayout.JAVA_SHORT
            java.lang.Integer.TYPE -> ValueLayout.JAVA_INT
            java.lang.Long.TYPE -> ValueLayout.JAVA_LONG
            java.lang.Float.TYPE -> ValueLayout.JAVA_FLOAT
            java.lang.Double.TYPE -> ValueLayout.JAVA_DOUBLE
            else -> throw IllegalArgumentException(
                "Unsupported native type: ${type.name}"
            )
        }
    }
}