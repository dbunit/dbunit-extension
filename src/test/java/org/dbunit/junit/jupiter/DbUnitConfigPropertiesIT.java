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
import java.util.Properties;

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
 * {@link IDatabaseConnection#getConfig()} for an annotated test that declares
 * {@code @DbUnitPrep} and for one that declares {@code @DbUnitExpected}, because
 * {@code DefaultPrepAndExpectedTestCase}'s own connection resolution applies it in both -
 * neither is something {@code AnnotatedTestExecutorTest}'s mocked connection can prove - and
 * that a connection kept open past the test carries the value into no later test.
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
    // databaseTester wraps the environment's shared connection (see the finally block above),
    // not one owned by this one test, so its listener is the no-op one.
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
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
    void testBeforeTestExecution_dbUnitPropertyDeclaredWithExpected_reachesConnectionConfig()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            ExpectedPropertySample.databaseTester = new DefaultDatabaseTester(connection);
            ExpectedPropertySample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExpectedPropertySample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(ExpectedPropertySample.batchedStatementsDuringTest)
                    .as("The @DbUnitProperty value must reach the connection"
                            + " DefaultPrepAndExpectedTestCase actually uses for"
                            + " setupData()/verifyData()/cleanupData().")
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
    // databaseTester wraps the environment's shared connection (see the finally block above),
    // not one owned by this one test, so its listener is the no-op one.
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
    @DbUnitExpected("empty.xml")
    static class ExpectedPropertySample
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
            KeptConnectionSample.observedBatchSizes.clear();
            final Properties batchSize = new Properties();
            batchSize.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, "50");

            runOnTheKeptConnection(connection, new Properties());
            runOnTheKeptConnection(connection, batchSize);
            runOnTheKeptConnection(connection, new Properties());

            final Object defaultBatchSize =
                    new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);
            assertThat(KeptConnectionSample.observedBatchSizes)
                    .as("A test case built with no properties, then one built with batchSize=50,"
                            + " then one with none again, on one connection kept across them:"
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

    private static void runOnTheKeptConnection(final IDatabaseConnection connection,
            final Properties properties)
    {
        final DefaultPrepAndExpectedTestCase testCase = new DefaultPrepAndExpectedTestCase(
                new FlatXmlDataFileLoader(), new DefaultDatabaseTester(connection), false);
        testCase.setDatabaseConfigProperties(properties);
        KeptConnectionSample.testCase = testCase;

        EngineTestKit.engine("junit-jupiter").selectors(selectClass(KeptConnectionSample.class))
                .execute().testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @ExtendWith(DbUnitExtension.class)
    static class KeptConnectionSample
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
}
