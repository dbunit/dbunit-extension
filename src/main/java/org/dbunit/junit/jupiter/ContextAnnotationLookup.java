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

import org.dbunit.annotation.runtime.AnnotationLookup;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Finds the {@code org.dbunit.annotation} annotations of the test an {@link ExtensionContext} is
 * running, and the classes declaring them, the JUnit Jupiter way: on the test method first and
 * the class hierarchy - including enclosing classes, for {@code @Nested} support - second, so a
 * method-level annotation continues to win over a class-level one. The method is searched on its
 * own, not through the methods it overrides.
 *
 * <p>The enclosing classes come from {@link EnclosingTestClasses}, and each class is searched on
 * its own, the closest enclosing class first, rather than through the {@link AnnotationSupport}
 * overload that takes the enclosing classes: that overload and
 * {@code ExtensionContext.getEnclosingTestClasses()} are newer than JUnit Jupiter 5.7, the
 * oldest release the extension supports, and JUnit marks both maintained API since its 5.13.3
 * release.
 *
 * <p>The declaring class comes from {@link DeclaringClassFinder}, which looks where
 * {@link AnnotationSupport} does, in the same order, so it names the declaration
 * {@link #find(Class)} returned. {@link #findAll(Class)} comes from {@link DeclarationsFinder},
 * which looks in that order too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class ContextAnnotationLookup implements AnnotationLookup
{
    private final ExtensionContext context;

    /**
     * Creates the lookup for the test the context is running.
     *
     * @param context The extension context of the test method, or of the test class when it has
     *            no test method.
     */
    ContextAnnotationLookup(final ExtensionContext context)
    {
        this.context = context;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Looks on the test method, then on the test class, then on the enclosing classes of a
     * nested test class, the closest one first.
     */
    @Override
    public <A extends Annotation> A find(final Class<A> annotationType)
    {
        final Optional<Method> method = context.getTestMethod();
        if (method.isPresent())
        {
            final Optional<A> onMethod =
                    AnnotationSupport.findAnnotation(method.get(), annotationType);
            if (onMethod.isPresent())
            {
                return onMethod.get();
            }
        }
        final Optional<A> onTestClass =
                AnnotationSupport.findAnnotation(context.getRequiredTestClass(), annotationType);
        if (onTestClass.isPresent())
        {
            return onTestClass.get();
        }
        return findOnEnclosingClasses(annotationType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Collects them with a {@link DeclarationsFinder}, which searches in the order
     * {@link #find(Class)} does.
     */
    @Override
    public <A extends Annotation> List<A> findAll(final Class<A> annotationType)
    {
        final DeclarationsFinder finder = new DeclarationsFinder(context.getTestMethod(),
                context.getRequiredTestClass(), EnclosingTestClasses.of(context));
        return finder.findAll(annotationType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Asks a {@link DeclaringClassFinder}, which looks where {@link #find(Class)} does, so the
     * class it names declares the annotation {@link #find(Class)} returns.
     */
    @Override
    public Class<?> findDeclaringClass(final Class<? extends Annotation> annotationType)
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(context.getTestMethod(),
                context.getRequiredTestClass(), EnclosingTestClasses.of(context));
        return finder.find(annotationType);
    }

    private <A extends Annotation> A findOnEnclosingClasses(final Class<A> annotationType)
    {
        final List<Class<?>> enclosingClasses = EnclosingTestClasses.of(context);
        for (int i = enclosingClasses.size() - 1; i >= 0; i--)
        {
            final Optional<A> onEnclosingClass =
                    AnnotationSupport.findAnnotation(enclosingClasses.get(i), annotationType);
            if (onEnclosingClass.isPresent())
            {
                return onEnclosingClass.get();
            }
        }
        return null;
    }
}
