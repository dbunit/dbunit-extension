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

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test that every annotation-driven test leaves the connection ledger
 * balanced: no connection the run opened is left unclosed, and the runtime holds no more than
 * one at a time. Runs each shape through a {@link DataSourceDatabaseTester} over a
 * {@link CountingDataSource}, which hands out real connections and tracks open/close and peak
 * concurrency.
 *
 * <p>Turns the "delicate connection lifecycle" concern into a per-shape assertion (matrix item 9 /
 * G-c3 companion). Also pins the peak concurrency every shape reaches, <strong>one</strong>:
 * {@code DefaultPrepAndExpectedTestCase} runs setup, verify and cleanup, with or without
 * {@code @DbUnitExpected}, all on its one reusable connection, which a connection parameter
 * shares and the row count check uses too.
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionConnectionBalanceIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_noExpectedAndNoRowCountCheck_balancedPeakOfOne()
            throws Exception
    {
        runBalanced(NoExpectedNoCheckSample.class, 1);
    }

    @Test
    void testAfterTestExecution_noExpectedAndRowCountCheck_balancedPeakOfOne()
            throws Exception
    {
        runBalanced(NoExpectedWithCheckSample.class, 1);
    }

    @Test
    void testAfterTestExecution_expectedAndRowCountCheck_balancedPeakOfOne()
            throws Exception
    {
        runBalanced(ExpectedWithCheckSample.class, 1);
    }

    @Test
    void testAfterTestExecution_injectedConnectionParameterWithRowCountCheck_balancedPeakOfOne()
            throws Exception
    {
        runBalanced(InjectedConnectionSample.class, 1);
    }

    @Test
    void testAfterEach_beforeEachFailsAfterConnectionInjected_closesEveryConnectionOpened()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(FailingBeforeEachSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(dataSource.opened())
                    .as("The failing @BeforeEach must have been handed a connection, or this"
                            + " test proves nothing.")
                    .isPositive();
            assertThat(dataSource.leaked())
                    .as("A connection injected into a @BeforeEach that then fails must still be"
                            + " closed, because no after-test callback runs once @BeforeEach"
                            + " fails.")
                    .isZero();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_propertyValueOfTheWrongType_failsBeforeAConnectionIsOpened()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(WrongTypePropertyValueSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(dataSource.opened())
                    .as("A @DbUnitProperty value that does not suit the type of its property, a"
                            + " number that is none here, fails the test before dbUnit opens a"
                            + " connection, so there is none to leak.")
                    .isZero();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_uninstantiablePropertyValueWithoutExpected_closesTheTestersConnection()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(UninstantiablePropertyValueSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(dataSource.opened())
                    .as("configureTest() must have opened a connection, or this test proves"
                            + " nothing.")
                    .isPositive();
            assertThat(dataSource.leaked())
                    .as("A connection the tester opened must be closed even when applying a"
                            + " @DbUnitProperty value to it fails in a test without"
                            + " @DbUnitExpected.")
                    .isZero();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_uninstantiablePropertyValueWithExpected_closesTheTestersConnection()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(UninstantiablePropertyValueWithExpectedSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(dataSource.opened())
                    .as("configureTest() must have opened a connection, or this test proves"
                            + " nothing.")
                    .isPositive();
            assertThat(dataSource.leaked())
                    .as("A connection the tester opened must be closed even when applying a"
                            + " @DbUnitProperty value to it fails in a test with @DbUnitExpected.")
                    .isZero();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_expectedFileMissing_closesTheConnectionConfigureTestAcquired()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(MissingExpectedFileSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(dataSource.opened())
                    .as("configureTest() must have opened a connection, or this test proves"
                            + " nothing.")
                    .isPositive();
            assertThat(dataSource.leaked())
                    .as("A connection configureTest() acquired must be closed when it then fails"
                            + " to load the expected data file, because the runtime skips"
                            + " postTest() for a test that never finished configuring.")
                    .isZero();
        } finally
        {
            environment.closeConnection();
        }
    }

    private void runBalanced(final Class<?> sampleClass, final int expectedPeak) throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource dataSource = new CountingDataSource(environment.getProfile());
        deleteAllRowsQuietly(environment);
        BalanceSample.databaseTester = new DataSourceDatabaseTester(dataSource,
                environment.getProfile().getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(sampleClass)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(dataSource.leaked())
                    .as("%s must close every connection it opened.", sampleClass.getSimpleName())
                    .isZero();
            assertThat(dataSource.peakConcurrent())
                    .as("%s reaches this peak connection concurrency - see the class Javadoc.",
                            sampleClass.getSimpleName())
                    .isEqualTo(expectedPeak);
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
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

    abstract static class BalanceSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    static class NoExpectedNoCheckSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void runsTheLifecycle()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class NoExpectedWithCheckSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void runsTheLifecycle()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class ExpectedWithCheckSample extends BalanceSample
    {
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
    @DbUnitRowCountCheck
    static class InjectedConnectionSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void readsThroughTheInjectedConnection(final IDatabaseConnection connection)
                throws Exception
        {
            try (Statement statement = connection.getConnection().createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT COUNT(*) FROM " + TEST_TABLE + " WHERE COLUMN0 = 'row0'"))
            {
                resultSet.next();
                assertThat(resultSet.getInt(1))
                        .as("The injected connection must see @DbUnitPrep's seeded row.")
                        .isEqualTo(1);
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    static class FailingBeforeEachSample extends BalanceSample
    {
        @BeforeEach
        void failAfterTheConnectionIsInjected(final IDatabaseConnection connection)
        {
            throw new IllegalStateException("intentional @BeforeEach failure");
        }

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        void neverRuns()
        {
        }
    }

    /** A data type factory that dbUnit can load but not instantiate. */
    public abstract static class UninstantiableDataTypeFactory extends DefaultDataTypeFactory
    {
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "notANumber"))
    static class WrongTypePropertyValueSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        void neverRuns()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitConfig(properties = @DbUnitProperty(name = "datatypeFactory",
            value = "org.dbunit.junit.jupiter.DbUnitExtensionConnectionBalanceIT"
                    + "$UninstantiableDataTypeFactory"))
    static class UninstantiablePropertyValueSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        void neverRuns()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitConfig(properties = @DbUnitProperty(name = "datatypeFactory",
            value = "org.dbunit.junit.jupiter.DbUnitExtensionConnectionBalanceIT"
                    + "$UninstantiableDataTypeFactory"))
    static class UninstantiablePropertyValueWithExpectedSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        void neverRuns()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    static class MissingExpectedFileSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "no-such-expected-file.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        void neverRuns()
        {
        }
    }
}
