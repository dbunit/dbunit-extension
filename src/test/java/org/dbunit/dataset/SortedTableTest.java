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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class SortedTableTest extends AbstractTableTest
{
    private File sortedTableTestFile =
            TestUtils.getFile("xml/sortedTableTest.xml");

    @Override
    protected ITable createTable() throws Exception
    {
        return createDataSet().getTable("TEST_TABLE");
    }

    protected IDataSet createDataSet() throws Exception
    {
        return new SortedDataSet(createUnsortedDataSet());
    }

    private IDataSet createUnsortedDataSet()
            throws DataSetException, IOException
    {
        return new FlatXmlDataSetBuilder().build(sortedTableTestFile);
    }

    private ITable createNumericTable() throws Exception
    {
        // Create a table that has numeric values in the first column
        final Column[] columns =
                new Column[] {new Column("COLUMN0", DataType.NUMERIC),
                        new Column("COLUMN1", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        final Object[] row1 = new Object[] {Integer.valueOf(9), "row 9"};
        final Object[] row2 = new Object[] {Integer.valueOf(10), "row 10"};
        final Object[] row3 = new Object[] {Integer.valueOf(11), "row 11"};
        table.addRow(row1);
        table.addRow(row2);
        table.addRow(row3);
        return table;
    }

    @Test
    void testSetUseComparableTooLate_afterTableAccessed_throwsIllegalStateException() throws Exception
    {
        final ITable table = createTable();
        final SortedTable sortedTable = new SortedTable(table);
        // access a value to initialize the array
        sortedTable.getValue(0, "COLUMN0");
        // now set the "useComparable" flag which should fail
        final IllegalStateException expected = assertThrows(
                IllegalStateException.class,
                () -> sortedTable.setUseComparable(true),
                "Should not be able to set 'useComparable' after table has already been in use");

        final String msgStart =
                "Do not use this method after the table has been used";
        assertThat(expected).as("Msg should start with: " + msgStart)
                .hasMessageStartingWith(msgStart);

    }

    @Test
    void testSortByComparable_withNumericColumn_sortsNumerically() throws Exception
    {
        // Sort by column0 which is a numeric column
        final String columnName = "COLUMN0";

        final ITable table = createNumericTable();
        final SortedTable sortedTable =
                new SortedTable(table, new String[] {columnName});
        sortedTable.setUseComparable(true);

        final Column[] columns = sortedTable.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(2);
        assertThat(sortedTable.getRowCount()).as("row count").isEqualTo(3);

        final Object[] expected =
                {Integer.valueOf(9), Integer.valueOf(10), Integer.valueOf(11)};
        for (int i = 0; i < sortedTable.getRowCount(); i++)
        {
            assertThat(sortedTable.getValue(i, columnName)).as("value row " + i)
                    .isEqualTo(expected[i]);
        }
    }

    /**
     * Tests the sort by string which is the default behavior
     * 
     * @throws Exception
     */
    @Test
    void testSortByString_withNumericColumn_sortsByStringValue() throws Exception
    {
        // Sort by column0 which is a numeric column
        final String columnName = "COLUMN0";

        final ITable table = createNumericTable();
        final SortedTable sortedTable =
                new SortedTable(table, new String[] {columnName});

        final Column[] columns = sortedTable.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(2);
        assertThat(sortedTable.getRowCount()).as("row count").isEqualTo(3);

        final Object[] expected =
                {Integer.valueOf(10), Integer.valueOf(11), Integer.valueOf(9)};
        for (int i = 0; i < sortedTable.getRowCount(); i++)
        {
            assertThat(sortedTable.getValue(i, columnName)).as("value row " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetMissingValue_withMissingCells_returnsExpectedValues() throws Exception
    {
        final String columnName = "COLUMN2";
        final Object[] expected = {null, null, null, "0", "1"};

        final ITable table = createDataSet().getTable("MISSING_VALUES");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(3);
        assertThat(table.getRowCount()).as("row count").isEqualTo(5);
        for (int i = 0; i < table.getRowCount(); i++)
        {
            assertThat(table.getValue(i, columnName)).as("value row " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    void testCustomColumnsWithUnknownColumnName_withUndefinedSortColumn_throwsNoSuchColumnException() throws Exception
    {
        final String[] sortColumnNames =
                new String[] {"COLUMN2", "COLUMNXY_UNDEFINED"};

        final ITable unsortedTable =
                createUnsortedDataSet().getTable("MISSING_VALUES");
        final NoSuchColumnException expected = assertThrows(
                NoSuchColumnException.class,
                () -> new SortedTable(unsortedTable, sortColumnNames),
                "Should not be able to create a SortedTable with unexisting columns");

        assertThat(expected)
                .hasMessageStartingWith("MISSING_VALUES.COLUMNXY_UNDEFINED");

    }

    @Test
    void testCustomColumnsWithUnknownColumn_withUndefinedSortColumnObject_throwsNoSuchColumnException() throws Exception
    {
        final Column[] sortColumns = new Column[] {
                new Column("COLUMN2", DataType.UNKNOWN, Column.NULLABLE),
                new Column("COLUMNXY_UNDEFINED", DataType.UNKNOWN,
                        Column.NULLABLE)};

        final ITable unsortedTable =
                createUnsortedDataSet().getTable("MISSING_VALUES");
        final NoSuchColumnException expected = assertThrows(
                NoSuchColumnException.class,
                () -> new SortedTable(unsortedTable, sortColumns),
                "Should not be able to create a SortedTable with unexisting columns");
        assertThat(expected)
                .hasMessageStartingWith("MISSING_VALUES.COLUMNXY_UNDEFINED");
    }

    @Test
    void testCustomColumnsWithDifferentColumnTypesButSameName_withDifferentTypeForSortColumn_usesActualTableColumn() throws Exception
    {
        final Column sortColumn =
                new Column("COLUMN2", DataType.CHAR, Column.NO_NULLS);
        final Column[] sortColumns = new Column[] {sortColumn};
        // Use different columns (different datatype) in ITableMetaData that
        // have valid column names
        final ITable unsortedTable =
                createUnsortedDataSet().getTable("MISSING_VALUES");
        final SortedTable sortedTable =
                new SortedTable(unsortedTable, sortColumns);
        // Check the results
        final Column actualSortColumn = sortedTable.getSortColumns()[0];
        // The column actually used for sorting must has some different
        // attributes than the one passed in (dataType, nullable)
        assertThat(actualSortColumn).isNotSameAs(sortColumn);
        assertThat(actualSortColumn.getDataType()).isEqualTo(DataType.UNKNOWN);
        assertThat(actualSortColumn.getColumnName()).isEqualTo("COLUMN2");
        assertThat(actualSortColumn.getNullable()).isEqualTo(Column.NULLABLE);
    }

    @Test
    void testSort_nullValuesInSortColumnByStringMode_sortsNullsFirst() throws Exception
    {
        final Column[] columns = new Column[] {new Column("COL0", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("NULL_ORDER_TABLE", columns);
        table.addRow(new Object[] {"b"});
        table.addRow(new Object[] {null});
        table.addRow(new Object[] {"a"});

        final SortedTable sortedTable = new SortedTable(table, new String[] {"COL0"});

        final Object[] expected = {null, "a", "b"};
        for (int i = 0; i < sortedTable.getRowCount(); i++)
        {
            assertThat(sortedTable.getValue(i, "COL0")).as("value row " + i + ".")
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    void testSort_nullValuesInSortColumnComparableMode_sortsNullsFirst() throws Exception
    {
        final Column[] columns = new Column[] {new Column("COL0", DataType.NUMERIC)};
        final DefaultTable table = new DefaultTable("NULL_ORDER_TABLE", columns);
        table.addRow(new Object[] {Integer.valueOf(2)});
        table.addRow(new Object[] {null});
        table.addRow(new Object[] {Integer.valueOf(1)});

        final SortedTable sortedTable = new SortedTable(table, new String[] {"COL0"});
        sortedTable.setUseComparable(true);

        final Object[] expected = {null, Integer.valueOf(1), Integer.valueOf(2)};
        for (int i = 0; i < sortedTable.getRowCount(); i++)
        {
            assertThat(sortedTable.getValue(i, "COL0")).as("value row " + i + ".")
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    void testGetValue_sortTriggered_readsEachSortCellOnce() throws Exception
    {
        final int rowCount = 5;
        final Column[] columns = new Column[] {new Column("COL0", DataType.VARCHAR),
                new Column("COL1", DataType.VARCHAR), new Column("COL2", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("PERF_TABLE", columns);
        for (int i = 0; i < rowCount; i++)
        {
            table.addRow(new Object[] {String.valueOf(rowCount - i), "b" + i, "c" + i});
        }
        final CountingTable countingTable = new CountingTable(table);

        final SortedTable sortedTable =
                new SortedTable(countingTable, new String[] {"COL0", "COL1"});
        // Trigger the lazy sort-key precompute. This getValue call also reads one additional
        // cell -- the actually-requested value -- on top of the precompute itself.
        sortedTable.getValue(0, "COL0");

        final int sortColumnCount = sortedTable.getSortColumns().length;
        final int expectedCalls = rowCount * sortColumnCount + 1;
        assertThat(countingTable.getValueCallCount())
                .as("Sorting should read each (row, sort column) cell exactly once during the precompute, "
                        + "plus one call for the actually-requested cell.")
                .isEqualTo(expectedCalls);
    }

    @Test
    void testGetValue_customComparatorSubclassOverride_isNotBypassedByPrecompute()
            throws Exception
    {
        final Column[] columns = new Column[] {new Column("COL0", DataType.VARCHAR)};
        final DefaultTable table = new DefaultTable("REVERSED_TABLE", columns);
        table.addRow(new Object[] {"a"});
        table.addRow(new Object[] {"c"});
        table.addRow(new Object[] {"b"});

        final SortedTable sortedTable =
                new SortedTable(table, new String[] {"COL0"});
        // ReverseOrderComparator is a subclass of the built-in RowComparatorByString,
        // installed through the public setRowComparator() extension point. The
        // precomputed-key fast path must be gated by exact class, not instanceof,
        // or this override is silently bypassed and the table sorts in the built-in
        // (ascending) order instead of the subclass's overridden (descending) order.
        sortedTable.setRowComparator(
                new ReverseOrderComparator(table, sortedTable.getSortColumns()));

        final Object[] actual = {sortedTable.getValue(0, "COL0"),
                sortedTable.getValue(1, "COL0"), sortedTable.getValue(2, "COL0")};

        assertThat(actual)
                .as("A RowComparatorByString subclass installed via setRowComparator() "
                        + "must have its overridden compare() honored, sorting "
                        + "descending instead of the built-in ascending order.")
                .containsExactly("c", "b", "a");
    }

    /**
     * Subclass of the built-in string comparator, overriding {@code compare} to sort
     * descending instead of ascending -- used to prove the precomputed-key fast path
     * does not silently bypass a caller-supplied comparator's overridden behavior.
     */
    private static final class ReverseOrderComparator
            extends SortedTable.RowComparatorByString
    {
        private ReverseOrderComparator(final ITable table, final Column[] sortColumns)
        {
            super(table, sortColumns);
        }

        @Override
        protected int compare(final Column column, final Object value1,
                final Object value2) throws TypeCastException
        {
            return -super.compare(column, value1, value2);
        }
    }

    /**
     * {@link ITable} decorator counting {@link #getValue(int, String)} calls, to prove the
     * sort precompute reads each (row, sort column) cell exactly once.
     */
    private static final class CountingTable implements ITable
    {
        private final ITable delegate;
        private int valueCallCount;

        private CountingTable(final ITable delegate)
        {
            this.delegate = delegate;
        }

        int getValueCallCount()
        {
            return valueCallCount;
        }

        @Override
        public ITableMetaData getTableMetaData()
        {
            return delegate.getTableMetaData();
        }

        @Override
        public int getRowCount()
        {
            return delegate.getRowCount();
        }

        @Override
        public Object getValue(final int row, final String columnName) throws DataSetException
        {
            valueCallCount++;
            return delegate.getValue(row, columnName);
        }
    }

}
