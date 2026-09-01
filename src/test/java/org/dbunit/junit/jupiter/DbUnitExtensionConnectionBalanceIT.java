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
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test that every annotation-driven path leaves the connection ledger
 * balanced: no connection the run opened is left unclosed, and the runtime never holds more than
 * its memoized connection plus one the tester opens for an operation. Runs each path through a
 * {@link DataSourceDatabaseTester} over a {@link CountingDataSource}, which hands out real
 * connections and tracks open/close and peak concurrency.
 *
 * <p>Turns the "delicate connection lifecycle" concern into a per-path assertion (matrix item 9 /
 * G-c3 companion). Also pins the peak concurrency each path actually reaches:
 * <ul>
 * <li>The prep/expected path reaches only <strong>one</strong> - {@code DefaultPrepAndExpectedTestCase}
 * runs setup, verify and cleanup all on its one reusable connection.</li>
 * <li>The setup/teardown path reaches <strong>two</strong> - the extension memoizes the
 * connection {@code onSetup()} retrieves (for a possible row count check baseline or a parameter
 * injection) and holds it to end of test, so {@code onTearDown()} opening its own makes two.
 * This holds even with the row count check disabled: the memoize is unconditional on that path.</li>
 * </ul>
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionConnectionBalanceIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_setupTeardownPathNoRowCountCheck_balancedPeakOfTwo()
            throws Exception
    {
        runBalanced(SetupTeardownNoCheckSample.class, 2);
    }

    @Test
    void testAfterTestExecution_setupTeardownPathWithRowCountCheck_balancedPeakOfTwo()
            throws Exception
    {
        runBalanced(SetupTeardownWithCheckSample.class, 2);
    }

    @Test
    void testAfterTestExecution_prepExpectedPathWithRowCountCheck_balancedPeakOfOne()
            throws Exception
    {
        runBalanced(PrepExpectedWithCheckSample.class, 1);
    }

    @Test
    void testAfterTestExecution_injectedConnectionParameterWithRowCountCheck_balancedPeakOfTwo()
            throws Exception
    {
        runBalanced(InjectedConnectionSample.class, 2);
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
    static class SetupTeardownNoCheckSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void runsTheLifecycle()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class SetupTeardownWithCheckSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void runsTheLifecycle()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class PrepExpectedWithCheckSample extends BalanceSample
    {
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
    static class InjectedConnectionSample extends BalanceSample
    {
        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
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
}
