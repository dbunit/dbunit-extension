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

class DefaultMethodOverrideCheckTest
{
    @Test
    void testOverridesDefaultMethod_typeOverridesIt_returnsTrue()
    {
        final boolean result = DefaultMethodOverrideCheck.overridesDefaultMethod(
                Overriding.class, WithDefaultMethod.class, "greet");

        assertThat(result).as("A type declaring its own greet() overrides the default.")
                .isTrue();
    }

    @Test
    void testOverridesDefaultMethod_typeDoesNotOverrideIt_returnsFalse()
    {
        final boolean result = DefaultMethodOverrideCheck.overridesDefaultMethod(
                NotOverriding.class, WithDefaultMethod.class, "greet");

        assertThat(result)
                .as("A type inheriting the interface's own default body does not override it.")
                .isFalse();
    }

    @Test
    void testOverridesDefaultMethod_methodDoesNotExistAtAll_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> DefaultMethodOverrideCheck.overridesDefaultMethod(
                NotOverriding.class, WithDefaultMethod.class, "noSuchMethod"))
                        .as("A method name the interface does not actually declare must be"
                                + " rejected with a clear message, not a raw"
                                + " NoSuchMethodException - this should be unreachable for a"
                                + " method that is really declared on declaringInterface.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(WithDefaultMethod.class.getName())
                        .hasMessageContaining("noSuchMethod");
    }

    interface WithDefaultMethod
    {
        default String greet()
        {
            return "default";
        }
    }

    static class NotOverriding implements WithDefaultMethod
    {
    }

    static class Overriding implements WithDefaultMethod
    {
        @Override
        public String greet()
        {
            return "overridden";
        }
    }
}
