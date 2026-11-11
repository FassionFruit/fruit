package io.github.fassionfruit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Assigns the integer value used for an enum constant in native code. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NativeEnumValue {
    /** The native integer representation. */
    int value();
}
