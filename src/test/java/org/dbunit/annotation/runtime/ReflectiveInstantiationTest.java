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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReflectiveInstantiationTest
{
    private final ReflectiveInstantiation instantiation = new ReflectiveInstantiation();

    @Test
    void testInstantiate_publicNoArgConstructor_returnsNewInstance()
    {
        final PublicNoArg instance = instantiation.instantiate(PublicNoArg.class, "someAttribute()");

        assertThat(instance).as("A public no-arg constructor must be invoked directly.")
                .isNotNull();
    }

    @Test
    void testInstantiate_privateNoArgConstructor_returnsNewInstanceAnyway()
    {
        final PrivateNoArg instance =
                instantiation.instantiate(PrivateNoArg.class, "someAttribute()");

        assertThat(instance)
                .as("A private no-arg constructor must still be invoked - setAccessible(true)"
                        + " overrides Java's own access check - since an annotation-named"
                        + " implementation class is commonly package-private or"
                        + " private-constructed.")
                .isNotNull();
    }

    @Test
    void testInstantiate_noNoArgConstructor_throwsIllegalStateExceptionNamingTheAttribute()
    {
        assertThatThrownBy(
                () -> instantiation.instantiate(NoNoArgConstructor.class, "someAttribute()"))
                        .as("A class with no no-arg constructor must be rejected with a clear"
                                + " message naming both the attribute that named it and the"
                                + " class itself, not a raw NoSuchMethodException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("someAttribute()")
                        .hasMessageContaining(NoNoArgConstructor.class.getName())
                        .hasMessageContaining("no accessible no-arg constructor");
    }

    @Test
    void testInstantiate_constructorThrows_throwsIllegalStateExceptionWithOriginalCauseUnwrapped()
    {
        assertThatThrownBy(() -> instantiation.instantiate(ThrowingNoArg.class, "someAttribute()"))
                .as("A constructor that itself throws must be rejected with a clear message"
                        + " naming both the attribute and the class, with the original failure"
                        + " as the cause - not the InvocationTargetException the reflective call"
                        + " throws.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("someAttribute()")
                .hasMessageContaining(ThrowingNoArg.class.getName())
                .hasMessageContaining("threw from its no-arg constructor")
                .cause().isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated constructor failure");
    }

    public static class PublicNoArg
    {
    }

    public static class PrivateNoArg
    {
        private PrivateNoArg()
        {
        }
    }

    public static class NoNoArgConstructor
    {
        public NoNoArgConstructor(final String required)
        {
        }
    }

    public static class ThrowingNoArg
    {
        public ThrowingNoArg()
        {
            throw new IllegalStateException("simulated constructor failure");
        }
    }
}
