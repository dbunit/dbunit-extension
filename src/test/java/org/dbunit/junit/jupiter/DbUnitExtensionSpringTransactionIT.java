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
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Integration test of the warning that {@link DbUnitExtension} logs when a test runs in a
 * transaction that Spring's test context framework manages, which {@code SpringExtension} starts
 * for a {@code @Transactional} test before the extension's steps run. The tests with a tester of
 * its own change no data, so they cannot wait for a lock of the test transaction; the test with a
 * tester on a {@code TransactionAwareDataSourceProxy} changes the rows its prep seeded, inside
 * the transaction that dbUnit then joins.
 */
class DbUnitExtensionSpringTransactionIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testBeforeTestExecution_transactionalSpringTest_warnsOnceForTheClass() throws Exception
    {
        final List<ILoggingEvent> warnings = warningsWhileRunning(TransactionalSample.class, 2);

        assertThat(warnings).as("Two transactional test methods warn once for their class.")
                .hasSize(1);
        assertThat(warnings.get(0).getFormattedMessage())
                .as("The warning names the test class, the effect and the alternatives, with"
                        + " the @Commit that @DbUnitSpringTest needs to verify the test's"
                        + " changes.")
                .contains(TransactionalSample.class.getName(), "Spring test-managed transaction",
                        "TransactionAwareDataSourceProxy", "@DbUnitSpringTest", "@Commit",
                        "@Transactional");
    }

    @Test
    void testBeforeTestExecution_transactionAwareTesterThatVerifiesTheTestsChange_passes()
            throws Exception
    {
        final List<ILoggingEvent> warnings =
                warningsWhileRunning(TransactionAwareTesterSample.class, 1);

        assertThat(warnings).as("The warning does not tell the two kinds of tester apart, so it"
                + " is logged for a tester that joins the transaction too.").hasSize(1);
        assertThat(warnings.get(0).getFormattedMessage())
                .as("The warning says that a tester on a TransactionAwareDataSourceProxy runs in"
                        + " the transaction, which this test does.")
                .contains("TransactionAwareDataSourceProxy");
    }

    @Test
    void testBeforeTestExecution_springTestWithoutATransaction_doesNotWarn() throws Exception
    {
        final List<ILoggingEvent> warnings =
                warningsWhileRunning(NonTransactionalSample.class, 2);

        assertThat(warnings).as("A Spring test with no test-managed transaction is not warned"
                + " about.").isEmpty();
    }

    private static List<ILoggingEvent> warningsWhileRunning(final Class<?> sampleClass,
            final int tests)
    {
        final Logger logger = (Logger) LoggerFactory.getLogger(DbUnitExtension.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try
        {
            final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
            prepareSample(sampleClass, environment);
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(sampleClass)).execute()
                    .testEvents()
                    .assertStatistics(stats -> stats.started(tests).succeeded(tests));
        } catch (final Exception e)
        {
            throw new IllegalStateException(e);
        } finally
        {
            logger.detachAppender(appender);
            appender.stop();
            deleteAllRowsQuietly();
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    private static void prepareSample(final Class<?> sampleClass,
            final DatabaseEnvironment environment) throws Exception
    {
        if (sampleClass == TransactionAwareTesterSample.class)
        {
            TransactionAwareTesterSample.schema = environment.getProfile().getSchema();
        } else if (sampleClass == TransactionalSample.class)
        {
            TransactionalSample.databaseTester = noOpListenerTesterOf(environment);
        } else if (sampleClass == NonTransactionalSample.class)
        {
            NonTransactionalSample.databaseTester = noOpListenerTesterOf(environment);
        } else
        {
            throw new IllegalArgumentException(
                    "No preparation for the sample " + sampleClass.getName());
        }
    }

    private static DefaultDatabaseTester noOpListenerTesterOf(
            final DatabaseEnvironment environment) throws Exception
    {
        final DefaultDatabaseTester tester =
                new DefaultDatabaseTester(environment.getConnection());
        tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        return tester;
    }

    private static void deleteAllRowsQuietly()
    {
        try (Statement statement = DatabaseEnvironment.getInstance().getConnection()
                .getConnection().createStatement())
        {
            statement.execute("DELETE FROM " + TEST_TABLE);
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    @Configuration
    static class TransactionConfig
    {
        @Bean
        DataSource dataSource() throws Exception
        {
            final DatabaseProfile profile = DatabaseEnvironment.getInstance().getProfile();
            return new DriverManagerDataSource(profile.getConnectionUrl(), profile.getUser(),
                    profile.getPassword());
        }

        @Bean
        PlatformTransactionManager transactionManager(final DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionConfig.class)
    @DbUnitTest
    static class TransactionalSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @Transactional
        void firstTransactionalTest()
        {
        }

        @Test
        @Transactional
        void secondTransactionalTest()
        {
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionConfig.class)
    @DbUnitTest
    static class TransactionAwareTesterSample
    {
        static String schema;

        @Autowired
        DataSource dataSource;

        @DbUnitTester
        IDatabaseTester databaseTester;

        @BeforeEach
        void joinTheTransaction()
        {
            databaseTester = new DataSourceDatabaseTester(
                    new TransactionAwareDataSourceProxy(dataSource), schema);
        }

        @Test
        @Transactional
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow() throws Exception
        {
            final Connection connection = DataSourceUtils.getConnection(dataSource);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TransactionConfig.class)
    @DbUnitTest
    static class NonTransactionalSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void firstTest()
        {
        }

        @Test
        void secondTest()
        {
        }
    }
}
