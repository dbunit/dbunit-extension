/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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
package org.dbunit.database.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.sql.Connection;
import java.util.HashSet;
import java.util.TreeSet;

import org.dbunit.DdlExecutor;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.PrimaryKeyFilter.PkTableMap;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.search.SearchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Aug 28, 2005
 */
class TablesDependencyHelperTest
{

    private Connection jdbcConnection;

    private IDatabaseConnection connection;

    protected void setUp(final String sqlFile) throws Exception
    {
        this.setUp(new String[] {sqlFile});
    }

    protected void setUp(final String[] sqlFileList) throws Exception
    {
        this.jdbcConnection =
                HypersonicEnvironment.createJdbcConnection("mem:tempdb");
        for (int i = 0; i < sqlFileList.length; i++)
        {
            final File sql = TestUtils.getFile("sql/" + sqlFileList[i]);
            DdlExecutor.executeDdlFile(sql, this.jdbcConnection);
        }
        this.connection = new DatabaseConnection(jdbcConnection);
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        HypersonicEnvironment.shutdown(this.jdbcConnection);
        this.jdbcConnection.close();
        // HypersonicEnvironment.deleteFiles( "tempdb" );
    }

    @Test
    void testGetDependentTablesFromOneTable() throws Exception
    {
        setUp(ImportNodesFilterSearchCallbackTest.SQL_FILE);
        final String[][] allInput =
                ImportNodesFilterSearchCallbackTest.SINGLE_INPUT;
        final String[][] allExpectedOutput =
                ImportNodesFilterSearchCallbackTest.SINGLE_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final String[] actualOutput = TablesDependencyHelper
                    .getDependentTables(this.connection, input[0]);
            assertThat(actualOutput).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    @Test
    void testGetDependentTablesFromOneTable_RootTableDoesNotExist()
            throws Exception
    {
        setUp(ImportNodesFilterSearchCallbackTest.SQL_FILE);

        try
        {
            TablesDependencyHelper.getDependentTables(this.connection,
                    "XXXXXX_TABLE_NON_EXISTING");
            fail("Should not be able to get the dependent tables for a non existing input table");
        } catch (final SearchException expected)
        {
            final Throwable cause = expected.getCause();
            assertThat(cause).isInstanceOf(NoSuchTableException.class);

            final String expectedMessage =
                    "The table 'XXXXXX_TABLE_NON_EXISTING' does not exist in schema 'null'";
            assertThat(cause).hasMessage(expectedMessage);
        }
    }

    @Test
    void testGetDependentTablesFromManyTables() throws Exception
    {
        setUp(ImportNodesFilterSearchCallbackTest.SQL_FILE);
        final String[][] allInput =
                ImportNodesFilterSearchCallbackTest.COMPOUND_INPUT;
        final String[][] allExpectedOutput =
                ImportNodesFilterSearchCallbackTest.COMPOUND_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final String[] actualOutput = TablesDependencyHelper
                    .getDependentTables(this.connection, input);
            assertThat(actualOutput).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    @Test
    void testGetAllDependentTablesFromOneTable() throws Exception
    {
        setUp(ImportAndExportKeysSearchCallbackOwnFileTest.SQL_FILE);
        final String[][] allInput =
                ImportAndExportKeysSearchCallbackOwnFileTest.SINGLE_INPUT;
        final String[][] allExpectedOutput =
                ImportAndExportKeysSearchCallbackOwnFileTest.SINGLE_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final String[] actualOutput = TablesDependencyHelper
                    .getAllDependentTables(this.connection, input[0]);
            assertThat(actualOutput).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    @Test
    void testGetAllDependentTablesFromManyTables() throws Exception
    {
        setUp(ImportAndExportKeysSearchCallbackOwnFileTest.SQL_FILE);
        final String[][] allInput =
                ImportAndExportKeysSearchCallbackOwnFileTest.COMPOUND_INPUT;
        final String[][] allExpectedOutput =
                ImportAndExportKeysSearchCallbackOwnFileTest.COMPOUND_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final String[] actualOutput = TablesDependencyHelper
                    .getAllDependentTables(this.connection, input);
            assertThat(actualOutput).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    @Test
    void testGetAllDatasetFromOneTable() throws Exception
    {
        setUp(ImportAndExportKeysSearchCallbackOwnFileTest.SQL_FILE);
        final String[][] allInput =
                ImportAndExportKeysSearchCallbackOwnFileTest.SINGLE_INPUT;
        final String[][] allExpectedOutput =
                ImportAndExportKeysSearchCallbackOwnFileTest.SINGLE_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final IDataSet actualOutput = TablesDependencyHelper
                    .getAllDataset(this.connection, input[0], new HashSet<>());
            final String[] actualOutputTables = actualOutput.getTableNames();
            assertThat(actualOutputTables).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    @Test
    void testGetAllDatasetFromOneTable_SeparateSchema() throws Exception
    {
        setUp(new String[] {"hypersonic_switch_schema.sql",
                ImportAndExportKeysSearchCallbackOwnFileTest.SQL_FILE});

        final String[][] allInputWithSchema =
                ImportAndExportKeysSearchCallbackOwnFileTest
                        .getSingleInputWithSchema("TEST_SCHEMA");
        final String[][] allExpectedOutput =
                ImportAndExportKeysSearchCallbackOwnFileTest.SINGLE_OUTPUT;
        for (int i = 0; i < allInputWithSchema.length; i++)
        {
            final String[] input = allInputWithSchema[i];
            final String[] expectedOutput = allExpectedOutput[i];
            final IDataSet actualOutput = TablesDependencyHelper
                    .getAllDataset(this.connection, input[0], new HashSet<>());
            final String[] actualOutputTables = actualOutput.getTableNames();
            assertThat(actualOutputTables).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    /**
     * Ensure the order is not lost on the way because of the conversion between
     * Map and Array
     *
     * @throws Exception
     */
    @Test
    void testGetDatasetFromManyTables() throws Exception
    {
        setUp(ImportNodesFilterSearchCallbackTest.SQL_FILE);
        final String[][] allInput =
                ImportNodesFilterSearchCallbackTest.COMPOUND_INPUT;
        final String[][] allExpectedOutput =
                ImportNodesFilterSearchCallbackTest.COMPOUND_OUTPUT;
        for (int i = 0; i < allInput.length; i++)
        {
            final String[] input = allInput[i];
            final PkTableMap inputMap = new PkTableMap();
            for (int j = 0; j < input.length; j++)
            {
                inputMap.put(input[j], new TreeSet<>());
            }

            final String[] expectedOutput = allExpectedOutput[i];
            final IDataSet actualOutput = TablesDependencyHelper
                    .getDataset(this.connection, inputMap);
            final String[] actualOutputArray = actualOutput.getTableNames();
            assertThat(actualOutputArray).as("output didn't match for i=" + i)
                    .isEqualTo(expectedOutput);
        }
    }

    // TODO ImportAndExportKeysSearchCallbackOwnFileTest

}
