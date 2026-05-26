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
import java.util.UUID;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UuidType}.
 *
 * @author DbUnit.org
 * @since 2.4.5
 */
class UuidTypeTest
{
    @Test
    void testGetSqlType_onNewInstance_returnsTypesOther()
    {
        final UuidType type = new UuidType();
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.OTHER for uuid type.")
                .isEqualTo(Types.OTHER);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final UuidType type = new UuidType();
        assertThat(type.isNumber())
                .as("isNumber() should return false for uuid type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final UuidType type = new UuidType();
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }

    @Test
    void testTypeCast_withUuidString_returnsStringRepresentation() throws TypeCastException
    {
        final UuidType type = new UuidType();
        final String uuidString = "550e8400-e29b-41d4-a716-446655440000";
        final Object result = type.typeCast(uuidString);
        assertThat(result)
                .as("typeCast() should return the string representation of the UUID.")
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void testTypeCast_withUuidObject_returnsStringRepresentation() throws TypeCastException
    {
        final UuidType type = new UuidType();
        final UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        final Object result = type.typeCast(uuid);
        assertThat(result)
                .as("typeCast() should call toString() on a UUID object.")
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final UuidType type = new UuidType();
        final Object result = type.typeCast(null);
        assertThat(result)
                .as("typeCast() should return null when given null.")
                .isNull();
    }
}
