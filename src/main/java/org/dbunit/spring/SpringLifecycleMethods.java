/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.dbunit.annotation.runtime.DeclaredMethodsOfHierarchy;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * Finds the lifecycle methods of a test class that JUnit Jupiter and Spring run around a Spring
 * test: the {@code @BeforeEach}, {@code @AfterEach}, {@code @BeforeAll} and {@code @AfterAll}
 * methods the class declares or inherits from superclasses and interfaces, including interface
 * default methods and methods that carry the annotation as a meta-annotation, the
 * {@code @BeforeParameterizedClassInvocation} and {@code @AfterParameterizedClassInvocation}
 * methods of a JUnit {@code @ParameterizedClass}, and the {@code @BeforeTransaction} and {@code @AfterTransaction} methods of Spring's transaction
 * support, which no dbUnit annotation is read on either.
 *
 * <p>A method counts where its class declares it, so the list holds a superclass method that a
 * subclass overrides too, whether or not the overriding method carries the annotation. JUnit runs
 * only the overriding method, or none when the override has no lifecycle annotation, yet no
 * binding reads an annotation of dbUnit on the overridden method.
 *
 * <p>The listener does not depend on JUnit Jupiter, so it matches the annotations by name, as it
 * does for {@code @ExtendWith}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class SpringLifecycleMethods
{
    private static final List<String> LIFECYCLE_ANNOTATION_NAMES =
            Arrays.asList("org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.AfterEach",
                    "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.AfterAll",
                    "org.junit.jupiter.params.BeforeParameterizedClassInvocation",
                    "org.junit.jupiter.params.AfterParameterizedClassInvocation",
                    "org.springframework.test.context.transaction.BeforeTransaction",
                    "org.springframework.test.context.transaction.AfterTransaction");

    private SpringLifecycleMethods()
    {
    }

    /**
     * Lists the lifecycle methods of a class.
     *
     * @param type The test class, or a class enclosing it.
     * @return The lifecycle methods the class and its superclasses and interfaces declare, each
     *         once, an overridden one included.
     */
    static Collection<Method> of(final Class<?> type)
    {
        final List<Method> lifecycleMethods = new ArrayList<>();
        for (final Method method : DeclaredMethodsOfHierarchy.of(type))
        {
            if (isLifecycleMethod(method))
            {
                lifecycleMethods.add(method);
            }
        }
        return lifecycleMethods;
    }

    /**
     * Tells whether an annotation is on a method itself: among the method's own annotations or
     * the annotations that those carry, and not among those of a method it overrides. The check
     * names the method that declares the annotation with it.
     *
     * @param method The method to look at.
     * @param annotationType The annotation type to look for.
     * @return Whether the method declares the annotation, directly or as a meta-annotation.
     */
    static boolean declares(final Method method, final Class<? extends Annotation> annotationType)
    {
        return MergedAnnotations.from(method, SearchStrategy.DIRECT).isPresent(annotationType);
    }

    private static boolean isLifecycleMethod(final Method method)
    {
        final MergedAnnotations annotations =
                MergedAnnotations.from(method, SearchStrategy.DIRECT);
        for (final String annotationName : LIFECYCLE_ANNOTATION_NAMES)
        {
            if (annotations.isPresent(annotationName))
            {
                return true;
            }
        }
        return false;
    }
}
