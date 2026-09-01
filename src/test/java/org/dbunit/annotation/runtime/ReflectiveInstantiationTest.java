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

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class ReflectiveInstantiationTest
{
    @Test
    void testNewInstance_publicNoArgConstructor_returnsNewInstance() throws Exception
    {
        final PublicNoArg instance = ReflectiveInstantiation.newInstance(PublicNoArg.class);

        assertThat(instance).as("A public no-arg constructor must be invoked directly.")
                .isNotNull();
    }

    @Test
    void testNewInstance_privateNoArgConstructor_returnsNewInstanceAnyway() throws Exception
    {
        final PrivateNoArg instance = ReflectiveInstantiation.newInstance(PrivateNoArg.class);

        assertThat(instance)
                .as("A private no-arg constructor must still be invoked - setAccessible(true)"
                        + " overrides Java's own access check - since an annotation-named"
                        + " implementation class is commonly package-private or"
                        + " private-constructed.")
                .isNotNull();
    }

    @Test
    void testNewInstance_noNoArgConstructor_throwsNoSuchMethodException()
    {
        assertThatThrownBy(() -> ReflectiveInstantiation.newInstance(NoNoArgConstructor.class))
                .as("A class with no no-arg constructor at all must surface as a"
                        + " ReflectiveOperationException the caller can catch specifically, not"
                        + " some other unrelated failure.")
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void testNewInstance_constructorThrows_throwsInvocationTargetExceptionWrappingIt()
    {
        assertThatThrownBy(() -> ReflectiveInstantiation.newInstance(ThrowingNoArg.class))
                .as("A no-arg constructor that itself throws must surface as an"
                        + " InvocationTargetException wrapping the original failure, not the"
                        + " original failure directly nor some other exception type.")
                .isInstanceOf(InvocationTargetException.class)
                .cause().isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated constructor failure");
    }

    @Test
    void testInstantiate_publicNoArgConstructor_returnsNewInstance()
    {
        final PublicNoArg instance =
                ReflectiveInstantiation.instantiate(PublicNoArg.class, "someAttribute()");

        assertThat(instance).as("A public no-arg constructor must be invoked directly.")
                .isNotNull();
    }

    @Test
    void testInstantiate_noNoArgConstructor_throwsIllegalStateExceptionNamingTheAttribute()
    {
        assertThatThrownBy(() -> ReflectiveInstantiation.instantiate(NoNoArgConstructor.class,
                "someAttribute()"))
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
        assertThatThrownBy(() -> ReflectiveInstantiation.instantiate(ThrowingNoArg.class,
                "someAttribute()"))
                        .as("A constructor that itself throws must be rejected with a clear"
                                + " message naming both the attribute and the class, with the"
                                + " original failure as the cause - not the"
                                + " InvocationTargetException wrapper newInstance() itself"
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
