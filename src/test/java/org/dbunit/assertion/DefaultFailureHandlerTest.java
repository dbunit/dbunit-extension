/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2004, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.junit.jupiter.api.Test;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @since 2.4.0
 */
class DefaultFailureHandlerTest
{
    private static final String MY_TABLE = "MY_TABLE";

    private static final String COL_NAME_1 = "COL_1";
    private static final String COL_NAME_2 = "COL_2";

    private static final String COL_VALUE_1 = "value1";
    private static final String COL_VALUE_2 = "value2";

    @Test
    void testGetColumn() throws Exception
    {
        final Column[] cols =
                new Column[] {new Column(COL_NAME_1, DataType.UNKNOWN),
                        new Column(COL_NAME_2, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable(MY_TABLE, cols);
        table.addRow(new Object[] {COL_VALUE_1, COL_VALUE_2});

        // Filter COL_NAME_1
        final ITable tableFiltered = DefaultColumnFilter
                .excludedColumnsTable(table, new String[] {COL_NAME_1});

        final DefaultFailureHandler failureHandler =
                new DefaultFailureHandler(cols);
        final String info = failureHandler.getAdditionalInfo(tableFiltered,
                tableFiltered, 0, COL_NAME_1);

        final String expectedInfo = "Additional row info: ('" + COL_NAME_1
                + "': expected=<" + COL_VALUE_1 + ">, actual=<" + COL_VALUE_1
                + ">) ('" + COL_NAME_2 + "': expected=<" + COL_VALUE_2
                + ">, actual=<" + COL_VALUE_2 + ">)";
        assertThat(info).isEqualTo(expectedInfo);
    }

    @Test
    void testMakeAdditionalColumnInfoErrorMessage()
    {
        final DefaultFailureHandler defaultFailureHandler =
                new DefaultFailureHandler();

        final String columnName = "testColumnName";
        final DataSetException e =
                new DataSetException("test exception message");
        final String actual = defaultFailureHandler
                .makeAdditionalColumnInfoErrorMessage(columnName, e);
        assertThat(actual).as("Error message is null.").isNotNull();

        // manually review log for acceptable message content
    }

    @Test
    void testGetColumnValue_Found() throws DataSetException
    {
        final Column[] cols =
                new Column[] {new Column(COL_NAME_1, DataType.UNKNOWN),
                        new Column(COL_NAME_2, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable(MY_TABLE, cols);
        table.addRow(new Object[] {COL_VALUE_1, COL_VALUE_2});

        DefaultColumnFilter.excludedColumnsTable(table,
                new String[] {COL_NAME_1});
        final DefaultFailureHandler defaultFailureHandler =
                new DefaultFailureHandler();

        final Object expected = COL_VALUE_1;

        final int rowIndex = 0;
        final String columnName = COL_NAME_1;
        final Object actual = defaultFailureHandler.getColumnValue(table,
                rowIndex, columnName);

        assertThat(actual).as("Wrong column value found.").isEqualTo(expected);
    }

    @Test
    void testGetColumnValue_NotFound() throws DataSetException
    {
        final Column[] cols =
                new Column[] {new Column(COL_NAME_1, DataType.UNKNOWN),
                        new Column(COL_NAME_2, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable(MY_TABLE, cols);
        table.addRow(new Object[] {COL_VALUE_1, COL_VALUE_2});

        DefaultColumnFilter.excludedColumnsTable(table,
                new String[] {COL_NAME_1});
        final DefaultFailureHandler defaultFailureHandler =
                new DefaultFailureHandler();

        final Object expected = COL_VALUE_1;

        final int rowIndex = 0;
        final String columnName = "NonExistingColumnName";
        final Object actual = defaultFailureHandler.getColumnValue(table,
                rowIndex, columnName);

        assertNotSame(expected, actual, "Wrong column value found.");
    }
}
