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

import java.util.Arrays;

import org.dbunit.AbstractDatabaseIT;
import org.junit.jupiter.api.Assumptions;
import org.dbunit.DdlExecutor;
import org.dbunit.H2Environment;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.dataset.filter.IncludeTableFilter;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

/**
 * @author Manuel Laflamme
 * @since May 8, 2004
 * @version $Revision$
 */
class DatabaseSequenceFilterIT extends AbstractDatabaseIT
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
    void testGetTableNames() throws Exception
    {
        final String[] testTableNames =
                {"A", "B", "C", "D", "E", "F", "G", "H"};
        final String[] expectedNoFilter =
                {"A", "B", "C", "D", "E", "F", "G", "H",};
        final String[] expectedFiltered =
                {"D", "A", "F", "C", "G", "E", "H", "B",};

        DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                "H", "F", "G", "A", "D");
        DdlExecutor.executeDdlFile(TestUtils.getFile("sql/hypersonic_fk.sql"),
                _connection.getConnection(), false);
        try
        {
            final IDataSet allTables = _connection.createDataSet();
            final IDataSet databaseDataset =
                    new FilteredDataSet(new IncludeTableFilter(testTableNames),
                            allTables);
            final String[] actualNoFilter = databaseDataset.getTableNames();
            assertThat(actualNoFilter).as("no filter")
                    .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                    .containsExactly(expectedNoFilter);

            final ITableFilter filter =
                    new DatabaseSequenceFilter(_connection);
            final IDataSet filteredDataSet =
                    new FilteredDataSet(filter, databaseDataset);
            final String[] actualFiltered = filteredDataSet.getTableNames();
            assertThat(actualFiltered).as("filtered")
                    .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                    .containsExactly(expectedFiltered);
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "B", "C", "E",
                    "H", "F", "G", "A", "D");
            refreshConnection();
        }
    }

    @Test
    void testGetTableNamesCyclic() throws Exception
    {
        final String[] testTableNames = {"A", "B", "C", "D", "E"};
        final String[] expectedNoFilter = {"A", "B", "C", "D", "E",};

        DdlExecutor.dropTables(_connection.getConnection(), "A", "B", "C",
                "D", "E");
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_cyclic.sql"),
                _connection.getConnection(), false);
        try
        {
            final IDataSet allTables = _connection.createDataSet();
            final IDataSet databaseDataset =
                    new FilteredDataSet(new IncludeTableFilter(testTableNames),
                            allTables);
            final String[] actualNoFilter = databaseDataset.getTableNames();
            assertThat(actualNoFilter).as("no filter")
                    .usingElementComparator(String.CASE_INSENSITIVE_ORDER)
                    .containsExactly(expectedNoFilter);

            assertThrows(CyclicTablesDependencyException.class, () -> {
                final ITableFilter filter =
                        new DatabaseSequenceFilter(_connection);
                final IDataSet filteredDataSet =
                        new FilteredDataSet(filter, databaseDataset);
                filteredDataSet.getTableNames();
                fail("Should not be here!");
            }, "Expected CyclicTablesDependencyException was not raised");
        }
        finally
        {
            DdlExecutor.dropTables(_connection.getConnection(), "A", "B", "C",
                    "D", "E");
            refreshConnection();
        }
    }

    @Test
    void testCaseSensitiveTableNames() throws Exception
    {
        final java.sql.DatabaseMetaData dbMeta =
                _connection.getConnection().getMetaData();
        Assumptions.assumeTrue(
                dbMeta.supportsMixedCaseQuotedIdentifiers(),
                "Skip: database does not treat quoted identifiers as case-sensitive.");
        Assumptions.assumeTrue(
                "\"".equals(dbMeta.getIdentifierQuoteString()),
                "Skip: database does not use ANSI double-quote identifier syntax.");
        Assumptions.assumeFalse(
                dbMeta.storesLowerCaseIdentifiers(),
                "Skip: database stores unquoted identifiers in lowercase.");
        final String[] testTableNames = {"MixedCaseTable", "UPPER_CASE_TABLE"};
        final String[] expectedNoFilter =
                {"MixedCaseTable", "UPPER_CASE_TABLE"};
        final String[] expectedFiltered =
                {"MixedCaseTable", "UPPER_CASE_TABLE"};

        DdlExecutor.dropTables(_connection.getConnection(), "UPPER_CASE_TABLE");
        try
        {
            DdlExecutor.executeSql(_connection.getConnection(),
                    "DROP TABLE \"MixedCaseTable\"");
        }
        catch (final Exception ignored)
        {
            logger.debug("Pre-drop of MixedCaseTable ignored: {}",
                    ignored.getMessage());
        }
        DdlExecutor.executeDdlFile(
                TestUtils.getFile("sql/hypersonic_case_sensitive_test.sql"),
                _connection.getConnection(), false);
        try
        {
            _connection.getConfig().setProperty(
                    DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES,
                    Boolean.TRUE);

            final IDataSet allTables = _connection.createDataSet();
            final IDataSet databaseDataset =
                    new FilteredDataSet(new IncludeTableFilter(testTableNames),
                            allTables);
            final String[] actualNoFilter = databaseDataset.getTableNames();
            assertThat(Arrays.asList(actualNoFilter)).as("no filter")
                    .isEqualTo(Arrays.asList(expectedNoFilter));

            final ITableFilter filter =
                    new DatabaseSequenceFilter(_connection);
            final IDataSet filteredDataSet =
                    new FilteredDataSet(filter, databaseDataset);
            final String[] actualFiltered = filteredDataSet.getTableNames();
            assertThat(Arrays.asList(actualFiltered)).as("filtered")
                    .isEqualTo(Arrays.asList(expectedFiltered));
        }
        finally
        {
            _connection.getConfig().setProperty(
                    DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES,
                    Boolean.FALSE);
            DdlExecutor.dropTables(_connection.getConnection(),
                    "UPPER_CASE_TABLE");
            try
            {
                DdlExecutor.executeSql(_connection.getConnection(),
                        "DROP TABLE \"MixedCaseTable\"");
            }
            catch (final Exception ignored)
            {
                logger.debug("Post-drop of MixedCaseTable ignored: {}",
                        ignored.getMessage());
            }
            refreshConnection();
        }
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
