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

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DdlExecutor;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.PrimaryKeyFilter.PkTableMap;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.search.SearchException;
import org.junit.jupiter.api.Test;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Aug 28, 2005
 */
class TablesDependencyHelperIT extends AbstractDatabaseIT
{

    /**
     * Replaces {@code _connection} with a fresh connection after test tables
     * have been dropped, so that {@code AbstractDatabaseIT.tearDown()} uses an
     * up-to-date dataset that does not include the dropped tables.
     *
     * @throws Exception
     */
    private void refreshConnection() throws Exception
    {
        _connection.close();
        _connection = getDatabaseTester().getConnection();
        setUpDatabaseConfig(_connection.getConfig());
    }


    @Test
    void testGetDependentTables_withSingleRootTable_returnsDependentTablesInFkOrder() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                "H", "F", "G", "A", "D");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportNodesFilterSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportNodesFilterSearchCallbackIT.SINGLE_INPUT;
            final String[][] allExpectedOutput =
                    ImportNodesFilterSearchCallbackIT.SINGLE_OUTPUT;
            for (int i = 0; i < allInput.length; i++)
            {
                final String[] input = allInput[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final String[] actualOutput = TablesDependencyHelper
                        .getDependentTables(_connection, input[0]);
                assertThat(actualOutput).as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                    "H", "F", "G", "A", "D");
            refreshConnection();
        }
    }

    @Test
    void testGetDependentTables_whenRootTableDoesNotExist_throwsSearchException()
            throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                "H", "F", "G", "A", "D");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportNodesFilterSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            TablesDependencyHelper.getDependentTables(_connection,
                    "XXXXXX_TABLE_NON_EXISTING");
            fail("Should not be able to get the dependent tables for a non existing input table");
        }
        catch (final SearchException expected)
        {
            final Throwable cause = expected.getCause();
            assertThat(cause).isInstanceOf(NoSuchTableException.class);

            final String expectedSchema =
                    getEnvironment().getProfile().getSchema();
            final String expectedMessage =
                    "The table 'XXXXXX_TABLE_NON_EXISTING' does not exist in schema '"
                            + expectedSchema + "'";
            assertThat(cause.getMessage())
                    .as("NoSuchTableException message.")
                    .isEqualToIgnoringCase(expectedMessage);
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                    "H", "F", "G", "A", "D");
            refreshConnection();
        }
    }

    @Test
    void testGetDependentTables_withMultipleRootTables_returnsDependentTablesInFkOrder() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                "H", "F", "G", "A", "D");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportNodesFilterSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportNodesFilterSearchCallbackIT.COMPOUND_INPUT;
            final String[][] allExpectedOutput =
                    ImportNodesFilterSearchCallbackIT.COMPOUND_OUTPUT;
            for (int i = 0; i < allInput.length; i++)
            {
                final String[] input = allInput[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final String[] actualOutput = TablesDependencyHelper
                        .getDependentTables(_connection, input);
                assertThat(actualOutput).as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                    "H", "F", "G", "A", "D");
            refreshConnection();
        }
    }

    @Test
    void testGetAllDependentTables_withSingleRootTable_returnsAllDependentTablesInOrder() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                "A", "B", "F");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportAndExportKeysSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportAndExportKeysSearchCallbackIT.SINGLE_INPUT;
            final String[][] allExpectedOutput =
                    ImportAndExportKeysSearchCallbackIT.SINGLE_OUTPUT;
            for (int i = 0; i < allInput.length; i++)
            {
                final String[] input = allInput[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final String[] actualOutput = TablesDependencyHelper
                        .getAllDependentTables(_connection, input[0]);
                assertThat(actualOutput).as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                    "A", "B", "F");
            refreshConnection();
        }
    }

    @Test
    void testGetAllDependentTables_withMultipleRootTables_returnsAllDependentTablesInOrder() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                "A", "B", "F");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportAndExportKeysSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportAndExportKeysSearchCallbackIT.COMPOUND_INPUT;
            final String[][] allExpectedOutput =
                    ImportAndExportKeysSearchCallbackIT.COMPOUND_OUTPUT;
            for (int i = 0; i < allInput.length; i++)
            {
                final String[] input = allInput[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final String[] actualOutput = TablesDependencyHelper
                        .getAllDependentTables(_connection, input);
                assertThat(actualOutput).as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                    "A", "B", "F");
            refreshConnection();
        }
    }

    @Test
    void testGetAllDataset_withSingleTableInput_returnsDataSetContainingDependentTables() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                "A", "B", "F");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportAndExportKeysSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportAndExportKeysSearchCallbackIT.SINGLE_INPUT;
            final String[][] allExpectedOutput =
                    ImportAndExportKeysSearchCallbackIT.SINGLE_OUTPUT;
            for (int i = 0; i < allInput.length; i++)
            {
                final String[] input = allInput[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final IDataSet actualOutput = TablesDependencyHelper
                        .getAllDataset(_connection, input[0], new HashSet<>());
                final String[] actualOutputTables =
                        actualOutput.getTableNames();
                assertThat(actualOutputTables)
                        .as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "C", "D", "E",
                    "A", "B", "F");
            refreshConnection();
        }
    }

    @Test
    void testGetAllDataset_withTableInSeparateSchema_returnsDataSetContainingDependentTables() throws Exception
    {
        // This test requires HSQLDB-specific SET SCHEMA DDL; kept on HSQLDB intentionally.
        final Connection hsqlConn =
                HypersonicEnvironment.createJdbcConnection("mem:schema_test");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_switch_schema.sql"),
                hsqlConn, false);
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportAndExportKeysSearchCallbackIT.SQL_FILE),
                hsqlConn, false);
        final org.dbunit.database.IDatabaseConnection hsqlDbConn =
                new DatabaseConnection(hsqlConn);
        try
        {
            final String[][] allInputWithSchema =
                    ImportAndExportKeysSearchCallbackIT
                            .getSingleInputWithSchema("TEST_SCHEMA");
            final String[][] allExpectedOutput =
                    ImportAndExportKeysSearchCallbackIT.SINGLE_OUTPUT;
            for (int i = 0; i < allInputWithSchema.length; i++)
            {
                final String[] input = allInputWithSchema[i];
                final String[] expectedOutput = allExpectedOutput[i];
                final IDataSet actualOutput = TablesDependencyHelper
                        .getAllDataset(hsqlDbConn, input[0], new HashSet<>());
                final String[] actualOutputTables =
                        actualOutput.getTableNames();
                assertThat(actualOutputTables)
                        .as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(hsqlConn, "C", "D", "E", "A", "B", "F");
            HypersonicEnvironment.shutdown(hsqlConn);
        }
    }

    /**
     * Ensure the order is not lost on the way because of the conversion between
     * Map and Array.
     *
     * @throws Exception
     */
    @Test
    void testGetAllDataset_withMultipleTableInputs_returnsDataSetPreservingDependencyOrder() throws Exception
    {
        DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                "H", "F", "G", "A", "D");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile(
                        "sql/" + ImportNodesFilterSearchCallbackIT.SQL_FILE),
                _connection.getConnection(), false);
        try
        {
            final String[][] allInput =
                    ImportNodesFilterSearchCallbackIT.COMPOUND_INPUT;
            final String[][] allExpectedOutput =
                    ImportNodesFilterSearchCallbackIT.COMPOUND_OUTPUT;
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
                        .getDataset(_connection, inputMap);
                final String[] actualOutputArray =
                        actualOutput.getTableNames();
                assertThat(actualOutputArray)
                        .as("output didn't match for i=" + i + ".")
                        .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                        .containsExactly(expectedOutput);
            }
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                    "H", "F", "G", "A", "D");
            refreshConnection();
        }
    }

}
