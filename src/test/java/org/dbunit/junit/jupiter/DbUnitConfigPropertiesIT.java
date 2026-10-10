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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test proving an {@code @DbUnitProperty} value actually reaches
 * {@link IDatabaseConnection#getConfig()} on both the setup/teardown path (the {@code
 * IOperationListener} wiring described in the annotations plan's execution model) and the
 * prep/expected path ({@code DefaultPrepAndExpectedTestCase}'s own connection resolution,
 * which never goes through that listener) - neither is something
 * {@code AnnotatedTestExecutorTest}'s mocked connection can prove - and that a connection kept
 * open past the test carries the value into no later test.
 */
class DbUnitConfigPropertiesIT
{
    @Test
    void testBeforeTestExecution_dbUnitPropertyDeclared_reachesConnectionConfig()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            PropertySample.databaseTester = new DefaultDatabaseTester(connection);
            PropertySample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(PropertySample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(PropertySample.batchedStatementsDuringTest)
                    .as("The @DbUnitProperty value must be applied to the real connection's"
                            + " DatabaseConfig while the test runs.")
                    .isTrue();
            assertThat(
                    connection.getConfig().getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                            .as("The connection is kept open past the test, so the value must be"
                                    + " put back for whatever uses it next.")
                            .isFalse();
        } finally
        {
            environment.closeConnection();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the environment's shared
    // connection (see the finally block above), not one owned by this one test.
    @DbUnitConfig(closeConnectionAfterTest = false,
            properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
    @DbUnitPrep("empty.xml")
    static class PropertySample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        static boolean batchedStatementsDuringTest;

        @Test
        void testPropertyIsAppliedBeforeTheTestRuns() throws Exception
        {
            final DatabaseConfig config = databaseTester.getConnection().getConfig();
            batchedStatementsDuringTest =
                    config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS);
        }
    }

    @Test
    void testBeforeTestExecution_dbUnitPropertyDeclaredOnExpectedPath_reachesConnectionConfig()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            ExpectedPathPropertySample.databaseTester = new DefaultDatabaseTester(connection);
            ExpectedPathPropertySample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExpectedPathPropertySample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(ExpectedPathPropertySample.batchedStatementsDuringTest)
                    .as("The @DbUnitProperty value must reach the connection"
                            + " DefaultPrepAndExpectedTestCase actually uses for"
                            + " setupData()/verifyData()/cleanupData() - the"
                            + " setup/teardown path's IOperationListener wiring is never"
                            + " triggered by those methods.")
                    .isTrue();
            assertThat(
                    connection.getConfig().getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                            .as("The connection is kept open past the test, so the value must be"
                                    + " put back for whatever uses it next - including when the"
                                    + " test method took the connection as a parameter.")
                            .isFalse();
        } finally
        {
            environment.closeConnection();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the environment's shared
    // connection (see the finally block above), not one owned by this one test.
    @DbUnitConfig(closeConnectionAfterTest = false,
            properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
    @DbUnitExpected("empty.xml")
    static class ExpectedPathPropertySample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        static boolean batchedStatementsDuringTest;

        @Test
        void testPropertyIsAppliedBeforeVerification(final IDatabaseConnection connection)
        {
            batchedStatementsDuringTest = connection.getConfig()
                    .getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS);
        }
    }

    @Test
    void testAfterTestExecution_testCaseKeepsItsConnectionAcrossTests_eachTestSeesOnlyItsOwnProperties()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            KeptConnectionBase.observedBatchSizes.clear();
            KeptConnectionBase.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(), new DefaultDatabaseTester(connection), false);

            runOnTheKeptConnection(DeclaresNothingFirst.class);
            runOnTheKeptConnection(DeclaresBatchSize.class);
            runOnTheKeptConnection(DeclaresNothingLast.class);

            final Object defaultBatchSize =
                    new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);
            assertThat(KeptConnectionBase.observedBatchSizes)
                    .as("A test declaring nothing, then one declaring batchSize=50, then one"
                            + " declaring nothing again, on one connection kept across them:"
                            + " the middle value must not bleed into the last test.")
                    .containsExactly(defaultBatchSize, 50, defaultBatchSize);
            assertThat(connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                    .as("Nothing is left on the connection once the last test is done.")
                    .isEqualTo(defaultBatchSize);
        } finally
        {
            environment.closeConnection();
        }
    }

    private static void runOnTheKeptConnection(final Class<?> sampleClass)
    {
        EngineTestKit.engine("junit-jupiter").selectors(selectClass(sampleClass)).execute()
                .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    static class KeptConnectionBase
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;

        static final List<Object> observedBatchSizes = new ArrayList<>();

        @Test
        @DbUnitExpected("empty.xml")
        void testObservesBatchSize(final IDatabaseConnection connection)
        {
            observedBatchSizes
                    .add(connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class DeclaresNothingFirst extends KeptConnectionBase
    {
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    static class DeclaresBatchSize extends KeptConnectionBase
    {
    }

    @ExtendWith(DbUnitExtension.class)
    static class DeclaresNothingLast extends KeptConnectionBase
    {
    }
}
