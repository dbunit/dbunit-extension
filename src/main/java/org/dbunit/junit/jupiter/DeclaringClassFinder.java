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
import java.util.List;
import java.util.Optional;

import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Finds the class that declares an annotation the extension resolved for a test, so a dataset
 * path written relative to a package can be resolved against the package of the class the
 * author wrote it in, rather than that of the concrete test class that merely inherits it.
 *
 * <p>Looks where {@link AnnotationSupport} does, in the same order: the test method first, then
 * the test class - the class itself, then its interfaces, then its superclass, each searched the
 * same way - then the enclosing classes of a {@code @Nested} test class, closest first. An
 * annotation found through a composed annotation counts as declared by the class or interface
 * carrying the composed annotation.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class DeclaringClassFinder
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
    DeclaringClassFinder(final Optional<Method> testMethod, final Class<?> testClass,
            final List<Class<?>> enclosingTestClasses)
    {
        this.testMethod = testMethod;
        this.testClass = testClass;
        this.enclosingTestClasses = enclosingTestClasses;
    }

    /**
     * Finds the class declaring {@code annotationType} for this test.
     *
     * @param annotationType The annotation to find the declaring class of.
     * @return The class or interface declaring it, or the test class when it is not found on any
     *         class this looks at.
     */
    Class<?> find(final Class<? extends Annotation> annotationType)
    {
        if (testMethod.isPresent() && AnnotationSupport.isAnnotated(testMethod.get(), annotationType))
        {
            return testMethod.get().getDeclaringClass();
        }
        final Class<?> inHierarchy = findInHierarchy(testClass, annotationType);
        if (inHierarchy != null)
        {
            return inHierarchy;
        }
        for (int i = enclosingTestClasses.size() - 1; i >= 0; i--)
        {
            final Class<?> inEnclosing = findInHierarchy(enclosingTestClasses.get(i), annotationType);
            if (inEnclosing != null)
            {
                return inEnclosing;
            }
        }
        return testClass;
    }

    private static Class<?> findInHierarchy(final Class<?> candidate,
            final Class<? extends Annotation> annotationType)
    {
        if (candidate == null)
        {
            return null;
        }
        if (declares(candidate, annotationType))
        {
            return candidate;
        }
        for (final Class<?> implemented : candidate.getInterfaces())
        {
            final Class<?> inInterface = findInHierarchy(implemented, annotationType);
            if (inInterface != null)
            {
                return inInterface;
            }
        }
        return findInHierarchy(candidate.getSuperclass(), annotationType);
    }

    private static boolean declares(final Class<?> candidate,
            final Class<? extends Annotation> annotationType)
    {
        for (final Annotation declared : candidate.getDeclaredAnnotations())
        {
            final Class<? extends Annotation> declaredType = declared.annotationType();
            if (declaredType == annotationType
                    || AnnotationSupport.isAnnotated(declaredType, annotationType))
            {
                return true;
            }
        }
        return false;
    }
}
