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

package org.dbunit.ext.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MultiSchemaMySqlMetadataHandler}: verifies the no-single-schema path
 * enumerates and unions results across the connection's user catalogs (skipping MySQL's system
 * catalogs), while a caller that already supplies a single schema still gets the plain
 * {@link MySqlMetadataHandler} single-catalog passthrough.
 *
 * @since 3.4.1
 */
class MultiSchemaMySqlMetadataHandlerTest
{
    private final MultiSchemaMySqlMetadataHandler handler = new MultiSchemaMySqlMetadataHandler();

    @Test
    void testGetTables_withNoSchemaConfigured_unionsAcrossUserCatalogsExcludingSystemCatalogs()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs = mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"),
                row("information_schema"), row("shop2"));
        final ResultSet shop1Tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME"},
                row("shop1", null, "ORDERS"));
        final ResultSet shop2Tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME"},
                row("shop2", null, "PRODUCTS"));
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getTables("shop1", null, "%", null)).thenReturn(shop1Tables);
        when(metaData.getTables("shop2", null, "%", null)).thenReturn(shop2Tables);

        final ResultSet merged = handler.getTables(metaData, null, null);

        assertThat(merged.next()).as("first row present.").isTrue();
        assertThat(merged.getString(3)).as("first table name.").isEqualTo("ORDERS");
        assertThat(merged.next()).as("second row present.").isTrue();
        assertThat(merged.getString(3)).as("second table name.").isEqualTo("PRODUCTS");
        assertThat(merged.next()).as("no third row.").isFalse();
        verify(metaData, never()).getTables(eq("information_schema"), any(), any(), any());
    }

    @Test
    void testGetTables_withSchemaConfigured_delegatesWithoutEnumeratingCatalogs()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet expected = mock(ResultSet.class);
        when(metaData.getTables("shop1", null, "%", null)).thenReturn(expected);

        final ResultSet actual = handler.getTables(metaData, "shop1", null);

        assertThat(actual).as("single-schema call bypasses catalog enumeration.")
                .isSameAs(expected);
        verify(metaData, never()).getCatalogs();
    }

    @Test
    void testGetTables_withNoSchemaConfigured_closesAlreadyOpenedResultSetsWhenALaterCatalogFails()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs =
                mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"), row("shop2"));
        final ResultSet shop1Tables = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME"},
                row("shop1", null, "ORDERS"));
        final SQLException shop2Failure = new SQLException("shop2 unreachable");
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getTables("shop1", null, "%", null)).thenReturn(shop1Tables);
        when(metaData.getTables("shop2", null, "%", null)).thenThrow(shop2Failure);

        assertThatThrownBy(() -> handler.getTables(metaData, null, null))
                .as("The second catalog's failure must propagate.").isSameAs(shop2Failure);
        verify(shop1Tables).close();
    }

    @Test
    void testGetTables_withOnlySystemCatalogsVisible_returnsEmptyResultSet() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs = mockRowsResultSet(new String[] {"TABLE_CAT"},
                row("information_schema"), row("performance_schema"));
        when(metaData.getCatalogs()).thenReturn(catalogs);

        final ResultSet merged = handler.getTables(metaData, null, null);

        assertThat(merged.next()).as("no rows when only system catalogs are visible.").isFalse();
    }

    @Test
    void testGetColumns_withNoSchemaConfigured_unionsAcrossUserCatalogs() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs =
                mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"), row("shop2"));
        final ResultSet shop1Columns = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME"},
                row("shop1", null, "ORDERS", "ID"));
        final ResultSet shop2Columns = mockRowsResultSet(
                new String[] {"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME"},
                row("shop2", null, "ORDERS", "ID"));
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getColumns("shop1", null, "ORDERS", "%")).thenReturn(shop1Columns);
        when(metaData.getColumns("shop2", null, "ORDERS", "%")).thenReturn(shop2Columns);

        final ResultSet merged = handler.getColumns(metaData, null, "ORDERS");

        assertThat(merged.next()).as("first row present.").isTrue();
        assertThat(merged.getString(1)).as("first row catalog.").isEqualTo("shop1");
        assertThat(merged.next()).as("second row present.").isTrue();
        assertThat(merged.getString(1)).as("second row catalog.").isEqualTo("shop2");
        assertThat(merged.next()).as("no third row.").isFalse();
    }

    @Test
    void testGetColumns_withSchemaConfigured_delegatesWithoutEnumeratingCatalogs()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet expected = mock(ResultSet.class);
        when(metaData.getColumns("shop1", null, "ORDERS", "%")).thenReturn(expected);

        final ResultSet actual = handler.getColumns(metaData, "shop1", "ORDERS");

        assertThat(actual).as("single-schema call bypasses catalog enumeration.")
                .isSameAs(expected);
        verify(metaData, never()).getCatalogs();
    }

    @Test
    void testGetColumns_mergedResultSet_supportsGetIntByColumnLabel() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs = mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"));
        final ResultSet columns = mockRowsResultSet(
                new String[] {"TABLE_CAT", "DATA_TYPE", "SOURCE_DATA_TYPE"},
                row("shop1", Types.DISTINCT, 12));
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getColumns("shop1", null, "ORDERS", "%")).thenReturn(columns);

        final ResultSet merged = handler.getColumns(metaData, null, "ORDERS");

        assertThat(merged.next()).isTrue();
        assertThat(merged.getInt("SOURCE_DATA_TYPE")).as("named-column lookup.").isEqualTo(12);
    }

    @Test
    void testGetPrimaryKeys_withNoSchemaConfigured_unionsAcrossUserCatalogs() throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs =
                mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"), row("shop2"));
        final ResultSet shop1Keys = mockRowsResultSet(
                new String[] {"COLUMN_NAME", "KEY_SEQ"}, row("ID", 1));
        final ResultSet shop2Keys = mockRowsResultSet(
                new String[] {"COLUMN_NAME", "KEY_SEQ"}, row("ORDER_ID", 1));
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getPrimaryKeys("shop1", null, "ORDERS")).thenReturn(shop1Keys);
        when(metaData.getPrimaryKeys("shop2", null, "ORDERS")).thenReturn(shop2Keys);

        final ResultSet merged = handler.getPrimaryKeys(metaData, null, "ORDERS");

        assertThat(merged.next()).as("first row present.").isTrue();
        assertThat(merged.getString(1)).as("first PK column.").isEqualTo("ID");
        assertThat(merged.next()).as("second row present.").isTrue();
        assertThat(merged.getString(1)).as("second PK column.").isEqualTo("ORDER_ID");
        assertThat(merged.next()).as("no third row.").isFalse();
    }

    @Test
    void testTableExists_withNoSchemaConfigured_returnsTrueWhenFoundInAnyUserCatalog()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs =
                mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"), row("shop2"));
        final ResultSet shop1Tables = mockRowsResultSet(new String[] {"TABLE_NAME"});
        final ResultSet shop2Tables =
                mockRowsResultSet(new String[] {"TABLE_NAME"}, row("ORDERS"));
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getTables("shop1", null, "ORDERS", null)).thenReturn(shop1Tables);
        when(metaData.getTables("shop2", null, "ORDERS", null)).thenReturn(shop2Tables);

        final boolean exists = handler.tableExists(metaData, null, "ORDERS");

        assertThat(exists).as("found in the second catalog.").isTrue();
    }

    @Test
    void testTableExists_withNoSchemaConfigured_returnsFalseWhenNotFoundInAnyUserCatalog()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet catalogs =
                mockRowsResultSet(new String[] {"TABLE_CAT"}, row("shop1"), row("shop2"));
        final ResultSet shop1Tables = mockRowsResultSet(new String[] {"TABLE_NAME"});
        final ResultSet shop2Tables = mockRowsResultSet(new String[] {"TABLE_NAME"});
        when(metaData.getCatalogs()).thenReturn(catalogs);
        when(metaData.getTables("shop1", null, "ORDERS", null)).thenReturn(shop1Tables);
        when(metaData.getTables("shop2", null, "ORDERS", null)).thenReturn(shop2Tables);

        final boolean exists = handler.tableExists(metaData, null, "ORDERS");

        assertThat(exists).as("not found in any catalog.").isFalse();
    }

    @Test
    void testTableExists_withSchemaConfigured_delegatesWithoutEnumeratingCatalogs()
            throws SQLException
    {
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        final ResultSet shop1Tables =
                mockRowsResultSet(new String[] {"TABLE_NAME"}, row("ORDERS"));
        when(metaData.getTables("shop1", null, "ORDERS", null)).thenReturn(shop1Tables);

        final boolean exists = handler.tableExists(metaData, "shop1", "ORDERS");

        assertThat(exists).as("single-schema call bypasses catalog enumeration.").isTrue();
        verify(metaData, never()).getCatalogs();
    }

    private static Object[] row(final Object... values)
    {
        return values;
    }

    /**
     * Mocks a {@link ResultSet} over the given rows, driven purely by {@code next()} and
     * {@code getObject(int)}/{@code getMetaData()} - the only members
     * {@link MultiSchemaMySqlMetadataHandler}'s merge logic reads from a source result set - plus
     * {@code getString(int)} so a test can also read a non-merged result set returned directly by
     * a single-schema delegate call.
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
