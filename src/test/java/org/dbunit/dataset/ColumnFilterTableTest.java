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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.dataset.filter.IColumnFilter;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ColumnFilterTable}.
 */
class ColumnFilterTableTest
{
    private static final String TABLE_NAME = "MY_TABLE";

    private ITable buildTable() throws DataSetException
    {
        final Column[] columns = new Column[] {
                new Column("ID", DataType.INTEGER),
                new Column("NAME", DataType.VARCHAR),
                new Column("SECRET", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable(TABLE_NAME, columns);
        table.addRow(new Object[] {1, "Alice", "pass1"});
        table.addRow(new Object[] {2, "Bob", "pass2"});
        return table;
    }

    private IColumnFilter includeFilter(final String... columnNames)
    {
        return (tableName, column) -> {
            for (final String name : columnNames)
            {
                if (column.getColumnName().equalsIgnoreCase(name))
                {
                    return true;
                }
            }
            return false;
        };
    }

    // -------------------------------------------------------------------------
    // Constructor null guards
    // -------------------------------------------------------------------------

    @Test
    void testConstructor_withNullTable_throwsNullPointerException() throws DataSetException
    {
        final IColumnFilter filter = includeFilter("ID");

        assertThatThrownBy(() -> new ColumnFilterTable(null, filter))
                .as("null table throws NullPointerException.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructor_withNullFilter_throwsNullPointerException() throws DataSetException
    {
        final ITable table = buildTable();

        assertThatThrownBy(() -> new ColumnFilterTable(table, null))
                .as("null filter throws NullPointerException.")
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------
    // getRowCount
    // -------------------------------------------------------------------------

    @Test
    void testGetRowCount_withTwoRowTable_returnsTwoRows() throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID", "NAME"));

        assertThat(filtered.getRowCount()).as("row count unchanged by column filtering.").isEqualTo(2);
    }

    @Test
    void testGetRowCount_withEmptyTable_returnsZero() throws DataSetException
    {
        final Column[] columns = new Column[] {new Column("ID", DataType.INTEGER)};
        final DefaultTable empty = new DefaultTable(TABLE_NAME, columns);
        final ColumnFilterTable filtered =
                new ColumnFilterTable(empty, includeFilter("ID"));

        assertThat(filtered.getRowCount()).as("empty table returns 0.").isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // getTableMetaData — filtered columns
    // -------------------------------------------------------------------------

    @Test
    void testGetTableMetaData_withIncludeFilter_exposesOnlyIncludedColumns()
            throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID", "NAME"));

        final Column[] columns = filtered.getTableMetaData().getColumns();
        final String[] columnNames = new String[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            columnNames[i] = columns[i].getColumnName().toUpperCase();
        }

        assertThat(columnNames).as("filtered columns.")
                .containsExactlyInAnyOrder("ID", "NAME");
        assertThat(columnNames).as("SECRET column excluded.").doesNotContain("SECRET");
    }

    @Test
    void testGetTableMetaData_withIncludeFilter_returnsOriginalTableName() throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID"));

        assertThat(filtered.getTableMetaData().getTableName()).as("table name preserved.")
                .isEqualTo(TABLE_NAME);
    }

    @Test
    void testGetTableMetaData_withAllColumnsExcluded_returnsEmptyColumnArray()
            throws DataSetException
    {
        final ITable original = buildTable();
        final IColumnFilter excludeAll = (tableName, column) -> false;
        final ColumnFilterTable filtered = new ColumnFilterTable(original, excludeAll);

        assertThat(filtered.getTableMetaData().getColumns()).as("no columns exposed.").isEmpty();
    }

    // -------------------------------------------------------------------------
    // getValue — delegates to original table
    // -------------------------------------------------------------------------

    @Test
    void testGetValue_withValidRowAndColumn_returnsExpectedValue() throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID", "NAME"));

        assertThat(filtered.getValue(0, "NAME")).as("first row NAME.").isEqualTo("Alice");
        assertThat(filtered.getValue(1, "NAME")).as("second row NAME.").isEqualTo("Bob");
    }

    @Test
    void testGetValue_forFilteredOutColumn_stillDelegatesToOriginalTable()
            throws DataSetException
    {
        // getValue delegates to the original table, so accessing a filtered-out column
        // still works at the getValue level (filtering is enforced via metadata only)
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID"));

        assertThat(filtered.getValue(0, "SECRET")).as("filtered-out column still accessible via getValue.")
                .isEqualTo("pass1");
    }

    // -------------------------------------------------------------------------
    // getOriginalMetaData
    // -------------------------------------------------------------------------

    @Test
    void testGetOriginalMetaData_withIncludeFilter_returnsAllOriginalColumns() throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID"));

        final Column[] originalColumns = filtered.getOriginalMetaData().getColumns();
        assertThat(originalColumns).as("original metadata has all 3 columns.").hasSize(3);
    }

    @Test
    void testGetOriginalMetaData_withIncludeFilter_isDistinctFromFilteredMetaData() throws DataSetException
    {
        final ITable original = buildTable();
        final ColumnFilterTable filtered =
                new ColumnFilterTable(original, includeFilter("ID"));

        assertThat(filtered.getTableMetaData().getColumns()).as("filtered: 1 column.").hasSize(1);
        assertThat(filtered.getOriginalMetaData().getColumns()).as("original: 3 columns.").hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Integration with DefaultColumnFilter
    // -------------------------------------------------------------------------

    @Test
    void testWithDefaultColumnFilter_includedColumnsTable_exposesSpecifiedColumns()
            throws DataSetException
    {
        final ITable original = buildTable();
        final String[] keepColumns = {"ID", "NAME"};
        final ITable filtered =
                DefaultColumnFilter.includedColumnsTable(original, keepColumns);

        assertThat(filtered.getTableMetaData().getColumns()).as("two columns after filter.")
                .hasSize(2);
        assertThat(filtered.getValue(0, "ID")).as("ID value accessible.").isEqualTo(1);
        assertThat(filtered.getValue(0, "NAME")).as("NAME value accessible.").isEqualTo("Alice");
    }
}
