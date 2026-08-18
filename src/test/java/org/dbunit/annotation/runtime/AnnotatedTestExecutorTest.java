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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.Properties;

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
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataFileLoader;
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
                AnnotatedTestConfiguration.from(WithPrep.class, null, prep, null, null, null);
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
                .from(WithTwoDifferentlyCasedPrepFiles.class, null, prep, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(WithTearDown.class, null, null, null, null, tearDown);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.afterTest(false);

        verify(tester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_tearDownNotDeclared_onlyCallsOnTearDown() throws Exception {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, times(2)).createDataSet();
        verify(tester, times(1)).getConnection();
        verify(tester).onTearDown();
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
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
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();

        executor.afterTest(false);

        verify(tester, times(1)).getConnection();
    }

    // ---- expected (prep/expected) path ----

    @Test
    void testBeforeTest_expectedPathWithInjectedTestCase_usesInjectedInstance()
            throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null);
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
                .from(WithRecordingTestCase.class, config, null, null, expected, null);
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
                .from(WithRecordingTestCase.class, config, null, null, expected, null);
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
    void testAfterTest_expectedPathTestPassed_postTestCalledWithTrue() throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null);
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
                .from(WithExpected.class, null, null, null, expected, null);
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
                .from(WithProperties.class, config, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_noPropertiesConfigured_stillInstallsOperationListenerToProtectMemoizedConnection() {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester).setOperationListener(any());
    }

    @Test
    void testConstructor_propertiesConfiguredWithExistingListener_wrapsExistingListenerAsDelegate()
            throws Exception {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null);
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
                .from(WithProperties.class, config, null, null, null, null);
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
    void testConstructor_secondExecutorSharesTesterAlreadyWrappingProperties_appliesPropertiesOnlyOnce()
            throws Exception {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null);
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
    void testBeforeTest_expectedPathWithFailureHandler_appliesToDefaultPrepAndExpectedTestCase()
            throws Exception {
        final DbUnitConfig config =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithFailureHandlerAndExpected.class, config, null, null, expected, null);
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
                .from(WithExpected.class, null, null, null, expected, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setFailureHandler(any());
    }

    @Test
    void testBeforeTest_expectedPathNoProperties_neverCallsSetDatabaseConfigProperties()
            throws Exception {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null);
        final DefaultPrepAndExpectedTestCase mockTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setDatabaseConfigProperties(any());
    }

    // ---- helpers ----

    private void stubDisabledConnection() throws Exception {
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
    }

    private void stubEnabledConnection(final String... tableNames) throws Exception {
        when(tester.getConnection()).thenReturn(connection);
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(databaseConfig);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(tableNames);
        when(connection.createDataSet()).thenReturn(dataSet);
        stubOpenJdbcConnection();
    }

    /**
     * Stubs {@link #connection}'s raw JDBC {@link Connection} as not yet closed, the default
     * {@link AnnotatedTestExecutor#closeResolvedConnectionIfOwned()} expects before it will
     * close a memoized connection.
     */
    private void stubOpenJdbcConnection() throws Exception {
        // lenient(): most callers only exercise beforeTest(), never reaching the close check
        // that consults isClosed() - without lenient(), Mockito's strict stubbing would flag
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

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class WithTearDown {
    }

    @DbUnitExpected("expected.xml")
    static class WithExpected {
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithRecordingTestCase {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    static class WithProperties {
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
}
