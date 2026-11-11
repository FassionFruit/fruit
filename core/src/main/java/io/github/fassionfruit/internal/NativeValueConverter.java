package io.github.fassionfruit.internal;

import io.github.fassionfruit.annotation.NativeCallback;
import io.github.fassionfruit.annotation.NativeEnumValue;
import io.github.fassionfruit.annotation.NativePointer;
import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;
import io.github.fassionfruit.structure.NativeStructureHandler;
import io.github.fassionfruit.structure.NativeStructureLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.concurrent.ConcurrentHashMap;

/** Converts Java binding values to and from FFM carrier values. */
public final class NativeValueConverter {

    private static final long MAX_STRING_BYTES = 1024 * 1024;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle INVOKE_CALLBACK = createCallbackInvoker();
    private static final ConcurrentHashMap<Class<?>, CallbackPlan> CALLBACK_PLANS =
            new ConcurrentHashMap<>();

    private NativeValueConverter() {
    }

    /** Converts a Java value to the carrier expected by a native call. */
    public static Object toNative(Class<?> type, Object value, Arena arena) {
        if (value == null) {
            if (type == String.class
                    || type == MemorySegment.class
                    || type.isAnnotationPresent(NativeStructure.class)
                    || type.isAnnotationPresent(NativeUnion.class)
                    || type.isAnnotationPresent(NativeCallback.class)) {
                return MemorySegment.NULL;
            }

            return null;
        }

        if (type.isAnnotationPresent(NativeCallback.class)) {
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Expected native callback: " + type.getName()
                );
            }

