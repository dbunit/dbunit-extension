/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Types;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GenericEnumType}.
 *
 * @author DbUnit.org
 * @since 2.4.6
 */
class GenericEnumTypeTest
{
    @Test
    void testConstructor_withValidSqlTypeName_storesSqlTypeName()
    {
        final GenericEnumType type = new GenericEnumType("my_enum");
        assertThat(type.getSqlTypeName())
                .as("getSqlTypeName() should return the name passed to the constructor.")
                .isEqualTo("my_enum");
    }

    @Test
    void testConstructor_withNullSqlTypeName_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new GenericEnumType(null))
                .as("Constructor should throw NullPointerException when sqlTypeName is null.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetSqlType_onNewInstance_returnsTypesOther()
    {
        final GenericEnumType type = new GenericEnumType("status_enum");
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.OTHER.")
                .isEqualTo(Types.OTHER);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final GenericEnumType type = new GenericEnumType("status_enum");
        assertThat(type.isNumber())
                .as("isNumber() should return false for enum type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final GenericEnumType type = new GenericEnumType("status_enum");
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }

    @Test
    void testTypeCast_withStringValue_returnsStringRepresentation() throws TypeCastException
    {
        final GenericEnumType type = new GenericEnumType("mood_enum");
        final String value = "HAPPY";
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should return the string representation of the value.")
                .isEqualTo("HAPPY");
    }

    @Test
    void testTypeCast_withEnumValue_returnsStringRepresentation() throws TypeCastException
    {
        final GenericEnumType type = new GenericEnumType("direction_enum");
        final Object result = type.typeCast(TestDirection.NORTH);
        assertThat(result)
                .as("typeCast() should convert enum constant to its name via toString().")
                .isEqualTo("NORTH");
    }

    private enum TestDirection
    {
        NORTH, SOUTH, EAST, WEST
    }
}
