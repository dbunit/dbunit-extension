/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.CachedResultSetTableFactory;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.IResultSetTable;
import org.dbunit.database.IResultSetTableFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Real-database integration test that {@link DefaultPrepAndExpectedTestCase} selects only the
 * columns it compares when {@link VerifyTableDefinition#isSortOnFilteredColumnsOnly()} is true,
 * so an excluded column, typically a large one, is not fetched, and that it keeps selecting every
 * column otherwise. A recording {@link IResultSetTableFactory} over the real one captures what the
 * verification asked the database for.
 *
 * @since 3.6.0
 */
class DefaultPrepAndExpectedTestCaseColumnProjectionIT
{
    private static final String ALL_TABLES_DATA_FILE_NAME = "/xml/flatXmlDataSetTest.xml";
    private static final String TEST_TABLE_DATA_FILE_NAME =
            "/xml/columnProjectionPrepAndExpected.xml";
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final String[] NO_COLUMNS = {};

    private final DataFileLoader dataFileLoader = new FlatXmlDataFileLoader();

    @Test
    void testPostTest_sortOnFilteredColumnsOnlyOnEveryTable_passesOnTheDatabaseColumns()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {sorted("TEST_TABLE"), sorted("SECOND_TABLE"),
                sorted("EMPTY_TABLE"), sorted("PK_TABLE"), sorted("ONLY_PK_TABLE"),
                sorted("EMPTY_MULTITYPE_TABLE")};

        runWithRecordingFactory(ALL_TABLES_DATA_FILE_NAME, tables, (testCase, recording) ->
                assertThatCode(testCase::postTest)
                        .as("Selecting the compared columns from the database's own column"
                                + " definitions must verify the same data as selecting every"
                                + " column, on every table shape.")
                        .doesNotThrowAnyException());
    }

    @Test
    void testPostTest_sortOnFilteredColumnsOnlyWithExcludedColumns_selectsOnlyTheComparedColumns()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {new VerifyTableDefinition(TEST_TABLE,
                new String[] {"COLUMN2", "COLUMN3"}, null, null, true)};

        runWithRecordingFactory(TEST_TABLE_DATA_FILE_NAME, tables, (testCase, recording) ->
        {
            testCase.postTest();

            assertThat(recording.getRequestedColumnNames())
                    .as("An excluded column plays no part in sorting or comparing, so it must not"
                            + " be selected.")
                    .containsExactly(Arrays.asList("COLUMN0", "COLUMN1"));
            assertThat(recording.getSelectStatements())
                    .as("No select * statement may be issued when the columns can be projected.")
                    .isEmpty();
        });
    }

    @Test
    void testPostTest_defaultSortWithExcludedColumns_selectsEveryColumn() throws Exception
    {
        final VerifyTableDefinition[] tables = {new VerifyTableDefinition(TEST_TABLE,
                new String[] {"COLUMN2", "COLUMN3"}, null, null, false)};

        runWithRecordingFactory(TEST_TABLE_DATA_FILE_NAME, tables, (testCase, recording) ->
        {
            testCase.postTest();

            assertThat(recording.getSelectStatements())
                    .as("Every database column is a sort key in the default mode, so every column"
                            + " must still be selected.")
                    .singleElement().asString().containsIgnoringCase("select *");
            assertThat(recording.getRequestedColumnNames())
                    .as("The default mode must not project the columns, so no column list may be"
                            + " requested.")
                    .isEmpty();
        });
    }

    @Test
    void testPostTest_sortOnFilteredColumnsOnlyAndEveryColumnExcluded_fallsBackToSelectingEveryColumn()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {new VerifyTableDefinition(TEST_TABLE,
                new String[] {"COLUMN0", "COLUMN1", "COLUMN2", "COLUMN3"}, null, null, true)};

        runWithRecordingFactory(TEST_TABLE_DATA_FILE_NAME, tables, (testCase, recording) ->
        {
            assertThatCode(testCase::postTest)
                    .as("Nothing is compared, so the verification must pass.")
                    .doesNotThrowAnyException();

            assertThat(recording.getSelectStatements())
                    .as("A select list with no columns is invalid, so the whole table must be"
                            + " selected instead.")
                    .singleElement().asString().containsIgnoringCase("select *");
        });
    }

    @Test
    void testPostTest_sortOnFilteredColumnsOnlyAndLoadTableDataFromDatabaseOverridden_callsTheOverride()
            throws Exception
    {
        final VerifyTableDefinition[] tables = {new VerifyTableDefinition(TEST_TABLE,
                new String[] {"COLUMN2", "COLUMN3"}, null, null, true)};
        final AtomicInteger overrideCalls = new AtomicInteger();
        final IDatabaseTester tester = makeDatabaseTester();
        final DefaultPrepAndExpectedTestCase testCase =
                new DefaultPrepAndExpectedTestCase(dataFileLoader, tester)
                {
                    @Override
                    public ITable loadTableDataFromDatabase(final String tableName,
                            final IDatabaseConnection connection) throws Exception
                    {
                        overrideCalls.incrementAndGet();
                        return super.loadTableDataFromDatabase(tableName, connection);
                    }
                };
        testCase.configureTest(tables, new String[] {TEST_TABLE_DATA_FILE_NAME},
                new String[] {TEST_TABLE_DATA_FILE_NAME});
        testCase.preTest();

        try
        {
            testCase.postTest();
        } finally
        {
            DatabaseEnvironment.getInstance().closeConnection();
        }

        assertThat(overrideCalls)
                .as("A subclass that overrides how the table is loaded, for example to add a"
                        + " where clause, must keep deciding that, so the columns are not"
                        + " projected behind its back.")
                .hasValue(1);
    }

    private interface Verification
    {
        void run(DefaultPrepAndExpectedTestCase testCase, RecordingResultSetTableFactory recording)
                throws Exception;
    }

    private void runWithRecordingFactory(final String dataFileName,
            final VerifyTableDefinition[] tables, final Verification verification)
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            final RecordingResultSetTableFactory recording = new RecordingResultSetTableFactory();
            connection.getConfig().setProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY,
                    recording);
            final IDatabaseTester tester = new DefaultDatabaseTester(connection);
            tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
            final DefaultPrepAndExpectedTestCase testCase =
                    new DefaultPrepAndExpectedTestCase(dataFileLoader, tester);
            testCase.configureTest(tables, new String[] {dataFileName},
                    new String[] {dataFileName});
            testCase.preTest();

            verification.run(testCase, recording);
        } finally
        {
            environment.closeConnection();
        }
    }

    private static VerifyTableDefinition sorted(final String tableName)
    {
        return new VerifyTableDefinition(tableName, NO_COLUMNS, null, null, true);
    }

    private IDatabaseTester makeDatabaseTester() throws Exception
    {
        final IDatabaseConnection connection = DatabaseEnvironment.getInstance().getConnection();
        final IDatabaseTester tester = new DefaultDatabaseTester(connection);
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        return tester;
    }

    private static final class RecordingResultSetTableFactory implements IResultSetTableFactory
    {
        private final IResultSetTableFactory delegate = new CachedResultSetTableFactory();
        private final List<String> selectStatements = new ArrayList<>();
        private final List<List<String>> requestedColumnNames = new ArrayList<>();

        @Override
        public IResultSetTable createTable(final String tableName, final String selectStatement,
                final IDatabaseConnection connection) throws SQLException, DataSetException
        {
            selectStatements.add(selectStatement);
            return delegate.createTable(tableName, selectStatement, connection);
        }

        @Override
        public IResultSetTable createTable(final ITableMetaData metaData,
                final IDatabaseConnection connection) throws SQLException, DataSetException
        {
            final List<String> columnNames = new ArrayList<>();
            for (final Column column : metaData.getColumns())
            {
                columnNames.add(column.getColumnName().toUpperCase(Locale.ENGLISH));
            }
            requestedColumnNames.add(columnNames);
            return delegate.createTable(metaData, connection);
        }

        @Override
        public IResultSetTable createTable(final String tableName,
                final PreparedStatement preparedStatement, final IDatabaseConnection connection)
                throws SQLException, DataSetException
        {
            return delegate.createTable(tableName, preparedStatement, connection);
        }

        private List<String> getSelectStatements()
        {
            return selectStatements;
        }

        private List<List<String>> getRequestedColumnNames()
        {
            return requestedColumnNames;
        }
    }
}