            return createCallbackStub(type, value, arena);
        }

        if (type.isAnnotationPresent(NativeStructure.class)) {
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Expected native structure: " + type.getName()
                );
            }

            return NativeStructureHandler.getSegment(value);
        }

        if (type.isAnnotationPresent(NativeUnion.class)) {
            if (!type.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Expected native union: " + type.getName()
                );
            }

            return NativeStructureHandler.getSegment(value);
        }

        if (type == String.class) {
            return arena.allocateFrom((String) value);
        }

        if (type == MemorySegment.class) {
            return value;
        }

        if (type == char.class || type == Character.class) {
            return (short) ((Character) value).charValue();
        }

        if (type == boolean.class || type == Boolean.class) {
            return (boolean) value ? 1 : 0;
        }

        if (type.isEnum()) {
            return getEnumValue((Enum<?>) value);
        }

        return value;
    }

    public static Object toNative(
            Parameter parameter,
            Object value,
            Arena arena
    ) {
        NativePointer pointer = parameter.getAnnotation(
                NativePointer.class
        );

        if (pointer != null
                && !pointer.nullable()
                && value == null) {
            throw new IllegalArgumentException(
                    "Native pointer cannot be null: "
                            + parameter.getName()
            );
        }

        return toNative(
                parameter.getType(),
                value,
                arena
        );
    }

    /** Converts a native carrier value to the declared Java type. */
    public static Object fromNative(Class<?> type, Object value) {
        if (type == void.class) return null;

        if (type.isAnnotationPresent(NativeStructure.class)) {
            MemorySegment segment = (MemorySegment) value;

            if (segment.address() == 0) {
                return null;
            }

            long size = NativeStructureLayout.create(type).byteSize();

            MemorySegment structureSegment = segment.reinterpret(size);

            return NativeStructureHandler.create(type, structureSegment);
        }

        if (type.isAnnotationPresent(NativeUnion.class)) {
            MemorySegment segment = (MemorySegment) value;

            if (segment.address() == 0) {
                return null;
            }

            long size = NativeStructureLayout.create(type).byteSize();
            return NativeStructureHandler.create(
                    type,
                    segment.reinterpret(size)
            );
        }

        if (type == String.class) {
            MemorySegment segment = (MemorySegment) value;

            if (segment.address() == 0) return null;

            return segment.reinterpret(MAX_STRING_BYTES).getString(0);
        }

        if (type == MemorySegment.class) return value;

        if (type == char.class || type == Character.class) {
            return (char) (short) value;
        }

        if (type == boolean.class || type == Boolean.class) {
            return ((int) value) != 0;
        }

        if (type.isEnum()) {
            return getEnumConstant(type, (int) value);
        }

        return value;
    }

    private static MemorySegment createCallbackStub(
            Class<?> type,
            Object callback,
            Arena arena
    ) {
        CallbackPlan plan = CALLBACK_PLANS.computeIfAbsent(
                type,
                NativeValueConverter::createCallbackPlan
        );

        MethodHandle target = MethodHandles.insertArguments(
                INVOKE_CALLBACK,
                0,
                callback,
                plan.method(),
                arena
        );

        target = target.asCollector(
                Object[].class,
                plan.method().getParameterCount()
        );

        target = target.asType(plan.methodType());

        return LINKER.upcallStub(target, plan.descriptor(), arena);
    }

    private static CallbackPlan createCallbackPlan(Class<?> type) {
        Method method = findCallbackMethod(type);
        FunctionDescriptor descriptor = NativeTypeMapper.createDescriptor(method);

        return new CallbackPlan(
                method,
                descriptor,
                toMethodType(descriptor)
        );
    }

    private static MethodHandle createCallbackInvoker() {
        try {
            return MethodHandles.lookup().findStatic(
                    NativeValueConverter.class,
                    "invokeCallback",
                    MethodType.methodType(
                            Object.class,
                            Object.class,
                            Method.class,
                            Arena.class,
                            Object[].class
                    )
            );
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Method findCallbackMethod(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        Method[] methods = type.getMethods();

        Method callbackMethod = null;

        for (Method method : methods) {
            int modifiers = method.getModifiers();

            if (!Modifier.isAbstract(modifiers)
                    || Modifier.isStatic(modifiers)
                    || method.isBridge()
                    || method.isSynthetic()
                    || method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (callbackMethod != null) {
                throw new IllegalArgumentException(
                        "Native callback must have exactly one abstract method: "
                                + type.getName()
                );
            }

            callbackMethod = method;
        }

        if (callbackMethod == null) {
            throw new IllegalArgumentException(
                    "Native callback must declare an abstract method: "
                            + type.getName()
            );
        }

        return callbackMethod;
    }

    private static MethodType toMethodType(FunctionDescriptor descriptor) {
        Class<?>[] parameterTypes = descriptor.argumentLayouts()
                .stream()
                .map(NativeValueConverter::getCarrier)
                .toArray(Class<?>[]::new);

        Class<?> returnType = descriptor.returnLayout()
                .<Class<?>>map(NativeValueConverter::getCarrier)
                .orElse(void.class);

        return MethodType.methodType(returnType, parameterTypes);
    }

    private static Class<?> getCarrier(MemoryLayout layout) {
        if (layout instanceof ValueLayout valueLayout) {
            return valueLayout.carrier();
        }

        throw new IllegalArgumentException(
                "Unsupported callback layout: " + layout
        );
    }

    private static Object invokeCallback(
            Object callback,
            Method method,
            Arena arena,
            Object[] nativeArguments
    ) throws Throwable {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = fromNative(
                    parameterTypes[i],
                    nativeArguments[i]
            );
        }

        Object result;

        try {
            result = method.invoke(callback, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }

        return toNative(
                method.getReturnType(),
                result,
                arena
        );
    }

    private static int getEnumValue(Enum<?> value) {
        try {
            Field field = value.getDeclaringClass().getField(value.name());
            NativeEnumValue annotation = field.getAnnotation(NativeEnumValue.class);

            if (annotation == null) {
                throw new IllegalArgumentException(
                        "Missing @NativeEnumValue on " + value.name()
                );
            }

            return annotation.value();
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T> T getEnumConstant(Class<T> type, int value) {
        for (Object constant : type.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;

            if (getEnumValue(enumValue) == value) {
                return type.cast(constant);
            }
        }

        throw new IllegalArgumentException("Unknown native enum value: " + value);
    }

    private record CallbackPlan(
            Method method,
            FunctionDescriptor descriptor,
            MethodType methodType
    ) {
    }
}
