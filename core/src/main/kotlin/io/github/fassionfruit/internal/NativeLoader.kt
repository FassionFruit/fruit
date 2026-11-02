package io.github.fassionfruit.internal

import io.github.fassionfruit.annotation.NativeSymbol
import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.collections.emptyList

internal object NativeLoader {
    internal val linker = Linker.nativeLinker()

    fun <T : Any> load(type: Class<T>, libraryName: String): T {
        require(type.isInterface) {
            "${type.name} must be an interface"
        }

        val arena = Arena.ofAuto()

        val libraryPath = java.nio.file.Path.of(libraryName)
            .toAbsolutePath()
            .normalize()

        val lookup = try {
            SymbolLookup.libraryLookup(libraryPath, arena)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to load native library: $libraryPath",
                e
            )
        }

        val handler = NativeInvocationHandler(lookup)

        return Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
            handler
        ).let(type::cast)
    }
}

private class NativeInvocationHandler(
    private val lookup: SymbolLookup
) : InvocationHandler {

    private val handles = mutableMapOf<Method, java.lang.invoke.MethodHandle>()

    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any>?
    ): Any? {
        if (method.declaringClass == Any::class.java) {
            return when (method.name) {
                "toString" -> "NativeProxy(${proxy.javaClass.interfaces.first().name})"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }

        val handle = handles.getOrPut(method) {
            createHandle(method)
        }

        return handle.invokeWithArguments(args?.toList() ?: emptyList<Any>())
    }

    private fun createHandle(method: Method): java.lang.invoke.MethodHandle {
        val annotation = method.getAnnotation(NativeSymbol::class.java)
            ?: throw IllegalStateException(
                "${method.name} is missing @NativeSymbol"
            )

        val symbol = lookup.find(annotation.value).orElseThrow {
            UnsatisfiedLinkError(
                "Native symbol not found: ${annotation.value}"
            )
        }

        val descriptor = NativeTypeMapper.createDescriptor(method)

        return NativeLoader.linker.downcallHandle(symbol, descriptor)
    }
}