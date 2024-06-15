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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public abstract class AbstractTableTest
{
    protected static final int ROW_COUNT = 6;
    protected static final int COLUMN_COUNT = 4;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Creates a table having 6 row and 4 column where columns are named
     * "COLUMN1, COLUMN2, COLUMN3, COLUMN4" and values are string follwing this
     * template "row ? col ?"
     */
    protected abstract ITable createTable() throws Exception;

    /**
     * Returns the string converted as an identifier according to the metadata
     * rules of the database environment. Most databases convert all metadata
     * identifiers to uppercase. PostgreSQL converts identifiers to lowercase.
     * MySQL preserves case.
     * 
     * @param str
     *            The identifier.
     * @return The identifier converted according to database rules.
     */
    protected String convertString(final String str) throws Exception
    {
        return str;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Test methods
    @Test
    void testGetRowCount() throws Exception
    {
        assertThat(createTable().getRowCount()).as("row count")
                .isEqualTo(ROW_COUNT);
    }

    @Test
    void testTableMetaData() throws Exception
    {
        final Column[] columns = createTable().getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(COLUMN_COUNT);
        for (int i = 0; i < columns.length; i++)
        {
            final String expected = convertString("COLUMN" + i);
            final String actual = columns[i].getColumnName();
            assertThat(actual).as("column name").isEqualTo(expected);
        }
    }

    @Test
    protected void testGetValue() throws Exception
    {
        final ITable table = createTable();
        for (int i = 0; i < ROW_COUNT; i++)
        {
            for (int j = 0; j < COLUMN_COUNT; j++)
            {
                final String columnName = "COLUMN" + j;
                final String expected = "row " + i + " col " + j;
                final Object value = table.getValue(i, columnName);
                assertThat(value).as("value").isEqualTo(expected);
            }
        }
    }

    @Test
    void testGetValueCaseInsensitive() throws Exception
    {
        final ITable table = createTable();
        for (int i = 0; i < ROW_COUNT; i++)
        {
            for (int j = 0; j < COLUMN_COUNT; j++)
            {
                final String columnName = "CoLUmN" + j;
                final String expected = "row " + i + " col " + j;
                final Object value = table.getValue(i, columnName);
                assertThat(value).as("value").isEqualTo(expected);
            }
        }
    }

    public abstract void testGetMissingValue() throws Exception;

    @Test
    void testGetValueRowBounds() throws Exception
    {
        final int[] rows =
                new int[] {-2, -1, -ROW_COUNT, ROW_COUNT, ROW_COUNT + 1};
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
    void testGetValueAndNoSuchColumn() throws Exception
    {
        final ITable table = createTable();
        final String columnName = "Unknown";

        assertThrows(NoSuchColumnException.class,
                () -> table.getValue(0, columnName),
                "Should throw a NoSuchColumnException!");

    }

    /**
     * This method is used so sub-classes can disable the tests according to
     * some characteristics of the environment
     * 
     * @param testName
     *            name of the test to be checked
     * @return flag indicating if the test should be executed or not
     */
    protected boolean runTest(final String testName)
    {
        return true;
    }
}