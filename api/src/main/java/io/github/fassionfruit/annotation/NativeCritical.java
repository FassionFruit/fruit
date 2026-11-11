package io.github.fassionfruit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests a critical downcall for the annotated native function.
 *
 * <p>Critical downcalls are intended for short-running native functions
 * that do not call back into the JVM or block for extended periods.</p>
 *
 * <p>This option cannot be combined with {@link NativeErrno}.</p>
 *
 * <p>The native linker may reject unsupported function descriptors.</p>
 *
 * @see NativeErrno
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NativeCritical {
}
