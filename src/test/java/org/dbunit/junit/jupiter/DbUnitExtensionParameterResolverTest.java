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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Verifies {@link DbUnitExtension}'s {@code ParameterResolver} support for {@code @Test} and
 * {@code @BeforeEach} method parameters.
 */
class DbUnitExtensionParameterResolverTest
{
    @Test
    void testResolveParameter_beforeEachAndTestMethods_bothReceiveTheResolvedTester()
    {
        InjectionSample.injectedInBeforeEach = null;
        InjectionSample.injectedInTest = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InjectionSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(InjectionSample.injectedInBeforeEach)
                .as("A @BeforeEach parameter must receive the resolved tester.")
                .isSameAs(InjectionSample.TESTER);
        assertThat(InjectionSample.injectedInTest)
                .as("A @Test parameter must receive the resolved tester.")
                .isSameAs(InjectionSample.TESTER);
    }

    @Test
    void testResolveParameter_testerFactoryConfigured_factoryInvokedExactlyOnce()
    {
        CountingFactory.count = 0;
        CountingFactory.produced = null;
        FactoryInjectionSample.injectedInBeforeEach = null;
        FactoryInjectionSample.injectedInTest = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FactoryInjectionSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(CountingFactory.count)
                .as("A @BeforeEach parameter, beforeTestExecution's own lifecycle driving, and"
                        + " a @Test parameter must all share one resolution - the factory must"
                        + " run once per test, not once per resolution point.")
                .isEqualTo(1);
        assertThat(FactoryInjectionSample.injectedInBeforeEach)
                .as("A @BeforeEach parameter must receive the factory-produced tester.")
                .isSameAs(CountingFactory.produced);
        assertThat(FactoryInjectionSample.injectedInTest)
                .as("A @Test parameter must receive the same tester beforeTestExecution used,"
                        + " not a second, independently-constructed one.")
                .isSameAs(CountingFactory.produced);
    }

