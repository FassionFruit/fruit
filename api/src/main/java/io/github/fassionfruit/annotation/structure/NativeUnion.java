package io.github.fassionfruit.annotation.structure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as an inline native union.
 *
 * <p>Each {@link NativeField} occupies the same storage. The Java binding is
 * responsible for selecting the member to read or write; a union does not
 * carry a discriminator by itself.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NativeUnion {
}
