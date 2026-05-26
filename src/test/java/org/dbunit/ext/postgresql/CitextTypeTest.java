/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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
package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CitextType}.
 *
 * @author DbUnit.org
 * @since 2.4.5
 */
class CitextTypeTest
{
    @Test
    void testGetSqlType_onNewInstance_returnsTypesOther()
    {
        final CitextType type = new CitextType();
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.OTHER.")
                .isEqualTo(Types.OTHER);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final CitextType type = new CitextType();
        assertThat(type.isNumber())
                .as("isNumber() should return false for citext type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final CitextType type = new CitextType();
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }

    @Test
    void testTypeCast_withStringValue_returnsStringRepresentation() throws TypeCastException
    {
        final CitextType type = new CitextType();
        final String value = "Hello World";
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should return the string representation.")
                .isEqualTo("Hello World");
    }

    @Test
    void testTypeCast_withIntegerValue_returnsStringRepresentation() throws TypeCastException
    {
        final CitextType type = new CitextType();
        final Integer value = 42;
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() of an Integer should return its toString() value.")
                .isEqualTo("42");
    }

    @Test
    void testTypeCast_withMixedCaseString_preservesCase() throws TypeCastException
    {
        final CitextType type = new CitextType();
        final String value = "CaseInsensitiveText";
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should preserve the original string case.")
                .isEqualTo("CaseInsensitiveText");
    }
}
