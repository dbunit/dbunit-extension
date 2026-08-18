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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.function.Supplier;

import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Lifecycle integration tests for {@link DbUnitRowCountCheck} through the real
 * {@link DbUnitExtension}, verifying {@code DbUnitExtension.resolveConfiguration} finds the
 * annotation and wires it into the row count check - complementing
 * {@code AnnotatedTestExecutorTest}'s direct, connection-mocked coverage of the resolution
 * itself.
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionRowCountCheckLifecycleTest {
    @Test
    void testAfterTestExecution_rowCountCheckAndLeakedRows_failsNamingTheTable() {
        LeakedRowsSample.connection = fakeConnection("ACCOUNT", 5);

        final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(LeakedRowsSample.class)).execute().testEvents()
                .failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));

        final Throwable reported = failedEvent.getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported)
                .as("A leaked row must fail the test, naming the table.")
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testAfterTestExecution_rowCountCheckAndFailingTest_skipsCheckAndReportsOriginalFailure()
            throws Exception {
        FailingTestSample.connection = fakeConnection("ACCOUNT", 5);

        final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingTestSample.class)).execute().testEvents()
                .failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));

        final Throwable reported = failedEvent.getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported)
                .as("The row count check must be skipped after a test failure, so the"
                        + " original failure is the one reported - not a row count"
                        + " mismatch masking it.")
                .hasMessageContaining("intentional test failure");
    }

    @Test
    void testAfterTestExecution_excludedTableLeaksRows_passes() {
        ExcludedTableLeaksSample.connection = fakeConnection("IGNORED_TABLE", 5);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ExcludedTableLeaksSample.class)).execute().testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    private static IDatabaseConnection fakeConnection(final String tableName,
            final int initialRowCount) {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        try {
            when(connection.getConfig()).thenReturn(new DatabaseConfig());
            final IDataSet dataSet = mock(IDataSet.class);
            when(dataSet.getTableNames()).thenReturn(new String[] {tableName});
            when(connection.createDataSet()).thenReturn(dataSet);
            when(connection.getRowCount(anyString())).thenReturn(initialRowCount);
            // isClosed() defaults to false on this mock, matching a not-yet-closed connection.
            when(connection.getConnection()).thenReturn(mock(Connection.class));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
        return connection;
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitRowCountCheck
    static class LeakedRowsSample {
        static IDatabaseConnection connection;
        IDatabaseTester databaseTester = new FixedConnectionTester(() -> connection);

        @Test
        void testLeaksARow() throws Exception {
            when(connection.getRowCount("ACCOUNT")).thenReturn(6);
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitRowCountCheck
    static class FailingTestSample {
        static IDatabaseConnection connection;
        IDatabaseTester databaseTester = new FixedConnectionTester(() -> connection);

        @Test
        void testFailsAfterChangingTheCount() throws Exception {
            when(connection.getRowCount("ACCOUNT")).thenReturn(9);
            throw new AssertionError("intentional test failure");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitRowCountCheck(exclude = "IGNORED_TABLE")
    static class ExcludedTableLeaksSample {
        static IDatabaseConnection connection;
        IDatabaseTester databaseTester = new FixedConnectionTester(() -> connection);

        @Test
        void testExcludedTableLeaksARowButPasses() throws Exception {
            when(connection.getRowCount("IGNORED_TABLE")).thenReturn(6);
        }
    }

    /** Returns the same connection from every call, like a real fixed-connection tester. */
    private static final class FixedConnectionTester implements IDatabaseTester {
        private final Supplier<IDatabaseConnection> connectionSupplier;

        private FixedConnectionTester(final Supplier<IDatabaseConnection> connectionSupplier) {
            this.connectionSupplier = connectionSupplier;
        }

        @Override
        public void onSetup() {
        }

        @Override
        public void onTearDown() {
        }

        @Override
        public IDatabaseConnection getConnection() {
            return connectionSupplier.get();
        }

        @Override
        public IDataSet getDataSet() {
            return null;
        }

        @Override
        public void setDataSet(final IDataSet dataSet) {
        }

        @Override
        public DatabaseOperation getSetUpOperation() {
            return DatabaseOperation.NONE;
        }

        @Override
        public DatabaseOperation getTearDownOperation() {
            return DatabaseOperation.NONE;
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation) {
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation) {
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener) {
        }

        @Override
        public IOperationListener getOperationListener() {
            return null;
        }

        @Override
        @Deprecated
        public void closeConnection(final IDatabaseConnection connection) {
        }

        @Override
        @Deprecated
        public void setSchema(final String schema) {
        }
    }
}