    @Test
    void testResolveParameter_testCaseFieldDeclared_beforeEachAndTestBothReceiveIt()
    {
        TestCaseInjectionSample.testCase = new DefaultPrepAndExpectedTestCase(
                new FlatXmlDataFileLoader(), new NoOpTester());
        TestCaseInjectionSample.injectedInBeforeEach = null;
        TestCaseInjectionSample.injectedInTest = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(TestCaseInjectionSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(TestCaseInjectionSample.injectedInBeforeEach)
                .as("A @BeforeEach PrepAndExpectedTestCase parameter must receive the"
                        + " @DbUnitTestCase field's instance.")
                .isSameAs(TestCaseInjectionSample.testCase);
        assertThat(TestCaseInjectionSample.injectedInTest)
                .as("A @Test PrepAndExpectedTestCase parameter must receive the same"
                        + " instance too.")
                .isSameAs(TestCaseInjectionSample.testCase);
    }

    @Test
    void testResolveParameter_testCaseParameterWithoutMarkedField_failsClearly()
    {
        final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(NoTestCaseFieldSample.class))
                .execute()
                .testEvents()
                .failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));

        final Throwable reported = failedEvent.getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported.getCause())
                .as("Injecting a PrepAndExpectedTestCase parameter with no @DbUnitTestCase"
                        + " field must fail clearly rather than injecting null.")
                .hasMessageContaining("@DbUnitTestCase");
    }

    @Test
    void testResolveParameter_connectionParameterTypes_bothReceiveTheTestersConnection()
    {
        ConnectionInjectionSample.injectedConnection = null;
        ConnectionInjectionSample.injectedJdbcConnection = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ConnectionInjectionSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(ConnectionInjectionSample.injectedConnection)
                .as("An IDatabaseConnection parameter must receive the tester's connection.")
                .isSameAs(ConnectionInjectionSample.TESTER_CONNECTION);
        assertThat(ConnectionInjectionSample.injectedJdbcConnection)
                .as("A java.sql.Connection parameter must receive that connection's own"
                        + " wrapped JDBC connection.")
                .isSameAs(ConnectionInjectionSample.JDBC_CONNECTION);
    }

    @Test
    void testResolveParameter_parameterizedTestMethod_receivesTheResolvedTester()
    {
        ParameterizedTestInjectionSample.injected = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ParameterizedTestInjectionSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(ParameterizedTestInjectionSample.injected)
                .as("A @ParameterizedTest parameter must still receive the resolved tester -"
                        + " supportsParameter()'s executable check must recognize a"
                        + " @ParameterizedTest method as the current test method.")
                .isSameAs(ParameterizedTestInjectionSample.TESTER);
    }

    @Test
    void testSupportsParameter_afterEachConnectionParameter_rejectedNotResolved()
    {
        final Event failedEvent = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AfterEachConnectionInjectionSample.class))
                .execute()
                .testEvents()
                .failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));

        final Throwable reported = failedEvent.getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported)
                .as("A Connection parameter on @AfterEach must be rejected, not resolved:"
                        + " afterTestExecution() already ran - closing this executor's"
                        + " connection, or never resolving one - by the time @AfterEach runs,"
                        + " so resolving one here would hand out an already-closed connection,"
                        + " or leak a freshly-opened one that nothing would ever close.")
                .isInstanceOf(ParameterResolutionException.class);
    }

    @Test
    void testSupportsParameter_bareExtendWithNoDbUnitAnnotation_leavesConnectionToAnotherResolver()
    {
        ForeignConnectionResolverSample.injected = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ForeignConnectionResolverSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(ForeignConnectionResolverSample.injected)
                .as("A bare @ExtendWith(DbUnitExtension.class) class with only a plain,"
                        + " unannotated IDatabaseTester field must not claim a Connection"
                        + " parameter, so a co-registered resolver takes it without a"
                        + " \"multiple competing ParameterResolvers\" clash.")
                .isSameAs(ForeignConnectionResolver.CONNECTION);
    }

    @Test
    void testSupportsParameter_injectConnectionParameterFalse_leavesConnectionToAnotherResolver()
    {
        ForeignResolverWithInjectConnectionParameterFalseSample.injected = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        ForeignResolverWithInjectConnectionParameterFalseSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(ForeignResolverWithInjectConnectionParameterFalseSample.injected)
                .as("@DbUnitConfig(injectConnectionParameter = false) on an annotation-driven"
                        + " test must yield java.sql.Connection to a co-registered resolver,"
                        + " avoiding JUnit's \"multiple competing ParameterResolvers\" failure.")
                .isSameAs(ForeignConnectionResolver.CONNECTION);
    }

    @ExtendWith(DbUnitExtension.class)
    static class InjectionSample
    {
        static final IDatabaseTester TESTER = new NoOpTester();
        static IDatabaseTester injectedInBeforeEach;
        static IDatabaseTester injectedInTest;

        // @DbUnitTester (rather than a plain field) so the class opts into parameter injection.
        @DbUnitTester
        IDatabaseTester databaseTester = TESTER;

        @BeforeEach
        void beforeEach(final IDatabaseTester tester)
        {
            injectedInBeforeEach = tester;
        }

        @Test
        void testParameterInjected(final IDatabaseTester tester)
        {
            injectedInTest = tester;
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitConfig(databaseTesterFactory = CountingFactory.class)
    static class FactoryInjectionSample
    {
        static IDatabaseTester injectedInBeforeEach;
        static IDatabaseTester injectedInTest;

        @BeforeEach
        void beforeEach(final IDatabaseTester tester)
        {
            injectedInBeforeEach = tester;
        }

        @Test
        void testParameterInjected(final IDatabaseTester tester)
        {
            injectedInTest = tester;
        }
    }

    static class CountingFactory implements DatabaseTesterFactory
    {
        static int count;
        static IDatabaseTester produced;

        @Override
        public IDatabaseTester createDatabaseTester()
        {
            count++;
            produced = new NoOpTester();
            return produced;
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class TestCaseInjectionSample
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;
        static PrepAndExpectedTestCase injectedInBeforeEach;
        static PrepAndExpectedTestCase injectedInTest;

        @BeforeEach
        void beforeEach(final PrepAndExpectedTestCase testCase)
        {
            injectedInBeforeEach = testCase;
        }

        @Test
        void testParameterInjected(final PrepAndExpectedTestCase testCase)
        {
            injectedInTest = testCase;
        }
    }

    // @DbUnitTest (an opt-in annotation) rather than a bare @ExtendWith, so parameter
    // resolution is claimed and the missing-@DbUnitTestCase-field failure comes from
    // resolveParameter() with its clear message, not from JUnit finding no resolver at all.
    @DbUnitTest
    static class NoTestCaseFieldSample
    {
        IDatabaseTester databaseTester = new NoOpTester();

        @Test
        void testParameterInjected(final PrepAndExpectedTestCase testCase)
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class ConnectionInjectionSample
    {
        static final Connection JDBC_CONNECTION = mock(Connection.class);
        static final IDatabaseConnection TESTER_CONNECTION = mockConnection();
        static IDatabaseConnection injectedConnection;
        static Connection injectedJdbcConnection;

        @DbUnitTester
        IDatabaseTester databaseTester = new NoOpTester(TESTER_CONNECTION);

        private static IDatabaseConnection mockConnection()
        {
            final IDatabaseConnection connection = mock(IDatabaseConnection.class);
            when(connection.getConfig()).thenReturn(new DatabaseConfig());
            try
            {
                when(connection.getConnection()).thenReturn(JDBC_CONNECTION);
            } catch (final SQLException e)
            {
                throw new IllegalStateException(e);
            }
            return connection;
        }

        @Test
        void testParametersInjected(final IDatabaseConnection connection,
                final Connection jdbcConnection)
        {
            injectedConnection = connection;
            injectedJdbcConnection = jdbcConnection;
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class ParameterizedTestInjectionSample
    {
        static final IDatabaseTester TESTER = new NoOpTester();
        static IDatabaseTester injected;

        @DbUnitTester
        IDatabaseTester databaseTester = TESTER;

        @ParameterizedTest
        @ValueSource(ints = {1})
        void testParameterInjected(final int value, final IDatabaseTester tester)
        {
            injected = tester;
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class AfterEachConnectionInjectionSample
    {
        // @DbUnitTester so the @AfterEach Connection parameter is declined only for the
        // @AfterEach-runs-too-late reason, not also for the class not being annotation-driven.
        @DbUnitTester
        IDatabaseTester databaseTester = new NoOpTester();

        @Test
        void testSomething()
        {
        }

        @AfterEach
        void afterEach(final Connection connection)
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ExtendWith(ForeignConnectionResolver.class)
    static class ForeignConnectionResolverSample
    {
        static Connection injected;

        IDatabaseTester databaseTester = new NoOpTester();

        @Test
        void testForeignResolverWins(final Connection connection)
        {
            injected = connection;
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @ExtendWith(ForeignConnectionResolver.class)
    @DbUnitConfig(injectConnectionParameter = false)
    static class ForeignResolverWithInjectConnectionParameterFalseSample
    {
        static Connection injected;

        @DbUnitTester
        IDatabaseTester databaseTester = new NoOpTester();

        @Test
        void testForeignResolverWins(final Connection connection)
        {
            injected = connection;
        }
    }

    static class ForeignConnectionResolver implements ParameterResolver
    {
        static final Connection CONNECTION = mock(Connection.class);

        @Override
        public boolean supportsParameter(final ParameterContext parameterContext,
                final ExtensionContext extensionContext)
        {
            return parameterContext.getParameter().getType() == Connection.class;
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext,
                final ExtensionContext extensionContext)
        {
            return CONNECTION;
        }
    }

    private static class NoOpTester implements IDatabaseTester
    {
        private final IDatabaseConnection connection;

        private NoOpTester()
        {
            this(null);
        }

        private NoOpTester(final IDatabaseConnection connection)
        {
            this.connection = connection;
        }

        @Override
        public void onSetup()
        {
        }

        @Override
        public void onTearDown()
        {
        }

        @Override
        public IDatabaseConnection getConnection()
        {
            return connection;
        }

        @Override
        public IDataSet getDataSet()
        {
            return null;
        }

        @Override
        public void setDataSet(final IDataSet dataSet)
        {
        }

        @Override
        public DatabaseOperation getSetUpOperation()
        {
            return null;
        }

        @Override
        public DatabaseOperation getTearDownOperation()
        {
            return null;
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation)
        {
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation)
        {
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener)
        {
        }

        @Override
        public IOperationListener getOperationListener()
        {
            return null;
        }

        @Override
        @Deprecated
        public void closeConnection(final IDatabaseConnection connection)
        {
        }

        @Override
        @Deprecated
        public void setSchema(final String schema)
        {
        }
    }
}
