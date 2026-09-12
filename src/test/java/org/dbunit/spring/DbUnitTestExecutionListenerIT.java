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
import java.util.stream.Collectors;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineExecutionResults;
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
    private static final String PK_TABLE = "PK_TABLE";

    @Test
    void testBeforeAndAfterTestMethod_dbUnitTesterField_setsUpAndTearsDown() throws Exception
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
            assertThat(TestTableRows.count(verifyConnection, TEST_TABLE))
                    .as("@DbUnitTearDown(operation = DELETE_ALL), driven through"
                            + " DbUnitTestExecutionListener via a real SpringExtension-managed"
                            + " test, must have cleaned up.")
                    .isZero();
        } finally
        {
            SetupTeardownSample.databaseTester = null;
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeAndAfterTestMethod_applicationContextBeanResolvedTester_prepMutateExpectedVerifiesAndCleansUp()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ApplicationContextTesterSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(TestTableRows.count(verifyConnection, TEST_TABLE))
                    .as("The tester resolved from the sole IDatabaseTester ApplicationContext"
                            + " bean - no @DbUnitTester/@DbUnitTestCase field at all - must still"
                            + " drive prep, verify, and @DbUnitTearDown(DELETE_ALL) end to end.")
                    .isZero();
        } finally
        {
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeAndAfterTestMethod_threeMethodsOnOneTestContext_eachMethodGetsItsOwnLifecycle()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            ThreeMethodsSample.databaseTester = new DefaultDatabaseTester(connection);
            ThreeMethodsSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            final EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ThreeMethodsSample.class)).execute();

            results.testEvents()
                    .assertStatistics(stats -> stats.started(3).succeeded(2).failed(1));
            final List<String> failedTests = results.testEvents().failed()
                    .map(event -> event.getTestDescriptor().getDisplayName())
                    .collect(Collectors.toList());
            assertThat(failedTests)
                    .as("Only the third method declares an @DbUnitExpected its test body does not"
                            + " meet, so only it may fail: Spring's TestContext serves all three"
                            + " methods, and each must get its own @DbUnitPrep, @DbUnitExpected"
                            + " and @DbUnitTearDown rather than the first method's.")
                    .containsExactly("testThirdMethod_expectedNeverMet_failsVerification()");
        } finally
        {
            ThreeMethodsSample.databaseTester = null;
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE, PK_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestMethod_dbUnitSpringTestButNoDbUnitUsage_testRunsWithoutAnyDbUnitStep()
            throws Exception
    {
        EngineTestKit.engine("junit-jupiter").selectors(selectClass(NoDbUnitUsageSample.class))
                .execute().testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeAndAfterTestMethod_nestedClassInheritingEnclosingClassConfiguration_usesEnclosingTesterAndTearDown()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            NestedSample.sharedTester = new DefaultDatabaseTester(connection);
            NestedSample.sharedTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(NestedSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(TestTableRows.count(environment.getConnection(), TEST_TABLE))
                    .as("The enclosing class's @DbUnitTearDown(DELETE_ALL) must apply to the"
                            + " @Nested class's test, driven through the enclosing instance's"
                            + " @DbUnitTester field.")
                    .isZero();
        } finally
        {
            NestedSample.sharedTester = null;
            TestTableRows.deleteAllQuietly(environment, TEST_TABLE);
            environment.closeConnection();
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
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testPrepSeedsRow_dbUnitTesterField_rowVisibleToTestMethod() throws Exception
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

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @DbUnitSpringTest
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class ThreeMethodsSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @Order(1)
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testFirstMethod_prepSeedsTestTable_testTableHasTheRow() throws Exception
        {
            assertThat(TestTableRows.count(databaseTester.getConnection(), TEST_TABLE))
                    .as("The first method's @DbUnitPrep must have seeded TEST_TABLE.")
                    .isEqualTo(1);
        }

        @Test
        @Order(2)
        @DbUnitPrep(ItDataSets.PK_PREP)
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testSecondMethod_prepSeedsPkTable_onlyItsOwnPrepWasLoaded() throws Exception
        {
            assertThat(TestTableRows.count(databaseTester.getConnection(), PK_TABLE))
                    .as("The second method's own @DbUnitPrep must have seeded PK_TABLE.")
                    .isEqualTo(1);
            assertThat(TestTableRows.count(databaseTester.getConnection(), TEST_TABLE))
                    .as("The first method's @DbUnitPrep must not have been loaded again for the"
                            + " second method.")
                    .isZero();
        }

        @Test
        @Order(3)
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitExpected(value = ItDataSets.EXPECTED,
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testThirdMethod_expectedNeverMet_failsVerification()
        {
            // changes nothing, so TEST_TABLE still holds the prep's "before" value where
            // @DbUnitExpected says "after"
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = EmptyConfig.class)
    @DbUnitSpringTest
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class NestedSample
    {
        static IDatabaseTester sharedTester;

        @DbUnitTester
        final IDatabaseTester databaseTester = sharedTester;

        @Nested
        class WhenSeeded
        {
            @Test
            @DbUnitPrep(ItDataSets.PREP)
            void testPrepSeedsRow_enclosingInstancesTester_rowVisibleToNestedTest() throws Exception
            {
                assertThat(TestTableRows.count(databaseTester.getConnection(), TEST_TABLE))
                        .as("@DbUnitPrep must have seeded the row through the enclosing"
                                + " instance's tester before the nested test method ran.")
                        .isEqualTo(1);
            }
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TesterBeanConfig.class)
    @DbUnitSpringTest
    static class NoDbUnitUsageSample
    {
        @Test
        void testNoDbUnitAnnotationAndNoMarkerField_testerBeanInContext_testerBeanNotDriven()
        {
            // a DbUnit-free test registered with the listener, the way a shared base class or
            // composed annotation carrying @DbUnitSpringTest registers it on every test
        }
    }

    @Configuration
    static class TesterBeanConfig
    {
        @Bean
        IDatabaseTester databaseTester() throws Exception
        {
            final DatabaseProfile profile = DatabaseEnvironment.getInstance().getProfile();
            return new DataSourceDatabaseTester(new ProfileDataSource(profile),
                    profile.getSchema());
        }
    }

    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = TesterBeanConfig.class)
    @DbUnitSpringTest
    static class ApplicationContextTesterSample
    {
        @Test
        @DbUnitPrep(ItDataSets.PREP)
        @DbUnitExpected(value = ItDataSets.EXPECTED,
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testUpdateRow_preppedRowAndTesterBean_matchesExpectedDataSet(
                @Autowired final IDatabaseTester databaseTester) throws Exception
        {
            try (Connection connection = databaseTester.getConnection().getConnection();
                    Statement statement = connection.createStatement())
            {
                statement.execute(
                        "UPDATE " + TEST_TABLE + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }
}
