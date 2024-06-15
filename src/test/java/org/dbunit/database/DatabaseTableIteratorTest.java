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

import org.dbunit.dataset.AbstractTableIteratorTest;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.MockDataSet;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Apr 6, 2003
 * @version $Revision$
 */
class DatabaseTableIteratorTest extends AbstractTableIteratorTest
{

    private MockDataSet createMockDataSet(final String[] expectedNames)
    {
        final MockDataSet dataSet = new MockDataSet();
        for (int i = 0; i < expectedNames.length; i++)
        {
            final String tableName = expectedNames[i];
            final MockResultSetTable table = new MockResultSetTable();
            table.setupTableMetaData(tableName);
            table.setExpectedCloseCalls(1);
            dataSet.addTable(table);
        }
        return dataSet;
    }

    @Override
    protected ITableIterator getIterator() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final MockDataSet dataSet = createMockDataSet(expectedNames);

        return new DatabaseTableIterator(expectedNames, dataSet);
    }

    @Override
    protected ITableIterator getEmptyIterator() throws Exception
    {
        return new DatabaseTableIterator(new String[0], new MockDataSet());
    }

    @Test
    void testGetTableClose() throws Exception
    {
        int i = 0;
        final String[] expectedNames = getExpectedNames();
        final MockDataSet dataSet = createMockDataSet(expectedNames);

        final ITableIterator iterator =
                new DatabaseTableIterator(expectedNames, dataSet);
        while (iterator.next())
        {
            final ITable table = iterator.getTable();
            assertThat(table.getTableMetaData().getTableName()).as("name " + i)
                    .isEqualTo(expectedNames[i]);
            i++;
        }
        assertThat(i).as("count").isEqualTo(expectedNames.length);
        dataSet.verify();
    }
}
