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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.dbunit.database.AmbiguousTableNameException;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 22, 2002
 */
public abstract class AbstractDataSetTest extends AbstractTest
{

    protected int[] getExpectedDuplicateRows()
    {
        return new int[] {1, 0, 2};
    }

    /**
     * This method exclude BLOB_TABLE and CLOB_TABLE from the specified dataset
     * because BLOB and CLOB are not supported by all database vendor. It also
     * excludes tables with Identity columns (MSSQL) because they are specific
     * to MSSQL. TODO : should be refactored into the various
     * DatabaseEnvironments!
     */
    public static IDataSet removeExtraTestTables(final IDataSet dataSet)
            throws Exception
    {
        String[] names = dataSet.getTableNames();

        // exclude BLOB_TABLE and CLOB_TABLE from test since not supported by
        // all database vendor
        final List<String> nameList = new LinkedList<>(Arrays.asList(names));
        nameList.remove("BLOB_TABLE");
        nameList.remove("CLOB_TABLE");
        nameList.remove("SDO_GEOMETRY_TABLE");
        nameList.remove("XML_TYPE_TABLE");
        nameList.remove("DBUNIT.BLOB_TABLE");
        nameList.remove("DBUNIT.CLOB_TABLE");
        nameList.remove("DBUNIT.SDO_GEOMETRY");
        nameList.remove("DBUNIT.XML_TYPE_TABLE");
        /*
         * this table shows up on MSSQLServer. It is a user table for storing
         * diagram information that really should be considered a system table.
         */
        nameList.remove("DBUNIT.dtproperties");
        nameList.remove("dtproperties");
        /*
         * these noted in mcr.microsoft.com/mssql/server:2019-latest
         */
        nameList.remove("MSreplication_options");
        final List<String> removeList = nameList.stream()
                .filter(t -> t.startsWith("spt")).collect(Collectors.toList());
        nameList.removeAll(removeList);
        /*
         * These tables are created specifically for testing identity columns on
         * MSSQL server. They should be ignored on other platforms.
         */
        nameList.remove("DBUNIT.IDENTITY_TABLE");
        nameList.remove("IDENTITY_TABLE");
        nameList.remove("DBUNIT.TEST_IDENTITY_NOT_PK");
        nameList.remove("TEST_IDENTITY_NOT_PK");

        names = nameList.toArray(new String[0]);

        return new FilteredDataSet(names, dataSet);
    }

    protected abstract IDataSet createDataSet() throws Exception;

    protected abstract IDataSet createDuplicateDataSet() throws Exception;

    /**
     * Create a dataset with duplicate tables having different char case in name
     * 
     * @return
     */
    protected abstract IDataSet createMultipleCaseDuplicateDataSet()
            throws Exception;

    protected void assertEqualsTableName(final String mesage,
            final String expected, final String actual)
    {
        assertThat(actual).as(mesage).isEqualTo(expected);
    }

    @Test
    void testGetTableNames() throws Exception
    {
        final String[] expected = getExpectedNames();
        assertContainsIgnoreCase("minimal names subset",
                super.getExpectedNames(), expected);

        final IDataSet dataSet = createDataSet();
        final String[] names = dataSet.getTableNames();

        assertThat(names).as("table count").hasSize(expected.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEqualsTableName("name " + i, expected[i], names[i]);
        }
    }

    @Test
    void testGetTableNamesDefensiveCopy() throws Exception
    {
        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("Should not be same intance")
                .isNotSameAs(dataSet.getTableNames());
    }

    @Test
    void testGetTable() throws Exception
    {
        final String[] expected = getExpectedNames();

        final IDataSet dataSet = createDataSet();
        for (int i = 0; i < expected.length; i++)
        {
            final ITable table = dataSet.getTable(expected[i]);
            assertEqualsTableName("name " + i, expected[i],
                    table.getTableMetaData().getTableName());
        }
    }

