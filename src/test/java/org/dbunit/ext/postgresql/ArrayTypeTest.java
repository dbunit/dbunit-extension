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
package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ArrayType}.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
@ExtendWith(MockitoExtension.class)
class ArrayTypeTest
{
    @Mock
    private ResultSet resultSet;

    @Mock
    private PreparedStatement statement;

    @Mock
    private Connection connection;

    @Mock
    private Array array;

    @Test
    void testGetSqlType_onNewInstance_returnsTypesArray()
    {
        final ArrayType type = new ArrayType("_int4");
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.ARRAY.")
                .isEqualTo(Types.ARRAY);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final ArrayType type = new ArrayType("_int4");
        assertThat(type.isNumber()).as("isNumber() should return false.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final ArrayType type = new ArrayType("_int4");
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }

    @Test
    void testConstructor_withValidSqlTypeName_storesSqlTypeName()
    {
        final ArrayType type = new ArrayType("_int4");
        assertThat(type.getSqlTypeName())
                .as("getSqlTypeName() should return the name passed to the constructor.")
                .isEqualTo("_int4");
    }

    @Test
    void testConstructor_withNullSqlTypeName_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new ArrayType(null))
                .as("Constructor should throw NullPointerException when sqlTypeName is null.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testTypeCast_withArrayLiteralString_returnsStringRepresentation()
            throws TypeCastException
    {
        final ArrayType type = new ArrayType("_int4");
        final Object result = type.typeCast("{1,2,3}");
        assertThat(result)
                .as("typeCast() should return the string representation of the value.")
                .isEqualTo("{1,2,3}");
    }

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final ArrayType type = new ArrayType("_int4");
        assertThat(type.typeCast(null))
                .as("typeCast() should return null when given null.").isNull();
    }

    @Test
    void testGetSqlValue_withArrayColumn_returnsLiteralTextFromToString()
            throws Exception
    {
        final ArrayType type = new ArrayType("_int4");
        when(resultSet.getArray(1)).thenReturn(array);
        when(array.toString()).thenReturn("{1,2,3}");

        final Object result = type.getSqlValue(1, resultSet);

        assertThat(result)
                .as("getSqlValue() should return the array's string representation.")
                .isEqualTo("{1,2,3}");
    }

    @Test
    void testGetSqlValue_withNullColumn_returnsNull() throws Exception
    {
        final ArrayType type = new ArrayType("_int4");
        when(resultSet.getArray(1)).thenReturn(null);

        final Object result = type.getSqlValue(1, resultSet);

        assertThat(result)
                .as("getSqlValue() should return null when the column is SQL NULL.")
                .isNull();
    }

    @Test
    void testSetSqlValue_withNullValue_doesNotThrowAndSetsSqlNull()
            throws Exception
    {
        final ArrayType type = new ArrayType("_int4");

        assertThatCode(() -> type.setSqlValue(null, 1, statement))
                .as("setSqlValue() should not throw when value is null.")
                .doesNotThrowAnyException();

        verify(statement).setNull(1, Types.ARRAY);
    }

    @Test
    void testSetSqlValue_withArrayValue_bindsArrayDirectly() throws Exception
    {
        final ArrayType type = new ArrayType("_int4");

        type.setSqlValue(array, 1, statement);

        verify(statement).setArray(1, array);
    }

    @Test
    void testSetSqlValue_withSimpleLiteral_createsArrayFromParsedElements()
            throws Exception
    {
        final ArrayType type = new ArrayType("_int4");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("int4"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{1,2,3}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should receive the literal's top-level elements.")
                .containsExactly("1", "2", "3");
        verify(statement).setArray(1, array);
    }

    @Test
    void testSetSqlValue_withWhitespaceAroundElements_trimsUnquotedElements()
            throws Exception
    {
        final ArrayType type = new ArrayType("_int4");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("int4"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{1, 2, 3}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should receive whitespace-trimmed elements.")
                .containsExactly("1", "2", "3");
    }

    @Test
    void testSetSqlValue_withQuotedAndNullElements_parsesEscapesAndNullKeyword()
            throws Exception
    {
        final ArrayType type = new ArrayType("_text");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{\"a,b\",\"c\\\"d\",NULL,e}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should receive unescaped, unquoted elements, "
                        + "with the unquoted NULL keyword resolved to a null entry.")
                .containsExactly("a,b", "c\"d", null, "e");
    }

    @Test
    void testSetSqlValue_withBackslashEscapedDelimiterOutsideQuotes_treatsAsOneElement()
            throws Exception
    {
        final ArrayType type = new ArrayType("_text");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{a\\,b,c}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should treat a backslash-escaped comma "
                        + "outside quotes as literal data, not a delimiter.")
                .containsExactly("a,b", "c");
    }

    @Test
    void testSetSqlValue_withBackslashEscapedBraceOutsideQuotes_treatsAsLiteralCharacter()
            throws Exception
    {
        final ArrayType type = new ArrayType("_text");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{a\\{b}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should treat a backslash-escaped brace "
                        + "outside quotes as literal data, not a nested array.")
                .containsExactly("a{b");
    }

    @Test
    void testSetSqlValue_withEmptyArrayLiteral_createsArrayWithNoElements()
            throws Exception
    {
        final ArrayType type = new ArrayType("_int4");
        final ArgumentCaptor<Object[]> elementsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("int4"),
                elementsCaptor.capture())).thenReturn(array);

        type.setSqlValue("{}", 1, statement);

        assertThat(elementsCaptor.getValue())
                .as("createArrayOf() should receive zero elements for an empty array literal.")
                .isEmpty();
    }

    @Test
    void testSetSqlValue_withSqlTypeNameNotUnderscorePrefixed_usesFullNameAsElementTypeName()
            throws Exception
    {
        final ArrayType type = new ArrayType("int4");
        final ArgumentCaptor<String> typeNameCaptor =
                ArgumentCaptor.forClass(String.class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(typeNameCaptor.capture(),
                any(Object[].class))).thenReturn(array);

        type.setSqlValue("{1,2,3}", 1, statement);

        assertThat(typeNameCaptor.getValue())
                .as("createArrayOf() should use the sql type name as-is when it "
                        + "has no leading underscore.")
                .isEqualTo("int4");
    }

    @Test
    void testSetSqlValue_withMalformedLiteral_throwsTypeCastException()
    {
        final ArrayType type = new ArrayType("_int4");

        assertThatThrownBy(() -> type.setSqlValue("1,2,3", 1, statement))
                .as("setSqlValue() should reject a literal not enclosed in { }.")
                .isInstanceOf(TypeCastException.class);
    }

    @Test
    void testSetSqlValue_withNestedArrayLiteral_throwsTypeCastException()
    {
        final ArrayType type = new ArrayType("_int4");

        assertThatThrownBy(
                () -> type.setSqlValue("{{1,2},{3,4}}", 1, statement))
                        .as("setSqlValue() should reject a multi-dimensional array literal.")
                        .isInstanceOf(TypeCastException.class);
    }
}
