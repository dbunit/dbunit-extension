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
 * <p>The declaring class comes from {@link DeclaringClassFinder}, which looks where
 * {@link AnnotationSupport} does, in the same order, so it names the declaration
 * {@link #find(Class)} returned.
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
        return AnnotationSupport.findAnnotation(context.getRequiredTestClass(), annotationType,
                context.getEnclosingTestClasses()).orElse(null);
    }

    @Override
    public Class<?> findDeclaringClass(final Class<? extends Annotation> annotationType)
    {
        final DeclaringClassFinder finder = new DeclaringClassFinder(context.getTestMethod(),
                context.getRequiredTestClass(), context.getEnclosingTestClasses());
        return finder.find(annotationType);
    }
}
