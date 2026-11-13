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
package org.dbunit.ext.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.junit.jupiter.api.Test;

class H2JsonTypeTest
{
    private final H2JsonType sut = new H2JsonType();

    @Test
    void testTypeCast_null_returnsNull() throws Exception
    {
        final Object actual = sut.typeCast(null);

        assertThat(actual).as("A null value stays null.").isNull();
    }

    @Test
    void testTypeCast_string_returnsTheText() throws Exception
    {
        final Object actual = sut.typeCast("{\"a\":1}");

        assertThat(actual).as("The text of the document.").isEqualTo("{\"a\":1}");
    }

    @Test
    void testTypeCast_utf8Bytes_returnsTheText() throws Exception
    {
        final byte[] bytes = "{\"a\":\"\u00e4\"}".getBytes(StandardCharsets.UTF_8);

        final Object actual = sut.typeCast(bytes);

        assertThat(actual).as("H2 hands the document over as the UTF-8 bytes of its text.")
                .isEqualTo("{\"a\":\"\u00e4\"}");
    }

    @Test
    void testGetSqlValue_resultSet_readsTheText() throws Exception
    {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(2)).thenReturn("{\"a\":1}");

        final Object actual = sut.getSqlValue(2, resultSet);

        assertThat(actual).as("The value is read as the text of the document.")
                .isEqualTo("{\"a\":1}");
    }

    @Test
    void testSetSqlValue_text_bindsItsUtf8Bytes() throws Exception
    {
        final PreparedStatement statement = mock(PreparedStatement.class);

        sut.setSqlValue("{\"a\":\"\u00e4\"}", 3, statement);

        verify(statement).setBytes(3, "{\"a\":\"\u00e4\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testSetSqlValue_null_bindsNullOfTypeOther() throws Exception
    {
        final PreparedStatement statement = mock(PreparedStatement.class);

        sut.setSqlValue(null, 3, statement);

        verify(statement).setNull(3, Types.OTHER);
    }
}
