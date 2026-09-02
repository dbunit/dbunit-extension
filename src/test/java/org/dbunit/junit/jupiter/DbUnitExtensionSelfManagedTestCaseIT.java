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
import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCaseSteps;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Real-database integration test of the composition-style {@link PrepAndExpectedTestCase}:
 * a hand-written implementation (not a {@link DefaultPrepAndExpectedTestCase} subclass) that
 * does <em>not</em> override {@code getDatabaseTester()}/{@code setDatabaseTester()} and manages
 * its own tester and connection internally, injected through a {@code @DbUnitTestCase} field.
 * {@code annotations.adoc} documents this pattern as supported (with
 * {@code databaseTesterFactory} configured so the extension's own machinery still has a tester);
 * only mock-based unit tests exercise the resolution fallback, so a real database confirms the
 * full {@code configureTest} / {@code preTest} / {@code @DbUnitExpected} verify / {@code cleanupData}
 * lifecycle actually runs through such an instance, and that the round-trip-fallback stays a
 * diagnostic log rather than an error.
 *
 * <p>Matrix row 10 (G-c2).
 */
class DbUnitExtensionSelfManagedTestCaseIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_selfManagedConnectionTestCaseWithoutTesterOverride_runsTheFullLifecycle()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final DatabaseProfile profile = environment.getProfile();
        deleteAllRowsQuietly(environment);
        SelfManagedSample.testCase = new SelfManagedTestCase(profile);

        final Logger extensionLogger =
                (Logger) LoggerFactory.getLogger("org.dbunit.junit.jupiter.DbUnitExtension");
        final Level originalLevel = extensionLogger.getLevel();
        extensionLogger.setLevel(Level.DEBUG);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        extensionLogger.addAppender(appender);
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(SelfManagedSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(appender.list)
                    .as("A @DbUnitTestCase whose type does not override"
                            + " getDatabaseTester()/setDatabaseTester() is the documented"
                            + " self-managed-connection pattern - the resolution round-trip"
                            + " failure must be a diagnostic log, never an exception.")
                    .anyMatch(event -> event.getFormattedMessage().contains("does not round-trip"));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The composition test case's own cleanupData() (DELETE_ALL on its"
                            + " internal tester) must have run and committed - the @DbUnitExpected"
                            + " verify passing already proves configureTest/preTest/verifyData"
                            + " ran through it end to end.")
                    .isZero();
        } finally
        {
            extensionLogger.detachAppender(appender);
            appender.stop();
            extensionLogger.setLevel(originalLevel);
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
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
    @DbUnitConfig(databaseTesterFactory = SelfManagedSample.NoOpTesterFactory.class)
    static class SelfManagedSample
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        void mutateTheSeededRow(final Connection connection) throws Exception
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }

        /**
         * Supplies the tester the extension's own machinery needs (it never reaches the
         * composition test case's internal one); a NO_OP listener so nothing it does closes a
         * connection out from under that internal lifecycle.
         */
        static class NoOpTesterFactory implements DatabaseTesterFactory
        {
            @Override
            public IDatabaseTester createDatabaseTester() throws Exception
            {
                final IDatabaseTester tester = new DefaultDatabaseTester(
                        DatabaseEnvironment.getInstance().getConnection());
                tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
                return tester;
            }
        }
    }

    /**
     * A composition {@link PrepAndExpectedTestCase}: every abstract method delegates to an
     * internal {@link DefaultPrepAndExpectedTestCase} built over its own {@link JdbcDatabaseTester},
     * with its own {@code DELETE_ALL} teardown operation. It deliberately does not override
     * {@code getDatabaseTester()}/{@code setDatabaseTester()} - the resolution fallback the
     * enclosing test asserts about. It does override {@code getReusableConnection()} so an
     * injected {@code Connection} parameter still reaches the connection its own lifecycle uses.
     */
    static final class SelfManagedTestCase implements PrepAndExpectedTestCase
    {
        private final DefaultPrepAndExpectedTestCase delegate;

        SelfManagedTestCase(final DatabaseProfile profile) throws Exception
        {
            final JdbcDatabaseTester tester = new JdbcDatabaseTester(profile.getDriverClass(),
                    profile.getConnectionUrl(), profile.getUser(), profile.getPassword(),
                    profile.getSchema());
            tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
            delegate = new DefaultPrepAndExpectedTestCase(
                    new org.dbunit.util.fileloader.FlatXmlDataFileLoader(), tester);
        }

        @Override
        public IDatabaseConnection getReusableConnection() throws Exception
        {
            return delegate.getReusableConnection();
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles) throws Exception
        {
            delegate.configureTest(verifyTableDefinitions, prepDataFiles, expectedDataFiles);
        }

        @Override
        public void preTest() throws Exception
        {
            delegate.preTest();
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles) throws Exception
        {
            delegate.preTest(verifyTables, prepDataFiles, expectedDataFiles);
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final PrepAndExpectedTestCaseSteps testSteps) throws Exception
        {
            return delegate.runTest(verifyTables, prepDataFiles, expectedDataFiles, testSteps);
        }

        @Override
        public void postTest() throws Exception
        {
            delegate.postTest();
        }

        @Override
        public void postTest(final boolean verifyData) throws Exception
        {
            delegate.postTest(verifyData);
        }

        @Override
        public void verifyData() throws Exception
        {
            delegate.verifyData();
        }

        @Override
        public void cleanupData() throws Exception
        {
            delegate.cleanupData();
        }

        @Override
        public IDataSet getPrepDataset()
        {
            return delegate.getPrepDataset();
        }

        @Override
        public IDataSet getExpectedDataset()
        {
            return delegate.getExpectedDataset();
        }
    }
}
