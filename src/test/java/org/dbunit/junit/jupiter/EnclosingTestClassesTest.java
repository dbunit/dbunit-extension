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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Unit tests of {@link EnclosingTestClasses}. The contexts are {@link NestedContextChain} stubs,
 * which fail {@link ExtensionContext#getEnclosingTestClasses()} like the JUnit Jupiter 5.7 floor,
 * which lacks it, so the classes can only come from the parent contexts.
 */
class EnclosingTestClassesTest
{
    @Test
    void testOf_topLevelTestClass_returnsEmptyList()
    {
        final ExtensionContext context = NestedContextChain.methodContextOf(Outer.class);

        assertThat(EnclosingTestClasses.of(context))
                .as("A test class that is not nested has no enclosing test class.")
                .isEqualTo(Collections.emptyList());
    }

    @Test
    void testOf_nestedTestClass_returnsTheEnclosingClass()
    {
        final ExtensionContext context =
                NestedContextChain.methodContextOf(Outer.Middle.class, Outer.class);

        assertThat(EnclosingTestClasses.of(context))
                .as("The class enclosing a nested test class must be listed.")
                .isEqualTo(Collections.singletonList(Outer.class));
    }

    @Test
    void testOf_nestedTwice_returnsTheEnclosingClassesOutermostFirst()
    {
        final ExtensionContext context = NestedContextChain.methodContextOf(
                Outer.Middle.Inner.class, Outer.class, Outer.Middle.class);

        assertThat(EnclosingTestClasses.of(context))
                .as("The enclosing classes must be listed from the outermost to the closest.")
                .isEqualTo(Arrays.asList(Outer.class, Outer.Middle.class));
    }

    @Test
    void testOf_contextOfTheNestedClassItself_returnsTheEnclosingClass()
    {
        final ExtensionContext rootContext = NestedContextChain.rootContext();
        final ExtensionContext outerContext =
                NestedContextChain.contextOf(Outer.class, rootContext);
        final ExtensionContext classContext =
                NestedContextChain.contextOf(Outer.Middle.class, outerContext);

        assertThat(EnclosingTestClasses.of(classContext))
                .as("The context of a test class, as opposed to one of its methods, must list the"
                        + " same enclosing classes.")
                .isEqualTo(Collections.singletonList(Outer.class));
    }

    @Test
    void testOf_ancestorContextsRepeatAClass_listsEachClassOnce()
    {
        final ExtensionContext rootContext = NestedContextChain.rootContext();
        final ExtensionContext templateContext =
                NestedContextChain.contextOf(Outer.class, rootContext);
        final ExtensionContext invocationContext =
                NestedContextChain.contextOf(Outer.class, templateContext);
        final ExtensionContext methodContext =
                NestedContextChain.contextOf(Outer.Middle.class, invocationContext);

        assertThat(EnclosingTestClasses.of(methodContext))
                .as("Several ancestor contexts of one class, as a class template has, must list"
                        + " the class once.")
                .isEqualTo(Collections.singletonList(Outer.class));
    }

    static class Outer
    {
        class Middle
        {
            class Inner
            {
            }
        }
    }
}
