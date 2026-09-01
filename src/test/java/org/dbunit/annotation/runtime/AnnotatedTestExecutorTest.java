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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.RowCountCheck;
import org.dbunit.database.rowcount.RowCountCheckConfiguration;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class AnnotatedTestExecutorTest
{
    @Mock
    IDatabaseTester tester;

    @Mock
    IDatabaseConnection connection;

    @Mock
    Connection jdbcConnection;

    @BeforeEach
    void resetRecordingPrepAndExpectedTestCase()
    {
        RecordingPrepAndExpectedTestCase.instance = null;
        RecordingPrepAndExpectedTestCase.lastTester = null;
        RecordingPrepAndExpectedTestCase.lastCloseConnectionAfterTest = false;
        RecordingPrepAndExpectedTestCase.throwFromConstructor = false;
    }

    // ---- setup/teardown path: setup ----

    @Test
    void testBeforeTest_setupTeardownPathWithPrepFiles_setsDataSetAndOperationThenOnSetup()
            throws Exception
    {
        stubDisabledConnection();
        final DbUnitPrep prep = WithPrep.class.getAnnotation(DbUnitPrep.class);
        final AnnotatedTestConfiguration configuration =
                AnnotatedTestConfiguration.from(WithPrep.class, null, prep, null, null, null,
                        null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(tester).setDataSet(any(IDataSet.class));
        verify(tester).setSetUpOperation(DatabaseOperation.CLEAN_INSERT);
        verify(tester).onSetup();
    }

    @Test
    void testBeforeTest_multiplePrepFilesAndCaseSensitiveTableNames_doesNotMergeDifferentlyCasedTables()
            throws Exception
    {
        when(tester.getConnection()).thenReturn(connection);
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES, true);
        when(connection.getConfig()).thenReturn(databaseConfig);
        stubOpenJdbcConnection();
        final DbUnitPrep prep =
                WithTwoDifferentlyCasedPrepFiles.class.getAnnotation(DbUnitPrep.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithTwoDifferentlyCasedPrepFiles.class, null, prep, null, null, null,
                        null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        final ArgumentCaptor<IDataSet> captor = ArgumentCaptor.forClass(IDataSet.class);
        verify(tester).setDataSet(captor.capture());
        assertThat(captor.getValue().getTableNames())
                .as("Combining multiple @DbUnitPrep files must honor the connection's"
                        + " FEATURE_CASE_SENSITIVE_TABLE_NAMES, the same feature"
                        + " DefaultPrepAndExpectedTestCase resolves for its own multi-file"
                        + " combining, instead of always combining case-insensitively and"
                        + " silently merging 'ACCOUNT' and 'account' into one table.")
                .containsExactlyInAnyOrder("ACCOUNT", "account");
    }

    @Test
    void testBeforeTest_setupTeardownPathNoPrepFiles_onlyCallsOnSetup() throws Exception
    {
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(tester, never()).setDataSet(any());
        verify(tester, never()).setSetUpOperation(any());
        verify(tester).onSetup();
    }

    // ---- setup/teardown path: teardown ----

    @Test
    void testAfterTest_tearDownDeclared_setsOperationThenOnTearDown() throws Exception
    {
        final DbUnitTearDown tearDown =
                WithTearDown.class.getAnnotation(DbUnitTearDown.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithTearDown.class, null, null, null, null, tearDown, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();
        executor.afterTest(false);

        verify(tester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_tearDownNotDeclared_runsWithAndRestoresTheBeforeEachSetTeardownOperation()
            throws Exception
    {
        // A @DbUnitTearDown-less method must run onTearDown() with the operation a @BeforeEach
        // method set (not a forced NONE - issue 945), then restore it so a shared tester is
        // left exactly as it was found.
        final RecordingAbstractDatabaseTester sharedTester = new RecordingAbstractDatabaseTester();
        sharedTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, sharedTester, null);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(sharedTester.tearDownOperationsRun)
                .as("onTearDown() runs with the @BeforeEach-set DELETE_ALL, not a forced NONE.")
                .containsExactly(DatabaseOperation.DELETE_ALL);
        assertThat(sharedTester.getTearDownOperation())
                .as("The tester is left with the operation it came in with.")
                .isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testAfterTest_sharedTesterAcrossMethods_anEarlierMethodsTearDownOperationDoesNotBleedOver()
            throws Exception
    {
        // Two methods share one tester (a static @DbUnitTester field, or @TestInstance(PER_CLASS)).
        // Method A declares @DbUnitTearDown(DELETE_ALL); method B declares none. B must run
        // onTearDown() with the tester's baseline NONE, not A's leftover DELETE_ALL.
        final RecordingAbstractDatabaseTester sharedTester = new RecordingAbstractDatabaseTester();
        final DbUnitTearDown tearDown = WithTearDown.class.getAnnotation(DbUnitTearDown.class);

        final AnnotatedTestExecutor methodA = new AnnotatedTestExecutor(
                AnnotatedTestConfiguration.from(WithTearDown.class, null, null, null, null,
                        tearDown, null),
                sharedTester, null);
        methodA.beforeTest();
        methodA.afterTest(false);

        assertThat(sharedTester.getTearDownOperation())
                .as("Method A's @DbUnitTearDown is undone after A, not left on the shared tester.")
                .isEqualTo(DatabaseOperation.NONE);

        final AnnotatedTestExecutor methodB = new AnnotatedTestExecutor(
                AnnotatedTestConfiguration.from(AnnotatedTestExecutorTest.class, null, null, null,
                        null, null, null),
                sharedTester, null);
        methodB.beforeTest();
        methodB.afterTest(false);

        assertThat(sharedTester.tearDownOperationsRun)
                .as("A ran DELETE_ALL (its own annotation); B ran NONE (the tester baseline),"
                        + " not A's leftover DELETE_ALL.")
                .containsExactly(DatabaseOperation.DELETE_ALL, DatabaseOperation.NONE);
    }

    @Test
    void testAfterTest_tearDownNonNoneWithNoPrepAndNoDataset_warnsBeforeOnTearDown()
            throws Exception
    {
        // Mirrors the setup-side warnIfNonNoneSetupOperationHasNoDataset(): a non-NONE
        // @DbUnitTearDown operation with no @DbUnitPrep files and no dataset on the tester
        // would run against a null dataset - a built-in tester NPEs deep inside the operation
        // with no hint - so warn, naming the fixes, rather than fail silently.
        final DbUnitTearDown tearDown = WithTearDown.class.getAnnotation(DbUnitTearDown.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithTearDown.class, null, null, null, null, tearDown, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.afterTest(false);

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("@DbUnitTearDown(operation = DELETE_ALL) with no @DbUnitPrep and no"
                            + " dataset on the tester must warn before onTearDown() - not fail"
                            + " silently or NPE with no hint - mirroring the setup-side"
                            + " warning.")
                    .hasSize(1)
                    .allSatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("@DbUnitTearDown").contains("onTearDown()")
                            .contains("NONE"));
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testAfterTest_bareTearDownNoneWithNoDataset_doesNotWarn() throws Exception
    {
        final DbUnitTearDown tearDown =
                WithBareTearDown.class.getAnnotation(DbUnitTearDown.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithBareTearDown.class, null, null, null, null, tearDown, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.afterTest(false);

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A bare @DbUnitTearDown resolves to NONE, which needs no dataset, so"
                            + " this must not warn.")
                    .isEmpty();
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testAfterTest_beforeTestThrewBeforeOnSetup_doesNotRunOnTearDown() throws Exception
    {
        // beforeTest() fails before onSetup() runs - here, resolving the row count baseline
        // connection throws. afterTest() must not then call onTearDown(): it would act on state
        // onSetup() never established (a declared teardown operation against a null dataset,
        // say) and throw a second exception masking the real failure. Mirrors afterExpectedTest()'s
        // guard on the prep/expected path.
        when(tester.getConnection()).thenThrow(new SQLException("cannot connect"));
        final DbUnitTearDown tearDown = WithTearDown.class.getAnnotation(DbUnitTearDown.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithTearDown.class, null, null, null, null, tearDown, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatThrownBy(executor::beforeTest).isInstanceOf(SQLException.class);
        executor.afterTest(true);

        verify(tester, never()).onTearDown();
    }

    // ---- row count check wiring (base tiers; the annotation tier is WI-04) ----

    @Test
    void testBeforeTest_rowCountCheckEnabled_capturesBaselineWithoutClosingConnection()
            throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection).createDataSet();
        verify(connection, never()).close();
        verify(tester).onSetup();
    }

    @Test
    void testBeforeTest_rowCountCheckDisabled_neverQueriesTheConnection() throws Exception
    {
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).createDataSet();
    }

    @Test
    void testAfterTest_testFailed_skipsRowCountVerification() throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(true);

        verify(connection, times(1)).createDataSet();
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_testPassed_verifiesRowCountAgainstBaseline() throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, times(2)).createDataSet();
        verify(tester, times(1)).getConnection();
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_expectedPathPostTestThrowsError_stillClosesConnectionAndPreservesErrorType()
            throws Exception
    {
        // A comparison mismatch fails via DbComparisonFailure, an Error subclass, not an
        // Exception - catching only Exception would skip closing the connection on every
        // ordinary verification failure, the single most common way this step throws at all.
        stubOpenJdbcConnection();
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase mockTestCase = mock(PrepAndExpectedTestCase.class);
        when(mockTestCase.getReusableConnection()).thenReturn(connection);
        final AssertionError verificationFailure = new AssertionError("expected mismatch");
        doThrow(verificationFailure).when(mockTestCase).postTest(true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);
        // Runs configureTest() so afterTest() actually calls postTest() below, instead of
        // skipping it the way it must for a test case whose configureTest() never ran.
        executor.beforeTest();
        // Memoizes a connection the way a Connection/IDatabaseConnection parameter injection
        // would, so there is something for afterTest() to close.
        executor.getConnection();

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("An Error - e.g. a comparison mismatch via DbComparisonFailure - must"
                        + " propagate with its original type, not be swallowed or wrapped in"
                        + " an Exception.")
                .isSameAs(verificationFailure);
        verify(connection).close();
    }

    @Test
    void testAfterTest_verifyThrowsAndClosingConnectionAlsoThrows_reportsVerifyFailureWithCloseFailureSuppressed()
            throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(6);
        final SQLException closeFailure = new SQLException("connection lost");
        when(jdbcConnection.isClosed()).thenThrow(closeFailure);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The row count mismatch must be reported as the primary failure, not"
                        + " silently replaced by a later failure closing the connection - a"
                        + " plain finally block would let the SQLException from"
                        + " closeResolvedConnectionIfOwned() do exactly that.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(thrown.getSuppressed())
                .as("The failure closing the connection must still be visible, attached as"
                        + " suppressed onto the primary failure rather than discarded.")
                .containsExactly(closeFailure);
    }

    @Test
    void testBeforeTest_realTesterWithFixedConnection_neverClosesConnectionDuringOnSetup()
            throws Exception
    {
        // DefaultDatabaseTester(connection) returns this same connection from every
        // getConnection() call, including the one onSetup() makes right after the row
        // count check captures its baseline; NONE + a non-closing listener isolates that
        // onSetup() call so any close() interaction can only have come from the check itself.
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(databaseConfig);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final IDatabaseTester fixedConnectionTester = new DefaultDatabaseTester(connection);
        fixedConnectionTester.setSetUpOperation(DatabaseOperation.NONE);
        fixedConnectionTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, fixedConnectionTester, null);

        executor.beforeTest();

        verify(connection, never()).close();
    }

    @Test
    void testConstructor_installedListenerNotifiedAboutTheMemoizedConnection_doesNotCloseIt()
            throws Exception
    {
        // Simulates what AbstractDatabaseTester.executeOperation() triggers internally during a
        // real onSetup(), for a tester with no listener of its own - so it would otherwise
        // default to a plain, closing DefaultOperationListener - and a non-NONE setup
        // operation: without this executor's protection, the connection already memoized here
        // for the row count check / parameter injection would be closed the instant setup
        // finished, long before afterTest() ever runs.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        executor.beforeTest();

        captor.getValue().operationSetUpFinished(connection);

        verify(connection, never()).close();
    }

    @Test
    void testConstructor_installedListenerNotifiedAboutADifferentConnection_stillClosesIt()
            throws Exception
    {
        // Guards the fix above against becoming a blanket suppression: a tester that hands out
        // a fresh connection per call (e.g. a plain JdbcDatabaseTester) must still have that
        // connection closed normally - only this executor's own memoized connection (never
        // resolved in this test) is protected.
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        final IDatabaseConnection otherConnection = mock(IDatabaseConnection.class);

        captor.getValue().operationSetUpFinished(otherConnection);

        verify(otherConnection).close();
    }

    @Test
    void testConstructor_installedListenerNotifiedAboutTheMemoizedConnectionViaTearDown_doesNotCloseIt()
            throws Exception
    {
        // The teardown-side twin of
        // testConstructor_installedListenerNotifiedAboutTheMemoizedConnection_doesNotCloseIt:
        // a tester's onTearDown() notifies the same listener, and it must protect this
        // executor's memoized connection there too, so afterTest()'s own close is the only one
        // that ever touches it.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        executor.beforeTest();

        captor.getValue().operationTearDownFinished(connection);

        verify(connection, never()).close();
    }

    @Test
    void testConstructor_installedListenerNotifiedAboutADifferentConnectionViaTearDown_stillClosesIt()
            throws Exception
    {
        // The teardown-side twin of
        // testConstructor_installedListenerNotifiedAboutADifferentConnection_stillClosesIt:
        // a tester that hands out a fresh connection per call must still have that one closed
        // on teardown - only this executor's own memoized connection (never resolved here) is
        // protected.
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        final IDatabaseConnection otherConnection = mock(IDatabaseConnection.class);

        captor.getValue().operationTearDownFinished(otherConnection);

        verify(otherConnection).close();
    }

    @Test
    void testConstructor_baselineCaptureInsideTheListenerCallbackFails_wrapsItInADatabaseUnitRuntimeException()
            throws Exception
    {
        // captureRowCountBaselineOnFirstConnectionRetrieved() runs from
        // ExecutorOperationListener.connectionRetrieved(), which declares no checked exception,
        // so a DatabaseUnitException/SQLException from the baseline capture there must surface
        // as an unchecked DatabaseUnitRuntimeException rather than be swallowed.
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(databaseConfig);
        when(connection.createDataSet())
                .thenThrow(new SQLException("connection lost mid-capture"));
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());

        assertThatThrownBy(() -> captor.getValue().connectionRetrieved(connection))
                .as("A baseline-capture failure inside the listener callback must be wrapped,"
                        + " not swallowed.")
                .isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void testBeforeTest_testerConnectionNull_doesNotThrowAndStillRunsOnSetup() throws Exception
    {
        when(tester.getConnection()).thenReturn(null);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatCode(() -> executor.beforeTest())
                .as("A tester with no connection to inspect (e.g. a test double) must be"
                        + " tolerated, not throw a NullPointerException.")
                .doesNotThrowAnyException();
        verify(tester).onSetup();
    }

    @Test
    void testAfterTest_rowCountCheckDisabled_neverAcquiresASecondConnection() throws Exception
    {
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(tester, times(1)).getConnection();
    }

    @Test
    void testAfterTest_connectionResolved_closesItByDefault() throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection).close();
    }

    @Test
    void testAfterTest_connectionAlreadyClosed_doesNotCloseItAgain() throws Exception
    {
        // Reproduces DefaultPrepAndExpectedTestCase's own cleanupData() independently closing
        // the connection this executor's own getConnection() also memoized - e.g. for an
        // injected IDatabaseConnection/Connection parameter - before afterTest()'s finally block
        // runs. isClosed() true here stands in for that prior close, without needing a real
        // DefaultPrepAndExpectedTestCase to exercise its own connection handling. Not
        // @DbUnitRowCountCheck-related - applyRowCountCheckOverride() needs no connection of
        // its own since the DefaultPrepAndExpectedTestCase pass-through fix, so a plain
        // @DbUnitExpected test double is enough to reproduce this.
        stubOpenJdbcConnection();
        when(jdbcConnection.isClosed()).thenReturn(true);
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = mock(DefaultPrepAndExpectedTestCase.class);
        when(injected.getReusableConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_closeConnectionAfterTestFalse_leavesConnectionOpen() throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final DbUnitConfig config =
                WithCloseConnectionAfterTestFalse.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithCloseConnectionAfterTestFalse.class, config, null, null, null, null,
                        null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_listenerIsNoOp_leavesConnectionOpenEvenByDefault() throws Exception
    {
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_listenerIsNoOpWrappedByPropertyListener_leavesConnectionOpen()
            throws Exception
    {
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> installedListener =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(installedListener.capture());
        when(tester.getOperationListener()).thenReturn(installedListener.getValue());
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    // ---- @DbUnitRowCountCheck precedence ----

    @Test
    void testAfterTest_rowCountCheckWithPrepAndNoTeardown_exceptionNamesTheTeardownFix()
            throws Exception
    {
        // The classic first surprise: the baseline is captured before @DbUnitPrep loads, so a
        // prep dataset that is never torn down reads as leaked rows and fails the check. The
        // exception must name the fix - @DbUnitTearDown(DELETE_ALL) or an exclude - not just
        // the per-table delta.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(0);
        when(tester.getTearDownOperation()).thenReturn(DatabaseOperation.NONE);
        final DbUnitPrep prep = WithPrepAndRowCountCheck.class.getAnnotation(DbUnitPrep.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithPrepAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPrepAndRowCountCheck.class, null, prep, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The mismatch must still be an UnexpectedRowCountException, so callers"
                        + " asserting on the type keep working.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(thrown.getMessage())
                .as("The message must name the teardown fix and explain the before-prep"
                        + " baseline, on top of the standard per-table detail.")
                .contains("baseline is captured before @DbUnitPrep loads")
                .contains("@DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)")
                .contains("ACCOUNT");
    }

    @Test
    void testAfterTest_rowCountCheckWithPrepButTeardownDeletes_exceptionHasNoTeardownHint()
            throws Exception
    {
        // The hint is gated on the teardown operation that actually ran being NONE: with
        // DELETE_ALL declared, an unrelated count mismatch must not be misattributed to
        // un-torn-down prep.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(0);
        when(tester.getTearDownOperation()).thenReturn(DatabaseOperation.DELETE_ALL);
        final DbUnitPrep prep = WithPrepAndRowCountCheck.class.getAnnotation(DbUnitPrep.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithPrepAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPrepAndRowCountCheck.class, null, prep, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown).isInstanceOf(UnexpectedRowCountException.class);
        assertThat(thrown.getMessage())
                .as("No teardown hint when a non-NONE teardown operation ran.")
                .doesNotContain("@DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)");
    }

    @Test
    void testBeforeTest_systemPropertyFalseAndAnnotationPresent_disablesCheck()
            throws Exception
    {
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, "false");
        stubDisabledConnection();
        final DbUnitRowCountCheck rowCountCheck =
                WithBareRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                AnnotatedTestExecutorTest.class, null, null, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).createDataSet();
    }

    @Test
    void testBeforeTest_systemPropertyTrueAndNoAnnotation_enablesCheck() throws Exception
    {
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, "true");
        stubConnection(false, "ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection).createDataSet();
    }

    @Test
    void testBeforeTest_annotationPresentAndFeatureFalse_enablesCheck() throws Exception
    {
        stubConnection(false, "ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final DbUnitRowCountCheck rowCountCheck =
                WithBareRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                AnnotatedTestExecutorTest.class, null, null, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection).createDataSet();
    }

    @Test
    void testBeforeTest_rowCountCheckExcludePatterns_excludedTableNeverQueried()
            throws Exception
    {
        stubConnection(false, "ACCOUNT", "IGNORED_TABLE");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final DbUnitRowCountCheck rowCountCheck =
                WithExcludedTable.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                AnnotatedTestExecutorTest.class, null, null, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).getRowCount("IGNORED_TABLE");
        verify(connection).getRowCount("ACCOUNT");
    }

    // ---- expected (prep/expected) path ----

    @Test
    void testBeforeTest_expectedPathWithInjectedTestCase_usesInjectedInstance()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        executor.beforeTest();

        verify(injected).configureTest(configuration.getVerifyTableDefinitions(),
                configuration.getPrepDataFiles(), configuration.getExpectedDataFiles());
        verify(injected).preTest();
    }

    @Test
    void testBeforeTest_expectedPathWithoutInjectedTestCase_constructsConfiguredClass()
            throws Exception
    {
        final DbUnitConfig config = WithRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithRecordingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithRecordingTestCase.class, config, null, null, expected, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        assertThat(RecordingPrepAndExpectedTestCase.lastTester)
                .as("The configured class must be constructed with this executor's tester.")
                .isSameAs(tester);
        assertThat(RecordingPrepAndExpectedTestCase.lastCloseConnectionAfterTest)
                .as("closeConnectionAfterTest must be forwarded from the configuration.")
                .isTrue();
        assertThat(RecordingPrepAndExpectedTestCase.instance.preTestCalled)
                .as("beforeTest() must invoke preTest() on the constructed test case.")
                .isTrue();
    }

    @Test
    void testBeforeTest_configuredClassConstructorThrows_surfacesCauseNotInvocationTargetException()
            throws Exception
    {
        final DbUnitConfig config = WithRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithRecordingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithRecordingTestCase.class, config, null, null, expected, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        RecordingPrepAndExpectedTestCase.throwFromConstructor = true;

        assertThatThrownBy(executor::beforeTest)
                .as("A throwing DbUnitConfig.prepAndExpectedTestCase constructor must surface"
                        + " as an IllegalStateException naming the class, with the real"
                        + " failure as its cause - not a raw reflective"
                        + " InvocationTargetException, which every other reflective"
                        + " instantiation in this feature already avoids via"
                        + " ReflectiveInstantiation.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RecordingPrepAndExpectedTestCase.class.getName())
                .cause()
                .hasMessage("simulated constructor failure");
    }

    @Test
    void testBeforeTest_configuredClassHasNoMatchingConstructor_throwsIllegalStateExceptionNamingClass()
            throws Exception
    {
        final DbUnitConfig config =
                WithNoMatchingConstructorTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithNoMatchingConstructorTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithNoMatchingConstructorTestCase.class, config, null, null, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatThrownBy(executor::beforeTest)
                .as("DbUnitConfig.prepAndExpectedTestCase naming a class with no"
                        + " (DataFileLoader, IDatabaseTester, boolean) constructor must be"
                        + " rejected with a clear message naming the class, not a raw"
                        + " NoSuchMethodException.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NoMatchingConstructorTestCase.class.getName())
                .hasMessageContaining("no (DataFileLoader, IDatabaseTester, boolean)"
                        + " constructor");
    }

    @Test
    void testAfterTest_expectedPathBeforeTestNeverRan_doesNotThrow() throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatCode(() -> executor.afterTest(true))
                .as("afterTest() must tolerate a PrepAndExpectedTestCase that was never"
                        + " constructed - e.g. beforeTest() never ran, or failed before"
                        + " constructing one - rather than throwing a NullPointerException"
                        + " deep inside postTest().")
                .doesNotThrowAnyException();
    }

    @Test
    void testAfterTest_expectedPathBeforeTestFailsAfterInjectingTestCaseButBeforeConfigureTest_postTestNotCalled()
            throws Exception
    {
        final DbUnitConfig config =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithFailureHandlerAndExpected.class, config, null, null, expected, null,
                        null);
        final RecordingPrepAndExpectedTestCase reusedTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, reusedTestCase);
        assertThatThrownBy(executor::beforeTest).isInstanceOf(IllegalStateException.class);

        executor.afterTest(true);

        assertThat(reusedTestCase.postTestCalled)
                .as("postTest() must not run against a test case whose configureTest() never"
                        + " completed this test - here, applyFailureHandler() failed fast"
                        + " first. A test case reused across several tests (e.g. a"
                        + " @DbUnitTestCase static field) would otherwise run cleanup against"
                        + " an earlier test's stale prep/expected state instead of nothing.")
                .isFalse();
    }

    @Test
    void testAfterTest_expectedPathInjectedTestCaseFailsBeforeConfigureTest_leavesItsCachedConnectionOpen()
            throws Exception
    {
        // A3: an injected @DbUnitTestCase instance caching getReusableConnection() across test
        // methods (a static field, or any field under @TestInstance(PER_CLASS)) must NOT have
        // that connection closed when beforeExpectedTest() fails fast before configureTest() -
        // a @BeforeEach parameter resolved it, postTest()/cleanupData() never runs to close it
        // the instance's own way, and closing it here would strand the instance holding a
        // closed connection for the next test method.
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        final CachingConnectionPrepAndExpectedTestCase injected =
                new CachingConnectionPrepAndExpectedTestCase(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();

        assertThatThrownBy(executor::beforeTest)
                .as("@DbUnitProperty on a test case not overriding setDatabaseConfigProperties()"
                        + " must fail fast before configureTest().")
                .isInstanceOf(IllegalStateException.class);
        executor.afterTest(true);

        verify(connection, never()).close();
        assertThat(injected.configureTestCalled)
                .as("configureTest() must not have run.")
                .isFalse();
    }

    @Test
    void testAfterTest_constructedTestCaseFailsBeforeConfigureTest_stillClosesItsConnection()
            throws Exception
    {
        // The mirror of the case above: a test case this executor constructed itself (via
        // @DbUnitConfig.prepAndExpectedTestCase, no @DbUnitTestCase field) is single-use and
        // discarded after this test, so its connection is still closed on an early failure
        // rather than leaked.
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final DbUnitConfig config = WithConstructedCachingTestCase.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected = WithConstructedCachingTestCase.class
                .getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithConstructedCachingTestCase.class, config, null, null, expected, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.getConnection();

        assertThatThrownBy(executor::beforeTest)
                .isInstanceOf(IllegalStateException.class);
        executor.afterTest(true);

        verify(connection).close();
    }

    @Test
    void testAfterTest_expectedPathTestPassed_postTestCalledWithTrue() throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.beforeTest();

        executor.afterTest(false);

        verify(injected).postTest(true);
    }

    @Test
    void testAfterTest_expectedPathTestFailed_postTestCalledWithFalse() throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.beforeTest();

        executor.afterTest(true);

        verify(injected).postTest(false);
    }

    @Test
    void testGetConnection_expectedPathWithInjectedTestCase_returnsItsConnectionNotTesters()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final IDatabaseConnection testCaseConnection = mock(IDatabaseConnection.class);
        when(injected.getReusableConnection()).thenReturn(testCaseConnection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        final IDatabaseConnection resolved = executor.getConnection();

        assertThat(resolved)
                .as("On the expected path, getConnection() must return the injected test"
                        + " case's own connection instead of resolving a second, independent"
                        + " one from the tester directly - otherwise a Connection/"
                        + "IDatabaseConnection parameter injection would not see the same"
                        + " connection the test case's own prep/verify/cleanup steps use.")
                .isSameAs(testCaseConnection);
        verify(tester, never()).getConnection();
    }

    @Test
    void testConstructor_expectedPathListenerNotifiedDirectly_getConnectionStillReturnsTestCasesConnection()
            throws Exception
    {
        // Simulates a PrepAndExpectedTestCase implementation that - unlike
        // DefaultPrepAndExpectedTestCase - calls tester.onSetup()/onTearDown() directly instead
        // of driving its own internally-built tester with its own, separate listener; that
        // would notify this executor's installed listener with a connection from
        // tester.getConnection() directly, a different object than getReusableConnection()
        // returns.
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final IDatabaseConnection testCaseConnection = mock(IDatabaseConnection.class);
        when(injected.getReusableConnection()).thenReturn(testCaseConnection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        final ArgumentCaptor<IOperationListener> installedListener =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(installedListener.capture());

        installedListener.getValue().connectionRetrieved(connection);
        final IDatabaseConnection resolved = executor.getConnection();

        assertThat(resolved)
                .as("Even when this executor's own listener is notified directly on the"
                        + " expected path, getConnection() must still return the injected test"
                        + " case's own connection, not the one the listener saw - otherwise a"
                        + " parameter injection would silently receive a different physical"
                        + " connection than the test case's own steps use, for any"
                        + " PrepAndExpectedTestCase implementation that triggers the listener"
                        + " this way.")
                .isSameAs(testCaseConnection);
    }

    @Test
    void testGetConnection_expectedPathNoInjectedTestCase_constructsOneAndReusesItsConnection()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        when(tester.getConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        final IDatabaseConnection resolved = executor.getConnection();

        assertThat(executor.getPrepAndExpectedTestCase())
                .as("getConnection() on the expected path must construct prepAndExpectedTestCase"
                        + " eagerly - e.g. for an early @BeforeEach parameter - rather than"
                        + " leaving nothing to delegate the connection to.")
                .isNotNull();
        assertThat(resolved)
                .as("getConnection() must resolve to the tester's connection when constructing"
                        + " prepAndExpectedTestCase eagerly.")
                .isSameAs(connection);
        verify(tester, times(1)).getConnection();
    }

    // ---- @DbUnitProperty listener installation ----

    @Test
    void testConstructor_propertiesConfigured_installsOperationListener()
    {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_noPropertiesConfigured_stillInstallsOperationListenerToProtectMemoizedConnection()
    {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_propertiesConfiguredWithExistingListener_wrapsExistingListenerAsDelegate()
            throws Exception
    {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);

        new AnnotatedTestExecutor(configuration, tester, null);

        final ArgumentCaptor<IOperationListener> installedListener =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(installedListener.capture());
        installedListener.getValue().operationSetUpFinished(connection);
        verify(connection, never()).close();
    }

    // ---- classic path (annotationDriven = false): the 3.5.0 lifecycle, preserved ----

    @Test
    void testConstructor_classicPath_doesNotInstallOperationListener()
    {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null, false);

        verify(tester, never()).setOperationListener(any());
    }

    @Test
    void testConstructor_classicPathTesterCarriesCustomListener_leavesItInPlace()
    {
        // The zero-annotation @ExtendWith(DbUnitExtension.class) style must not replace a custom
        // IOperationListener the test author configured on the tester - a from-scratch
        // IDatabaseTester predating IDatabaseTester#getOperationListener() would otherwise have
        // its real listener silently swapped for a fresh DefaultOperationListener the executor
        // cannot even see to wrap.
        final IOperationListener custom = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(custom);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, realTester, null, false);

        assertThat(realTester.getOperationListener())
                .as("The classic path must leave the tester's own IOperationListener exactly as"
                        + " configured, neither wrapping nor replacing it.")
                .isSameAs(custom);
    }

    @Test
    void testBeforeTest_classicPathRowCountDisabled_doesNotCloseTheTesterConnectionBeforeOnSetup()
            throws Exception
    {
        // A fixed-connection tester (e.g. DefaultDatabaseTester) hands the same connection to
        // onSetup(). An earlier revision closed the eagerly-resolved baseline connection before
        // onSetup() to free a bounded pool - which ran onSetup() against a closed connection.
        // The classic path now holds that connection until afterTest(), honoring the 3.5.0
        // contract that the extension never closes a tester's connection early.
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);

        executor.beforeTest();

        verify(connection, never()).close();
        verify(tester).onSetup();

        executor.afterTest(false);
        verify(connection).close();
    }

    @Test
    void testBeforeTest_classicPathRealAbstractDatabaseTester_capturesBaselineEagerlyNotViaPiggyback()
            throws Exception
    {
        // canPiggybackRowCountBaseline() is gated on annotationDriven: with no listener
        // installed there is nothing to piggyback on, so the classic path always resolves its
        // own connection for the baseline and lets onSetup() resolve its own - the same
        // two-connection shape DbUnitExtension had before this executor existed, not the
        // annotation paths' piggybacked one.
        final AtomicInteger getConnectionCalls = new AtomicInteger();
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                getConnectionCalls.incrementAndGet();
                return InMemoryDatabaseConnection.create();
            }
        };
        realTester.setDataSet(new DefaultDataSet());
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        assertThat(realTester.getOperationListener())
                .as("The classic path must not install an operation listener at construction.")
                .isNull();

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(getConnectionCalls.get())
                .as("The classic path resolves its own connection for the baseline and lets"
                        + " onSetup() resolve its own - two, matching the 3.5.0 lifecycle, not"
                        + " the annotation paths' single piggybacked one.")
                .isEqualTo(2);
    }

    @Test
    void testAfterTest_classicPathRowCountEnabledViaConnectionConfig_stillDetectsALeak()
            throws Exception
    {
        // The issue #939 row count check still works on the classic path: enabled via the
        // connection's own DatabaseConfig (no @DbUnitRowCountCheck), the baseline is captured
        // before the test and verified after, failing when a count moved - exactly as
        // DbUnitExtension did in 3.5.0, just without leaking the connection it used.
        stubConnection(true, "ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(6);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("A leaked row must still fail a classic-path test whose connection config"
                        + " enables the row count check.")
                .isInstanceOf(UnexpectedRowCountException.class);
    }

    // ---- ExecutorOperationListener: delegate ordering ----

    @Test
    void testConnectionRetrieved_delegateConfiguresDatabaseConfig_rowCountCheckSeesTheChange()
            throws Exception
    {
        // The tester's own, pre-existing IOperationListener is the documented place to
        // configure a connection's DatabaseConfig - e.g. to enable the row count check, or set
        // a custom RowCounter - inside its own connectionRetrieved(). That must run before this
        // executor reads the same DatabaseConfig for its own purposes (here, the row count
        // check's baseline capture), not after, or the delegate's configuration arrives too
        // late to matter: the check would still see the config as it was before the delegate
        // ever ran.
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(connection.getConfig()).thenReturn(realConfig);
        when(connection.createDataSet()).thenReturn(new DefaultDataSet());
        final IOperationListener delegateListener = new IOperationListener()
        {
            @Override
            public void connectionRetrieved(final IDatabaseConnection retrieved)
            {
                realConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
            }

            @Override
            public void operationSetUpFinished(final IDatabaseConnection finished)
            {
            }

            @Override
            public void operationTearDownFinished(final IDatabaseConnection finished)
            {
            }
        };
        when(tester.getOperationListener()).thenReturn(delegateListener);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null);
        final ArgumentCaptor<IOperationListener> installedListener =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(installedListener.capture());

        installedListener.getValue().connectionRetrieved(connection);

        verify(connection).createDataSet();
    }

    // ---- @DbUnitProperty timing ----

    @Test
    void testGetConnection_propertiesConfigured_appliesThemBeforeReturningConnection()
            throws Exception
    {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.getConnection();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("@DbUnitProperty values must be applied as soon as the connection is"
                        + " resolved - e.g. before the row count check captures its baseline -"
                        + " not only later, whenever onSetup()/onTearDown() happens to run.")
                .isEqualTo(50);
    }

    @Test
    void testBeforeTest_bothOperationsNoneAndPropertiesConfigured_stillAppliesPropertiesToTheConnection()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitConfig.class);
        final DbUnitSetup setup =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitSetup.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndSetupNone.class, config, null, setup, null, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("@DbUnitProperty values must reach the connection even when both the setup"
                        + " and teardown operations resolve to NONE - a connection is always"
                        + " resolved once per setup/teardown-path test for the row count check baseline,"
                        + " regardless of the configured operations, so there is no"
                        + " configuration under which properties silently fail to apply.")
                .isEqualTo(50);
    }

    @Test
    void testBeforeTest_propertiesConfiguredWithNoneSetupAndTesterConnectionNull_doesNotThrow()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitConfig.class);
        final DbUnitSetup setup =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitSetup.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndSetupNone.class, config, null, setup, null, null, null);
        when(tester.getConnection()).thenReturn(null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatCode(() ->
        {
            executor.beforeTest();
            executor.afterTest(false);
        }).as("@DbUnitProperty values, a NONE setup operation, and a tester with no connection"
                + " (a test double) together must be tolerated - the properties reach nothing,"
                + " without a NullPointerException.")
                .doesNotThrowAnyException();
        verify(tester).onSetup();
    }

    @Test
    void testGetConnection_invalidPropertyValue_throwsIllegalStateExceptionNotRawDatabaseUnitException()
            throws Exception
    {
        final DbUnitConfig config = WithInvalidProperty.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithInvalidProperty.class, config, null, null, null, null, null);
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatThrownBy(executor::getConnection)
                .as("An invalid @DbUnitProperty value applied on the setup/teardown path - via"
                        + " getConnection()/the installed listener, not"
                        + " DefaultPrepAndExpectedTestCase's own setUpDatabaseConfig() - must"
                        + " be reported clearly, not left as a raw DatabaseUnitException"
                        + " surfacing from deep inside DatabaseConfig.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitProperty");
    }

    @Test
    void testConstructor_secondExecutorSharesTesterAlreadyWrappingProperties_appliesPropertiesOnlyOnce()
            throws Exception
    {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        final DatabaseConfig realConfig = spy(new DatabaseConfig());
        when(connection.getConfig()).thenReturn(realConfig);
        final IOperationListener originalListener = mock(IOperationListener.class);
        when(tester.getOperationListener()).thenReturn(originalListener);
        final ArgumentCaptor<IOperationListener> listenerCaptor =
                ArgumentCaptor.forClass(IOperationListener.class);

        new AnnotatedTestExecutor(configuration, tester, null);
        verify(tester).setOperationListener(listenerCaptor.capture());
        when(tester.getOperationListener()).thenReturn(listenerCaptor.getValue());

        new AnnotatedTestExecutor(configuration, tester, null);
        verify(tester, times(2)).setOperationListener(listenerCaptor.capture());

        listenerCaptor.getValue().connectionRetrieved(connection);

        // The second executor's wrapper must delegate straight through to the tester's
        // original listener instead of nesting on top of the first executor's wrapper, so a
        // shared tester does not grow one listener layer - and one redundant property
        // application - per test.
        verify(realConfig, times(1)).setPropertiesByString(any());
        verify(originalListener).connectionRetrieved(connection);
    }

    @Test
    void testBeforeTest_expectedPathWithProperties_appliesPropertiesToInjectedTestCase()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        // A DefaultPrepAndExpectedTestCase mock, not a bare interface mock: Mockito's default
        // answer calls the interface's own no-op default methods (setDatabaseConfigProperties()
        // among them) rather than overriding them, so a bare PrepAndExpectedTestCase mock is
        // indistinguishable from a genuinely non-overriding implementation - see
        // applyDatabaseConfigProperties()'s IllegalStateException tests below for that case.
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        final ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        verify(mockTestCase).setDatabaseConfigProperties(captor.capture());
        assertThat(captor.getValue().getProperty("batchSize"))
                .as("@DbUnitProperty values must reach the prep/expected path's injected"
                        + " PrepAndExpectedTestCase - the setup/teardown path's"
                        + " installOperationListener() is never triggered by"
                        + " setupData()/verifyData()/cleanupData().")
                .isEqualTo("50");
    }

    @Test
    void testBeforeTest_expectedPathPropertiesConfiguredAndTestCaseDoesNotOverrideSetter_throwsIllegalStateException()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatThrownBy(executor::beforeTest)
                .as("@DbUnitProperty values the injected test case cannot apply must fail"
                        + " loudly, since the prep/expected path has no other route to the"
                        + " connection at all - a silent no-op here would leave them applied"
                        + " nowhere.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitProperty")
                .hasMessageContaining(RecordingPrepAndExpectedTestCase.class.getName());
    }

    @Test
    void testBeforeTest_expectedPathSubclassOverridesSetUpDatabaseConfig_warnsPropertiesMayNotApply()
            throws Exception
    {
        // A DefaultPrepAndExpectedTestCase subclass overriding setUpDatabaseConfig() - the
        // pre-3.6.0 way to configure a DatabaseConfig - drops @DbUnitProperty values unless the
        // override calls super.setUpDatabaseConfig(config), since that hook is where they are
        // applied. Reflection cannot see whether super is called, so warn rather than fail.
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        final PrepAndExpectedTestCase overriding =
                new OverridesSetUpDatabaseConfigTestCase(tester);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, overriding);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A DefaultPrepAndExpectedTestCase subclass overriding"
                            + " setUpDatabaseConfig() alongside @DbUnitProperty must be warned"
                            + " that the values apply only if the override calls super.")
                    .hasSize(1)
                    .allSatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("setUpDatabaseConfig").contains("super"));
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_expectedPathDefaultTestCaseWithProperties_doesNotWarnAboutSetUpDatabaseConfig()
            throws Exception
    {
        // A plain DefaultPrepAndExpectedTestCase (no setUpDatabaseConfig override) applies
        // @DbUnitProperty values through its own setUpDatabaseConfig - no trap, no warning.
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase plain = mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, plain);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A plain DefaultPrepAndExpectedTestCase must not trip the"
                            + " setUpDatabaseConfig-override warning.")
                    .isEmpty();
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_expectedPathNoPropertiesAndTestCaseDoesNotOverrideSetter_doesNotThrow()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatCode(executor::beforeTest)
                .as("No @DbUnitProperty values configured means nothing for a non-overriding"
                        + " test case to fail to apply.")
                .doesNotThrowAnyException();
    }

    @Test
    void testBeforeTest_expectedPathWithFailureHandler_appliesToInjectedTestCase()
            throws Exception
    {
        final DbUnitConfig config =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithFailureHandlerAndExpected.class, config, null, null, expected, null, null);
        // A DefaultPrepAndExpectedTestCase mock, not a bare interface mock: Mockito's default
        // answer calls the interface's own no-op default methods (setFailureHandler() among
        // them) rather than overriding them, so a bare PrepAndExpectedTestCase mock is
        // indistinguishable from a genuinely non-overriding implementation - see
        // applyFailureHandler()'s IllegalStateException tests below for that case.
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        final ArgumentCaptor<FailureHandler> captor =
                ArgumentCaptor.forClass(FailureHandler.class);
        verify(mockTestCase).setFailureHandler(captor.capture());
        assertThat(captor.getValue())
                .as("@DbUnitConfig.failureHandler() must be applied to the prep/expected"
                        + " path's injected PrepAndExpectedTestCase.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
    }

    @Test
    void testBeforeTest_expectedPathNoFailureHandler_appliesNullToResetAnyStaleHandler()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase mockTestCase =
                mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // Must still call setFailureHandler(null), not skip it, so a testCase reused across
        // several tests (e.g. a @DbUnitTestCase static field) resets to dbUnit's own default
        // handler instead of silently keeping an earlier test's @DbUnitConfig.failureHandler().
        verify(mockTestCase).setFailureHandler(isNull());
    }

    @Test
    void testBeforeTest_expectedPathFailureHandlerConfiguredAndTestCaseDoesNotOverrideSetter_throwsIllegalStateException()
            throws Exception
    {
        final DbUnitConfig config =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithFailureHandlerAndExpected.class, config, null, null, expected, null,
                        null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatThrownBy(executor::beforeTest)
                .as("A DbUnitConfig.failureHandler() the injected DbUnitTestCase cannot apply"
                        + " must fail loudly, since it would otherwise silently keep dbUnit's"
                        + " own default handler instead.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failureHandler")
                .hasMessageContaining(RecordingPrepAndExpectedTestCase.class.getName());
    }

    @Test
    void testBeforeTest_expectedPathNoFailureHandlerAndTestCaseDoesNotOverrideSetter_doesNotThrow()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatCode(executor::beforeTest)
                .as("No DbUnitConfig.failureHandler() configured means there is nothing for a"
                        + " non-overriding test case to fail to apply, so this must not throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testBeforeTest_expectedPathWithRowCountCheck_appliesOverrideToInjectedTestCase()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        // A DefaultPrepAndExpectedTestCase mock, not a bare interface mock: Mockito's default
        // answer calls the interface's own no-op default methods
        // (setRowCountCheckOverride() among them) rather than overriding them, so a bare
        // PrepAndExpectedTestCase mock is indistinguishable from a genuinely non-overriding
        // implementation - see applyRowCountCheckOverride()'s IllegalStateException test below
        // for that case.
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase).setRowCountCheckOverride(true, new String[0]);
    }

    @Test
    void testBeforeTest_expectedPathRowCountCheckAndTesterConnectionNull_stillAppliesOverrideWithoutResolvingConnection()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // tester.getConnection() is unstubbed here, so would return null (e.g. a test double
        // with no real connection) if ever called; the override must still apply, proving it
        // needs no connection of this executor's own to build - the wasted-connection bug this
        // pass-through exists to fix.
        verify(mockTestCase).setRowCountCheckOverride(true, new String[0]);
        verify(tester, never()).getConnection();
    }

    @Test
    void testBeforeTest_expectedPathRowCountCheckDeclaredAndTestCaseDoesNotOverrideSetter_throwsIllegalStateException()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatThrownBy(executor::beforeTest)
                .as("@DbUnitRowCountCheck declared on a test case that cannot apply the"
                        + " override must fail loudly, since the check would otherwise"
                        + " silently never run for this test at all.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbUnitRowCountCheck")
                .hasMessageContaining(RecordingPrepAndExpectedTestCase.class.getName());
    }

    @Test
    void testBeforeTest_expectedPathNoRowCountCheck_clearsAnyStaleOverride()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase mockTestCase =
                mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // Must clear, not skip, so a testCase reused across several tests (e.g. a
        // @DbUnitTestCase static field) resets to the connection's own DatabaseConfig instead
        // of silently keeping an earlier test's @DbUnitRowCountCheck override.
        verify(mockTestCase, never()).setRowCountCheckOverride(anyBoolean(), any());
        verify(mockTestCase).clearRowCountCheckOverride();
    }

    @Test
    void testBeforeTest_expectedPathNoProperties_appliesEmptyPropertiesToResetAnyStaleValue()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase mockTestCase =
                mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // Must still call setDatabaseConfigProperties(empty), not skip it, so a testCase
        // reused across several tests (e.g. a @DbUnitTestCase static field) resets to no
        // properties instead of silently keeping an earlier test's @DbUnitConfig.properties().
        final ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        verify(mockTestCase).setDatabaseConfigProperties(captor.capture());
        assertThat(captor.getValue())
                .as("No @DbUnitConfig.properties() must reset to empty properties.")
                .isEmpty();
    }

    @Test
    void testBeforeTest_expectedPathInjectedTestCaseWithDataFileLoader_appliesToInjectedInstance()
            throws Exception
    {
        final DbUnitConfig config =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithDataFileLoaderAndExpected.class, config, null, null, expected, null, null);
        // A DefaultPrepAndExpectedTestCase mock, not a bare interface mock: Mockito's default
        // answer calls the interface's own no-op default methods (setDataFileLoader() among
        // them) rather than overriding them, so a bare PrepAndExpectedTestCase mock is
        // indistinguishable from a genuinely non-overriding implementation - see
        // applyDataFileLoader()'s IllegalStateException test below for that case.
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // A @DbUnitTestCase-injected PrepAndExpectedTestCase must receive
        // @DbUnitConfig.dataFileLoader() the same way a freshly-constructed instance already
        // receives it through its constructor - otherwise a @DbUnitTestCase field built with
        // the no-arg constructor fails every test with "dataFileLoader is null".
        verify(mockTestCase).setDataFileLoader(any(FlatXmlDataFileLoader.class));
    }

    @Test
    void testBeforeTest_expectedPathDataFileLoaderConfiguredAndTestCaseDoesNotOverrideSetter_throwsIllegalStateException()
            throws Exception
    {
        final DbUnitConfig config =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithDataFileLoaderAndExpected.class, config, null, null, expected, null,
                        null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatThrownBy(executor::beforeTest)
                .as("A DbUnitConfig.dataFileLoader() the injected test case cannot apply must"
                        + " fail loudly instead of silently loading with whatever loader it"
                        + " was already constructed with.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dataFileLoader")
                .hasMessageContaining(RecordingPrepAndExpectedTestCase.class.getName());
    }

    @Test
    void testBeforeTest_expectedPathDefaultDataFileLoaderAndTestCaseDoesNotOverrideSetter_doesNotThrow()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);

        assertThatCode(executor::beforeTest)
                .as("The default DbUnitConfig.dataFileLoader() means nothing for a"
                        + " non-overriding test case to fail to apply.")
                .doesNotThrowAnyException();
    }

    @Test
    void testBeforeTest_expectedPathInjectedTestCaseWithCloseConnectionAfterTestFalse_appliesToInjectedInstance()
            throws Exception
    {
        final DbUnitConfig config = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithCloseConnectionAfterTestFalseAndExpected.class, config, null, null, expected,
                null, null);
        final PrepAndExpectedTestCase mockTestCase =
                mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // A @DbUnitTestCase-injected PrepAndExpectedTestCase must receive
        // @DbUnitConfig.closeConnectionAfterTest() the same way a freshly-constructed instance
        // already receives it through its constructor - otherwise a shared
        // CachingConnectionProvider connection gets closed out from under other tests.
        verify(mockTestCase).setCloseConnectionAfterTest(false);
    }

    @Test
    void testBeforeTest_closeConnectionAfterTestFalseAndTestCaseDoesNotOverrideSetter_logsWarning()
            throws Exception
    {
        final DbUnitConfig config = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithCloseConnectionAfterTestFalseAndExpected.class, config, null, null, expected,
                null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            // Unlike the other @DbUnitConfig-driven setters, this executor's own connection
            // still honors closeConnectionAfterTest regardless, so this must not throw - only
            // warn - even though the injected test case cannot apply it to its own connection.
            executor.beforeTest();

            assertThat(appender.list)
                    .as("A DbUnitConfig.closeConnectionAfterTest() the injected test case"
                            + " cannot apply must log a warning naming it, since a shared"
                            + " connection could otherwise be closed out from under other"
                            + " tests with no diagnostic at all.")
                    .hasSize(1);
            assertThat(appender.list.get(0).getLevel())
                    .as("Must log at WARN, not throw - see the test's leading comment.")
                    .isEqualTo(Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains(RecordingPrepAndExpectedTestCase.class.getName());
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_closeConnectionAfterTestDefaultAndTestCaseDoesNotOverrideSetter_doesNotLogWarning()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase nonOverridingTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester, true);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, nonOverridingTestCase);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .as("The default DbUnitConfig.closeConnectionAfterTest() (true) means"
                            + " nothing for a non-overriding test case to fail to apply, so"
                            + " this must not warn.")
                    .isEmpty();
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_setupDeclaredWithoutPrepFiles_appliesOperationWithoutChangingDataSet()
            throws Exception
    {
        stubDisabledConnection();
        final DbUnitSetup setup = WithSetupNoPrep.class.getAnnotation(DbUnitSetup.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(tester, never()).setDataSet(any());
        verify(tester).setSetUpOperation(DatabaseOperation.NONE);
        verify(tester).onSetup();
    }

    @Test
    void testBeforeTest_bareSetupNoPrepAndTesterHasNoDataset_warnsBeforeOnSetup()
            throws Exception
    {
        final DbUnitSetup setup = WithBareSetupNoPrep.class.getAnnotation(DbUnitSetup.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithBareSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A bare @DbUnitSetup (resolves to CLEAN_INSERT) with no @DbUnitPrep and"
                            + " no dataset on the tester must warn - a built-in tester will fail"
                            + " in onSetup() - not fail silently or throw (a custom tester or"
                            + " test double may legitimately source its dataset elsewhere).")
                    .hasSize(1)
                    .allSatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("@DbUnitSetup").contains("NONE"));
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_setupNoneNoPrepAndTesterHasNoDataset_doesNotWarn() throws Exception
    {
        final DbUnitSetup setup = WithSetupNoPrep.class.getAnnotation(DbUnitSetup.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final Logger executorLogger =
                (Logger) LoggerFactory.getLogger(AnnotatedTestExecutor.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        executorLogger.addAppender(appender);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("@DbUnitSetup(operation = NONE) with no dataset is the documented"
                            + " suppress-the-tester's-dataset case and must not warn.")
                    .isEmpty();
        } finally
        {
            executorLogger.detachAppender(appender);
        }
    }

    // ---- row count check baseline: piggybacking on onSetup()'s own connection ----

    @Test
    void testBeforeTest_realAbstractDatabaseTesterDefaultOperationsNoRowCountCheckDeclared_resolvesExactlyOneConnection()
            throws Exception
    {
        // A mocked IDatabaseTester (as almost every other test in this file uses) never
        // exercises this path at all: canPiggybackRowCountBaseline() only predicts a piggyback
        // for a real AbstractDatabaseTester, and a plain Mockito mock of the IDatabaseTester
        // interface is never an instanceof it. Only a real subclass - and a real connection,
        // since onSetup() actually runs CLEAN_INSERT against it - can prove the optimization
        // itself: that no @DbUnitRowCountCheck declared, plus AbstractDatabaseTester's own
        // default (CLEAN_INSERT) setup operation, resolves exactly one physical connection -
        // shared between the row count check baseline and onSetup() - not two.
        final AtomicInteger getConnectionCalls = new AtomicInteger();
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                getConnectionCalls.incrementAndGet();
                return InMemoryDatabaseConnection.create();
            }
        };
        realTester.setDataSet(new DefaultDataSet());
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(getConnectionCalls.get())
                .as("With no @DbUnitRowCountCheck declared and the tester's default CLEAN_INSERT"
                        + " setup operation (not NONE, so onSetup() retrieves a connection on"
                        + " its own), the row count check baseline must piggyback on that same"
                        + " connection instead of this executor eagerly resolving a second,"
                        + " wasted one - the same physical-connection count as before the row"
                        + " count check baseline existed at all.")
                .isEqualTo(1);
    }

    @Test
    void testBeforeAndAfterTest_realDefaultPrepAndExpectedTestCaseWithRowCountCheck_resolvesExactlyOneConnection()
            throws Exception
    {
        // A mocked DefaultPrepAndExpectedTestCase (as the other @DbUnitExpected +
        // @DbUnitRowCountCheck tests in this file use) never exercises
        // DBTestCase.getConnection()'s real behavior at all - only a real
        // DefaultPrepAndExpectedTestCase, driven by a real AbstractDatabaseTester whose
        // getConnection() returns a genuinely distinct physical connection (a fresh H2
        // in-memory database) each call, can prove the fix end to end: that @DbUnitExpected +
        // @DbUnitRowCountCheck together resolves exactly one connection - not the second,
        // wasted one applyRowCountCheckOverride() used to open just to read
        // PROPERTY_ROW_COUNTER before handing off to DefaultPrepAndExpectedTestCase's own,
        // separate connection. The expected dataset here is empty specifically so
        // verifyData() has no tables to compare, needing no real schema in the H2 database.
        final AtomicInteger getConnectionCalls = new AtomicInteger();
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                getConnectionCalls.incrementAndGet();
                // Schema-qualified so table enumeration for the row count check baseline sees
                // only PUBLIC's (zero) tables, not H2's own INFORMATION_SCHEMA ones too.
                return InMemoryDatabaseConnection.create("PUBLIC");
            }
        };
        final DbUnitExpected expected =
                WithEmptyExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithEmptyExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithEmptyExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(getConnectionCalls.get())
                .as("@DbUnitExpected + @DbUnitRowCountCheck together must resolve exactly one"
                        + " physical connection through the real DefaultPrepAndExpectedTestCase"
                        + " machinery - the same count as @DbUnitExpected alone costs - not a"
                        + " second one opened just to build the row count check override before"
                        + " handing it off.")
                .isEqualTo(1);
    }

    @Test
    void testBeforeTest_abstractDatabaseTesterOverridesOnSetup_capturesBaselineEagerlyBeforeOnSetup()
            throws Exception
    {
        // A subclass that overrides onSetup() is not guaranteed to route through
        // executeOperation() and notify the listener, and might apply the setup operation some
        // other way. canPiggybackRowCountBaseline() must decline it, so the baseline is captured
        // eagerly BEFORE onSetup() - a baseline captured afterward (as the after-the-fact safety
        // net would) already includes the rows onSetup() inserts, hiding the very leak the check
        // exists to catch. Here onSetup() bumps ACCOUNT 5 -> 6; the baseline must read 5.
        final AtomicInteger accountRows = new AtomicInteger(5);
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenAnswer(invocation -> accountRows.get());
        final AbstractDatabaseTester overridingTester = new AbstractDatabaseTester()
        {
            @Override
            public void onSetup()
            {
                // applies its setup some other way than executeOperation() - here, it adds a row
                accountRows.set(6);
            }

            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                return tester.getConnection();
            }
        };
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, overridingTester, null);

        executor.beforeTest();
        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The baseline must be captured before onSetup() (reading ACCOUNT = 5), so"
                        + " the row onSetup() left behind (ACCOUNT = 6) is caught as a leak"
                        + " rather than folded into the baseline.")
                .isInstanceOf(UnexpectedRowCountException.class);
    }

    // ---- helpers ----

    private void stubDisabledConnection() throws Exception
    {
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
    }

    private void stubEnabledConnection(final String... tableNames) throws Exception
    {
        stubConnection(true, tableNames);
    }

    /**
     * Stubs a connection whose own {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} is
     * {@code rowCountCheckFeature}, so a caller testing whether an annotation or system
     * property overrides that feature states its base config explicitly, instead of
     * stubbing one value here and immediately re-stubbing another.
     */
    private void stubConnection(final boolean rowCountCheckFeature, final String... tableNames)
            throws Exception
    {
        when(tester.getConnection()).thenReturn(connection);
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, rowCountCheckFeature);
        when(connection.getConfig()).thenReturn(databaseConfig);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(tableNames);
        when(connection.createDataSet()).thenReturn(dataSet);
        stubOpenJdbcConnection();
    }

    /**
     * Stubs {@link #connection}'s raw JDBC {@link Connection} as not yet closed, the default
     * {@link AnnotatedTestExecutor#closeResolvedConnectionIfOwned()} expects before it will
     * close a memoized connection. A test exercising the already-closed guard re-stubs
     * {@code jdbcConnection.isClosed()} to {@code true} afterward.
     */
    private void stubOpenJdbcConnection() throws Exception
    {
        // lenient(): most callers only exercise beforeTest(), never reaching the
        // closeResolvedConnectionIfOwned() check this stubs for - strict stubbing would flag
        // that as unused rather than as the shared default it is.
        lenient().when(connection.getConnection()).thenReturn(jdbcConnection);
        lenient().when(jdbcConnection.isClosed()).thenReturn(false);
    }

    // ---- fixtures ----

    @DbUnitPrep("prep.xml")
    static class WithPrep
    {
    }

    @DbUnitPrep({"prep.xml", "prep-lowercase.xml"})
    static class WithTwoDifferentlyCasedPrepFiles
    {
    }

    @DbUnitConfig(closeConnectionAfterTest = false)
    static class WithCloseConnectionAfterTestFalse
    {
    }

    @DbUnitConfig(closeConnectionAfterTest = false)
    @DbUnitExpected("expected.xml")
    static class WithCloseConnectionAfterTestFalseAndExpected
    {
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
    @DbUnitExpected("expected.xml")
    static class WithDataFileLoaderAndExpected
    {
    }

    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class WithSetupNoPrep
    {
    }

    @DbUnitSetup
    static class WithBareSetupNoPrep
    {
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class WithTearDown
    {
    }

    @DbUnitTearDown
    static class WithBareTearDown
    {
    }

    @DbUnitRowCountCheck
    static class WithBareRowCountCheck
    {
    }

    @DbUnitPrep("prep.xml")
    @DbUnitRowCountCheck
    static class WithPrepAndRowCountCheck
    {
    }

    @DbUnitRowCountCheck(exclude = "IGNORED_TABLE")
    static class WithExcludedTable
    {
    }

    @DbUnitExpected("expected.xml")
    static class WithExpected
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitRowCountCheck
    static class WithExpectedAndRowCountCheck
    {
    }

    @DbUnitExpected("empty-expected.xml")
    @DbUnitRowCountCheck
    static class WithEmptyExpectedAndRowCountCheck
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithRecordingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = NoMatchingConstructorTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithNoMatchingConstructorTestCase
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    static class WithProperties
    {
    }

    @DbUnitConfig(
            properties = @DbUnitProperty(name = "datatypeFactory", value = "not.a.real.ClassName"))
    static class WithInvalidProperty
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class WithPropertiesAndSetupNone
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndExpected
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = CachingConnectionPrepAndExpectedTestCase.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithConstructedCachingTestCase
    {
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    @DbUnitExpected("expected.xml")
    static class WithFailureHandlerAndExpected
    {
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} subclass overriding {@code setUpDatabaseConfig()}
     * without calling {@code super} - the pre-3.6.0 pattern that silently drops
     * {@code @DbUnitProperty} values - with its lifecycle methods stubbed to no-ops so the test
     * only exercises {@code applyDatabaseConfigProperties()}'s warning, not a real database.
     */
    public static class OverridesSetUpDatabaseConfigTestCase extends DefaultPrepAndExpectedTestCase
    {
        OverridesSetUpDatabaseConfigTestCase(final IDatabaseTester tester)
        {
            super(new FlatXmlDataFileLoader(), tester, true);
        }

        @Override
        protected void setUpDatabaseConfig(final DatabaseConfig config)
        {
            // deliberately does NOT call super.setUpDatabaseConfig(config)
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public void preTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData)
        {
        }
    }

    /**
     * Records the constructor arguments and lifecycle calls it received, standing in for
     * {@link org.dbunit.DefaultPrepAndExpectedTestCase} so these tests do not need a real
     * database connection.
     */
    public static class RecordingPrepAndExpectedTestCase implements PrepAndExpectedTestCase
    {
        static RecordingPrepAndExpectedTestCase instance;
        static IDatabaseTester lastTester;
        static boolean lastCloseConnectionAfterTest;
        static boolean throwFromConstructor;

        boolean preTestCalled;
        boolean postTestCalled;

        public RecordingPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester, final boolean closeConnectionAfterTest)
        {
            if (throwFromConstructor)
            {
                throw new RuntimeException("simulated constructor failure");
            }
            instance = this;
            lastTester = tester;
            lastCloseConnectionAfterTest = closeConnectionAfterTest;
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public void preTest()
        {
            preTestCalled = true;
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
                throws Exception
        {
            configureTest(verifyTables, prepDataFiles, expectedDataFiles);
            preTest();
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final org.dbunit.PrepAndExpectedTestCaseSteps testSteps) throws Exception
        {
            return null;
        }

        @Override
        public void postTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData)
        {
            postTestCalled = true;
        }

        @Override
        public void verifyData()
        {
        }

        @Override
        public void cleanupData()
        {
        }

        @Override
        public IDataSet getPrepDataset()
        {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset()
        {
            return null;
        }
    }

    /**
     * A {@link PrepAndExpectedTestCase} with only a no-arg constructor, lacking the
     * {@code (DataFileLoader, IDatabaseTester, boolean)} constructor shape that
     * {@link AnnotatedTestExecutor} requires when it constructs the configured class.
     */
    public static class NoMatchingConstructorTestCase implements PrepAndExpectedTestCase
    {
        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public void preTest()
        {
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final org.dbunit.PrepAndExpectedTestCaseSteps testSteps)
        {
            return null;
        }

        @Override
        public void postTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData)
        {
        }

        @Override
        public void verifyData()
        {
        }

        @Override
        public void cleanupData()
        {
        }

        @Override
        public IDataSet getPrepDataset()
        {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset()
        {
            return null;
        }
    }

    /**
     * A {@link PrepAndExpectedTestCase} that caches its {@code getReusableConnection()} result
     * the way a real one reused across test methods would, and overrides no {@code @DbUnitConfig}
     * setter - so a configured {@code @DbUnitProperty} makes {@code beforeExpectedTest()} fail
     * fast before {@code configureTest()}. Two constructors: one for direct injection in a test,
     * one {@code (DataFileLoader, IDatabaseTester, boolean)} so {@code AnnotatedTestExecutor}
     * can construct it from {@code @DbUnitConfig.prepAndExpectedTestCase}.
     */
    public static class CachingConnectionPrepAndExpectedTestCase
            extends RecordingPrepAndExpectedTestCase
    {
        private final IDatabaseConnection cached;
        boolean configureTestCalled;

        CachingConnectionPrepAndExpectedTestCase(final IDatabaseConnection cached)
        {
            super(new FlatXmlDataFileLoader(), null, true);
            this.cached = cached;
        }

        CachingConnectionPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester, final boolean closeConnectionAfterTest)
                throws Exception
        {
            super(dataFileLoader, tester, closeConnectionAfterTest);
            this.cached = tester.getConnection();
        }

        @Override
        public IDatabaseConnection getReusableConnection()
        {
            return cached;
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
            configureTestCalled = true;
        }
    }

    /**
     * A real {@link AbstractDatabaseTester} (so its dataset / operation state is genuine) whose
     * {@code onSetup()}/{@code onTearDown()} touch no database - {@code onTearDown()} just
     * records the teardown operation in effect at the moment it ran. Used to prove
     * {@code AnnotatedTestExecutor} restores a shared tester between methods.
     */
    static class RecordingAbstractDatabaseTester extends AbstractDatabaseTester
    {
        final List<DatabaseOperation> tearDownOperationsRun = new ArrayList<>();

        @Override
        public IDatabaseConnection getConnection()
        {
            return null;
        }

        @Override
        public void onSetup()
        {
        }

        @Override
        public void onTearDown()
        {
            tearDownOperationsRun.add(getTearDownOperation());
        }
    }
}
