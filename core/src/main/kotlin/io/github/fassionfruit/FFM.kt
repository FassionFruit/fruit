package io.github.fassionfruit

import io.github.fassionfruit.annotation.NativeLibrary
import io.github.fassionfruit.internal.NativeLoader

object FFM {
    @JvmStatic
    fun <T : Any> load(type: Class<T>): T {
        val annotation = type.getAnnotation(NativeLibrary::class.java)
            ?: throw IllegalArgumentException("${type.name} is missing @NativeLibrary")

        require(type.isInterface) {
            "${type.name} must be an interface"
        }

        return NativeLoader.load(type, annotation.value)
    }

    inline fun <reified T : Any> load(): T {
        return load(T::class.java)
    }
}