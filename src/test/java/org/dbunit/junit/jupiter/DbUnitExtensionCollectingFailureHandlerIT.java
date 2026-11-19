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
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Real-database integration test of {@code @DbUnitConfig(failureHandler =
 * DiffCollectingFailureHandler.class)} through {@link DbUnitExtension}. That handler does not
 * throw on a value that differs, and the annotation-driven verification runs after the test
 * method, where nobody else reads what it collected, so the lifecycle has to fail the test with
 * the collected differences.
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionCollectingFailureHandlerIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    private static final String EMPTY_TABLE = "EMPTY_TABLE";

    @Test
    void testAfterTestExecution_collectingHandlerAndAValueThatDiffers_failsTheTest()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            DifferingValueSample.databaseTester = new DefaultDatabaseTester(connection);
            DifferingValueSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(DifferingValueSample.class)).execute().testEvents()
                    .assertThatEvents()
                    .haveExactly(1, finishedWithFailure(instanceOf(DbComparisonFailure.class),
                            message(text -> text.contains("table=TEST_TABLE")
                                    && text.contains("col=COLUMN1")
                                    && text.contains("expected:<after> but was:<before>"))));
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_collectingHandlerAndMatchingValues_passesTheTest() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            MatchingValueSample.databaseTester = new DefaultDatabaseTester(connection);
            MatchingValueSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(MatchingValueSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_collectingHandlerValueDiffersAndRowLeftBehind_failsWithTheCollectedDifferences()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            DifferingValueAndLeakedRowSample.databaseTester =
                    new DefaultDatabaseTester(connection);
            DifferingValueAndLeakedRowSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(DifferingValueAndLeakedRowSample.class)).execute()
                    .testEvents().failed().stream().findFirst().orElseThrow(
                            () -> new AssertionError("Expected one failed test event."));

            final Throwable reported = failedEvent.getRequiredPayload(TestExecutionResult.class)
                    .getThrowable()
                    .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
            assertThat(reported)
                    .as("The value difference that the handler collected is the failure, not the"
                            + " row count difference of the leaked row.")
                    .isInstanceOf(DbComparisonFailure.class).hasMessageContaining("table=TEST_TABLE")
                    .hasMessageContaining("col=COLUMN1");
            assertThat(reported.getSuppressed())
                    .as("The row count difference rides along as a suppressed exception.")
                    .hasSize(1);
            final Throwable rowCountFailure = reported.getSuppressed()[0];
            assertThat(rowCountFailure).isInstanceOf(UnexpectedRowCountException.class);
            assertThat(((UnexpectedRowCountException) rowCountFailure).getDifferences())
                    .as("The row count failure names the table the row leaked into; table name"
                            + " casing is database-dependent, so match case-insensitively.")
                    .anyMatch(difference -> difference.getTableName()
                            .equalsIgnoreCase(EMPTY_TABLE));
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    private static void deleteAllRowsQuietly(final DatabaseEnvironment environment)
    {
        deleteAllRowsQuietly(environment, TEST_TABLE);
        deleteAllRowsQuietly(environment, EMPTY_TABLE);
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

    @DbUnitTest
    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    static class DifferingValueSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void leaveTheSeededRowAsItIs()
        {
            // The expected file holds 'after'; the seeded row still holds 'before'.
        }
    }

    @DbUnitTest
    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    @DbUnitRowCountCheck
    static class DifferingValueAndLeakedRowSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void leaveTheSeededRowAsItIsAndLeakARow() throws Exception
        {
            // The expected file holds 'after'; the seeded row still holds 'before'.
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement())
            {
                statement.execute(
                        "INSERT INTO " + EMPTY_TABLE + " (COLUMN0) VALUES ('leaked')");
            }
        }
    }

    @DbUnitTest
    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    static class MatchingValueSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow() throws Exception
        {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }
}
