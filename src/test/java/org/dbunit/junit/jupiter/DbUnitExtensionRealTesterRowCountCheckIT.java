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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
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
 * Real-database integration test of {@code @DbUnitRowCountCheck} driven through a genuine
 * {@link JdbcDatabaseTester} - a fresh physical connection per {@code getConnection()} call, its
 * own {@code DefaultOperationListener}, no {@code CachingConnectionProvider} and no fixed
 * connection. The other row-count-check ITs use a {@code DefaultDatabaseTester} built from one
 * connection with a {@code NO_OP} listener, so the baseline/verify connection identity and the
 * piggyback on {@code onSetup()}'s connection are never exercised against a real fresh-connection
 * tester end to end.
 *
 * <p>Matrix rows 3 & 9 with a real tester; partial G-c1.
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionRealTesterRowCountCheckIT
{
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final String EMPTY_TABLE = "EMPTY_TABLE";

    @Test
    void testAfterTestExecution_setupTeardownPathWithRowCountCheck_piggybacksBaselineAndVerifiesClean()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        runSampleExpectingSuccess(environment, SetupTeardownPathSample.class);

        final IDatabaseConnection verifyConnection = environment.getConnection();
        assertThat(rowCount(verifyConnection, TEST_TABLE))
                .as("The setup/teardown path with @DbUnitRowCountCheck, driven through a real"
                        + " JdbcDatabaseTester: baseline piggybacks on onSetup()'s connection,"
                        + " verify re-uses that memoized connection, and"
                        + " @DbUnitTearDown(DELETE_ALL) commits - a separate connection sees"
                        + " TEST_TABLE empty.")
                .isZero();
    }

    @Test
    void testAfterTestExecution_prepExpectedPathWithRowCountCheck_verifiesDataAndRowCountsClean()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        runSampleExpectingSuccess(environment, PrepExpectedPathSample.class);

        final IDatabaseConnection verifyConnection = environment.getConnection();
        assertThat(rowCount(verifyConnection, TEST_TABLE))
                .as("The prep/expected path with @DbUnitRowCountCheck, driven through a real"
                        + " JdbcDatabaseTester: DefaultPrepAndExpectedTestCase captures the"
                        + " baseline and verifies data + row counts on its reusable connection,"
                        + " and @DbUnitTearDown(DELETE_ALL) commits.")
                .isZero();
    }

    @Test
    void testAfterTestExecution_rowLeakedThroughARealTester_failsProvingTheCheckActuallyRan()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            LeakThroughRealTesterSample.databaseTester = newJdbcDatabaseTester(environment);

            final Event failed = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(LeakThroughRealTesterSample.class)).execute()
                    .testEvents().failed().stream().findFirst().orElseThrow(
                            () -> new AssertionError("Expected one failed test event."));
            final Throwable reported = failed.getRequiredPayload(TestExecutionResult.class)
                    .getThrowable().orElseThrow(
                            () -> new AssertionError("Expected a reported throwable."));

            assertThat(reported)
                    .as("A row left in an unlisted table must fail with"
                            + " UnexpectedRowCountException even when the tester is a real"
                            + " fresh-connection JdbcDatabaseTester - proving the check is"
                            + " genuinely running, not silently no-op because a fresh"
                            + " connection defeated the baseline.")
                    .isInstanceOf(UnexpectedRowCountException.class);
        } finally
        {
            deleteAllRowsQuietly(environment, EMPTY_TABLE);
            environment.closeConnection();
        }
    }

    private void runSampleExpectingSuccess(final DatabaseEnvironment environment,
            final Class<?> sampleClass) throws Exception
    {
        try
        {
            setSampleTester(sampleClass, newJdbcDatabaseTester(environment));

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(sampleClass)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            deleteAllRowsQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    private void setSampleTester(final Class<?> sampleClass, final IDatabaseTester tester)
    {
        if (sampleClass == SetupTeardownPathSample.class)
        {
            SetupTeardownPathSample.databaseTester = tester;
        } else
        {
            PrepExpectedPathSample.databaseTester = tester;
        }
    }

    private static IDatabaseTester newJdbcDatabaseTester(final DatabaseEnvironment environment)
            throws Exception
    {
        final DatabaseProfile profile = environment.getProfile();
        return new JdbcDatabaseTester(profile.getDriverClass(), profile.getConnectionUrl(),
                profile.getUser(), profile.getPassword(), profile.getSchema());
    }

    private static int rowCount(final IDatabaseConnection connection, final String tableName)
            throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + tableName))
        {
            resultSet.next();
            return resultSet.getInt(1);
        }
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
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class SetupTeardownPathSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow(final Connection connection) throws Exception
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class PrepExpectedPathSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow(final Connection connection) throws Exception
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class LeakThroughRealTesterSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void leaveARowInAnUnlistedTable(final Connection connection) throws Exception
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("INSERT INTO " + EMPTY_TABLE + " (COLUMN0) VALUES ('leaked')");
            }
        }
    }
}
