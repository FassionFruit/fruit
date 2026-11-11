package io.github.fassionfruit.annotation.structure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares one method slot in a {@link JvmStructure} vtable. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JvmMethod {
    /** The zero-based vtable slot order. */
    int order() default -1;
}
