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
 * Unit tests for {@link GenericEnumType}.
 *
 * @author DbUnit.org
 * @since 2.4.6
 */
@ExtendWith(MockitoExtension.class)
class GenericEnumTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Mock
    private PreparedStatement statement;

    @Override
    protected AbstractDataType createType()
    {
        return new GenericEnumType("test_enum");
    }

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

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final GenericEnumType type = new GenericEnumType("mood_enum");
        final Object result = type.typeCast(null);
        assertThat(result)
                .as("typeCast() should return null when given null.")
                .isNull();
    }

    /**
     * Issue 677: AbstractDataType.compare() calls typeCast() directly, even
     * when comparing a null value against a non-null one.
     */
    @Test
    void testCompare_withNullAndNonNullValue_doesNotThrow() throws TypeCastException
    {
        final GenericEnumType type = new GenericEnumType("mood_enum");

        assertThatCode(() -> type.compare(null, "HAPPY"))
                .as("compare() should not throw NullPointerException when one value is null.")
                .doesNotThrowAnyException();

        assertThat(type.compare(null, "HAPPY"))
                .as("compare() should treat null as less than a non-null value.")
                .isEqualTo(-1);
    }

    /**
     * Issue 930: setSqlValue() bypasses typeCast() and dereferences the raw
     * value directly, so it needs its own null guard.
     */
    @Test
    void testSetSqlValue_withNullValue_doesNotThrowAndSetsSqlNull() throws Exception
    {
        final GenericEnumType type = new GenericEnumType("mood_enum");

        assertThatCode(() -> type.setSqlValue(null, 1, statement))
                .as("setSqlValue() should not throw NullPointerException when value is null.")
                .doesNotThrowAnyException();

        verify(statement).setNull(1, Types.OTHER);
    }

    private enum TestDirection
    {
        NORTH, SOUTH, EAST, WEST
    }
}
