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
package org.dbunit.junit.jupiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Real-database integration test of {@link DbUnitRowCountCheck}: a leaked row in a table the
 * test never lists, and the exclude list silencing a legitimate case of that. Both fixture
 * classes explicitly disable {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} on the shared
 * connection first, so a passing run proves the annotation - not the feature - is what
 * enables the check.
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionRowCountCheckIT
{
    private static final String EMPTY_TABLE = "EMPTY_TABLE";

    @Test
    void testAfterTestExecution_rowLeakedIntoUnlistedTable_failsNamingIt() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = connectionWithFeatureDisabled(environment);
            LeakedRowSample.databaseTester = new DefaultDatabaseTester(connection);
            LeakedRowSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(LeakedRowSample.class)).execute().testEvents()
                    .failed().stream().findFirst().orElseThrow(
                            () -> new AssertionError("Expected one failed test event."));

            final Throwable reported = failedEvent
                    .getRequiredPayload(TestExecutionResult.class).getThrowable()
                    .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
            assertThat(reported)
                    .as("A row left behind in a table absent from teardown must fail the"
                            + " test with UnexpectedRowCountException.")
                    .isInstanceOf(UnexpectedRowCountException.class);
            assertThat(((UnexpectedRowCountException) reported).getDifferences())
                    .as("The failure must name the table the row leaked into; table name"
                            + " casing is database-dependent (PostgreSQL folds it to"
                            + " lowercase), so match case-insensitively.")
                    .anyMatch(difference -> difference.getTableName()
                            .equalsIgnoreCase(EMPTY_TABLE));
        } finally
        {
            deleteAllRowsQuietly(environment, EMPTY_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_excludedTableLeaksRows_passes() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = connectionWithFeatureDisabled(environment);
            ExcludedTableSample.databaseTester = new DefaultDatabaseTester(connection);
            ExcludedTableSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExcludedTableSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            deleteAllRowsQuietly(environment, EMPTY_TABLE);
            environment.closeConnection();
        }
    }

    private static IDatabaseConnection connectionWithFeatureDisabled(
            final DatabaseEnvironment environment) throws Exception
    {
        final IDatabaseConnection connection = environment.getConnection();
        connection.getConfig().setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, false);
        return connection;
    }

    private static void deleteAllRowsQuietly(final DatabaseEnvironment environment,
            final String tableName)
    {
        try (Statement statement =
                environment.getConnection().getConnection().createStatement())
        {
            statement.execute("DELETE FROM " + tableName);
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitRowCountCheck
    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class LeakedRowSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void testInsertRow_intoUnlistedTable_leavesRowForRowCountCheckToDetect() throws Exception
        {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement())
            {
                statement.execute(
                        "INSERT INTO " + EMPTY_TABLE + " (COLUMN0) VALUES ('leaked')");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitRowCountCheck(exclude = EMPTY_TABLE)
    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class ExcludedTableSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void testInsertRow_intoExcludedTable_rowCountCheckIgnoresIt() throws Exception
        {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement())
            {
                statement.execute(
                        "INSERT INTO " + EMPTY_TABLE + " (COLUMN0) VALUES ('leaked')");
            }
        }
    }
}
