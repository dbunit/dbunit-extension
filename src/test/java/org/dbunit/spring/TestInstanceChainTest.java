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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

class TestInstanceChainTest
{
    @Test
    void testOf_topLevelInstance_chainHoldsOnlyThatInstance()
    {
        final Object instance = new Object();

        final TestInstanceChain chain = TestInstanceChain.of(instance);

        assertThat(chain.getInnermostFirst())
                .as("An instance of a class that is not an inner class has no enclosing"
                        + " instance.")
                .containsExactly(instance);
        assertThat(chain.getUnreachableEnclosingClasses())
                .as("Nothing is missing from a chain of one.").isEmpty();
    }

    @Test
    void testOf_innerClassInstance_chainHoldsInnerThenEnclosingInstance()
    {
        final Outer outer = new Outer();
        final Outer.Inner inner = outer.new Inner();

        final TestInstanceChain chain = TestInstanceChain.of(inner);

        assertThat(chain.getInnermostFirst())
                .as("The chain must run from the inner instance out to the instance enclosing"
                        + " it.")
                .containsExactly(inner, outer);
        assertThat(chain.getUnreachableEnclosingClasses())
                .as("Nothing is missing from a complete chain.").isEmpty();
    }

    @Test
    void testOf_twoLevelsOfInnerClasses_chainRunsInnermostToOutermost()
    {
        final Outer outer = new Outer();
        final Outer.Inner inner = outer.new Inner();
        final Outer.Inner.Innermost innermost = inner.new Innermost();

        final TestInstanceChain chain = TestInstanceChain.of(innermost);

        assertThat(chain.getInnermostFirst())
                .as("The chain must follow the enclosing instances all the way out.")
                .containsExactly(innermost, inner, outer);
    }

    @Test
    void testOf_staticNestedClassInstance_enclosingClassNotSearched()
    {
        final Outer.StaticNested nested = new Outer.StaticNested();

        final TestInstanceChain chain = TestInstanceChain.of(nested);

        assertThat(chain.getInnermostFirst())
                .as("A static nested class has no enclosing instance to reach.")
                .containsExactly(nested);
        assertThat(chain.getUnreachableEnclosingClasses())
                .as("A static nested class has no enclosing instance to miss.").isEmpty();
    }

    @Test
    void testOf_innerClassOverridingNestedTestConfiguration_enclosingClassNotSearched()
    {
        final Outer outer = new Outer();
        final Outer.OverridingInner inner = outer.new OverridingInner();

        final TestInstanceChain chain = TestInstanceChain.of(inner);

        assertThat(chain.getInnermostFirst())
                .as("@NestedTestConfiguration(OVERRIDE) means Spring does not inherit the"
                        + " enclosing class's configuration, so its instance is not searched.")
                .containsExactly(inner);
        assertThat(chain.getUnreachableEnclosingClasses())
                .as("An enclosing class that is deliberately not searched is not missing.")
                .isEmpty();
    }

    @Test
    void testOf_enclosingInstanceReferenceNull_reportsEnclosingClassesAsUnreachable()
            throws Exception
    {
        final Outer outer = new Outer();
        final Outer.Inner inner = outer.new Inner();
        final Outer.Inner.Innermost innermost = inner.new Innermost();
        clearEnclosingReference(innermost);

        final TestInstanceChain chain = TestInstanceChain.of(innermost);

        assertThat(chain.getInnermostFirst())
                .as("The chain must stop at the last instance whose enclosing instance it can"
                        + " reach.")
                .containsExactly(innermost);
        assertThat(chain.getUnreachableEnclosingClasses())
                .as("Every enclosing class beyond the break must be reported, innermost first.")
                .containsExactly(Outer.Inner.class, Outer.class);
    }

    /**
     * Nulls the compiler-generated reference to the enclosing instance, as if {@code javac} had
     * omitted it.
     */
    static void clearEnclosingReference(final Object innerInstance) throws Exception
    {
        for (final Field field : innerInstance.getClass().getDeclaredFields())
        {
            if (field.isSynthetic())
            {
                field.setAccessible(true);
                field.set(innerInstance, null);
            }
        }
    }

    /**
     * Each inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    static class Outer
    {
        class Inner
        {
            class Innermost
            {
                Object enclosing()
                {
                    return Inner.this;
                }
            }

            Object enclosing()
            {
                return Outer.this;
            }
        }

        static class StaticNested
        {
        }

        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        class OverridingInner
        {
            Object enclosing()
            {
                return Outer.this;
            }
        }
    }
}
