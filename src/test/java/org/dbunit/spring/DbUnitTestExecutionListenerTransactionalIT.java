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
package org.dbunit.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultOperationListener;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Real-database integration test of {@link DbUnitTestExecutionListener} around Spring's
 * test-managed transactions: a {@code @Transactional} test run through {@code SpringExtension}
 * with a {@code DataSourceTransactionManager} over the integration-test database.
 *
 * <p>The listener hooks the same before-test-method and after-test-method phases the
 * transaction is started and ended in, so its order decides everything: its prep must commit
 * before the transaction opens and its verify and teardown must run after the transaction has
 * ended. The ordering test therefore changes no data, so a regression cannot leave the
 * listener's own steps waiting on a lock the open transaction holds. The commit and rollback tests
 * must change data, so they arm a watchdog that rolls the transaction back from another thread:
 * after a regression to the execution phases, where verify and teardown wait for a transaction
 * that cannot end until they return, the test then fails instead of hanging the build.
 */
class DbUnitTestExecutionListenerTransactionalIT
{
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final String COLUMN_QUERY =
            "SELECT COLUMN1 FROM " + TEST_TABLE + " WHERE COLUMN0 = 'row0'";
    private static final long LOCK_WAIT_LIMIT_SECONDS = 15;

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Test
    void testBeforeAndAfterTestMethod_transactionalTest_prepBeforeAndTearDownAfterTheTransaction()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        EVENTS.clear();
        try
        {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(OrderingSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(EVENTS)
                    .as("DbUnit's prep must run, and commit, before the test-managed transaction"
                            + " opens - so the test sees the prepped row - and its teardown"
                            + " after the transaction has completed.")
                    .containsExactly("DbUnit step, test transaction active: false",
                            "test body, prepped row visible in the test transaction: true",
                            "test transaction completed",
                            "DbUnit step, test transaction active: false");
            assertThat(TestTableRows.count(environment.getConnection(), TEST_TABLE))
                    .as("@DbUnitTearDown(DELETE_ALL) must have cleaned up the prepped row.")
                    .isZero();
        } finally
        {
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestMethod_committedTransactionAndExpectedDataSet_verifiesTheCommittedChange()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(CommitSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(TestTableRows.count(environment.getConnection(), TEST_TABLE))
                    .as("@DbUnitTearDown(DELETE_ALL) must have cleaned up after the"
                            + " verification.")
                    .isZero();
        } finally
        {
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestMethod_rolledBackTransactionAndExpectedDataSet_verificationDoesNotSeeTheChange()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(RollbackSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(TestTableRows.count(environment.getConnection(), TEST_TABLE))
                    .as("@DbUnitTearDown(DELETE_ALL) must still have cleaned up after the"
                            + " failed verification.")
                    .isZero();
        } finally
        {
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Configuration
    static class TransactionalConfig
    {
        @Bean
        DataSource dataSource() throws Exception
        {
            return new ProfileDataSource(DatabaseEnvironment.getInstance().getProfile());
        }

        @Bean
        PlatformTransactionManager transactionManager(final DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        IDatabaseTester databaseTester(final DataSource dataSource) throws Exception
        {
            final String schema = DatabaseEnvironment.getInstance().getProfile().getSchema();
            final DataSourceDatabaseTester tester =
                    new DataSourceDatabaseTester(dataSource, schema);
            tester.setOperationListener(new RecordingOperationListener());
            return tester;
        }
    }

    /**
     * Closes connections like the default listener does, and notes in {@link #EVENTS} whether a
     * test-managed transaction is active whenever DbUnit retrieves a connection for a step.
     */
    static class RecordingOperationListener implements IOperationListener
    {
        private final IOperationListener delegate = new DefaultOperationListener();

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            EVENTS.add("DbUnit step, test transaction active: " + TestTransaction.isActive());
            delegate.connectionRetrieved(connection);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
            delegate.operationSetUpFinished(connection);
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
            delegate.operationTearDownFinished(connection);
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionalConfig.class)
    @DbUnitSpringTest
    static class OrderingSample
    {
        @Autowired
        DataSource dataSource;

        @Test
        @Transactional
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testTransactionalTest_prepCommittedBeforeTransaction_rowVisibleInTransaction()
                throws Exception
        {
            final Connection transactionConnection = DataSourceUtils.getConnection(dataSource);
            final int rowCount;
            try (Statement statement = transactionConnection.createStatement();
                    ResultSet resultSet =
                            statement.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE))
            {
                resultSet.next();
                rowCount = resultSet.getInt(1);
            }
            EVENTS.add("test body, prepped row visible in the test transaction: "
                    + (rowCount == 1));
            TransactionSynchronizationManager
                    .registerSynchronization(new TransactionSynchronization()
                    {
                        @Override
                        public void afterCompletion(final int status)
                        {
                            EVENTS.add("test transaction completed");
                        }
                    });
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionalConfig.class)
    @DbUnitSpringTest
    static class CommitSample
    {
        @Autowired
        DataSource dataSource;

        @Test
        @Transactional
        @Commit
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitExpected(value = ItDataSets.EXPECTED,
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testTransactionalCommit_updatesPreppedRow_changeCommittedForVerification()
                throws Exception
        {
            updatePreppedRowInTestTransaction(dataSource);
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionalConfig.class)
    @DbUnitSpringTest
    static class RollbackSample
    {
        @Autowired
        DataSource dataSource;

        @Test
        @Transactional
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitExpected(value = ItDataSets.EXPECTED,
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testTransactionalRollback_updatesPreppedRow_changeRolledBackBeforeVerification()
                throws Exception
        {
            updatePreppedRowInTestTransaction(dataSource);
        }
    }

    private static void updatePreppedRowInTestTransaction(final DataSource dataSource)
            throws Exception
    {
        final Connection transactionConnection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = transactionConnection.createStatement())
        {
            statement.executeUpdate("UPDATE " + TEST_TABLE
                    + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
        }
        assertThat(columnInTestTransaction(transactionConnection))
                .as("The test transaction must see its own update.").isEqualTo("after");
        armLockWaitWatchdog(transactionConnection);
    }

    /**
     * Rolls the test transaction back from another thread unless it completes within a few
     * seconds, which it does at once when the listener runs outside the transaction.
     */
    private static void armLockWaitWatchdog(final Connection transactionConnection)
    {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable ->
                {
                    final Thread thread = new Thread(runnable, "lock-wait-watchdog");
                    thread.setDaemon(true);
                    return thread;
                });
        final ScheduledFuture<Void> rollback = scheduler.schedule(() ->
        {
            transactionConnection.rollback();
            return null;
        }, LOCK_WAIT_LIMIT_SECONDS, TimeUnit.SECONDS);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(final int status)
            {
                rollback.cancel(false);
                scheduler.shutdown();
            }
        });
    }

    private static String columnInTestTransaction(final Connection transactionConnection)
            throws Exception
    {
        try (Statement statement = transactionConnection.createStatement();
                ResultSet resultSet = statement.executeQuery(COLUMN_QUERY))
        {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
