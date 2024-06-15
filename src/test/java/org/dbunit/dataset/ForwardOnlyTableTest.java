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

import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Apr 11, 2003
 * @version $Revision$
 */
public class ForwardOnlyTableTest extends DefaultTableTest
{

    @Override
    protected ITable createTable() throws Exception
    {
        return new ForwardOnlyTable(super.createTable());
    }

    @Override
    @Test
    void testGetRowCount() throws Exception
    {
        final ITable table = createTable();
        assertThrows(UnsupportedOperationException.class,
                () -> table.getRowCount(),
                "Should have throw UnsupportedOperationException");
    }

    @Override
    @Test
    void testGetValueRowBounds() throws Exception
    {
        final int[] rows = new int[] {ROW_COUNT, ROW_COUNT + 1};
        final ITable table = createTable();
        final String columnName =
                table.getTableMetaData().getColumns()[0].getColumnName();

        for (int i = 0; i < rows.length; i++)
        {
            final int row = i;
            assertThrows(RowOutOfBoundsException.class,
                    () -> table.getValue(rows[row], columnName),
                    "Should throw a RowOutOfBoundsException!");
        }
    }

    @Test
    void testGetValueIterateBackward() throws Exception
    {
        final ITable table = createTable();
        for (int i = 0; i < ROW_COUNT; i++)
        {
            final int row = i;
            for (int j = 0; j < COLUMN_COUNT; j++)
            {
                final String columnName = "COLUMN" + j;
                final String expected = "row " + i + " col " + j;
                final Object value = table.getValue(i, columnName);
                assertThat(value).as("value").isEqualTo(expected);
            }

            // Try access values from previous row
            for (int j = 0; j < COLUMN_COUNT; j++)
            {
                final String columnName = "COLUMN" + j;
                assertThrows(UnsupportedOperationException.class,
                        () -> table.getValue(row - 1, columnName));
            }
        }
    }

    @Test
    void testGetValueOnEmptyTable() throws Exception
    {
        final MockTableMetaData metaData =
                new MockTableMetaData("TABLE", new String[] {"C1"});
        final ITable table = new ForwardOnlyTable(new DefaultTable(metaData));
        assertThrows(RowOutOfBoundsException.class,
                () -> table.getValue(0, "C1"),
                "Should have throw RowOutOfBoundsException");

    }

    /**
     * @throws Exception
     */
    @Override
    @Test
    public void testGetMissingValue() throws Exception
    {
        super.testGetMissingValue();
    }

}
