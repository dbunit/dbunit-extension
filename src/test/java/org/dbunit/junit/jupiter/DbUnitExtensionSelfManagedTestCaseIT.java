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
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCaseSteps;
import org.dbunit.VerifyTableDefinition;
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

/**
 * Real-database integration test of the composition-style {@link PrepAndExpectedTestCase}:
 * a hand-written implementation (not a {@link DefaultPrepAndExpectedTestCase} subclass) that
 * manages its own tester and connection internally, injected through a {@code @DbUnitTestCase}
 * field. dbUnit drives such an instance as it is: it gives it the test's files and calls
 * {@code configureTest()}, {@code preTest()} and {@code postTest()}. A real database confirms the
 * full {@code configureTest} / {@code preTest} / {@code @DbUnitExpected} verify / {@code cleanupData}
 * lifecycle actually runs through such an instance, with no tester and no connection of its own
 * known to dbUnit.
 *
 * <p>Matrix row 10 (G-c2).
 */
class DbUnitExtensionSelfManagedTestCaseIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_selfManagedConnectionTestCase_runsTheFullLifecycle()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final DatabaseProfile profile = environment.getProfile();
        deleteAllRowsQuietly(environment);
        SelfManagedSample.testCase = new SelfManagedTestCase(profile);

        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(SelfManagedSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The composition test case's own cleanupData() (DELETE_ALL on its"
                            + " internal tester) must have run and committed - the @DbUnitExpected"
                            + " verify passing already proves configureTest/preTest/verifyData"
                            + " ran through it end to end.")
                    .isZero();
        } finally
        {
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
    static class SelfManagedSample
    {
        @DbUnitTestCase
        static SelfManagedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        void mutateTheSeededRow() throws Exception
        {
            final IDatabaseConnection connection = testCase.getReusableConnection();
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    /**
     * A composition {@link PrepAndExpectedTestCase}: every abstract method delegates to an
     * internal {@link DefaultPrepAndExpectedTestCase} built over its own {@link JdbcDatabaseTester},
     * with its own {@code DELETE_ALL} teardown operation. It tells dbUnit of no tester and no
     * connection; a test method reaches the connection its own lifecycle uses through
     * {@code getReusableConnection()}.
     */
    static class SelfManagedTestCase implements PrepAndExpectedTestCase
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
