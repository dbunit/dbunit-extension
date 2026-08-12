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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JsonType}.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class JsonTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Mock
    private PreparedStatement statement;

    @Override
    protected AbstractDataType createType()
    {
        return new JsonType("json");
    }

    @Test
    void testConstructor_withValidSqlTypeName_storesSqlTypeName()
    {
        final JsonType type = new JsonType("jsonb");
        assertThat(type.getSqlTypeName())
                .as("getSqlTypeName() should return the name passed to the constructor.")
                .isEqualTo("jsonb");
    }

    @Test
    void testConstructor_withNullSqlTypeName_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new JsonType(null))
                .as("Constructor should throw NullPointerException when sqlTypeName is null.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testTypeCast_withJsonObjectString_returnsStringRepresentation()
            throws TypeCastException
    {
        final JsonType type = new JsonType("json");
        final String value = "{\"a\":1}";
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should return the string representation of the value.")
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void testTypeCast_withJsonArrayString_returnsStringRepresentation()
            throws TypeCastException
    {
        final JsonType type = new JsonType("jsonb");
        final String value = "[1,2,3]";
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should return the string representation of the value.")
                .isEqualTo("[1,2,3]");
    }

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final JsonType type = new JsonType("json");
        final Object result = type.typeCast(null);
        assertThat(result)
                .as("typeCast() should return null when given null.")
                .isNull();
    }

    /**
     * Issue 574: the reporter's original patch threw NPE from setSqlValue()
     * when the column value was null.
     */
    @Test
    void testSetSqlValue_withNullValue_doesNotThrowAndSetsSqlNull()
            throws Exception
    {
        final JsonType type = new JsonType("jsonb");

        assertThatCode(() -> type.setSqlValue(null, 1, statement))
                .as("setSqlValue() should not throw NullPointerException when value is null.")
                .doesNotThrowAnyException();

        verify(statement).setNull(1, Types.OTHER);
    }
}
