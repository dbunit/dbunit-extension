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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Finds the classes enclosing the test class of an {@link ExtensionContext}, which a
 * {@code @Nested} test class has and a top-level one does not.
 *
 * <p>The classes come from the test classes of the context's ancestors, which is where JUnit
 * Jupiter takes them from as well. Calling {@code ExtensionContext.getEnclosingTestClasses()}
 * instead would raise the JUnit Jupiter version the extension needs above 5.7, the oldest
 * release it supports, because that method is newer: JUnit marks it maintained API since 5.13.3.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class EnclosingTestClasses
{
    private EnclosingTestClasses()
    {
    }

    /**
     * Lists the classes enclosing the test class of a context.
     *
     * @param context The context of a test method or of a test class.
     * @return The test classes of the context's ancestors, outermost first, without the context's
     *         own test class and without repeats; empty for a test class that is not
     *         {@code @Nested}.
     */
    static List<Class<?>> of(final ExtensionContext context)
    {
        final Class<?> testClass = context.getRequiredTestClass();
        final List<Class<?>> closestFirst = new ArrayList<>();
        Optional<ExtensionContext> ancestor = context.getParent();
        while (ancestor.isPresent())
        {
            final ExtensionContext ancestorContext = ancestor.get();
            addIfEnclosing(closestFirst, ancestorContext.getTestClass(), testClass);
            ancestor = ancestorContext.getParent();
        }
        Collections.reverse(closestFirst);
        return Collections.unmodifiableList(closestFirst);
    }

    private static void addIfEnclosing(final List<Class<?>> enclosingClasses,
            final Optional<Class<?>> ancestorTestClass, final Class<?> testClass)
    {
        if (!ancestorTestClass.isPresent())
        {
            return;
        }
        final Class<?> candidate = ancestorTestClass.get();
        final boolean isEnclosing = candidate != testClass && !enclosingClasses.contains(candidate);
        if (isEnclosing)
        {
            enclosingClasses.add(candidate);
        }
    }
}
