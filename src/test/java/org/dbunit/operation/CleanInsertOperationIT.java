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
package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.Assertion;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.LowerCaseDataSet;
import org.dbunit.dataset.SortedTable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DatabaseOperation#CLEAN_INSERT}.
 *
 * @since 3.2.0
 */
class CleanInsertOperationIT extends AbstractDatabaseIT
{
    private static final String TABLE_NAME = "TEST_TABLE";

    private static final Column[] COLUMNS = new Column[]{
            new Column("COLUMN0", DataType.VARCHAR),
            new Column("COLUMN1", DataType.VARCHAR),
            new Column("COLUMN2", DataType.VARCHAR),
            new Column("COLUMN3", DataType.VARCHAR)
    };

    @Test
    void testExecute_withExistingRows_deletesAllBeforeInserting() throws Exception
    {
        assertThat(_connection.getRowCount(TABLE_NAME)).as("count before.").isEqualTo(6);

        final DefaultTable newData = new DefaultTable(TABLE_NAME, COLUMNS);
        newData.addRow(new Object[]{"new0", "v0", "v0", "v0"});
        newData.addRow(new Object[]{"new1", "v1", "v1", "v1"});

        DatabaseOperation.CLEAN_INSERT.execute(_connection, new DefaultDataSet(newData));

        assertThat(_connection.getRowCount(TABLE_NAME)).as("count after.").isEqualTo(2);
    }

    @Test
    void testExecute_withLowerCaseDataSet_deletesAndInsertsSuccessfully() throws Exception
    {
        final DefaultTable data = new DefaultTable(TABLE_NAME, COLUMNS);
        data.addRow(new Object[]{"r0", "v0", "v0", "v0"});
        data.addRow(new Object[]{"r1", "v1", "v1", "v1"});
        final LowerCaseDataSet lowerDataSet = new LowerCaseDataSet(new DefaultDataSet(data));

        DatabaseOperation.CLEAN_INSERT.execute(_connection, lowerDataSet);

        assertThat(_connection.getRowCount(TABLE_NAME)).as("count after.").isEqualTo(2);
    }

    @Test
    void testExecute_withXmlDataSet_replacesAllExistingRowsWithNewData() throws Exception
    {
        final DefaultTable initialData = new DefaultTable(TABLE_NAME, COLUMNS);
        initialData.addRow(new Object[]{"initial0", "v0", "v0", "v0"});
        DatabaseOperation.INSERT.execute(_connection,
                new DefaultDataSet(initialData));

        final DefaultTable newData = new DefaultTable(TABLE_NAME, COLUMNS);
        newData.addRow(new Object[]{"replaced0", "n0", "n0", "n0"});
        newData.addRow(new Object[]{"replaced1", "n1", "n1", "n1"});
        newData.addRow(new Object[]{"replaced2", "n2", "n2", "n2"});

        DatabaseOperation.CLEAN_INSERT.execute(_connection, new DefaultDataSet(newData));

        final ITable actual = _connection.createDataSet().getTable(TABLE_NAME);
        final SortedTable sortedExpected =
                new SortedTable(newData, new String[]{"COLUMN0"});
        final SortedTable sortedActual =
                new SortedTable(actual, new String[]{"COLUMN0"});
        Assertion.assertEquals(sortedExpected, sortedActual);
    }

    @Test
    void testExecute_withEmptyReplacementDataset_deletesAllExistingRows() throws Exception
    {
        assertThat(_connection.getRowCount(TABLE_NAME)).as("count before.").isEqualTo(6);

        final DefaultTable emptyData = new DefaultTable(TABLE_NAME, COLUMNS);

        DatabaseOperation.CLEAN_INSERT.execute(_connection, new DefaultDataSet(emptyData));

        assertThat(_connection.getRowCount(TABLE_NAME)).as("count after.").isZero();
    }
}
