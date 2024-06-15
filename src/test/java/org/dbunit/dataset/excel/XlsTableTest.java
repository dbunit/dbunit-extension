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
package org.dbunit.dataset.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.TimeZone;

import org.dbunit.dataset.AbstractTableTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Feb 21, 2003
 */
public class XlsTableTest extends AbstractTableTest
{
    // private static final long ONE_SECOND_IN_MILLIS = 1000;
    // private static final long ONE_MINUTE_IN_MILLIS = 60 * 1000;
    // private static final long ONE_HOUR_IN_MILLIS = 60 * ONE_MINUTE_IN_MILLIS;
    // private static final long ONE_DAY_IN_MILLIS = 24 * ONE_HOUR_IN_MILLIS;

    @Override
    protected ITable createTable() throws Exception
    {
        return createDataSet().getTable("TEST_TABLE");
    }

    protected IDataSet createDataSet() throws Exception
    {
        return new XlsDataSet(TestUtils.getFile("xml/tableTest.xls"));
    }

    @Override
    @Test
    public void testGetMissingValue() throws Exception
    {
        final int row = 0;
        final Object[] expected = {"row 0 col 0", null, "row 0 col 2"};

        final ITable table = createDataSet().getTable("MISSING_VALUES");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSameSizeAs(expected);
        assertThat(table.getRowCount()).as("row count").isEqualTo(1);
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(table.getValue(row, columns[i].getColumnName()))
                    .as("value " + i).isEqualTo(expected[i]);
        }
    }

    @Test
    void testEmptyTableColumns() throws Exception
    {
        final Column[] expectedColumns =
                new Column[] {new Column("COLUMN0", DataType.UNKNOWN),
                        new Column("COLUMN1", DataType.UNKNOWN),
                        new Column("COLUMN2", DataType.UNKNOWN),
                        new Column("COLUMN3", DataType.UNKNOWN)};
        final ITable table = createDataSet().getTable("EMPTY_TABLE");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("Column count").hasSameSizeAs(expectedColumns);
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(columns[i]).as("Column " + i)
                    .isEqualTo(expectedColumns[i]);
        }
    }

    @Test
    void testEmptySheet() throws Exception
    {
        final ITable table = createDataSet().getTable("EMPTY_SHEET");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("Column count").isEmpty();
    }

    @Test
    void testDifferentDatatypes() throws Exception
    {
        final int row = 0;
        final ITable table =
                createDataSet().getTable("TABLE_DIFFERENT_DATATYPES");

        // When cell type is numeric and cell value is datetime,
        // Apache-POI returns datetime with system default timezone offset.
        // And java.util.Date#getTime() returns time without timezone offset (=
        // UTC).
        // So actual time values in this case will be UTC time in the system
        // default timezone.
        // Expected time values also should be UTC time in the system default
        // timezone.
        final long tzOffset = TimeZone.getDefault().getRawOffset();
        final Object[] expected = {
                // new Date(0-tzOffset),
                // new Date(0-tzOffset + (10*ONE_HOUR_IN_MILLIS +
                // 45*ONE_MINUTE_IN_MILLIS)),
                // new Date(0-tzOffset + (13*ONE_HOUR_IN_MILLIS +
                // 30*ONE_MINUTE_IN_MILLIS + 55*ONE_SECOND_IN_MILLIS) ),
                // Long.valueOf(25569),// Dates stored as Long numbers
                // Long.valueOf(25569447916666668L),
                // Long.valueOf(563136574074074L),
                Long.valueOf(0 - tzOffset), // Dates stored as Long numbers
                Long.valueOf(38700000 - tzOffset),
                Long.valueOf(-2209026545000L - tzOffset),
                new BigDecimal("10000.00"), new BigDecimal("-200"),
                new BigDecimal("12345.123456789000"),
                Long.valueOf(1233398764000L - tzOffset),
                Long.valueOf(1233332866000L) // The last column is a
                                             // dbunit-date-formatted column in
                                             // the excel sheet
        };

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSameSizeAs(expected);
        for (int i = 0; i < columns.length; i++)
        {
            final Object actual =
                    table.getValue(row, columns[i].getColumnName());
            final String typesResult = " expected="
                    + (expected[i] != null ? expected[i].getClass().getName()
                            : "null")
                    + " - actual="
                    + (actual != null ? actual.getClass().getName() : "null");
            assertThat(actual).as("value " + i + " (" + typesResult + ")")
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    void testNumberAsText() throws Exception
    {
        final int row = 0;
        final ITable table = createDataSet().getTable("TABLE_NUMBER_AS_TEXT");

        final String[] expected = {"0", "666", "66.6", "66.6", "-6.66"};

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSameSizeAs(expected);
        for (int i = 0; i < columns.length; i++)
        {
            final String columnName = columns[i].getColumnName();
            final Object actual = table.getValue(row, columnName).toString();
            assertThat(actual).as(columns[i].getColumnName())
                    .isEqualTo(expected[i]);
        }
    }
}