    @Test
    void testGetUnknownTable() throws Exception
    {
        final IDataSet dataSet = createDataSet();
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTable("UNKNOWN_TABLE"),
                "Should throw a NoSuchTableException");

    }

    @Test
    void testGetTableMetaData() throws Exception
    {
        final String[] expected = getExpectedNames();

        final IDataSet dataSet = createDataSet();
        for (int i = 0; i < expected.length; i++)
        {
            final ITableMetaData metaData =
                    dataSet.getTableMetaData(expected[i]);
            assertEqualsTableName("name " + i, expected[i],
                    metaData.getTableName());
        }
    }

    @Test
    void testGetUnknownTableMetaData() throws Exception
    {
        final IDataSet dataSet = createDataSet();
        assertThrows(NoSuchTableException.class,
                () -> dataSet.getTableMetaData("UNKNOWN_TABLE"),
                "Should throw a NoSuchTableException");
    }

    @Test
    void testGetTables() throws Exception
    {
        final String[] expected = getExpectedNames();
        assertContainsIgnoreCase("minimal names subset",
                super.getExpectedNames(), expected);

        final IDataSet dataSet = createDataSet();
        final ITable[] tables = dataSet.getTables();
        assertThat(tables).as("table count").hasSize(expected.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEqualsTableName("name " + i, expected[i],
                    tables[i].getTableMetaData().getTableName());
        }
    }

    @Test
    public void testGetTablesDefensiveCopy() throws Exception
    {
        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTables()).as("Should not be same instance")
                .isNotSameAs(dataSet.getTables());
    }

    @Test
    public void testCreateDuplicateDataSet() throws Exception
    {
        assertThrows(AmbiguousTableNameException.class, () ->
        /* IDataSet dataSet = */createDuplicateDataSet(),
                "Should throw AmbiguousTableNameException in creation phase");

    }

    @Test
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        assertThrows(AmbiguousTableNameException.class, () ->
        /* IDataSet dataSet = */createMultipleCaseDuplicateDataSet(),
                "Should throw AmbiguousTableNameException in creation phase");
    }

    @Test
    public void testGetCaseInsensitiveTable() throws Exception
    {
        final String[] expectedNames = getExpectedLowerNames();

        final IDataSet dataSet = createDataSet();
        for (int i = 0; i < expectedNames.length; i++)
        {
            final String expected = expectedNames[i];
            final ITable table = dataSet.getTable(expected);
            final String actual = table.getTableMetaData().getTableName();

            if (!expected.equalsIgnoreCase(actual))
            {
                assertThat(actual).as("name " + i).isEqualTo(expected);
            }
        }
    }

    @Test
    public void testGetCaseInsensitiveTableMetaData() throws Exception
    {
        final String[] expectedNames = getExpectedLowerNames();
        final IDataSet dataSet = createDataSet();

        for (int i = 0; i < expectedNames.length; i++)
        {
            final String expected = expectedNames[i];
            final ITableMetaData metaData = dataSet.getTableMetaData(expected);
            final String actual = metaData.getTableName();

            if (!expected.equalsIgnoreCase(actual))
            {
                assertThat(actual).as("name " + i).isEqualTo(expected);
            }
        }
    }

    @Test
    void testIterator() throws Exception
    {
        final String[] expected = getExpectedNames();
        assertContainsIgnoreCase("minimal names subset",
                super.getExpectedNames(), expected);

        int i = 0;
        final ITableIterator iterator = createDataSet().iterator();
        while (iterator.next())
        {
            assertEqualsTableName("name " + i, expected[i],
                    iterator.getTableMetaData().getTableName());
            i++;
        }
        assertThat(i).as("table count").isEqualTo(expected.length);
    }

    @Test
    void testReverseIterator() throws Exception
    {
        final String[] expected =
                DataSetUtils.reverseStringArray(getExpectedNames());
        assertContainsIgnoreCase("minimal names subset",
                super.getExpectedNames(), expected);

        int i = 0;
        final ITableIterator iterator = createDataSet().reverseIterator();
        while (iterator.next())
        {
            assertEqualsTableName("name " + i, expected[i],
                    iterator.getTableMetaData().getTableName());
            i++;
        }
        assertThat(i).as("table count").isEqualTo(expected.length);
    }

    protected ITable[] createDuplicateTables(final boolean multipleCase)
            throws AmbiguousTableNameException
    {
        final ITable table1 = new DefaultTable("DUPLICATE_TABLE");
        final ITable table2 = new DefaultTable("EMPTY_TABLE");
        ITable table3;
        if (!multipleCase)
        {
            table3 = new DefaultTable("DUPLICATE_TABLE");
        } else
        {
            table3 = new DefaultTable("duplicate_TABLE");
        }
        final ITable[] tables = new ITable[] {table1, table2, table3};
        return tables;
    }
}