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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds every declaration of an annotation that applies to a test, nearest first, looking where
 * {@link DeclaringClassFinder} does, in the same order: the test method, then the test class -
 * the class itself, then its interfaces, then its superclass, each searched the same way - then
 * the enclosing classes of a {@code @Nested} test class, closest first. On each element the
 * annotations declared on it come before the ones reached through a composed annotation it
 * carries.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class DeclarationsFinder
{
    private final Optional<Method> testMethod;
    private final Class<?> testClass;
    private final List<Class<?>> enclosingTestClasses;

    /**
     * Creates a finder for one test.
     *
     * @param testMethod The test method, if the context has one.
     * @param testClass The concrete test class.
     * @param enclosingTestClasses The classes enclosing {@code testClass}, outermost first; empty
     *            for a class that is not {@code @Nested}.
     */
    DeclarationsFinder(final Optional<Method> testMethod, final Class<?> testClass,
            final List<Class<?>> enclosingTestClasses)
    {
        this.testMethod = testMethod;
        this.testClass = testClass;
        this.enclosingTestClasses = enclosingTestClasses;
    }

    /**
     * Finds the declarations of {@code annotationType} for this test.
     *
     * @param <A> The annotation type.
     * @param annotationType The annotation to find.
     * @return The declarations, nearest first; empty when there are none.
     */
    <A extends Annotation> List<A> findAll(final Class<A> annotationType)
    {
        final List<A> found = new ArrayList<>();
        if (testMethod.isPresent())
        {
            collectFrom(testMethod.get(), annotationType, found);
        }
        collectFromHierarchy(testClass, annotationType, found);
        for (int i = enclosingTestClasses.size() - 1; i >= 0; i--)
        {
            collectFromHierarchy(enclosingTestClasses.get(i), annotationType, found);
        }
        return found;
    }

    private static <A extends Annotation> void collectFromHierarchy(final Class<?> candidate,
            final Class<A> annotationType, final List<A> found)
    {
        if (candidate == null || candidate == Object.class)
        {
            return;
        }
        collectFrom(candidate, annotationType, found);
        for (final Class<?> implemented : candidate.getInterfaces())
        {
            collectFromHierarchy(implemented, annotationType, found);
        }
        collectFromHierarchy(candidate.getSuperclass(), annotationType, found);
    }

    private static <A extends Annotation> void collectFrom(final AnnotatedElement element,
            final Class<A> annotationType, final List<A> found)
    {
        collectFrom(element, annotationType, found, new HashSet<>());
    }

    private static <A extends Annotation> void collectFrom(final AnnotatedElement element,
            final Class<A> annotationType, final List<A> found,
            final Set<Class<? extends Annotation>> visited)
    {
        for (final Annotation declared : element.getDeclaredAnnotations())
        {
            if (declared.annotationType() == annotationType)
            {
                found.add(annotationType.cast(declared));
            }
        }
        for (final Annotation declared : element.getDeclaredAnnotations())
        {
            final Class<? extends Annotation> declaredType = declared.annotationType();
            if (declaredType != annotationType && !isInJavaLang(declaredType)
                    && visited.add(declaredType))
            {
                collectFrom(declaredType, annotationType, found, visited);
            }
        }
    }

    private static boolean isInJavaLang(final Class<? extends Annotation> annotationType)
    {
        return annotationType.getName().startsWith("java.lang.annotation.");
    }
}
