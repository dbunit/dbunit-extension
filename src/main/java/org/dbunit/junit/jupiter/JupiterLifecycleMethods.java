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
package org.dbunit.junit.jupiter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dbunit.annotation.runtime.DeclaredMethodsOfHierarchy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Finds the lifecycle methods of a test class: the {@code @BeforeEach}, {@code @AfterEach},
 * {@code @BeforeAll} and {@code @AfterAll} methods the class declares or inherits from
 * superclasses and interfaces, including interface default methods and methods that carry the
 * annotation as a meta-annotation, and the {@code @BeforeParameterizedClassInvocation} and
 * {@code @AfterParameterizedClassInvocation} methods of a {@code @ParameterizedClass}.
 *
 * <p>A method counts where its class declares it, so the list holds a superclass method that a
 * subclass overrides too, whether or not the overriding method carries the annotation. JUnit runs
 * only the overriding method, or none when the override has no lifecycle annotation, yet no
 * binding reads an annotation of dbUnit on the overridden method.
 *
 * <p>The two annotations of a {@code @ParameterizedClass} are matched by name, as the Spring
 * binding matches its annotations: they exist only since JUnit Jupiter 5.13 and live in the
 * separate {@code junit-jupiter-params} artifact, which a test class may not have.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class JupiterLifecycleMethods
{
    private static final List<Class<? extends Annotation>> LIFECYCLE_ANNOTATIONS =
            Arrays.asList(BeforeEach.class, AfterEach.class, BeforeAll.class, AfterAll.class);

    private static final Set<String> LIFECYCLE_ANNOTATION_NAMES = new HashSet<>(
            Arrays.asList("org.junit.jupiter.params.BeforeParameterizedClassInvocation",
                    "org.junit.jupiter.params.AfterParameterizedClassInvocation"));

    private JupiterLifecycleMethods()
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

    private static boolean isLifecycleMethod(final Method method)
    {
        for (final Class<? extends Annotation> annotationType : LIFECYCLE_ANNOTATIONS)
        {
            if (AnnotationSupport.isAnnotated(method, annotationType))
            {
                return true;
            }
        }
        return isAnnotatedByName(method.getAnnotations(), new HashSet<>());
    }

    private static boolean isAnnotatedByName(final Annotation[] annotations,
            final Set<Class<? extends Annotation>> visited)
    {
        for (final Annotation annotation : annotations)
        {
            final Class<? extends Annotation> annotationType = annotation.annotationType();
            final String annotationName = annotationType.getName();
            if (LIFECYCLE_ANNOTATION_NAMES.contains(annotationName))
            {
                return true;
            }
            final boolean firstVisit = visited.add(annotationType);
            if (firstVisit && isAnnotatedByName(annotationType.getAnnotations(), visited))
            {
                return true;
            }
        }
        return false;
    }
}
