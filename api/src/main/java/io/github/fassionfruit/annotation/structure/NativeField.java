package io.github.fassionfruit.annotation.structure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares one native field getter or setter. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NativeField {
    /** The native field name. */
    String value();

    /** The zero-based field order used to construct the layout. */
    int order();

    /** The fixed element count for a {@code NativeArray} field. */
    int length() default 0;
}
