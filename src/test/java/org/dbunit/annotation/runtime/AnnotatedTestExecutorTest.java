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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.AbstractDatabaseTester;
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

@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class AnnotatedTestExecutorTest {
    @Mock
    IDatabaseTester tester;

    @Mock
    IDatabaseConnection connection;

    @Mock
    Connection jdbcConnection;

    @BeforeEach
    void resetRecordingPrepAndExpectedTestCase() {
        RecordingPrepAndExpectedTestCase.instance = null;
        RecordingPrepAndExpectedTestCase.lastTester = null;
        RecordingPrepAndExpectedTestCase.lastCloseConnectionAfterTest = false;
        RecordingPrepAndExpectedTestCase.throwFromConstructor = false;
    }

    // ---- simple path: setup ----

    @Test
    void testBeforeTest_simplePathWithPrepFiles_setsDataSetAndOperationThenOnSetup()
            throws Exception {
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
            throws Exception {
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
    void testBeforeTest_simplePathNoPrepFiles_onlyCallsOnSetup() throws Exception {
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

    // ---- simple path: teardown ----

    @Test
    void testAfterTest_tearDownDeclared_setsOperationThenOnTearDown() throws Exception {
        final DbUnitTearDown tearDown =
                WithTearDown.class.getAnnotation(DbUnitTearDown.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithTearDown.class, null, null, null, null, tearDown, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.afterTest(false);

        verify(tester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_tearDownNotDeclared_onlyCallsOnTearDown() throws Exception {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.afterTest(false);

        verify(tester, never()).setTearDownOperation(any());
        verify(tester).onTearDown();
    }

    // ---- row count check wiring (base tiers; the annotation tier is WI-04) ----

    @Test
    void testBeforeTest_rowCountCheckEnabled_capturesBaselineWithoutClosingConnection()
            throws Exception {
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
    void testBeforeTest_rowCountCheckDisabled_neverQueriesTheConnection() throws Exception {
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).createDataSet();
    }

    @Test
    void testAfterTest_testFailed_skipsRowCountVerification() throws Exception {
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
    void testAfterTest_testPassed_verifiesRowCountAgainstBaseline() throws Exception {
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
    void testAfterTest_verifyThrowsAndClosingConnectionAlsoThrows_reportsVerifyFailureWithCloseFailureSuppressed()
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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
    void testBeforeTest_testerConnectionNull_doesNotThrowAndStillRunsOnSetup() throws Exception {
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
    void testAfterTest_rowCountCheckDisabled_neverAcquiresASecondConnection() throws Exception {
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
    void testAfterTest_connectionResolved_closesItByDefault() throws Exception {
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
    void testAfterTest_connectionAlreadyClosed_doesNotCloseItAgain() throws Exception {
        // Reproduces a tester returning the same connection object every call (e.g.
        // DefaultDatabaseTester built from one fixed connection) where this executor's own
        // getConnection() was already called once - e.g. for an injected IDatabaseConnection/
        // Connection parameter - and DefaultPrepAndExpectedTestCase's own cleanupData()
        // independently closes that same connection first, so by the time afterTest()'s
        // finally block runs, the connection this executor is still holding is already
        // closed. isClosed() true here stands in for that prior close, without needing a real
        // DefaultPrepAndExpectedTestCase to exercise its own connection handling. Not
        // @DbUnitRowCountCheck-related - applyRowCountCheckOverride() needs no connection of
        // its own since the DefaultPrepAndExpectedTestCase pass-through fix, so a plain
        // @DbUnitExpected test double is enough to reproduce this.
        when(tester.getConnection()).thenReturn(connection);
        stubOpenJdbcConnection();
        when(jdbcConnection.isClosed()).thenReturn(true);
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_closeConnectionAfterTestFalse_leavesConnectionOpen() throws Exception {
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
    void testAfterTest_listenerIsNoOp_leavesConnectionOpenEvenByDefault() throws Exception {
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
            throws Exception {
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
    void testBeforeTest_systemPropertyFalseAndAnnotationPresent_disablesCheck()
            throws Exception {
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
    void testBeforeTest_systemPropertyTrueAndNoAnnotation_enablesCheck() throws Exception {
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
    void testBeforeTest_annotationPresentAndFeatureFalse_enablesCheck() throws Exception {
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
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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
    void testAfterTest_expectedPathBeforeTestNeverRan_doesNotThrow() throws Exception {
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
    void testAfterTest_expectedPathTestPassed_postTestCalledWithTrue() throws Exception {
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
    void testAfterTest_expectedPathTestFailed_postTestCalledWithFalse() throws Exception {
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

    // ---- @DbUnitProperty listener installation ----

    @Test
    void testConstructor_propertiesConfigured_installsOperationListener() {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_noPropertiesConfigured_stillInstallsOperationListenerToProtectMemoizedConnection() {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_propertiesConfiguredWithExistingListener_wrapsExistingListenerAsDelegate()
            throws Exception {
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

    // ---- @DbUnitProperty timing ----

    @Test
    void testGetConnection_propertiesConfigured_appliesThemBeforeReturningConnection()
            throws Exception {
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
            throws Exception {
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
                        + " resolved once per simple-path test for the row count check baseline,"
                        + " regardless of the configured operations, so there is no"
                        + " configuration under which properties silently fail to apply.")
                .isEqualTo(50);
    }

    @Test
    void testGetConnection_invalidPropertyValue_throwsIllegalStateExceptionNotRawDatabaseUnitException()
            throws Exception {
        final DbUnitConfig config = WithInvalidProperty.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithInvalidProperty.class, config, null, null, null, null, null);
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        assertThatThrownBy(executor::getConnection)
                .as("An invalid @DbUnitProperty value applied on the simple path - via"
                        + " getConnection()/the installed listener, not"
                        + " DefaultPrepAndExpectedTestCase's own setUpDatabaseConfig() - must"
                        + " be reported clearly, not left as a raw DatabaseUnitException"
                        + " surfacing from deep inside DatabaseConfig.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitProperty");
    }

    @Test
    void testConstructor_secondExecutorSharesTesterAlreadyWrappingProperties_appliesPropertiesOnlyOnce()
            throws Exception {
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
    void testBeforeTest_expectedPathWithProperties_appliesPropertiesToDefaultPrepAndExpectedTestCase()
            throws Exception {
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        final ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        verify(mockTestCase).setDatabaseConfigProperties(captor.capture());
        assertThat(captor.getValue().getProperty("batchSize"))
                .as("@DbUnitProperty values must reach the prep/expected path's"
                        + " DefaultPrepAndExpectedTestCase - the simple path's"
                        + " installOperationListener() is never triggered by"
                        + " setupData()/verifyData()/cleanupData().")
                .isEqualTo("50");
    }

    @Test
    void testBeforeTest_expectedPathWithFailureHandler_appliesToDefaultPrepAndExpectedTestCase()
            throws Exception {
        final DbUnitConfig config =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithFailureHandlerAndExpected.class, config, null, null, expected, null, null);
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
                        + " path's DefaultPrepAndExpectedTestCase.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
    }

    @Test
    void testBeforeTest_expectedPathNoFailureHandler_neverCallsSetFailureHandler()
            throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setFailureHandler(any());
    }

    @Test
    void testBeforeTest_expectedPathWithRowCountCheck_appliesOverrideToDefaultPrepAndExpectedTestCase()
            throws Exception {
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

        verify(mockTestCase).setRowCountCheckOverride(true, new String[0]);
    }

    @Test
    void testBeforeTest_expectedPathRowCountCheckAndTesterConnectionNull_stillAppliesOverrideWithoutResolvingConnection()
            throws Exception {
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
    void testBeforeTest_expectedPathNoRowCountCheck_neverCallsSetRowCountCheckOverride()
            throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setRowCountCheckOverride(anyBoolean(), any());
    }

    @Test
    void testBeforeTest_expectedPathNoProperties_neverCallsSetDatabaseConfigProperties()
            throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setDatabaseConfigProperties(any());
    }

    @Test
    void testBeforeTest_expectedPathInjectedTestCaseWithDataFileLoader_appliesToInjectedInstance()
            throws Exception {
        final DbUnitConfig config =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithDataFileLoaderAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithDataFileLoaderAndExpected.class, config, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // A @DbUnitTestCase-injected DefaultPrepAndExpectedTestCase must receive
        // @DbUnitConfig.dataFileLoader() the same way a freshly-constructed instance already
        // receives it through its constructor - otherwise a @DbUnitTestCase field built with
        // the no-arg constructor fails every test with "dataFileLoader is null".
        verify(mockTestCase).setDataFileLoader(any(FlatXmlDataFileLoader.class));
    }

    @Test
    void testBeforeTest_expectedPathInjectedTestCaseWithCloseConnectionAfterTestFalse_appliesToInjectedInstance()
            throws Exception {
        final DbUnitConfig config = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected = WithCloseConnectionAfterTestFalseAndExpected.class
                .getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithCloseConnectionAfterTestFalseAndExpected.class, config, null, null, expected,
                null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // A @DbUnitTestCase-injected DefaultPrepAndExpectedTestCase must receive
        // @DbUnitConfig.closeConnectionAfterTest() the same way a freshly-constructed instance
        // already receives it through its constructor - otherwise a shared
        // CachingConnectionProvider connection gets closed out from under other tests.
        verify(mockTestCase).setCloseConnectionAfterTest(false);
    }

    @Test
    void testBeforeTest_setupDeclaredWithoutPrepFiles_appliesOperationWithoutChangingDataSet()
            throws Exception {
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

    // ---- row count check baseline: piggybacking on onSetup()'s own connection ----

    @Test
    void testBeforeTest_realAbstractDatabaseTesterDefaultOperationsNoRowCountCheckDeclared_resolvesExactlyOneConnection()
            throws Exception {
        // A mocked IDatabaseTester (as almost every other test in this file uses) never
        // exercises this path at all: canPiggybackRowCountBaseline() only predicts a piggyback
        // for a real AbstractDatabaseTester, and a plain Mockito mock of the IDatabaseTester
        // interface is never an instanceof it. Only a real subclass - and a real connection,
        // since onSetup() actually runs CLEAN_INSERT against it - can prove the optimization
        // itself: that no @DbUnitRowCountCheck declared, plus AbstractDatabaseTester's own
        // default (CLEAN_INSERT) setup operation, resolves exactly one physical connection -
        // shared between the row count check baseline and onSetup() - not two.
        final AtomicInteger getConnectionCalls = new AtomicInteger();
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester() {
            @Override
            public IDatabaseConnection getConnection() throws Exception {
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
            throws Exception {
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
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester() {
            @Override
            public IDatabaseConnection getConnection() throws Exception {
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
    void testBeforeTest_onSetupOverrideNeverRetrievesAConnection_safetyNetStillCapturesBaseline()
            throws Exception {
        // canPiggybackRowCountBaseline() predicts a piggyback for any real
        // AbstractDatabaseTester with a non-NONE setup operation - true here - but this
        // subclass's onSetup() override deliberately never retrieves a connection or notifies
        // the operation listener, the way a subclass entirely replacing onSetup() (rather than
        // relying on AbstractDatabaseTester's own executeOperation()) legitimately could. The
        // baseline must still end up captured via beforeSimpleTest()'s after-the-fact safety
        // net rather than silently never captured because a prediction about tester internals
        // turned out wrong.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AbstractDatabaseTester silentTester = new AbstractDatabaseTester() {
            @Override
            public void onSetup() {
                // deliberately does not call getConnection() or notify the listener
            }

            @Override
            public IDatabaseConnection getConnection() throws Exception {
                return tester.getConnection();
            }
        };
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, silentTester, null);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(6);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The baseline must have actually been captured before the test method ran -"
                        + " via the safety net, since the piggyback this tester predicted never"
                        + " happened - for this row count difference to be caught at all rather"
                        + " than silently passing with no baseline to compare against.")
                .isInstanceOf(UnexpectedRowCountException.class);
    }

    // ---- helpers ----

    private void stubDisabledConnection() throws Exception {
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
    }

    private void stubEnabledConnection(final String... tableNames) throws Exception {
        stubConnection(true, tableNames);
    }

    /**
     * Stubs a connection whose own {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} is
     * {@code rowCountCheckFeature}, so a caller testing whether an annotation or system
     * property overrides that feature states its base config explicitly, instead of
     * stubbing one value here and immediately re-stubbing another.
     */
    private void stubConnection(final boolean rowCountCheckFeature, final String... tableNames)
            throws Exception {
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
    private void stubOpenJdbcConnection() throws Exception {
        // lenient(): most callers only exercise beforeTest(), never reaching the
        // closeResolvedConnectionIfOwned() check this stubs for - strict stubbing would flag
        // that as unused rather than as the shared default it is.
        lenient().when(connection.getConnection()).thenReturn(jdbcConnection);
        lenient().when(jdbcConnection.isClosed()).thenReturn(false);
    }

    // ---- fixtures ----

    @DbUnitPrep("prep.xml")
    static class WithPrep {
    }

    @DbUnitPrep({"prep.xml", "prep-lowercase.xml"})
    static class WithTwoDifferentlyCasedPrepFiles {
    }

    @DbUnitConfig(closeConnectionAfterTest = false)
    static class WithCloseConnectionAfterTestFalse {
    }

    @DbUnitConfig(closeConnectionAfterTest = false)
    @DbUnitExpected("expected.xml")
    static class WithCloseConnectionAfterTestFalseAndExpected {
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
    @DbUnitExpected("expected.xml")
    static class WithDataFileLoaderAndExpected {
    }

    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class WithSetupNoPrep {
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class WithTearDown {
    }

    @DbUnitRowCountCheck
    static class WithBareRowCountCheck {
    }

    @DbUnitRowCountCheck(exclude = "IGNORED_TABLE")
    static class WithExcludedTable {
    }

    @DbUnitExpected("expected.xml")
    static class WithExpected {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitRowCountCheck
    static class WithExpectedAndRowCountCheck {
    }

    @DbUnitExpected("empty-expected.xml")
    @DbUnitRowCountCheck
    static class WithEmptyExpectedAndRowCountCheck {
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithRecordingTestCase {
    }

    @DbUnitConfig(prepAndExpectedTestCase = NoMatchingConstructorTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithNoMatchingConstructorTestCase {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    static class WithProperties {
    }

    @DbUnitConfig(
            properties = @DbUnitProperty(name = "datatypeFactory", value = "not.a.real.ClassName"))
    static class WithInvalidProperty {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitSetup(operation = DbUnitOperation.NONE)
    static class WithPropertiesAndSetupNone {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndExpected {
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    @DbUnitExpected("expected.xml")
    static class WithFailureHandlerAndExpected {
    }

    /**
     * Records the constructor arguments and lifecycle calls it received, standing in for
     * {@link org.dbunit.DefaultPrepAndExpectedTestCase} so these tests do not need a real
     * database connection.
     */
    public static class RecordingPrepAndExpectedTestCase implements PrepAndExpectedTestCase {
        static RecordingPrepAndExpectedTestCase instance;
        static IDatabaseTester lastTester;
        static boolean lastCloseConnectionAfterTest;
        static boolean throwFromConstructor;

        boolean preTestCalled;

        public RecordingPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester, final boolean closeConnectionAfterTest) {
            if (throwFromConstructor) {
                throw new RuntimeException("simulated constructor failure");
            }
            instance = this;
            lastTester = tester;
            lastCloseConnectionAfterTest = closeConnectionAfterTest;
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles) {
        }

        @Override
        public void preTest() {
            preTestCalled = true;
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles)
                throws Exception {
            configureTest(verifyTables, prepDataFiles, expectedDataFiles);
            preTest();
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final org.dbunit.PrepAndExpectedTestCaseSteps testSteps) throws Exception {
            return null;
        }

        @Override
        public void postTest() {
        }

        @Override
        public void postTest(final boolean verifyData) {
        }

        @Override
        public void verifyData() {
        }

        @Override
        public void cleanupData() {
        }

        @Override
        public IDataSet getPrepDataset() {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset() {
            return null;
        }
    }

    /**
     * A {@link PrepAndExpectedTestCase} with only a no-arg constructor, lacking the
     * {@code (DataFileLoader, IDatabaseTester, boolean)} constructor shape that
     * {@link AnnotatedTestExecutor} requires when it constructs the configured class.
     */
    public static class NoMatchingConstructorTestCase implements PrepAndExpectedTestCase {
        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles) {
        }

        @Override
        public void preTest() {
        }

        @Override
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles) {
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final org.dbunit.PrepAndExpectedTestCaseSteps testSteps) {
            return null;
        }

        @Override
        public void postTest() {
        }

        @Override
        public void postTest(final boolean verifyData) {
        }

        @Override
        public void verifyData() {
        }

        @Override
        public void cleanupData() {
        }

        @Override
        public IDataSet getPrepDataset() {
            return null;
        }

        @Override
        public IDataSet getExpectedDataset() {
            return null;
        }
    }
}
