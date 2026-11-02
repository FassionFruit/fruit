package io.github.fassionfruit.jvm;

import io.github.fassionfruit.annotation.structure.JvmMethod;
import io.github.fassionfruit.annotation.structure.JvmStructure;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class JvmStructureLayout {

    private JvmStructureLayout() {
    }

    public static MemoryLayout create(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException(
                    type.getName() + " must be an interface"
            );
        }

        if (!type.isAnnotationPresent(JvmStructure.class)) {
            throw new IllegalArgumentException(
                    type.getName() + " is missing @JvmStructure"
            );
        }

        List<Method> methods = getMethods(type);

        MemoryLayout[] layouts = new MemoryLayout[methods.size() + 1];

        layouts[0] = ValueLayout.ADDRESS.withName("context");

        for (int i = 0; i < methods.size(); i++) {
            layouts[i + 1] = ValueLayout.ADDRESS.withName(
                    methods.get(i).getName()
            );
        }

        return MemoryLayout.structLayout(layouts);
    }

    public static List<Method> getMethods(Class<?> type) {
        Method[] declaredMethods = type.getMethods();

        Set<Integer> orders = new HashSet<>();
        Set<String> names = new HashSet<>();

        List<Method> methods = Arrays.stream(declaredMethods)
                .filter(method -> {
                    int modifiers = method.getModifiers();

                    return Modifier.isAbstract(modifiers)
                            && !Modifier.isStatic(modifiers)
                            && !method.isBridge()
                            && !method.isSynthetic();
                })
                .peek(method -> {
                    JvmMethod annotation = method.getAnnotation(JvmMethod.class);

                    if (annotation == null) {
                        throw new IllegalArgumentException(
                                method.getName() + " is missing @JvmMethod"
                        );
                    }

                    if (annotation.order() < 0) {
                        throw new IllegalArgumentException(
                                method.getName() + " must declare a non-negative order"
                        );
                    }

                    if (!orders.add(annotation.order())) {
                        throw new IllegalArgumentException(
                                "Duplicate @JvmMethod order: " + annotation.order()
                        );
                    }

                    if (!names.add(method.getName())) {
                        throw new IllegalArgumentException(
                                "Overloaded @JvmMethod is not supported: "
                                        + method.getName()
                        );
                    }
                })
                .sorted(Comparator.comparingInt(
                        method -> method.getAnnotation(JvmMethod.class).order()
                ))
                .toList();

        if (methods.isEmpty()) {
            throw new IllegalArgumentException(
                    type.getName() + " must declare at least one @JvmMethod"
            );
        }

        for (int i = 0; i < methods.size(); i++) {
            int order = methods.get(i).getAnnotation(JvmMethod.class).order();

            if (order != i) {
                throw new IllegalArgumentException(
                        "@JvmMethod order must be continuous from 0"
                );
            }
        }

        return methods;
    }
}