package io.github.fassionfruit.annotation;

import io.github.fassionfruit.annotation.structure.NativeStructure;
import io.github.fassionfruit.annotation.structure.NativeUnion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a native pointer parameter or structure field.
 *
 * <p>This annotation allows the binding to validate whether a null
 * pointer is permitted.</p>
 *
 * <p>When {@code nullable} is {@code false}, passing a null value
 * results in an {@link IllegalArgumentException}.</p>
 *
 * <p>This annotation does not transfer ownership of the referenced
 * native memory. The caller is responsible for managing its lifetime.</p>
 *
 * @see NativeStructure
 * @see NativeUnion
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.TYPE_USE
})
public @interface NativePointer {

    /**
     * Whether the native pointer may be null.
     *
     * @return {@code true} if null pointers are allowed
     */
    boolean nullable() default true;
}
