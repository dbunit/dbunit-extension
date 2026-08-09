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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package org.dbunit.ext.h2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link H2MetadataHandler}: verifies {@link H2MetadataHandler#getTables} excludes
 * the {@code INFORMATION_SCHEMA} schema that H2 2.x reports with a JDBC {@code TABLE_TYPE} dbunit's
 * default table-type filter no longer excludes on its own.
 *
 * @since 3.4.1
 */
class H2MetadataHandlerTest
{
    private final H2MetadataHandler handler = new H2MetadataHandler();

    @Test
    void testGetTables_withInformationSchemaRowsPresent_excludesThemButKeepsUserTables()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE"},
                row(null, "INFORMATION_SCHEMA", "CONSTANTS", "BASE TABLE"),
                row(null, "DBUNITUSER", "BAR", "TABLE"),
                row(null, "INFORMATION_SCHEMA", "USERS", "BASE TABLE"),
                row(null, "DEFAULTUSER", "FOO", "TABLE"));
        when(metaData.getTables(null, null, "%", null)).thenReturn(tables);

        final ResultSet filtered = handler.getTables(metaData, null, null);

        assertThat(filtered.next()).as("first row present.").isTrue();
        assertThat(filtered.getString(3)).as("first surviving table.").isEqualTo("BAR");
        assertThat(filtered.next()).as("second row present.").isTrue();
        assertThat(filtered.getString(3)).as("second surviving table.").isEqualTo("FOO");
        assertThat(filtered.next()).as("no third row.").isFalse();
    }

    @Test
    void testGetTables_withInformationSchemaInLowerCase_excludesItToo() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE"},
                row(null, "information_schema", "settings", "BASE TABLE"),
                row(null, "public", "foo", "TABLE"));
        when(metaData.getTables(null, null, "%", null)).thenReturn(tables);

        final ResultSet filtered = handler.getTables(metaData, null, null);

        assertThat(filtered.next()).as("only the non-system row survives.").isTrue();
        assertThat(filtered.getString(3)).as("surviving table.").isEqualTo("foo");
        assertThat(filtered.next()).as("no second row.").isFalse();
    }

    @Test
    void testGetTables_withOnlyInformationSchemaRows_returnsEmptyResultSet() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE"},
                row(null, "INFORMATION_SCHEMA", "CONSTANTS", "BASE TABLE"));
        when(metaData.getTables(null, "INFORMATION_SCHEMA", "%", null)).thenReturn(tables);

        final ResultSet filtered = handler.getTables(metaData, "INFORMATION_SCHEMA", null);

        assertThat(filtered.next()).as("no rows survive.").isFalse();
    }

    @Test
    void testGetTables_withRows_closesTheUnderlyingResultSet() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE"},
                row(null, "PUBLIC", "FOO", "TABLE"));
        when(metaData.getTables(null, null, "%", null)).thenReturn(tables);

        handler.getTables(metaData, null, null);

        verify(tables).close();
    }

    private static Object[] row(final Object... values)
    {
        return values;
    }

    /**
     * Mocks a {@link ResultSet} over the given rows, driven purely by {@code next()},
     * {@code getObject(int)}/{@code getMetaData()} - the members
     * {@link org.dbunit.database.InMemoryMetadataResultSet}'s copy logic reads from a source
     * result set - plus {@code getString(int)} so the filter predicate and the filtered result can
     * both read column values by index.
     */
    private static ResultSet mockRowsResultSet(final String[] labels, final Object[]... rows)
            throws SQLException
    {
        final ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(labels.length);
        for (int i = 0; i < labels.length; i++)
        {
            when(metaData.getColumnLabel(i + 1)).thenReturn(labels[i]);
        }

        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        final AtomicInteger cursor = new AtomicInteger(-1);
        when(resultSet.next()).thenAnswer(invocation -> cursor.incrementAndGet() < rows.length);
        when(resultSet.getObject(anyInt())).thenAnswer(invocation -> {
            final int columnIndex = invocation.getArgument(0);
            return rows[cursor.get()][columnIndex - 1];
        });
        when(resultSet.getString(anyInt())).thenAnswer(invocation -> {
            final int columnIndex = invocation.getArgument(0);
            final Object value = rows[cursor.get()][columnIndex - 1];
            return value == null ? null : String.valueOf(value);
        });
        return resultSet;
    }
}
