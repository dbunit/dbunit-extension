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
import java.util.List;

import org.dbunit.annotation.runtime.AnnotationLookup;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Finds the {@code org.dbunit.annotation} annotations of the test method a Spring
 * {@link TestContext} is running, and the classes declaring them, the way {@code DbUnitExtension}
 * does for JUnit Jupiter: on the test method first and the test class second, so a method-level
 * annotation wins over a class-level one.
 *
 * <p>The method is searched on its own, including meta-annotations but not the methods it
 * overrides, so an overriding test method that declares nothing does not pick up the
 * annotations of the method it overrides - as under {@code DbUnitExtension}. The class is
 * searched the way Spring's {@link TestContextAnnotationUtils} searches a test class:
 * meta-annotations, superclasses and interfaces, and the enclosing classes of a {@code @Nested}
 * class whose configuration Spring inherits.
 *
 * <p>The annotation and the class declaring it come from one and the same search, so they never
 * name different declarations.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class SpringAnnotationLookup implements AnnotationLookup
{
    private final Class<?> testClass;
    private final Method testMethod;

    /**
     * Creates the lookup for the test the context is currently running.
     *
     * @param testContext The test context, with its test method set.
     */
    SpringAnnotationLookup(final TestContext testContext)
    {
        this.testClass = testContext.getTestClass();
        this.testMethod = testContext.getTestMethod();
    }

    @Override
    public <A extends Annotation> A find(final Class<A> annotationType)
    {
        final MergedAnnotation<A> found = search(annotationType);
        if (found.isPresent())
        {
            return found.synthesize();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Lists those on the test method first, then those Spring finds on the test class, through
     * its type hierarchy and the enclosing classes of a nested test class.
     */
    @Override
    public <A extends Annotation> List<A> findAll(final Class<A> annotationType)
    {
        final List<A> found = new ArrayList<>();
        MergedAnnotations.from(testMethod, SearchStrategy.DIRECT).stream(annotationType)
                .forEach(onMethod -> found.add(onMethod.synthesize()));
        MergedAnnotations.search(SearchStrategy.TYPE_HIERARCHY)
                .withEnclosingClasses(TestContextAnnotationUtils::searchEnclosingClass)
                .from(testClass).stream(annotationType)
                .forEach(onClass -> found.add(onClass.synthesize()));
        return found;
    }

    @Override
    public Class<?> findDeclaringClass(final Class<? extends Annotation> annotationType)
    {
        final MergedAnnotation<? extends Annotation> found = search(annotationType);
        final Object source = found.getSource();
        if (source instanceof Method)
        {
            return ((Method) source).getDeclaringClass();
        }
        if (source instanceof Class)
        {
            return (Class<?>) source;
        }
        return testClass;
    }

    private <A extends Annotation> MergedAnnotation<A> search(final Class<A> annotationType)
    {
        final MergedAnnotations onMethod =
                MergedAnnotations.from(testMethod, SearchStrategy.DIRECT);
        final MergedAnnotation<A> foundOnMethod = onMethod.get(annotationType);
        if (foundOnMethod.isPresent())
        {
            return foundOnMethod;
        }
        return MergedAnnotations.search(SearchStrategy.TYPE_HIERARCHY)
                .withEnclosingClasses(TestContextAnnotationUtils::searchEnclosingClass)
                .from(testClass).get(annotationType);
    }
}
