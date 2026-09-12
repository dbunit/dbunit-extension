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

import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Real-database integration test of {@link DbUnitTestExecutionListener} run through Spring's own
 * {@code SpringExtension}, proving the listener drives a real connection end to end via Spring's
 * TestContext Framework - not just the hand-rolled {@code TestContext} fakes
 * {@code DbUnitTestExecutionListenerTest} and {@code SpringTesterResolverTest} use.
 */
class DbUnitTestExecutionListenerIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_dbUnitTesterField_setsUpAndTearsDown() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            SetupTeardownSample.databaseTester = new DefaultDatabaseTester(connection);
            SetupTeardownSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(SetupTeardownSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("@DbUnitTearDown(operation = DELETE_ALL), driven through"
                            + " DbUnitTestExecutionListener via a real SpringExtension-managed"
                            + " test, must have cleaned up.")
                    .isZero();
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_applicationContextBeanResolvedTester_prepMutateExpectedVerifiesAndCleansUp()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ApplicationContextTesterSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The tester resolved from the sole IDatabaseTester ApplicationContext"
                            + " bean - no @DbUnitTester/@DbUnitTestCase field at all - must still"
                            + " drive prep, verify, and @DbUnitTearDown(DELETE_ALL) end to end.")
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

    @Configuration
    static class EmptyConfig
    {
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @DbUnitSpringTest
    static class SetupTeardownSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("spring-it-prep.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testPrepSeedsRow_visibleToTest() throws Exception
        {
            try (Statement statement = databaseTester.getConnection().getConnection()
                    .createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT COUNT(*) FROM " + TEST_TABLE + " WHERE COLUMN0 = 'row0'"))
            {
                resultSet.next();
                assertThat(resultSet.getInt(1))
                        .as("@DbUnitPrep's CLEAN_INSERT must have seeded the row before the test"
                                + " method body ran.")
                        .isEqualTo(1);
            }
        }
    }

    @Configuration
    static class TesterBeanConfig
    {
        @Bean
        IDatabaseTester databaseTester() throws Exception
        {
            final IDatabaseTester tester =
                    new DefaultDatabaseTester(DatabaseEnvironment.getInstance().getConnection());
            tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
            return tester;
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TesterBeanConfig.class)
    @DbUnitSpringTest
    static class ApplicationContextTesterSample
    {
        @Test
        @DbUnitPrep("spring-it-prep.xml")
        @DbUnitExpected(value = "spring-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testWithdraw_sufficientBalance_decrementsBalance(
                @Autowired final IDatabaseTester databaseTester) throws Exception
        {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement())
            {
                statement.execute(
                        "UPDATE " + TEST_TABLE + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }
}
