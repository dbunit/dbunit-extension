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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Real-database integration test of an annotated test that is handed a connection with
 * autocommit disabled (issue #965). {@code DefaultPrepAndExpectedTestCase}'s
 * setup/teardown operations do not manage a transaction, so dbUnit does not commit their writes
 * and, as it did not begin that transaction, does not end it either: the connection's owner does.
 * This proves it, end to end through {@link DbUnitExtension}: the warning fires, the
 * check-driven run finishes clean, and nothing the test wrote survives the owner's rollback. When
 * the test case closes the connection, the database decides what happens to the open
 * transaction: Derby and DB2 refuse the close, and dbUnit fails the test with the reason.
 *
 * <p>{@code DefaultPrepAndExpectedTestCaseTest} covers the warning itself with a mocked
 * connection; only a real database shows the consequences for the transaction.
 *
 * <p>Matrix modifier: autocommit-off across the prep/expected rows (G-c4).
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionAutoCommitOffIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_prepExpectedRowCountCheckOnANonAutocommitConnection_warnsRunsCleanAndPersistsNothing()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final DatabaseProfile profile = environment.getProfile();
        deleteAllRowsQuietly(environment);

        final Connection nonAutocommit = DriverManager.getConnection(
                profile.getConnectionUrl(), profile.getUser(), profile.getPassword());
        nonAutocommit.setAutoCommit(false);
        AutoCommitOffSample.databaseTester = new DefaultDatabaseTester(
                new DatabaseConnection(nonAutocommit, profile.getSchema()));
        AutoCommitOffSample.databaseTester
                .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

        final Logger warningLogger = (Logger) LoggerFactory
                .getLogger("org.dbunit.database.connection.AutoCommitOffWarning");
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        warningLogger.addAppender(appender);
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(AutoCommitOffSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("autocommit disabled"))
                    .as("A non-autocommit connection in a test with @DbUnitExpected must be"
                            + " warned about (#965).")
                    .hasSize(1);

            nonAutocommit.rollback();
            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("With autocommit disabled the setup/teardown operations never commit"
                            + " (#965), so once the connection's owner ends the transaction a"
                            + " separate connection must see TEST_TABLE unchanged from before the"
                            + " run - the @DbUnitPrep seed and the test's own UPDATE both gone.")
                    .isZero();
        } finally
        {
            warningLogger.detachAppender(appender);
            appender.stop();
            closeQuietly(nonAutocommit);
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_prepWithoutExpectedOnANonAutocommitConnection_warnsAndPersistsNothing()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final DatabaseProfile profile = environment.getProfile();
        deleteAllRowsQuietly(environment);

        final Connection nonAutocommit = DriverManager.getConnection(
                profile.getConnectionUrl(), profile.getUser(), profile.getPassword());
        nonAutocommit.setAutoCommit(false);
        AutoCommitOffNoExpectedSample.databaseTester = new DefaultDatabaseTester(
                new DatabaseConnection(nonAutocommit, profile.getSchema()));

        final Logger warningLogger = (Logger) LoggerFactory
                .getLogger("org.dbunit.database.connection.AutoCommitOffWarning");
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        warningLogger.addAppender(appender);
        try
        {
            final boolean closeIsRefused = closeWithOpenTransactionIsRefused(profile);
            final Events testEvents = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(AutoCommitOffNoExpectedSample.class)).execute()
                    .testEvents();
            if (closeIsRefused)
            {
                testEvents.assertStatistics(stats -> stats.started(1).failed(1));
                final Throwable failure = testEvents.failed().stream().findFirst()
                        .flatMap(event -> event.getRequiredPayload(TestExecutionResult.class)
                                .getThrowable())
                        .orElseThrow(() -> new AssertionError("The failed test has no exception."));
                assertThat(failure)
                        .as("A database that refuses to close a connection with an open"
                                + " transaction fails the test with the reason, as dbUnit does"
                                + " not end a transaction it did not begin.")
                        .hasMessageContaining("does not end a transaction it did not begin");
            } else
            {
                testEvents.assertStatistics(stats -> stats.started(1).succeeded(1));
            }

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("autocommit disabled"))
                    .as("A test without @DbUnitExpected must warn about a non-autocommit"
                            + " connection too - its @DbUnitPrep runs with no transaction,"
                            + " exactly the #965 shape a test with @DbUnitExpected already"
                            + " warns about.")
                    .hasSize(1);

            if (closeIsRefused)
            {
                return;
            }

            // The test case the runtime builds closes the tester's connection when the test
            // ends (its closeConnectionAfterTest is true and the tester keeps its default
            // listener), and the database discards the open transaction on close - Oracle
            // commits it, but the teardown DELETE_ALL ran in it too - so a separate connection
            // sees no rows.
            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The @DbUnitPrep CLEAN_INSERT and the teardown ran on the open"
                            + " transaction, so a separate connection sees no rows.")
                    .isZero();
        } finally
        {
            warningLogger.detachAppender(appender);
            appender.stop();
            closeQuietly(nonAutocommit);
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    private static boolean closeWithOpenTransactionIsRefused(final DatabaseProfile profile)
    {
        final String activeProfile = profile.getActiveProfile();
        return activeProfile.startsWith("derby") || activeProfile.startsWith("db2");
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

    private static void closeQuietly(final Connection connection)
    {
        try
        {
            if (!connection.isClosed())
            {
                connection.rollback();
                connection.close();
            }
        } catch (final Exception e)
        {
            // best-effort
        }
    }

    private static void deleteAllRowsQuietly(final DatabaseEnvironment environment)
    {
        try (Statement statement =
                environment.getConnection().getConnection().createStatement())
        {
            statement.execute("DELETE FROM " + TEST_TABLE);
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class AutoCommitOffSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow(final IDatabaseConnection connection) throws Exception
        {
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    static class AutoCommitOffNoExpectedSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void seedsThePrepDataset()
        {
            // The @DbUnitPrep CLEAN_INSERT is the point of interest; the body does nothing.
        }
    }
}
