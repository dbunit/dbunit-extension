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

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public class DefaultTableTest extends AbstractTableTest
{

    @Override
    protected ITable createTable() throws Exception
    {
        return createTable(COLUMN_COUNT, ROW_COUNT, 0);
    }

    protected ITable createTable(final int columnCount, final int rowCount,
            final int startRow) throws Exception
    {
        final DefaultTable table =
                new DefaultTable(createTableMetaData(columnCount));
        for (int i = 0; i < rowCount; i++)
        {
            final Object[] rowValues = new Object[columnCount];
            for (int j = 0; j < rowValues.length; j++)
            {
                rowValues[j] = "row " + (i + startRow) + " col " + j;
            }
            table.addRow(rowValues);
        }
        return table;
    }

    protected ITableMetaData createTableMetaData(final int columnCount)
            throws Exception
    {
        final Column[] columns = new Column[columnCount];
        for (int i = 0; i < columns.length; i++)
        {
            columns[i] = new Column("COLUMN" + i, DataType.UNKNOWN);
        }

        return new DefaultTableMetaData("myTable", columns);
    }

    @Override
    @Test
    public void testGetMissingValue() throws Exception
    {
        final String columnName = "COLUMN0";
        final Object expected = ITable.NO_VALUE;

        final DefaultTable table =
                new DefaultTable(createTableMetaData(COLUMN_COUNT));
        table.addRow(
                new Object[] {ITable.NO_VALUE, ITable.NO_VALUE, ITable.NO_VALUE,
                        ITable.NO_VALUE, ITable.NO_VALUE, ITable.NO_VALUE});
        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(Columns.getColumn(columnName, columns)).isNotNull();
        assertThat(table.getValue(0, columnName)).as("no value")
                .isEqualTo(expected);
    }

}
