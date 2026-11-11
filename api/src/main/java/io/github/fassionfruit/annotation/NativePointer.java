package io.github.fassionfruit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that a native value is an address.
 *
 * <p>Pointer values use {@code MemorySegment} as their Java representation.
 * The annotation is optional because {@code MemorySegment} already maps to an
 * address layout, but it makes pointer intent explicit in public bindings and
 * structure declarations.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
public @interface NativePointer {
    /** Whether the native pointer may be null. */
    boolean nullable() default true;
}
