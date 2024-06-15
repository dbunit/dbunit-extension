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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.util.Arrays;

import org.dbunit.DdlExecutor;
import org.dbunit.H2Environment;
import org.dbunit.HypersonicEnvironment;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since May 8, 2004
 * @version $Revision$
 */
class DatabaseSequenceFilterTest
{
    Connection _jdbcConnection;

    @BeforeEach
    protected void setUpConnection() throws Exception
    {

        _jdbcConnection = HypersonicEnvironment.createJdbcConnection("tempdb");
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        HypersonicEnvironment.shutdown(_jdbcConnection);
        _jdbcConnection.close();

        HypersonicEnvironment.deleteFiles("tempdb");
    }

    @Test
    void testGetTableNames() throws Exception
    {
        final String[] expectedNoFilter =
                {"A", "B", "C", "D", "E", "F", "G", "H",};
        final String[] expectedFiltered =
                {"D", "A", "F", "C", "G", "E", "H", "B",};

        DdlExecutor.executeDdlFile(TestUtils.getFile("sql/hypersonic_fk.sql"),
                _jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(_jdbcConnection);

        final IDataSet databaseDataset = connection.createDataSet();
        final String[] actualNoFilter = databaseDataset.getTableNames();
        assertThat(Arrays.asList(actualNoFilter)).as("no filter")
                .isEqualTo(Arrays.asList(expectedNoFilter));

        final ITableFilter filter = new DatabaseSequenceFilter(connection);
        final IDataSet filteredDataSet =
                new FilteredDataSet(filter, databaseDataset);
        final String[] actualFiltered = filteredDataSet.getTableNames();
        assertThat(Arrays.asList(actualFiltered)).as("filtered")
                .isEqualTo(Arrays.asList(expectedFiltered));
    }

    @Test
    void testGetTableNamesCyclic() throws Exception
    {
        final String[] expectedNoFilter = {"A", "B", "C", "D", "E",};

        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_cyclic.sql"),
                _jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(_jdbcConnection);

        final IDataSet databaseDataset = connection.createDataSet();
        final String[] actualNoFilter = databaseDataset.getTableNames();
        assertThat(Arrays.asList(actualNoFilter)).as("no filter")
                .isEqualTo(Arrays.asList(expectedNoFilter));

        assertThrows(CyclicTablesDependencyException.class, () -> {
            final ITableFilter filter = new DatabaseSequenceFilter(connection);
            final IDataSet filteredDataSet =
                    new FilteredDataSet(filter, databaseDataset);
            filteredDataSet.getTableNames();
            fail("Should not be here!");
        }, "Expected CyclicTablesDependencyException was not raised");

    }

    @Test
    void testCaseSensitiveTableNames() throws Exception
    {
        final String[] expectedNoFilter =
                {"MixedCaseTable", "UPPER_CASE_TABLE"};
        final String[] expectedFiltered =
                {"MixedCaseTable", "UPPER_CASE_TABLE"};

        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_case_sensitive_test.sql"),
                _jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(_jdbcConnection);

        connection.getConfig().setProperty(
                DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES,
                Boolean.TRUE);

        final IDataSet databaseDataset = connection.createDataSet();
        final String[] actualNoFilter = databaseDataset.getTableNames();
        assertThat(Arrays.asList(actualNoFilter)).as("no filter")
                .isEqualTo(Arrays.asList(expectedNoFilter));

        final ITableFilter filter = new DatabaseSequenceFilter(connection);
        final IDataSet filteredDataSet =
                new FilteredDataSet(filter, databaseDataset);
        final String[] actualFiltered = filteredDataSet.getTableNames();
        assertThat(Arrays.asList(actualFiltered)).as("filtered")
                .isEqualTo(Arrays.asList(expectedFiltered));
    }

    /**
     * Note that this test uses the H2 database because we could not find out
     * how to create 2 separate schemas in the hsqldb in memory DB.
     *
     * @throws Exception
     */
    @Test
    void testMultiSchemaFks() throws Exception
    {
        final Connection jdbcConnection =
                H2Environment.createJdbcConnection("test");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/h2_multischema_fk_test.sql"),
                jdbcConnection);
        final IDatabaseConnection connection =
                new DatabaseConnection(jdbcConnection);
        connection.getConfig().setProperty(
                DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, Boolean.TRUE);

        final IDataSet databaseDataset = connection.createDataSet();
        final ITableFilter filter = new DatabaseSequenceFilter(connection);
        final IDataSet filteredDataSet =
                new FilteredDataSet(filter, databaseDataset);

        final String[] actualNoFilter = databaseDataset.getTableNames();
        assertThat(actualNoFilter).hasSize(2);
        assertThat(actualNoFilter[0]).isEqualTo("A.FOO");
        assertThat(actualNoFilter[1]).isEqualTo("B.BAR");

        final String[] actualFiltered = filteredDataSet.getTableNames();
        assertThat(actualFiltered).hasSize(2);
        assertThat(actualFiltered[0]).isEqualTo("A.FOO");
        assertThat(actualFiltered[1]).isEqualTo("B.BAR");
    }
}
