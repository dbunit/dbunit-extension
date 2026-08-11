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
 * Unit tests for {@link CitextType}.
 *
 * @author DbUnit.org
 * @since 2.4.5
 */
@ExtendWith(MockitoExtension.class)
class CitextTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Mock
    private PreparedStatement statement;

    @Override
    protected AbstractDataType createType()
    {
        return new CitextType();
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

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final CitextType type = new CitextType();
        final Object result = type.typeCast(null);
        assertThat(result)
                .as("typeCast() should return null when given null.")
                .isNull();
    }

    /**
     * Issue 930: AbstractDataType.compare() calls typeCast() directly, even
     * when comparing a null value against a non-null one.
     */
    @Test
    void testCompare_withNullAndNonNullValue_doesNotThrow() throws TypeCastException
    {
        final CitextType type = new CitextType();

        assertThatCode(() -> type.compare(null, "Hello World"))
                .as("compare() should not throw NullPointerException when one value is null.")
                .doesNotThrowAnyException();

        assertThat(type.compare(null, "Hello World"))
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
        final CitextType type = new CitextType();

        assertThatCode(() -> type.setSqlValue(null, 1, statement))
                .as("setSqlValue() should not throw NullPointerException when value is null.")
                .doesNotThrowAnyException();

        verify(statement).setNull(1, Types.OTHER);
    }
}
