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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class DeclaredMethodsOfHierarchyTest
{
    @Test
    void testOf_classWithSuperclassAndInterfaces_listsTheMethodsEachDeclares()
    {
        final List<Method> methods = DeclaredMethodsOfHierarchy.of(Sub.class);

        assertThat(namesOf(methods))
                .as("The class's own methods come first, then those of its interfaces and"
                        + " superclass, a super-interface's included.")
                .contains("Sub#own", "Contract#contractDefault", "SuperContract#superDefault",
                        "Base#inherited")
                .containsSubsequence("Sub#own", "Base#inherited");
    }

    @Test
    void testOf_methodOverriddenInTheSubclass_listsItForEachDeclaringClass()
    {
        final List<Method> methods = DeclaredMethodsOfHierarchy.of(Sub.class);

        assertThat(namesOf(methods))
                .as("An overridden method is listed where each class declares it, not once.")
                .contains("Sub#overridden", "Base#overridden");
    }

    @Test
    void testOf_interfaceReachedTwice_listsItsMethodsOnce()
    {
        final List<Method> methods = DeclaredMethodsOfHierarchy.of(ImplementsTwice.class);

        assertThat(namesOf(methods).stream().filter("SuperContract#superDefault"::equals).count())
                .as("An interface that the hierarchy reaches by two routes is searched once.")
                .isEqualTo(1);
    }

    @Test
    void testOf_syntheticMethods_areLeftOut()
    {
        final List<Method> methods = DeclaredMethodsOfHierarchy.of(Generic.class);

        assertThat(methods).as("A bridge method that a compiler adds is no declared method.")
                .noneMatch(Method::isSynthetic);
        assertThat(namesOf(methods)).contains("Generic#apply");
    }

    private static List<String> namesOf(final List<Method> methods)
    {
        return methods.stream()
                .map(method -> method.getDeclaringClass().getSimpleName() + "#" + method.getName())
                .collect(Collectors.toList());
    }

    interface SuperContract
    {
        default void superDefault()
        {
        }
    }

    interface Contract extends SuperContract
    {
        default void contractDefault()
        {
        }
    }

    static class Base
    {
        void inherited()
        {
        }

        void overridden()
        {
        }
    }

    static class Sub extends Base implements Contract
    {
        void own()
        {
        }

        @Override
        void overridden()
        {
        }
    }

    static class ImplementsTwice extends Base implements Contract, SuperContract
    {
    }

    interface Transformer<T>
    {
        T apply(T value);
    }

    static class Generic implements Transformer<String>
    {
        @Override
        public String apply(final String value)
        {
            return value;
        }
    }
}
