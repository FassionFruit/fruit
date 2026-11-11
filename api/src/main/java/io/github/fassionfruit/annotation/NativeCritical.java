package io.github.fassionfruit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests a critical FFM downcall for the annotated native function.
 *
 * <p>Critical calls are intended for short-running native functions. The
 * native linker may reject a critical call when its descriptor is not
 * eligible for critical invocation.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NativeCritical {
}
