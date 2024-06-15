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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.LinkedList;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.ForwardOnlyTableTest;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.MockTableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @since Apr 11, 2003
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class ForwardOnlyResultSetTableIT extends ForwardOnlyTableTest
{

    @Mock
    private ResultSet mockResultSet;

    @Override
    protected ITable createTable() throws Exception
    {
        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = env.getConnection();

        DatabaseOperation.CLEAN_INSERT.execute(connection,
                env.getInitDataSet());

        final String selectStatement =
                "select * from TEST_TABLE order by COLUMN0";
        return new ForwardOnlyResultSetTable("TEST_TABLE", selectStatement,
                connection);
    }

    @Override
    protected String convertString(final String str) throws Exception
    {
        return DatabaseEnvironment.getInstance().convertString(str);
    }

    @Override
    public void testGetMissingValue() throws Exception
    {
        // Do not test this!
    }

    @Test
    void testGetValueOnLastRowIsClosingResultSet() throws Exception
    {
        final String tableName = "TABLE";
        final String[] columnNames = {"C0"};
        // String[] columnNames = {"C0", "C1", "C2"};
        final Object[][] expectedValues = new Object[][] {
                new Object[] {"1", "2", "3"}, new Object[] {"4", "5", "6"},
                new Object[] {"7", "8", "9"},};

        // Setup resultset
        // Our resultSet wasNull call the array
        final LinkedList<Object[]> results = new LinkedList<>();
        Arrays.asList(expectedValues).forEach(results::add);
        // We only have 1 column so just return value in position [0]
        when(mockResultSet.getObject(anyInt()))
                .thenAnswer(invocation -> results.removeFirst()[0]);
        when(mockResultSet.next()).thenReturn(true).thenReturn(true)
                .thenReturn(false);

        // Create table
        final MockTableMetaData metaData =
                new MockTableMetaData(tableName, columnNames);
        final ForwardOnlyResultSetTable table =
                new ForwardOnlyResultSetTable(metaData, mockResultSet);

        // Exercise getValue()
        try
        {
            final Column[] columns = table.getTableMetaData().getColumns();

            for (int i = 0;; i++)
            {
                for (int j = 0; j < columns.length; j++)
                {
                    final String columnName = columns[j].getColumnName();
                    final Object actualValue = table.getValue(i, columnName);
                    final Object expectedValue = expectedValues[i][j];
                    assertThat(actualValue)
                            .as("row=" + i + ", col=" + columnName)
                            .isEqualTo(expectedValue);

                }
            }
        } catch (final RowOutOfBoundsException e)
        {
            // end of table
        }

        // Verify that ResultSet have been closed
        verify(mockResultSet, times(1)).close();
    }

}
