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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultOperationListener;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestData;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.database.connection.LeftOpenConnectionWarning;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.RowCountCheck;
import org.dbunit.database.rowcount.RowCountCheckConfiguration;
import org.dbunit.database.rowcount.RowCountCheckOverride;
import org.dbunit.database.rowcount.RowCountDifference;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
    ListenerReportingTester tester;

    @Mock
    IDatabaseConnection connection;

    @Mock
    Connection jdbcConnection;

    @BeforeEach
    void resetRecordingPrepAndExpectedTestCase()
    {
        lenient().when(tester.getOperationListener()).thenReturn(new DefaultOperationListener());
        lenient().when(tester.getSetUpOperation()).thenReturn(DatabaseOperation.NONE);
        lenient().when(tester.getTearDownOperation()).thenReturn(DatabaseOperation.NONE);
        RecordingPrepAndExpectedTestCase.instance = null;
        RecordingPrepAndExpectedTestCase.lastTester = null;
        RecordingPrepAndExpectedTestCase.throwFromConstructor = false;
    }

    // ---- setup: combining prep files, and the classic path's onSetup() ----

    @Test
    void testBeforeTest_multiplePrepFilesAndCaseSensitiveTableNames_doesNotMergeDifferentlyCasedTables()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES, true);
        lenient().when(connection.getConfig()).thenReturn(databaseConfig);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithTwoDifferentlyCasedPrepFiles.class), ownTester, null);

        executor.beforeTest();

        final DefaultPrepAndExpectedTestCase testCase =
                (DefaultPrepAndExpectedTestCase) executor.getPrepAndExpectedTestCase();
        assertThat(testCase.getPrepDataset().getTableNames())
                .as("Combining multiple @DbUnitPrep files must honor the connection's"
                        + " FEATURE_CASE_SENSITIVE_TABLE_NAMES, the same feature"
                        + " DefaultPrepAndExpectedTestCase resolves for its own multi-file"
                        + " combining, instead of always combining case-insensitively and"
                        + " silently merging 'ACCOUNT' and 'account' into one table.")
                .containsExactlyInAnyOrder("ACCOUNT", "account");
    }

    @Test
    void testBeforeTest_multiplePrepFilesAndCaseInsensitiveTableNames_mergesDifferentlyCasedTables()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithTwoDifferentlyCasedPrepFiles.class), ownTester, null);

        executor.beforeTest();

        final DefaultPrepAndExpectedTestCase testCase =
                (DefaultPrepAndExpectedTestCase) executor.getPrepAndExpectedTestCase();
        assertThat(testCase.getPrepDataset().getTableNames())
                .as("With the connection's default case-insensitive table names, the two files"
                        + " combine into one table.")
                .hasSize(1);
    }

    @Test
    void testAfterTest_classicPathBeforeTestNeverRan_doesNotThrow() throws Exception
    {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);

        assertThatCode(() -> executor.afterTest(true))
                .as("afterTest() with no preceding beforeTest() must be a no-op.")
                .doesNotThrowAnyException();
        verify(tester, never()).onTearDown();
    }

    @Test
    void testBeforeTest_classicPath_onlyCallsOnSetupAndWritesNothing() throws Exception
    {
        stubDisabledConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);

        executor.beforeTest();

        verify(tester, never()).setDataSet(any());
        verify(tester, never()).setSetUpOperation(any());
        verify(tester).onSetup();
    }

    // ---- teardown: the classic path's onTearDown(), and a failing postTest() ----

    @Test
    void testAfterTest_classicPathTearDownOperationSetBeforeEach_runsItAndLeavesTheTesterAlone()
            throws Exception
    {
        // A zero-annotation method must run onTearDown() with the operation a @BeforeEach method
        // set (not a forced NONE - issue 945), and leave the tester exactly as it was found.
        final RecordingAbstractDatabaseTester sharedTester = new RecordingAbstractDatabaseTester();
        sharedTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, sharedTester, null, false);

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
    void testAfterTest_classicPathOnTearDownThrows_isReportedUnchanged() throws Exception
    {
        stubDisabledConnection();
        final RuntimeException tearDownFailure = new RuntimeException("onTearDown failed");
        doThrow(tearDownFailure).when(tester).onTearDown();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class), tester, null, false);
        executor.beforeTest();

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown).as("The onTearDown() failure is what the test reports.")
                .isSameAs(tearDownFailure);
    }

    @Test
    void testAfterTest_postTestThrowsAndUndoingTheConfigurationAlsoThrows_reportsPostTestWithUndoSuppressed()
            throws Exception
    {
        when(tester.getConnection()).thenReturn(connection);
        stubOpenJdbcConnection();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithThrowingPostTestAndUndoTestCase.class), tester, null);
        executor.beforeTest();

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown).as("The postTest() failure is the primary.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("postTest failed on purpose.");
        assertThat(thrown.getSuppressed())
                .as("A failure undoing what the configurer applied rides along, not replacing"
                        + " it.")
                .hasSize(1);
    }

    // ---- one annotation lifecycle: every annotation-driven test runs through a test case ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("annotationDrivenShapes")
    void testBeforeAndAfterTest_annotationDrivenShape_neverWritesTheTester(final String shape,
            final Class<?> annotatedClass) throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(annotatedClass, BuildsRecordingTestCase.class), ownTester, null);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(ownTester.writes)
                .as("An annotation-driven test with %s only reads the tester: its dataset, its"
                        + " operations and its listener stay as they are, so a tester shared by a"
                        + " static field, a Spring singleton or parallel tests carries nothing"
                        + " from one test to another.", shape)
                .isEmpty();
    }

    static Stream<Arguments> annotationDrivenShapes()
    {
        return Stream.of(Arguments.of("no annotation but a marker field", WithNothing.class),
                Arguments.of("@DbUnitPrep alone", WithPrep.class),
                Arguments.of("@DbUnitSetUpOperation alone", WithSetupNoPrep.class),
                Arguments.of("@DbUnitTearDownOperation alone", WithTearDown.class),
                Arguments.of("@DbUnitExpected alone", WithExpected.class),
                Arguments.of("@DbUnitRowCountCheck alone", WithBareRowCountCheck.class),
                Arguments.of("@DbUnitConfig properties alone", WithProperties.class),
                Arguments.of("every annotation", WithEveryAnnotation.class));
    }

    @Test
    void testBeforeTest_annotationDrivenPrepOnly_runsTheTestersOperationOnThePrepDataSetNotItsOnSetup()
            throws Exception
    {
        final RecordingDatabaseOperation testersOperation = new RecordingDatabaseOperation();
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(testersOperation);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithPrep.class), ownTester, null);

        executor.beforeTest();

        assertThat(testersOperation.getExecutedTableNames())
                .as("The test case runs the operation the tester has on the prep dataset.")
                .containsExactly(Arrays.asList("ACCOUNT"));
        assertThat(ownTester.setUpOperationsRun)
                .as("The tester's own onSetup() is not how an annotation-driven test sets up.")
                .isEmpty();
        executor.afterTest(false);
    }

    @Test
    void testBeforeTest_annotationDrivenSetupDeclared_runsTheDeclaredOperationAndLeavesTheTesterItsOwn()
            throws Exception
    {
        final RecordingDatabaseOperation testersOperation = new RecordingDatabaseOperation();
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(testersOperation);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithPrep.class, WithSetupNoPrep.class), ownTester, null);

        executor.beforeTest();

        assertThat(testersOperation.getExecutedTableNames())
                .as("The declared NONE replaces the tester's operation for this test.").isEmpty();
        assertThat(ownTester.getSetUpOperation())
                .as("The tester keeps the operation it has: the declared one is the test case's.")
                .isSameAs(testersOperation);
        executor.afterTest(false);
    }

    @Test
    void testBeforeAndAfterTest_annotationDrivenTesterOverridesOnSetup_neverCallsOnSetupOrOnTearDown()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithNothing.class), ownTester, null);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(ownTester.setUpOperationsRun)
                .as("A tester's onSetup() override does not run on an annotation-driven test.")
                .isEmpty();
        assertThat(ownTester.tearDownOperationsRun)
                .as("A tester's onTearDown() override does not run on an annotation-driven"
                        + " test.")
                .isEmpty();
    }

    @Test
    void testBeforeAndAfterTest_classicPathTesterOverridesOnSetup_callsOnSetupAndOnTearDown()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class), ownTester, null, false);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(ownTester.setUpOperationsRun)
                .as("A zero-annotation test keeps the 3.5.0 lifecycle: the tester's onSetup().")
                .containsExactly(DatabaseOperation.NONE);
        assertThat(ownTester.tearDownOperationsRun)
                .as("A zero-annotation test keeps the 3.5.0 lifecycle: the tester's"
                        + " onTearDown().")
                .containsExactly(DatabaseOperation.NONE);
    }

    @Test
    void testBeforeTest_noPrepFilesAndTesterHoldsADataSet_setsUpNothing() throws Exception
    {
        final RecordingDatabaseOperation testersOperation = new RecordingDatabaseOperation();
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(testersOperation);
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        ownTester.writes.clear();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithNothing.class), ownTester, null);

        executor.beforeTest();

        assertThat(testersOperation.getExecutedTableNames())
                .as("With no @DbUnitPrep files the setup operation runs over no tables: the"
                        + " dataset a tester holds, such as one a @BeforeEach method gave it, is"
                        + " never read.")
                .containsExactly(Collections.emptyList());
        assertThat(ownTester.writes).as("Nothing is written to the tester.").isEmpty();
        executor.afterTest(false);
    }

    @Test
    void testBeforeTest_noPrepFilesAndExpectedAndTesterHoldsADataSet_leavesThePrepEmpty()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithExpected.class, BuildsRecordingTestCase.class), ownTester,
                null);

        executor.beforeTest();

        assertThat(RecordingPrepAndExpectedTestCase.instance.getPrepDataset().getTableNames())
                .as("Adding @DbUnitExpected does not change what is set up: with no @DbUnitPrep"
                        + " files nothing is, whatever dataset the tester holds.")
                .isEmpty();
    }

    @Test
    void testBeforeTest_noPrepFilesAndTesterHoldsADataSet_warnsOnceThatItIsIgnored()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class, BuildsRecordingTestCase.class), ownTester,
                null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A dataset the tester holds that dbUnit ignores, such as one a"
                            + " @BeforeEach method gave it before 3.6.0, must be said.")
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("ignores").contains("@DbUnitPrep"));
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_noPrepFilesAndTesterHoldsNoDataSet_doesNotWarn() throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class, BuildsRecordingTestCase.class), ownTester,
                null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A tester that holds no dataset has nothing that is ignored.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_prepFilesAndTesterHoldsADataSet_doesNotWarn() throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithPrep.class, BuildsRecordingTestCase.class), ownTester, null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A test that names its @DbUnitPrep files chose its dataset, so the one a"
                            + " shared tester happens to hold is not worth a warning.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_annotationDrivenTesterWhoseDataSetThrows_runsTheTestAndClosesItsConnection()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester()
        {
            @Override
            public IDataSet getDataSet()
            {
                throw new IllegalStateException("The tester's dataset cannot be built.");
            }
        };
        ownTester.connection = connection;
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        lenient().when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithNothing.class), ownTester, null);

        final Throwable beforeFailure = catchThrowable(executor::beforeTest);
        executor.afterTest(beforeFailure != null);

        assertThat(beforeFailure)
                .as("The dataset an annotation-driven test ignores is read only to warn of it, so"
                        + " a tester that cannot build it must not fail the test.")
                .isNull();
        verify(connection, times(1)).close();
    }

    @Test
    void testBeforeTest_annotationDrivenTesterWhoseDataSetThrowsAnError_afterTestClosesItsConnection()
            throws Exception
    {
        final NoClassDefFoundError missingDependency =
                new NoClassDefFoundError("org/yaml/snakeyaml/Yaml");
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester()
        {
            @Override
            public IDataSet getDataSet()
            {
                throw missingDependency;
            }
        };
        ownTester.connection = connection;
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        lenient().when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithNothing.class), ownTester, null);

        final Throwable beforeFailure = catchThrowable(executor::beforeTest);
        executor.afterTest(true);

        assertThat(beforeFailure)
                .as("An Error is not one the warning's probe swallows, so it fails the test"
                        + " as it is.")
                .isSameAs(missingDependency);
        verify(connection, times(1)).close();
    }

    @Test
    void testBeforeTest_prepFilesAndTesterHoldsADataSet_preparesThePrepFiles() throws Exception
    {
        final RecordingDatabaseOperation testersOperation = new RecordingDatabaseOperation();
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(testersOperation);
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithPrep.class), ownTester, null);

        executor.beforeTest();

        assertThat(testersOperation.getExecutedTableNames())
                .as("@DbUnitPrep files, when declared, are the dataset to set up.")
                .containsExactly(Arrays.asList("ACCOUNT"));
        executor.afterTest(false);
    }

    @Test
    void testBeforeTest_injectedTestCaseNoPrepFilesAndTesterHoldsADataSet_leavesThePrepEmpty()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final RecordingPrepAndExpectedTestCase injected =
                new RecordingPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), ownTester);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class), ownTester, injected);

        executor.beforeTest();

        assertThat(injected.getPrepDataset().getTableNames())
                .as("An injected test case is used as its owner built it: its datasets are the"
                        + " test's @DbUnitPrep files, not what a tester holds.")
                .isEmpty();
    }

    @Test
    void testBeforeTest_noPrepFilesAndTesterHoldsNoDataSet_keepsTheTestCasesOwnEmptyPrep()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class, BuildsRecordingTestCase.class), ownTester,
                null);

        executor.beforeTest();

        assertThat(RecordingPrepAndExpectedTestCase.instance.getPrepDataset().getTableNames())
                .as("A tester with no dataset leaves the test case an empty one, not a null.")
                .isEmpty();
    }

    @Test
    void testGetConnection_annotationDrivenWithoutExpected_isTheTestCasesReusableConnection()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = testerWithConnection();
        ownTester.setSetUpOperation(DatabaseOperation.NONE);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configurationOf(WithPrep.class), ownTester, null);
        executor.beforeTest();

        final IDatabaseConnection injected = executor.getConnection();

        final DefaultPrepAndExpectedTestCase testCase =
                (DefaultPrepAndExpectedTestCase) executor.getPrepAndExpectedTestCase();
        assertThat(injected)
                .as("The IDatabaseConnection parameter is the connection the test case sets up"
                        + " and cleans up on, with or without @DbUnitExpected.")
                .isSameAs(testCase.getReusableConnection()).isSameAs(connection);
        executor.afterTest(false);
    }

    // ---- warnings and hints of the lifecycle ----

    @Test
    void testBeforeTest_bareSetupDeclaredAndNothingToActOn_warnsOnceNamingTheFixes()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithBareSetupNoPrep.class, BuildsRecordingTestCase.class),
                ownTester, null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A bare @DbUnitSetUpOperation resolves to CLEAN_INSERT, which needs a"
                            + " dataset: with no @DbUnitPrep files and none on the tester it has"
                            + " nothing to act on, which is more likely a mistake than a wish.")
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("@DbUnitSetUpOperation").contains("@DbUnitPrep")
                            .contains("NONE"));
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_setupNoneDeclaredAndNothingToActOn_doesNotWarn() throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithSetupNoPrep.class, BuildsRecordingTestCase.class), ownTester,
                null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("@DbUnitSetUpOperation(NONE) needs no dataset: it is the documented way"
                            + " to suppress the tester's own.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_setupDeclaredAndTesterHoldsADataSet_warnsThatItIsIgnoredAndNothingIsToActOn()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setDataSet(new DefaultDataSet(new DefaultTable("TESTERS_TABLE")));
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithBareSetupNoPrep.class, BuildsRecordingTestCase.class),
                ownTester, null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("The dataset the tester holds is not what the operation acts on, so both"
                            + " the dataset that is ignored and the operation with nothing to act"
                            + " on are said.")
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .hasSize(2)
                    .anySatisfy(message -> assertThat(message).contains("ignores"))
                    .anySatisfy(message -> assertThat(message)
                            .contains("@DbUnitSetUpOperation").contains("nothing"));
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_tearDownDeclaredAndNothingToActOn_warnsOnceNamingTheFixes()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithTearDown.class, BuildsRecordingTestCase.class), ownTester,
                null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("A teardown operation other than NONE with no @DbUnitPrep or"
                            + " @DbUnitExpected files and no dataset on the tester has nothing to"
                            + " act on.")
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage())
                            .contains("@DbUnitTearDownOperation").contains("@DbUnitExpected")
                            .contains("NONE"));
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testBeforeTest_tearDownDeclaredAndExpectedFilesDeclared_doesNotWarn() throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithTearDown.class, WithExpected.class,
                        BuildsRecordingTestCase.class),
                ownTester, null);
        final ListAppender<ILoggingEvent> appender = startCapturingLogOf(TestCaseLifecycle.class);
        try
        {
            executor.beforeTest();

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("The teardown operation also runs over the expected datasets, so it has"
                            + " something to act on.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(TestCaseLifecycle.class, appender);
        }
    }

    @Test
    void testAfterTest_rowCountMismatchWithPrepAndNoTeardownAndOtherFailuresAttached_hintsAndKeepsThem()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithPrepAndRowCountCheck.class, BuildsMismatchingTestCase.class),
                ownTester, null);
        executor.beforeTest();

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown).as("The mismatch stays an UnexpectedRowCountException.")
                .isInstanceOf(UnexpectedRowCountException.class)
                .hasMessageContaining("baseline is captured before @DbUnitPrep loads")
                .hasMessageContaining("@DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)")
                .hasMessageContaining("ACCOUNT");
        assertThat(thrown.getSuppressed())
                .as("What was attached to the mismatch, such as a failure closing the connection,"
                        + " still rides along on the exception that carries the hint.")
                .extracting(Throwable::getMessage).containsExactly("close failed");
        assertThat(thrown.getStackTrace())
                .as("The stack trace is that of the mismatch, not of the place that added the"
                        + " hint.")
                .startsWith(MismatchingTestCase.thrown.getStackTrace()[0]);
    }

    @Test
    void testAfterTest_rowCountMismatchWithPrepAndATeardownDeclared_isReportedUnchanged()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithPrepAndRowCountCheck.class, WithTearDown.class,
                        BuildsMismatchingTestCase.class),
                ownTester, null);
        executor.beforeTest();

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("A teardown operation was declared, so the mismatch is not misattributed to"
                        + " prep that was never torn down.")
                .isSameAs(MismatchingTestCase.thrown);
    }

    // ---- annotation-driven: operations are applied only when declared ----

    @Test
    void testBeforeTest_injectedTestCaseAndPrepFiles_leavesTheTestersOperationsAlone()
            throws Exception
    {
        // An injected test case is used as its owner built it, so @DbUnitPrep files do not turn
        // the setup operation of its tester into CLEAN_INSERT, as they do for one dbUnit builds.
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setSetUpOperation(DatabaseOperation.REFRESH);
        ownTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final DbUnitPrep prep = WithPrep.class.getAnnotation(DbUnitPrep.class);
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                AnnotatedTestConfiguration.from(WithExpected.class, null, prep, null, expected,
                        null, null),
                ownTester, injectedDefaultTestCase());

        executor.beforeTest();

        assertThat(ownTester.getSetUpOperation())
                .as("The setup operation its owner gave the tester stays, files or not.")
                .isEqualTo(DatabaseOperation.REFRESH);
        assertThat(ownTester.getTearDownOperation())
                .as("The teardown operation its owner gave the tester stays.")
                .isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testBeforeTest_expectedAlone_keepsTheTestersOwnOperations()
            throws Exception
    {
        // A @BeforeEach method may set operations DbUnitOperation cannot express, such as a
        // CompositeOperation or a TransactionOperation; with no annotation declaring one,
        // the test case must run the tester's own.
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setSetUpOperation(DatabaseOperation.REFRESH);
        ownTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                AnnotatedTestConfiguration.from(WithExpected.class, null, null, null, expected,
                        null, null),
                ownTester, injectedDefaultTestCase());

        executor.beforeTest();

        assertThat(ownTester.getSetUpOperation())
                .as("With no @DbUnitPrep and no @DbUnitSetUpOperation, the setup operation the"
                        + " tester"
                        + " already has must stay.")
                .isEqualTo(DatabaseOperation.REFRESH);
        assertThat(ownTester.getTearDownOperation())
                .as("With no @DbUnitTearDownOperation, the teardown operation the tester already"
                        + " has,"
                        + " such as one a @BeforeEach method set, must stay.")
                .isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testBeforeTest_prepFilesWithoutSetup_keepsTheTestersSetupOperation()
            throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.setSetUpOperation(DatabaseOperation.REFRESH);
        ownTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final DbUnitPrep prep = WithPrep.class.getAnnotation(DbUnitPrep.class);
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final DbUnitConfig recording =
                WithRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                AnnotatedTestConfiguration.from(WithRecordingTestCase.class, recording, prep,
                        null, expected, null, null),
                ownTester, null);

        executor.beforeTest();

        assertThat(ownTester.getSetUpOperation())
                .as("@DbUnitPrep without @DbUnitSetUpOperation names only the dataset, so the"
                        + " operation the tester already has stays.")
                .isEqualTo(DatabaseOperation.REFRESH);
        assertThat(ownTester.getTearDownOperation())
                .as("The teardown operation, which no @DbUnitTearDownOperation declared, must"
                        + " stay.")
                .isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testAfterTest_classicPathBeforeTestThrewBeforeOnSetup_doesNotRunOnTearDown()
            throws Exception
    {
        // beforeTest() fails before onSetup() runs - here, resolving the connection for the row
        // count check baseline throws. afterTest() must not then call onTearDown(): it would act
        // on state onSetup() never established and throw a second exception masking the real
        // failure. TestCaseLifecycle has the same guard for an annotation-driven test.
        when(tester.getConnection()).thenThrow(new SQLException("cannot connect"));
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);

        assertThatThrownBy(executor::beforeTest).isInstanceOf(SQLException.class);
        executor.afterTest(true);

        verify(tester, never()).onSetup();
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
                new AnnotatedTestExecutor(configuration, tester, null, false);

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
                new AnnotatedTestExecutor(configuration, tester, null, false);

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
                new AnnotatedTestExecutor(configuration, tester, null, false);
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
                new AnnotatedTestExecutor(configuration, tester, null, false);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, times(2)).createDataSet();
        verify(tester, times(1)).getConnection();
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTest_postTestThrowsError_stillClosesConnectionAndPreservesErrorType()
            throws Exception
    {
        // A comparison mismatch fails via DbComparisonFailure, an Error subclass, not an
        // Exception - catching only Exception would skip closing the connection on every
        // ordinary verification failure, the single most common way this step throws at all.
        stubOpenJdbcConnection();
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        when(mockTestCase.isConnectionLeftOpen()).thenReturn(false);
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
                new AnnotatedTestExecutor(configuration, tester, null, false);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(6);
        final SQLException closeFailure = new SQLException("connection lost");
        when(jdbcConnection.isClosed()).thenThrow(closeFailure);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The row count mismatch must be reported as the primary failure, not"
                        + " silently replaced by a later failure closing the connection - a"
                        + " plain finally block would let the SQLException from"
                        + " TestScopedConnection.release() do exactly that.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(thrown.getSuppressed())
                .as("The failure closing the connection must still be visible, attached as"
                        + " suppressed onto the primary failure rather than discarded.")
                .containsExactly(closeFailure);
    }

    @Test
    void testBeforeTest_classicPathRealTesterWithFixedConnection_neverClosesConnectionDuringOnSetup()
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
                new AnnotatedTestExecutor(configuration, fixedConnectionTester, null, false);

        executor.beforeTest();

        verify(connection, never()).close();
    }

    @Test
    void testConstructor_classicPathInstalledListenerNotifiedAboutAConnection_stillClosesIt()
            throws Exception
    {
        // The executor's listener shields no connection: a tester that hands out a fresh
        // connection per call (e.g. a plain JdbcDatabaseTester) has that connection closed by its
        // own listener, as in the 3.5.0 lifecycle.
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null, false);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        final IDatabaseConnection otherConnection = mock(IDatabaseConnection.class);

        captor.getValue().operationSetUpFinished(otherConnection);

        verify(otherConnection).close();
    }

    @Test
    void testConstructor_classicPathInstalledListenerNotifiedAboutAConnectionViaTearDown_stillClosesIt()
            throws Exception
    {
        // The teardown-side twin of
        // testConstructor_classicPathInstalledListenerNotifiedAboutAConnection_stillClosesIt.
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null, false);
        final ArgumentCaptor<IOperationListener> captor =
                ArgumentCaptor.forClass(IOperationListener.class);
        verify(tester).setOperationListener(captor.capture());
        final IDatabaseConnection otherConnection = mock(IDatabaseConnection.class);

        captor.getValue().operationTearDownFinished(otherConnection);

        verify(otherConnection).close();
    }

    @Test
    void testConstructor_classicPathBaselineCaptureInsideTheListenerCallbackFails_wrapsItInADatabaseUnitRuntimeException()
            throws Exception
    {
        // captureRowCountBaselineOnFirstConnectionRetrieved() runs from
        // ClassicOperationListener.connectionRetrieved(), which declares no checked exception,
        // so a DatabaseUnitException/SQLException from the baseline capture there must surface
        // as an unchecked DatabaseUnitRuntimeException rather than be swallowed.
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(databaseConfig);
        when(connection.createDataSet())
                .thenThrow(new SQLException("connection lost mid-capture"));
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        new AnnotatedTestExecutor(configuration, tester, null, false);
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
                new AnnotatedTestExecutor(configuration, tester, null, false);

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
                new AnnotatedTestExecutor(configuration, tester, null, false);
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
                new AnnotatedTestExecutor(configuration, tester, null, false);
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
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        when(injected.isConnectionLeftOpen()).thenReturn(false);
        when(injected.getReusableConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_connectionResolvedButAfterTestNeverRan_closesIt()
            throws Exception
    {
        when(tester.getConnection()).thenReturn(connection);
        stubOpenJdbcConnection();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);
        // Resolved the way a @BeforeEach parameter resolves it, before beforeTest() would run.
        executor.getConnection();

        executor.releaseIfAfterTestDidNotRun();

        verify(connection).close();
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_afterTestAlreadyRan_doesNotCloseAgain()
            throws Exception
    {
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);
        executor.beforeTest();
        executor.afterTest(false);
        // A connection resolved after afterTest(), as a binding's late parameter resolution
        // could, leaves something for a second release to close, so only the guard stops it.
        executor.getConnection();

        executor.releaseIfAfterTestDidNotRun();

        verify(connection, times(1)).close();
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_noConnectionEverResolved_doesNotAcquireOne()
            throws Exception
    {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);

        executor.releaseIfAfterTestDidNotRun();

        verify(tester, never()).getConnection();
    }

    @Test
    void testAfterTest_listenerIsNoOp_leavesConnectionOpen() throws Exception
    {
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_classicPathListenerIsNoOpWrappedByTheExecutor_leavesConnectionOpen()
            throws Exception
    {
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        final AnnotatedTestExecutor executor = new AnnotatedTestExecutor(
                configurationOf(WithNothing.class), tester, null, false);
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

    @Test
    void testBeforeTest_annotationDrivenConnectionAutoCommitOff_warnsOnce() throws Exception
    {
        // Issue #965: the test case runs the prep and teardown operations with no transaction,
        // so on an autocommit-off connection their writes are never committed. It warns about
        // that with or without @DbUnitExpected.
        stubDisabledConnection();
        when(jdbcConnection.getAutoCommit()).thenReturn(false);
        final DbUnitSetUpOperation setup =
                WithSetupNoPrep.class.getAnnotation(DbUnitSetUpOperation.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final Logger warningLogger = (Logger) LoggerFactory
                .getLogger("org.dbunit.database.connection.AutoCommitOffWarning");
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        warningLogger.addAppender(appender);
        try
        {
            executor.beforeTest();
            executor.afterTest(false);

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("autocommit disabled"))
                    .as("An annotation-driven test whose connection has autocommit off must be"
                            + " warned exactly once.")
                    .hasSize(1);
        } finally
        {
            warningLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testBeforeTest_classicPathConnectionAutoCommitOff_doesNotWarn() throws Exception
    {
        // The classic path resolves this executor's connection only for the read-only row
        // count check baseline - it never runs a write operation through it - so an
        // autocommit-off connection there is not a problem and must not warn.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        lenient().when(jdbcConnection.getAutoCommit()).thenReturn(false);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null, false);
        final Logger warningLogger = (Logger) LoggerFactory
                .getLogger("org.dbunit.database.connection.AutoCommitOffWarning");
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        warningLogger.addAppender(appender);
        try
        {
            executor.beforeTest();
            executor.afterTest(false);

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("The classic path never warns about autocommit off.").isEmpty();
        } finally
        {
            warningLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void testBeforeTest_annotationDrivenNoOpListenerAndTesterOpensNewConnectionOnEachCall_warnsOnce()
            throws Exception
    {
        // The no-op listener leaves the connection open for a real owner, but a tester that
        // opens a new connection on every call has none, so every test would leave one open.
        final JdbcDatabaseTester jdbcTester = jdbcTesterLeavingItsConnectionOpen(null);
        final DbUnitSetUpOperation setup =
                WithSetupNoPrep.class.getAnnotation(DbUnitSetUpOperation.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, jdbcTester, null);
        final ListAppender<ILoggingEvent> appender =
                startCapturingLogOf(LeftOpenConnectionWarning.class);
        try
        {
            executor.beforeTest();
            executor.afterTest(false);

            assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
                    .as("An annotation-driven test that leaves its connection open with a"
                            + " tester that opens a new one on every call must be warned"
                            + " exactly once.")
                    .hasSize(1);
        } finally
        {
            stopCapturingLogOf(LeftOpenConnectionWarning.class, appender);
        }
    }

    @Test
    void testBeforeTest_annotationDrivenCloseAfterTestDefaultAndTesterOpensNewConnectionOnEachCall_doesNotWarn()
            throws Exception
    {
        final JdbcDatabaseTester jdbcTester = jdbcTesterReturningTheConnection(null);
        final DbUnitSetUpOperation setup =
                WithSetupNoPrep.class.getAnnotation(DbUnitSetUpOperation.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, jdbcTester, null);
        final ListAppender<ILoggingEvent> appender =
                startCapturingLogOf(LeftOpenConnectionWarning.class);
        try
        {
            executor.beforeTest();
            executor.afterTest(false);

            assertThat(appender.list)
                    .as("The test case the runtime builds keeps closeConnectionAfterTest true"
                            + " and the tester's listener is not the no-op one, so the connection"
                            + " is closed when the test ends and nothing is left open to warn"
                            + " about.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(LeftOpenConnectionWarning.class, appender);
        }
    }

    @Test
    void testBeforeTest_annotationDrivenNoOpListenerAndTesterReusesItsConnection_doesNotWarn()
            throws Exception
    {
        final JdbcDatabaseTester jdbcTester =
                jdbcTesterLeavingItsConnectionOpen(new CachingConnectionProvider());
        final DbUnitSetUpOperation setup =
                WithSetupNoPrep.class.getAnnotation(DbUnitSetUpOperation.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithSetupNoPrep.class, null, null, setup, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, jdbcTester, null);
        final ListAppender<ILoggingEvent> appender =
                startCapturingLogOf(LeftOpenConnectionWarning.class);
        try
        {
            executor.beforeTest();
            executor.afterTest(false);

            assertThat(appender.list)
                    .as("A tester over a CachingConnectionProvider is the supported way to leave"
                            + " the connection open, so it must not warn.")
                    .isEmpty();
        } finally
        {
            stopCapturingLogOf(LeftOpenConnectionWarning.class, appender);
        }
    }

    // ---- @DbUnitRowCountCheck precedence ----

    @Test
    void testAfterTest_rowCountCheckWithPrepAndNoTeardown_exceptionNamesTheTeardownFix()
            throws Exception
    {
        // The classic first surprise: the baseline is captured before @DbUnitPrep loads, so a
        // prep dataset that is never torn down reads as leaked rows and fails the check. The
        // exception must name the fix - @DbUnitTearDownOperation(DELETE_ALL) or an exclude - not
        // just the per-table delta.
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
                .contains("@DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)")
                .contains("ACCOUNT");
    }

    @Test
    void testAfterTest_rowCountCheckWithPrepButTeardownDeletes_exceptionHasNoTeardownHint()
            throws Exception
    {
        // The hint is gated on the teardown operation that actually ran being NONE: with a real
        // teardown operation, an unrelated count mismatch must not be misattributed to
        // un-torn-down prep.
        stubEnabledConnection("ACCOUNT");
        when(connection.getRowCount("ACCOUNT")).thenReturn(0);
        when(tester.getTearDownOperation()).thenReturn(new RecordingDatabaseOperation());
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
                .doesNotContain("@DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)");
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
                new AnnotatedTestExecutor(configuration, tester, null, false);

        executor.beforeTest();

        verify(connection).createDataSet();
    }

    @Test
    void testBeforeTest_systemPropertyTrueAndAnnotationDisabled_leavesCheckDisabled()
            throws Exception
    {
        // A CI run forces the check on; a method that legitimately leaves rows behind opts out
        // with @DbUnitRowCountCheck(enabled = false), which must stay an opt-out there.
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, "true");
        stubDisabledConnection();
        final DbUnitRowCountCheck rowCountCheck =
                WithDisabledRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                AnnotatedTestExecutorTest.class, null, null, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).createDataSet();
    }

    @Test
    void testBeforeTest_excludeSystemPropertyAndAnnotationExclude_excludesBothTables()
            throws Exception
    {
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                "SYSTEM_PROPERTY_TABLE");
        stubConnection(false, "ACCOUNT", "IGNORED_TABLE", "SYSTEM_PROPERTY_TABLE");
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final DbUnitRowCountCheck rowCountCheck =
                WithExcludedTable.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                AnnotatedTestExecutorTest.class, null, null, null, null, null, rowCountCheck);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        verify(connection, never()).getRowCount("IGNORED_TABLE");
        verify(connection, never()).getRowCount("SYSTEM_PROPERTY_TABLE");
        verify(connection).getRowCount("ACCOUNT");
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

    // ---- annotation-driven: the test case the executor drives ----

    @Test
    void testBeforeTest_withInjectedTestCase_usesInjectedInstance()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        executor.beforeTest();

        final ArgumentCaptor<VerifyTableDefinition[]> definitions =
                ArgumentCaptor.forClass(VerifyTableDefinition[].class);
        final ArgumentCaptor<String[]> prepFiles = ArgumentCaptor.forClass(String[].class);
        final ArgumentCaptor<String[]> expectedFiles = ArgumentCaptor.forClass(String[].class);
        verify(injected).configureTest(definitions.capture(), prepFiles.capture(),
                expectedFiles.capture());
        assertThat(definitions.getValue()).extracting(VerifyTableDefinition::getTableName)
                .as("The tables derived from the expected dataset must be handed to the"
                        + " injected instance.")
                .containsExactly("ACCOUNT", "CUSTOMER");
        assertThat(prepFiles.getValue()).isEqualTo(configuration.getPrepDataFiles());
        assertThat(expectedFiles.getValue()).isEqualTo(configuration.getExpectedDataFiles());
        verify(injected).preTest();
    }

    @Test
    void testBeforeTest_withoutInjectedTestCase_constructsConfiguredClass()
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
        assertThat(RecordingPrepAndExpectedTestCase.instance.isCloseConnectionAfterTest())
                .as("A test case the runtime builds closes its connection after the test; the"
                        + " tester's listener is where that is changed.")
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
                        + " (DataFileLoader, IDatabaseTester) constructor must be"
                        + " rejected with a clear message naming the class, not a raw"
                        + " NoSuchMethodException.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NoMatchingConstructorTestCase.class.getName())
                .hasMessageContaining("no (DataFileLoader, IDatabaseTester) constructor");
    }

    @Test
    void testAfterTest_annotationDrivenBeforeTestNeverRan_doesNotThrow() throws Exception
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
    void testAfterTest_beforeTestFailsAfterInjectingTestCaseButBeforeConfigureTest_postTestNotCalled()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final RecordingPrepAndExpectedTestCase reusedTestCase =
                new RecordingPrepAndExpectedTestCase(mock(DataFileLoader.class), tester)
                {
                    @Override
                    public void setRowCountCheckOverride(final RowCountCheckOverride override)
                    {
                        throw new IllegalStateException(
                                "This instance rejects a row count check override.");
                    }
                };
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, reusedTestCase);
        assertThatThrownBy(executor::beforeTest).isInstanceOf(IllegalStateException.class);

        executor.afterTest(true);

        assertThat(reusedTestCase.postTestCalled)
                .as("postTest() must not run against a test case whose configureTest() never"
                        + " completed this test - here, applying the row count check override"
                        + " failed fast first. A test case reused across several tests (e.g. a"
                        + " @DbUnitTestCase static field) would otherwise run cleanup against"
                        + " an earlier test's stale prep/expected state instead of nothing.")
                .isFalse();
    }

    @Test
    void testAfterTest_injectedTestCaseFailsBeforeConfigureTest_closesTheConnectionLikeAfterAnyOtherFailure()
            throws Exception
    {
        // The connection a @BeforeEach parameter resolved is the executor's to release when
        // beforeTest() fails fast before configureTest(), as postTest()/cleanupData() never runs
        // to close it. An injected instance is used as it was built: it gets no special case, the
        // connection is released under the same ownership rules as for any other failure.
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final CachingConnectionPrepAndExpectedTestCase injected =
                new CachingConnectionPrepAndExpectedTestCase(connection, tester)
                {
                    @Override
                    public void configureTest(final PrepAndExpectedTestData testData)
                    {
                        throw new IllegalStateException("Configuring the test failed on purpose.");
                    }
                };
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();

        assertThatThrownBy(executor::beforeTest)
                .as("A failure before configureTest() must surface.")
                .isInstanceOf(IllegalStateException.class);
        executor.afterTest(true);

        verify(connection).close();
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
        stubOpenJdbcConnection();
        final DbUnitConfig config = WithFailingToTakeItsOperationTestCase.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitSetUpOperation setup =
                WithSetupNoPrep.class.getAnnotation(DbUnitSetUpOperation.class);
        final DbUnitExpected expected = WithFailingToTakeItsOperationTestCase.class
                .getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithFailingToTakeItsOperationTestCase.class, config, null, setup, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.getConnection();

        assertThatThrownBy(executor::beforeTest)
                .isInstanceOf(IllegalStateException.class);
        executor.afterTest(true);

        verify(connection).close();
    }

    @Test
    void testAfterTest_annotationDrivenTestPassed_postTestCalledWithTrue() throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.beforeTest();

        executor.afterTest(false);

        verify(injected).postTest(true);
    }

    @Test
    void testAfterTest_annotationDrivenTestFailed_postTestCalledWithFalse() throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.beforeTest();

        executor.afterTest(true);

        verify(injected).postTest(false);
    }

    @Test
    void testGetConnection_withInjectedTestCase_returnsItsConnectionNotTesters()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        final IDatabaseConnection testCaseConnection = mock(IDatabaseConnection.class);
        when(injected.getReusableConnection()).thenReturn(testCaseConnection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        final IDatabaseConnection resolved = executor.getConnection();

        assertThat(resolved)
                .as("On an annotated test, getConnection() must return the injected test"
                        + " case's own connection instead of resolving a second, independent"
                        + " one from the tester directly - otherwise a Connection/"
                        + "IDatabaseConnection parameter injection would not see the same"
                        + " connection the test case's own prep/verify/cleanup steps use.")
                .isSameAs(testCaseConnection);
        verify(tester, never()).getConnection();
    }

    @Test
    void testBeforeTest_noVerifySpecAndInjectedDefaultTestCase_asksItToVerifyEveryExpectedTable()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        executor.beforeTest();

        final ArgumentCaptor<PrepAndExpectedTestData> testData =
                ArgumentCaptor.forClass(PrepAndExpectedTestData.class);
        verify(injected).configureTest(testData.capture());
        assertThat(testData.getValue())
                .as("With no verify spec, the Default test case takes the tables to verify from"
                        + " the expected dataset it loads itself, with its own loader, which is"
                        + " also the one that later loads it for the comparison.")
                .isEqualTo(PrepAndExpectedTestData.verifyingExpectedTables(new String[0],
                        configuration.getExpectedDataFiles()));
    }

    @Test
    void testBeforeTest_classLevelCatalogAndInjectedDefaultTestCase_handsItTheCatalogsRules()
            throws Exception
    {
        final DbUnitConfig config = WithCatalogAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithCatalogAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithCatalogAndExpected.class, config, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        executor.beforeTest();

        final ArgumentCaptor<PrepAndExpectedTestData> testData =
                ArgumentCaptor.forClass(PrepAndExpectedTestData.class);
        verify(injected).configureTest(testData.capture());
        assertThat(testData.getValue())
                .as("The Default test case still takes the tables from the expected dataset it"
                        + " loads, with the class-level catalog's definitions as the rules of the"
                        + " tables they define, so the dataset is parsed once.")
                .isEqualTo(PrepAndExpectedTestData.verifyingExpectedTables(new String[0],
                        configuration.getExpectedDataFiles(),
                        new VerifyTableDefinition[] {RulesCatalog.ACCOUNT}));
    }

    @Test
    void testBeforeTest_injectedDefaultGivenItsLoaderAfterConstruction_accepts()
            throws Exception
    {
        // A @BeforeEach method of the test class runs after the executor is created and before
        // beforeTest(), and may still set the loader of the test case it injects.
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected =
                spy(new DefaultPrepAndExpectedTestCase(null, tester));
        doNothing().when(injected).configureTest(any(PrepAndExpectedTestData.class));
        doNothing().when(injected).preTest();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        injected.setDataFileLoader(new FlatXmlDataFileLoader());

        executor.beforeTest();

        verify(injected).preTest();
    }

    @Test
    void testBeforeTest_injectedDefaultWithoutALoader_failsSayingWhereToGiveIt()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected =
                new DefaultPrepAndExpectedTestCase(null, tester);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        assertThatThrownBy(executor::beforeTest)
                .as("dbUnit gives an injected test case no loader, so one built without it fails"
                        + " before any step runs, saying so.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no DataFileLoader")
                .hasMessageContaining("where it is built, or in a @BeforeEach method");
    }

    @Test
    void testBeforeTest_noVerifySpecAndInjectedOtherTestCase_derivesTablesThroughTheConfiguredLoader()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);

        executor.beforeTest();

        final ArgumentCaptor<VerifyTableDefinition[]> definitions =
                ArgumentCaptor.forClass(VerifyTableDefinition[].class);
        verify(injected).configureTest(definitions.capture(), any(String[].class),
                any(String[].class));
        assertThat(definitions.getValue()).extracting(VerifyTableDefinition::getTableName)
                .as("A test case that cannot take the tables from the dataset it loads is given"
                        + " the tables of the expected dataset as the configured loader reads"
                        + " it.")
                .containsExactly("ACCOUNT", "CUSTOMER");
    }

    @Test
    void testBeforeAndAfterTest_injectedOtherTestCaseWithoutTester_drivesOnlyItsLifecycle()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, (IDatabaseTester) null, injected);

        executor.beforeTest();
        executor.afterTest(false);
        executor.releaseIfAfterTestDidNotRun();

        final InOrder inOrder = inOrder(injected);
        inOrder.verify(injected).configureTest(any(VerifyTableDefinition[].class),
                any(String[].class), any(String[].class));
        inOrder.verify(injected).preTest();
        inOrder.verify(injected).postTest(true);
        verifyNoMoreInteractions(injected);
        assertThat(executor.getTester())
                .as("A test case that tells dbUnit of no tester leaves the executor without one.")
                .isNull();
        assertThatThrownBy(executor::getConnection)
                .as("Nor can it hand out a connection.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no connection");
    }

    @Test
    void testConstructor_injectedOtherTestCaseAndDeclarationItCannotBeGiven_throws()
    {
        final DbUnitExpected expected = WithExpectedAndRowCountCheck.class
                .getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck = WithExpectedAndRowCountCheck.class
                .getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final PrepAndExpectedTestCase injected = mock(PrepAndExpectedTestCase.class);

        assertThatThrownBy(() -> new AnnotatedTestExecutor(configuration, (IDatabaseTester) null,
                injected))
                        .as("The executor must refuse, before any database work, what the"
                                + " injected test case cannot be given.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("@DbUnitRowCountCheck");
    }

    @Test
    void testAfterTest_injectedTestCaseKeepsItsConnectionOpen_leavesTheResolvedConnectionOpen()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        when(injected.isConnectionLeftOpen()).thenReturn(true);
        when(injected.getReusableConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testAfterTest_injectedTestCaseClosesItsConnection_closesTheResolvedConnection()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        when(injected.isConnectionLeftOpen()).thenReturn(false);
        when(injected.getReusableConnection()).thenReturn(connection);
        when(connection.getConnection()).thenReturn(jdbcConnection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection).close();
    }

    @Test
    void testAfterTest_injectedTestCaseKeepsItsConnectionOpenThroughItsOwnNoOpListener_leavesTheResolvedConnectionOpen()
            throws Exception
    {
        // The test case leaves the connection open for its real owner through the no-op
        // listener it reports itself, which its own cleanupData() honors, so the executor that
        // resolved the same connection for a test method parameter must not close it afterwards.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setSetUpOperation(DatabaseOperation.NONE);
        final DefaultPrepAndExpectedTestCase injected =
                new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), realTester)
                {
                    @Override
                    protected IOperationListener getOperationListener()
                    {
                        return IOperationListener.NO_OP_OPERATION_LISTENER;
                    }
                };
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, injected);
        executor.getConnection();
        executor.beforeTest();

        executor.afterTest(false);

        verify(connection, never()).close();
    }

    @Test
    void testGetConnection_noInjectedTestCase_constructsOneAndReusesItsConnection()
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
                .as("getConnection() on an annotated test must construct prepAndExpectedTestCase"
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
    void testConstructor_annotationDrivenWithProperties_installsNothingOnTheTester()
    {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null);

        verify(tester, never()).setOperationListener(any());
    }

    // ---- a tester that cannot report its listener ----

    @Test
    void testConstructor_testerDoesNotOverrideGetOperationListener_neverReplacesItsListener()
    {
        final TesterNotReportingItsListener legacyTester =
                new TesterNotReportingItsListener(() -> connection);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, legacyTester, null);

        assertThat(legacyTester.listenersSet)
                .as("A tester that does not override IDatabaseTester.getOperationListener()"
                        + " cannot report the listener it carries, so the executor can neither"
                        + " wrap it nor put it back, and must not replace it.")
                .isEmpty();
    }

    @Test
    void testBeforeTest_testerDoesNotOverrideGetOperationListenerAndCarriesNoOpListener_neverClosesWhatOnSetupRetrieves()
            throws Exception
    {
        // A tester that hands out a connection per call and carries the no-op listener, the
        // signal that its connections are managed elsewhere. Had the executor replaced that
        // listener with a closing one, the connection onSetup() retrieves - a different one than
        // the executor resolved for itself, so not shielded by identity - would be closed.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IDatabaseConnection retrievedByOnSetup = mock(IDatabaseConnection.class);
        final Deque<IDatabaseConnection> handedOut =
                new ArrayDeque<>(Arrays.asList(connection, retrievedByOnSetup));
        final TesterNotReportingItsListener legacyTester =
                new TesterNotReportingItsListener(handedOut::remove);
        legacyTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, legacyTester, null, false);

        executor.beforeTest();

        assertThat(legacyTester.onSetupCalls)
                .as("onSetup() still runs on a tester that cannot report its listener.")
                .isEqualTo(1);
        verify(retrievedByOnSetup, never()).close();
    }

    @Test
    void testBeforeAndAfterTest_testerDoesNotOverrideGetOperationListenerAndOnSetupAddsARow_capturesBaselineBeforeOnSetupAndDetectsTheLeak()
            throws Exception
    {
        final DatabaseConfig enabled = new DatabaseConfig();
        enabled.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(enabled);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);
        stubOpenJdbcConnection();
        final AtomicInteger accountRows = new AtomicInteger(5);
        when(connection.getRowCount("ACCOUNT")).thenAnswer(invocation -> accountRows.get());
        final TesterNotReportingItsListener legacyTester =
                new TesterNotReportingItsListener(() -> connection);
        legacyTester.onSetupAction = () -> accountRows.set(6);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, legacyTester, null, false);

        executor.beforeTest();
        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("With no listener to piggyback on, the baseline is captured eagerly before"
                        + " onSetup() (ACCOUNT = 5), so the row onSetup() left behind"
                        + " (ACCOUNT = 6) is caught as a leak rather than folded into the"
                        + " baseline.")
                .isInstanceOf(UnexpectedRowCountException.class);
        assertThat(legacyTester.onTearDownCalls)
                .as("onTearDown() still runs on a tester that cannot report its listener.")
                .isEqualTo(1);
    }

    // ---- classic path (annotationDriven = false): the 3.5.0 lifecycle, preserved ----

    @Test
    void testConstructor_classicPathTesterReportsItsListener_wrapsItForTheTest()
    {
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, tester, null, false);

        verify(tester).setOperationListener(any(ClassicOperationListener.class));
    }

    @Test
    void testConstructor_classicPathTesterCannotReportItsListener_leavesItInPlace()
    {
        // A from-scratch IDatabaseTester predating IDatabaseTester#getOperationListener() has a
        // listener this executor cannot read, so wrapping it would discard it for good.
        final TesterNotReportingItsListener legacyTester =
                new TesterNotReportingItsListener(() -> connection);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);

        new AnnotatedTestExecutor(configuration, legacyTester, null, false);

        assertThat(legacyTester.listenersSet)
                .as("The classic path must not replace a listener it cannot read and put back.")
                .isEmpty();
    }

    @Test
    void testAfterTest_classicPathTesterCarriesCustomListener_forwardedEverythingAndPutBack()
            throws Exception
    {
        // The zero-annotation @ExtendWith(DbUnitExtension.class) style must keep a custom
        // IOperationListener the test author configured on the tester. It is wrapped for the
        // test only to take the row count baseline from the connection onSetup() retrieves, so
        // every callback still reaches it, closing included - none of the connections it
        // handles is shielded, as in the 3.5.0 lifecycle - and it is put back afterward.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IOperationListener custom = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(custom);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        final IOperationListener duringTest = realTester.getOperationListener();

        duringTest.connectionRetrieved(connection);
        duringTest.operationSetUpFinished(connection);
        duringTest.operationTearDownFinished(connection);
        executor.afterTest(false);

        verify(custom).connectionRetrieved(connection);
        verify(custom).operationSetUpFinished(connection);
        verify(custom).operationTearDownFinished(connection);
        assertThat(realTester.getOperationListener())
                .as("The classic path must put the tester's own IOperationListener back once the"
                        + " test is over.")
                .isSameAs(custom);
    }

    @Test
    void testAfterTest_classicPathTesterIgnoresTheExecutorsListener_takesTheBaselineBeforeOnSetup()
            throws Exception
    {
        // A tester whose setOperationListener() ignores the listener the executor installs never
        // hands the executor the connection onSetup() retrieves, so the baseline must be taken
        // before onSetup() inserts the prep row, or the teardown that deletes it reads as a row
        // count difference. The tester keeps the no-op listener, so the fixed in-memory
        // connection stays open across the steps.
        final IDatabaseConnection database = InMemoryDatabaseConnection.create("PUBLIC");
        try
        {
            try (Statement statement = database.getConnection().createStatement())
            {
                statement.execute("CREATE TABLE ACCOUNT (ID INT PRIMARY KEY)");
            }
            database.getConfig().setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
            final DefaultTable account = new DefaultTable("ACCOUNT",
                    new Column[] {new Column("ID", DataType.INTEGER)});
            account.addRow(new Object[] {1});
            final DefaultDatabaseTester listenerIgnoringTester = new DefaultDatabaseTester(database)
            {
                {
                    super.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
                }

                @Override
                public void setOperationListener(final IOperationListener operationListener)
                {
                    // deliberately ignores the executor's listener
                }
            };
            listenerIgnoringTester.setDataSet(new DefaultDataSet(account));
            listenerIgnoringTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
            final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                    .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
            final AnnotatedTestExecutor executor =
                    new AnnotatedTestExecutor(configuration, listenerIgnoringTester, null, false);

            executor.beforeTest();
            final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

            assertThat(thrown)
                    .as("A test that deletes the row its prep inserted leaves every count as it"
                            + " found it, so the row count check must pass.")
                    .isNull();
        } finally
        {
            database.close();
        }
    }

    @Test
    void testAfterTest_classicPathListenerKeepsOnSetupConnectionOpen_leavesItOpen()
            throws Exception
    {
        // A fixed-connection tester whose listener keeps the connection open for later tests:
        // the connection onSetup() retrieved is the listener's to close, as in the 3.5.0
        // lifecycle, so the executor that took it over for the row count baseline must not.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IOperationListener keepsConnectionsOpen = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(keepsConnectionsOpen);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        final IOperationListener duringTest = realTester.getOperationListener();
        duringTest.connectionRetrieved(connection);
        duringTest.operationSetUpFinished(connection);

        executor.afterTest(false);

        verify(connection, never()).close();
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
    void testBeforeAndAfterTest_classicPathRealAbstractDatabaseTester_opensOnlyTheConnectionsTheTesterOpens()
            throws Exception
    {
        // With the row count check off, as it is by default, the baseline has nothing to count,
        // so the classic path takes it from the connection onSetup() retrieves instead of
        // resolving one of its own: a plain JdbcDatabaseTester then opens the two connections
        // the 3.5.0 lifecycle did, one for onSetup() and one for onTearDown(), and never one
        // more, which a bounded pool sized for that lifecycle needs.
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
        realTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final IOperationListener testersOwnListener = realTester.getOperationListener();
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);

        executor.beforeTest();
        executor.afterTest(false);

        assertThat(getConnectionCalls.get())
                .as("The classic path must open only the connections onSetup() and onTearDown()"
                        + " retrieve, as the 3.5.0 lifecycle did.")
                .isEqualTo(2);
        assertThat(realTester.getOperationListener())
                .as("The listener the tester carried must be put back after the test.")
                .isSameAs(testersOwnListener);
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

    @Test
    void testAfterTest_classicPathListenerEnablesTheCheckInConnectionRetrieved_stillDetectsALeak()
            throws Exception
    {
        // The documented place to configure a connection's DatabaseConfig - and so to enable the
        // row count check - is the tester's IOperationListener.connectionRetrieved(). The
        // connection the executor resolves for the baseline is a connection retrieved from the
        // tester like any other, so that listener must run for it before its config is read.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setSetUpOperation(DatabaseOperation.NONE);
        realTester.setOperationListener(new RowCountCheckEnablingListener());
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        executor.beforeTest();
        when(connection.getRowCount("ACCOUNT")).thenReturn(6);

        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("A leaked row must fail a classic-path test whose listener enables the row"
                        + " count check on the connection it is handed.")
                .isInstanceOf(UnexpectedRowCountException.class);
    }

    @Test
    void testAfterTest_testerSharedAcrossMethods_putsTheTestersOwnListenerBack() throws Exception
    {
        // A tester shared across methods (a static @DbUnitTester field) must not keep the last
        // test's executor-installed wrapper - which holds that executor and applies its
        // @DbUnitProperty values - for whoever uses the tester next.
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IOperationListener custom = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setSetUpOperation(DatabaseOperation.NONE);
        realTester.setOperationListener(custom);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        executor.beforeTest();

        executor.afterTest(false);

        assertThat(realTester.getOperationListener())
                .as("The listener the tester carried into the test must be put back.")
                .isSameAs(custom);
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_testerSharedAcrossMethods_putsTheTestersOwnListenerBack()
            throws Exception
    {
        final IOperationListener custom = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(custom);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);

        executor.releaseIfAfterTestDidNotRun();

        assertThat(realTester.getOperationListener())
                .as("A test whose afterTest() never ran must still leave the listener the"
                        + " tester carried into it.")
                .isSameAs(custom);
    }

    @Test
    void testAfterTest_testMethodInstalledItsOwnListener_leavesThatListenerInPlace()
            throws Exception
    {
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setSetUpOperation(DatabaseOperation.NONE);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);
        executor.beforeTest();
        final IOperationListener installedByTheTest = mock(IOperationListener.class);
        realTester.setOperationListener(installedByTheTest);

        executor.afterTest(false);

        assertThat(realTester.getOperationListener())
                .as("A listener the test itself installed after the executor did is the test's"
                        + " to keep, not the executor's to overwrite.")
                .isSameAs(installedByTheTest);
    }

    /** Enables the row count check on every connection it is handed, as a user listener may. */
    private static final class RowCountCheckEnablingListener implements IOperationListener
    {
        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
        }
    }

    @Test
    void testGetConnection_annotationDriven_doesNotOfferTheConnectionToTheTestersListener()
            throws Exception
    {
        // An annotated test's connection is the test case's own, which tells the tester's
        // listener itself when it runs an operation, so the executor does not offer it too.
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final IOperationListener testersListener = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(testersListener);
        final DefaultPrepAndExpectedTestCase injected = injectedDefaultTestCase();
        when(injected.getReusableConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, injected);

        executor.getConnection();

        verify(testersListener, never()).connectionRetrieved(any());
    }

    @Test
    void testGetConnection_classicPath_offersTheConnectionOnceToTheTestersOwnListener()
            throws Exception
    {
        // The tester's own listener, not the executor's wrapper of it: the wrapper would also
        // offer the connection to the row count baseline capture, which waits for beforeTest().
        final DatabaseConfig enabled = new DatabaseConfig();
        enabled.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        lenient().when(connection.getConfig()).thenReturn(enabled);
        stubOpenJdbcConnection();
        final IOperationListener testersListener = mock(IOperationListener.class);
        final IDatabaseTester realTester = new DefaultDatabaseTester(connection);
        realTester.setOperationListener(testersListener);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null, false);

        executor.getConnection();

        verify(testersListener, times(1)).connectionRetrieved(connection);
        verify(connection, never()).createDataSet();
    }

    // ---- ClassicOperationListener: delegate ordering ----

    @Test
    void testConnectionRetrieved_classicPathDelegateConfiguresDatabaseConfig_rowCountCheckSeesTheChange()
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
        new AnnotatedTestExecutor(configuration, tester, null, false);
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
                .as("@DbUnitProperty values must be on the connection as soon as it is"
                        + " resolved, so its first use - an IDatabaseConnection parameter, the"
                        + " row count check baseline or the setup operation - already sees them.")
                .isEqualTo(50);
    }

    @Test
    void testBeforeTest_bothOperationsNoneAndPropertiesConfigured_stillAppliesPropertiesToTheConnection()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitConfig.class);
        final DbUnitSetUpOperation setup =
                WithPropertiesAndSetupNone.class.getAnnotation(DbUnitSetUpOperation.class);
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
                        + " resolved once per annotation-driven test, whatever the configured"
                        + " operations, so there is no configuration under which properties"
                        + " silently fail to apply.")
                .isEqualTo(50);
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
                .as("An invalid @DbUnitProperty value, applied by the test case when it acquires"
                        + " the connection, must be reported clearly, not left as a raw"
                        + " DatabaseUnitException surfacing from deep inside DatabaseConfig.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to apply a databaseConfigProperties value")
                .hasCauseInstanceOf(DatabaseUnitException.class);
    }

    @Test
    void testAfterTest_keptConnectionWithProperties_putsReplacedValuesBack() throws Exception
    {
        final DbUnitConfig config =
                WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.beforeTest();
        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The property must be in force while the test runs.").isEqualTo(50);

        executor.afterTest(false);

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A connection kept open past the test must not carry the test's"
                        + " @DbUnitProperty values into whatever uses it next.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_keptConnectionWithPropertiesResolved_putsReplacedValuesBack()
            throws Exception
    {
        final DbUnitConfig config =
                WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        when(tester.getOperationListener())
                .thenReturn(IOperationListener.NO_OP_OPERATION_LISTENER);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.getConnection();

        executor.releaseIfAfterTestDidNotRun();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A @BeforeEach that fails after a connection parameter resolved the"
                        + " connection must not leave the @DbUnitProperty values on a connection"
                        + " kept open past the test.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testGetConnection_withProperties_leavesApplyingAndRestoringToTheTestCase()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndCachingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndCachingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndCachingTestCase.class, config, null, null, expected, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        lenient().when(connection.getConfig()).thenReturn(realConfig);
        when(tester.getConnection()).thenReturn(connection);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.getConnection();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("On an annotated test the test case applies the values to the"
                        + " connection it owns and puts them back when it finishes; the executor"
                        + " applying them as well would record the test case's values as the"
                        + " ones to restore and leave them on the connection.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testGetConnection_beforeBeforeTest_connectionAlreadyHasTheAnnotationProperties()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithPropertiesAndExpected.class, config, null, null, expected, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        stubOpenJdbcConnection();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.getConnection();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A @BeforeEach parameter resolves the connection before beforeTest() runs,"
                        + " and must already see the @DbUnitProperty values the test declares.")
                .isEqualTo(50);
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_testCaseWithOwnPropertiesAndEarlyConnection_putsReplacedValuesBack()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DatabaseConfig realConfig = new DatabaseConfig();
        when(tester.getConnection()).thenReturn(connection);
        when(connection.getConfig()).thenReturn(realConfig);
        stubOpenJdbcConnection();
        final DefaultPrepAndExpectedTestCase testCase =
                new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), tester, false);
        final Properties ownProperties = new Properties();
        ownProperties.setProperty("batchSize", "50");
        testCase.setDatabaseConfigProperties(ownProperties);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, testCase);
        executor.getConnection();
        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The test case's own property must be in force on the early connection.")
                .isEqualTo(50);

        executor.releaseIfAfterTestDidNotRun();

        assertThat(realConfig.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A @BeforeEach that fails after resolving the connection never reaches"
                        + " cleanupData(), so the test case's values must be put back here, not"
                        + " left on a connection kept past the test.")
                .isEqualTo(new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE));
    }

    @Test
    void testReleaseIfAfterTestDidNotRun_releaseAndUndoBothFail_undoFailureIsSuppressedOnTheReleaseFailure()
            throws Exception
    {
        final DbUnitConfig config =
                WithThrowingUndoTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithThrowingUndoTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithThrowingUndoTestCase.class, config, null, null, expected, null, null);
        when(tester.getConnection()).thenReturn(connection);
        stubOpenJdbcConnection();
        final SQLException closeFailure = new SQLException("Close failed on purpose.");
        doThrow(closeFailure).when(connection).close();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        executor.getConnection();

        final Throwable thrown = catchThrowable(executor::releaseIfAfterTestDidNotRun);

        assertThat(thrown).as("The failure to close the connection is the one that surfaces.")
                .isSameAs(closeFailure);
        assertThat(thrown.getSuppressed())
                .as("A failure undoing the configuration must ride along, not replace it.")
                .hasSize(1);
    }

    @Test
    void testBeforeTest_withProperties_appliesPropertiesToTheBuiltTestCase()
            throws Exception
    {
        final DbUnitConfig config =
                WithPropertiesAndRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndRecordingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndRecordingTestCase.class, config, null, null, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        final Properties applied = RecordingPrepAndExpectedTestCase.instance
                .getDatabaseConfigProperties();
        assertThat(applied.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("@DbUnitProperty values must reach the PrepAndExpectedTestCase itself, as"
                        + " the executor installs no listener on the tester to apply them.")
                .isEqualTo("50");
    }

    @Test
    void testBeforeTest_subclassOverridesSetUpDatabaseConfig_warnsPropertiesMayNotApply()
            throws Exception
    {
        // A DefaultPrepAndExpectedTestCase subclass overriding setUpDatabaseConfig() - the
        // pre-3.6.0 way to configure a DatabaseConfig - drops @DbUnitProperty values unless the
        // override calls super.setUpDatabaseConfig(config), since that hook is where they are
        // applied. Reflection cannot see whether super is called, so warn rather than fail.
        final DbUnitConfig config =
                WithPropertiesAndOverridingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndOverridingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndOverridingTestCase.class, config, null, null, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final Logger configurerLogger =
                (Logger) LoggerFactory.getLogger(TestCaseConfigurer.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        configurerLogger.addAppender(appender);
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
            configurerLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_defaultTestCaseWithProperties_doesNotWarnAboutSetUpDatabaseConfig()
            throws Exception
    {
        // A plain DefaultPrepAndExpectedTestCase (no setUpDatabaseConfig override) applies
        // @DbUnitProperty values through its own setUpDatabaseConfig - no trap, no warning.
        final DbUnitConfig config =
                WithPropertiesAndRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithPropertiesAndRecordingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithPropertiesAndRecordingTestCase.class, config, null, null, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);
        final Logger configurerLogger =
                (Logger) LoggerFactory.getLogger(TestCaseConfigurer.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        configurerLogger.addAppender(appender);
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
            configurerLogger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeTest_withFailureHandler_appliesToTheBuiltTestCase()
            throws Exception
    {
        final DbUnitConfig config =
                WithFailureHandlerAndRecordingTestCase.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithFailureHandlerAndRecordingTestCase.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithFailureHandlerAndRecordingTestCase.class, config, null, null, expected, null,
                null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, null);

        executor.beforeTest();

        assertThat(RecordingPrepAndExpectedTestCase.instance.getFailureHandler())
                .as("@DbUnitConfig.failureHandler() must be applied to the built"
                        + " PrepAndExpectedTestCase.")
                .isInstanceOf(DiffCollectingFailureHandler.class);
    }

    @Test
    void testBeforeTest_noFailureHandler_leavesTheInstancesOwnHandlerAlone()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // Nothing configured is not a request to change the handler an injected instance was
        // built with; a reused instance is protected from an earlier test's handler by
        // restoring it afterward, not by overwriting it here.
        verify(mockTestCase, never()).setFailureHandler(any());
    }

    @Test
    void testBeforeTest_withRowCountCheck_appliesOverrideToInjectedTestCase()
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
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase)
                .setRowCountCheckOverride(new RowCountCheckOverride(true, new String[0]));
    }

    @Test
    void testBeforeTest_rowCountCheckAndTesterConnectionNull_stillAppliesOverrideWithoutResolvingConnection()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        // tester.getConnection() is unstubbed here, so would return null (e.g. a test double
        // with no real connection) if ever called; the override must still apply, proving it
        // needs no connection of this executor's own to build - the wasted-connection bug this
        // pass-through exists to fix.
        verify(mockTestCase)
                .setRowCountCheckOverride(new RowCountCheckOverride(true, new String[0]));
        verify(tester, never()).getConnection();
    }

    @Test
    void testBeforeTest_noRowCountCheck_neverTouchesTheOverride()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setRowCountCheckOverride(any());
    }

    @Test
    void testBeforeTest_noProperties_leavesTheInstancesOwnPropertiesAlone()
            throws Exception
    {
        final DbUnitExpected expected = WithExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(WithExpected.class, null, null, null, expected, null, null);
        final DefaultPrepAndExpectedTestCase mockTestCase = injectedDefaultTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, mockTestCase);

        executor.beforeTest();

        verify(mockTestCase, never()).setDatabaseConfigProperties(any());
    }

    @Test
    void testAfterTest_rowCountCheckOnSharedInstance_clearsTheOverrideForTheNextMethod()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final DefaultPrepAndExpectedTestCase shared = spy(
                new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), tester, true));
        doNothing().when(shared).configureTest(any(PrepAndExpectedTestData.class));
        doNothing().when(shared).preTest();
        doNothing().when(shared).postTest(anyBoolean());
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, shared);
        executor.beforeTest();
        assertThat(shared.getRowCountCheckOverride())
                .as("The declared check must be the override while the test runs.")
                .isEqualTo(new RowCountCheckOverride(true, new String[0]));

        executor.afterTest(false);

        assertThat(shared.getRowCountCheckOverride())
                .as("The shared instance must carry no override into the next method.")
                .isNull();
    }

    @Test
    void testAfterTest_postTestFailsOnSharedInstance_stillClearsTheRowCountCheckOverride()
            throws Exception
    {
        final DbUnitExpected expected =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitExpected.class);
        final DbUnitRowCountCheck rowCountCheck =
                WithExpectedAndRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithExpectedAndRowCountCheck.class, null, null, null, expected, null,
                rowCountCheck);
        final DefaultPrepAndExpectedTestCase shared = spy(
                new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), tester, true));
        doNothing().when(shared).configureTest(any(PrepAndExpectedTestData.class));
        doNothing().when(shared).preTest();
        doThrow(new AssertionError("verify boom")).when(shared).postTest(anyBoolean());
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, shared);
        executor.beforeTest();

        assertThatThrownBy(() -> executor.afterTest(false)).isInstanceOf(AssertionError.class);

        assertThat(shared.getRowCountCheckOverride())
                .as("A failed postTest() must still leave the shared instance with no override.")
                .isNull();
    }

    // ---- row count check baseline: piggybacking on onSetup()'s own connection ----

    @Test
    void testBeforeTest_realAbstractDatabaseTesterDefaultOperationsNoRowCountCheckDeclared_resolvesExactlyOneConnection()
            throws Exception
    {
        // A mocked IDatabaseTester (as almost every other test in this file uses) never
        // exercises this path at all: ClassicRowCountCheck.canPiggybackBaselineOn() trusts
        // only a real AbstractDatabaseTester, and a plain Mockito mock of the IDatabaseTester
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
                new AnnotatedTestExecutor(configuration, realTester, null, false);

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
    void testBeforeAndAfterTest_annotationDrivenMultiplePrepFiles_resolvesExactlyOneConnection()
            throws Exception
    {
        // Combining two @DbUnitPrep files reads the connection's
        // FEATURE_CASE_SENSITIVE_TABLE_NAMES. The test case reads it from the one connection it
        // keeps for the whole test, so combining costs no connection of its own, which a
        // fresh-connection tester (a plain JdbcDatabaseTester) would pay with a second physical
        // connection every test. Case sensitivity is off here, so prep.xml's ACCOUNT and
        // prep-lowercase.xml's account merge into one table and CLEAN_INSERT against the single
        // pre-created ACCOUNT table succeeds.
        final IDatabaseConnection sharedConnection = InMemoryDatabaseConnection.create("PUBLIC");
        try
        {
            sharedConnection.getConnection().createStatement()
                    .execute("CREATE TABLE ACCOUNT (ID INTEGER)");
            final AtomicInteger getConnectionCalls = new AtomicInteger();
            final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
            {
                @Override
                public IDatabaseConnection getConnection()
                {
                    getConnectionCalls.incrementAndGet();
                    return sharedConnection;
                }
            };
            realTester.setDataSet(new DefaultDataSet());
            final DbUnitPrep prep =
                    WithTwoDifferentlyCasedPrepFiles.class.getAnnotation(DbUnitPrep.class);
            final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                    WithTwoDifferentlyCasedPrepFiles.class, null, prep, null, null, null, null);
            final AnnotatedTestExecutor executor =
                    new AnnotatedTestExecutor(configuration, realTester, null);

            executor.beforeTest();
            executor.afterTest(false);

            assertThat(getConnectionCalls.get())
                    .as("Combining several @DbUnitPrep files must read the case sensitivity"
                            + " from the connection the test case keeps for the test, not resolve"
                            + " a second one.")
                    .isEqualTo(1);
        } finally
        {
            sharedConnection.close();
        }
    }

    @Test
    void testBeforeAndAfterTest_annotationDrivenMultiplePrepFilesCaseSensitive_keepsTheTablesApartOnOneConnection()
            throws Exception
    {
        // With case sensitive table names on, combining the two @DbUnitPrep files keeps
        // prep.xml's ACCOUNT and prep-lowercase.xml's account as two tables, and the setup
        // operation runs on that dataset - with exactly one connection, the one the test case
        // keeps for the whole test.
        final IDatabaseConnection sharedConnection = InMemoryDatabaseConnection.create("PUBLIC");
        try
        {
            sharedConnection.getConfig()
                    .setFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES, true);
            final AtomicReference<IDataSet> datasetOperatedOn = new AtomicReference<>();
            final DatabaseOperation recordingOperation = new DatabaseOperation()
            {
                @Override
                public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                {
                    datasetOperatedOn.set(dataSet);
                }
            };
            final AtomicInteger getConnectionCalls = new AtomicInteger();
            final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
            {
                @Override
                public IDatabaseConnection getConnection()
                {
                    getConnectionCalls.incrementAndGet();
                    return sharedConnection;
                }

                @Override
                public DatabaseOperation getSetUpOperation()
                {
                    return recordingOperation;
                }
            };
            final DbUnitPrep prep =
                    WithTwoDifferentlyCasedPrepFiles.class.getAnnotation(DbUnitPrep.class);
            final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                    WithTwoDifferentlyCasedPrepFiles.class, null, prep, null, null, null, null);
            final AnnotatedTestExecutor executor =
                    new AnnotatedTestExecutor(configuration, realTester, null);

            executor.beforeTest();
            executor.afterTest(false);

            assertThat(datasetOperatedOn.get().getTableNames())
                    .as("With case sensitive table names on, the setup operation must run on a"
                            + " dataset that keeps the two differently cased tables apart.")
                    .containsExactlyInAnyOrder("ACCOUNT", "account");
            assertThat(getConnectionCalls.get())
                    .as("Reading the case sensitivity must not resolve a second connection.")
                    .isEqualTo(1);
        } finally
        {
            sharedConnection.close();
        }
    }

    @Test
    void testAfterTest_classicPathTrustedPiggybackNeverFires_safetyNetBaselineCatchesALeakedRow()
            throws Exception
    {
        // ClassicRowCountCheck.canPiggybackBaselineOn() trusts a real AbstractDatabaseTester
        // that keeps the executor's listener, does not override onSetup() and has a setup
        // operation other than NONE. This tester breaks that contract: its setup operation is
        // CLEAN_INSERT when the executor asks and NONE once onSetup() runs, so onSetup()
        // retrieves no connection and the piggyback never fires. The safety net after onSetup()
        // must still capture a baseline, or the row the test leaves behind goes unnoticed.
        final IDatabaseConnection database = InMemoryDatabaseConnection.create("PUBLIC");
        try
        {
            try (Statement statement = database.getConnection().createStatement())
            {
                statement.execute("CREATE TABLE ACCOUNT (ID INT PRIMARY KEY)");
            }
            database.getConfig().setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
            final AtomicInteger setUpOperationCalls = new AtomicInteger();
            final AbstractDatabaseTester contractBreakingTester = new AbstractDatabaseTester()
            {
                @Override
                public IDatabaseConnection getConnection()
                {
                    return database;
                }

                @Override
                public DatabaseOperation getSetUpOperation()
                {
                    return setUpOperationCalls.getAndIncrement() == 0
                            ? DatabaseOperation.CLEAN_INSERT
                            : DatabaseOperation.NONE;
                }
            };
            contractBreakingTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
            contractBreakingTester.setDataSet(new DefaultDataSet());
            final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                    .from(AnnotatedTestExecutorTest.class, null, null, null, null, null, null);
            final AnnotatedTestExecutor executor =
                    new AnnotatedTestExecutor(configuration, contractBreakingTester, null, false);

            executor.beforeTest();
            try (Statement statement = database.getConnection().createStatement())
            {
                statement.execute("INSERT INTO ACCOUNT VALUES (1)");
            }
            final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

            assertThat(thrown)
                    .as("The safety net captured a baseline after onSetup(), so the row the test"
                            + " left in ACCOUNT is caught as a leak.")
                    .isInstanceOf(UnexpectedRowCountException.class);
        } finally
        {
            database.close();
        }
    }

    @Test
    void testBeforeTest_noVerifySpec_parsesEachExpectedFileOnceAndVerifiesItsTables()
            throws Exception
    {
        CountingFlatXmlDataFileLoader.loadedPaths.clear();
        final AbstractDatabaseTester realTester = new AbstractDatabaseTester()
        {
            @Override
            public IDatabaseConnection getConnection() throws Exception
            {
                return InMemoryDatabaseConnection.create("PUBLIC");
            }
        };
        final DbUnitConfig config =
                WithCountingLoaderAndExpected.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                WithCountingLoaderAndExpected.class.getAnnotation(DbUnitExpected.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration.from(
                WithCountingLoaderAndExpected.class, config, null, null, expected, null, null);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, realTester, null);

        executor.beforeTest();

        assertThat(CountingFlatXmlDataFileLoader.loadedPaths)
                .as("With no verify spec declared, the tables to verify come from the expected"
                        + " dataset the test case loads anyway, so a file is parsed once per"
                        + " test, not once to list its tables and again to compare.")
                .hasSize(1);
        final DefaultPrepAndExpectedTestCase testCase =
                (DefaultPrepAndExpectedTestCase) executor.getPrepAndExpectedTestCase();
        assertThat(testCase.getVerifyTableDefs())
                .as("One default definition is still derived per table of the expected dataset.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "CUSTOMER");
        executor.afterTest(true);
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
        // other way. ClassicRowCountCheck.canPiggybackBaselineOn() must decline it, so the
        // baseline is captured eagerly BEFORE onSetup() - a baseline captured afterward (as the
        // after-the-fact safety net would) already includes the rows onSetup() inserts, hiding
        // the very leak the check exists to catch. Here onSetup() bumps ACCOUNT 5 -> 6; the
        // baseline must read 5.
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
                new AnnotatedTestExecutor(configuration, overridingTester, null, false);

        executor.beforeTest();
        final Throwable thrown = catchThrowable(() -> executor.afterTest(false));

        assertThat(thrown)
                .as("The baseline must be captured before onSetup() (reading ACCOUNT = 5), so"
                        + " the row onSetup() left behind (ACCOUNT = 6) is caught as a leak"
                        + " rather than folded into the baseline.")
                .isInstanceOf(UnexpectedRowCountException.class);
    }

    // ---- helpers ----

    /**
     * A real {@link JdbcDatabaseTester}, so the code under test sees the real class and its
     * real answer to isReusingConnection(), whose getConnection() hands out the mocked
     * {@link #connection} instead of opening a database connection.
     */
    private JdbcDatabaseTester jdbcTesterReturningTheConnection(
            final CachingConnectionProvider provider) throws Exception
    {
        final JdbcDatabaseTester jdbcTester = spy(new JdbcDatabaseTester("org.h2.Driver",
                "jdbc:h2:mem:left_open_connection_executor", null, null, null, provider));
        lenient().doReturn(connection).when(jdbcTester).getConnection();
        when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        return jdbcTester;
    }

    /**
     * A {@link #jdbcTesterReturningTheConnection(CachingConnectionProvider)} whose listener is
     * the no-op one, the signal that its connections are managed elsewhere, as it is before the
     * executor under test wraps it.
     */
    private JdbcDatabaseTester jdbcTesterLeavingItsConnectionOpen(
            final CachingConnectionProvider provider) throws Exception
    {
        final JdbcDatabaseTester jdbcTester = jdbcTesterReturningTheConnection(provider);
        jdbcTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        clearInvocations(jdbcTester);
        return jdbcTester;
    }

    private static ListAppender<ILoggingEvent> startCapturingLogOf(final Class<?> loggedClass)
    {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggedClass);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void stopCapturingLogOf(final Class<?> loggedClass,
            final ListAppender<ILoggingEvent> appender)
    {
        final Logger logger = (Logger) LoggerFactory.getLogger(loggedClass);
        logger.detachAppender(appender);
        appender.stop();
    }

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
     * {@code TestScopedConnection.release()} expects before it will close a memoized
     * connection. A test exercising the already-closed guard re-stubs
     * {@code jdbcConnection.isClosed()} to {@code true} afterward.
     */
    private void stubOpenJdbcConnection() throws Exception
    {
        // lenient(): most callers only exercise beforeTest(), never reaching the release()
        // already-closed check this stubs for - strict stubbing would flag that as unused
        // rather than as the shared default it is.
        lenient().when(connection.getConnection()).thenReturn(jdbcConnection);
        lenient().when(jdbcConnection.isClosed()).thenReturn(false);
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} mock as its owner builds one for injection: with
     * the loader dbUnit needs of it to read the test's files, as dbUnit gives an injected instance
     * none.
     */
    private DefaultPrepAndExpectedTestCase injectedDefaultTestCase()
    {
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);
        lenient().when(testCase.getDataFileLoader()).thenReturn(mock(DataFileLoader.class));
        return testCase;
    }

    /**
     * A {@link RecordingAbstractDatabaseTester} that hands out the {@link #connection} mock, which
     * reports a default {@link DatabaseConfig} and an open JDBC connection.
     */
    private RecordingAbstractDatabaseTester testerWithConnection() throws Exception
    {
        final RecordingAbstractDatabaseTester ownTester = new RecordingAbstractDatabaseTester();
        ownTester.connection = connection;
        lenient().when(connection.getConfig()).thenReturn(new DatabaseConfig());
        stubOpenJdbcConnection();
        return ownTester;
    }

    /**
     * Resolves the configuration of a test that declares what {@code annotatedClasses} declare,
     * the first class declaring an annotation type naming the one that counts.
     */
    private static AnnotatedTestConfiguration configurationOf(
            final Class<?>... annotatedClasses)
    {
        FakeAnnotationLookup lookup = new FakeAnnotationLookup(AnnotatedTestExecutorTest.class);
        for (final Class<?> annotatedClass : annotatedClasses)
        {
            for (final Annotation annotation : annotatedClass.getDeclaredAnnotations())
            {
                lookup = lookup.finding(annotation, annotatedClass);
            }
        }
        return AnnotatedTestConfiguration.from(AnnotatedTestExecutorTest.class, lookup);
    }

    // ---- fixtures ----

    static class WithNothing
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class)
    static class BuildsRecordingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = MismatchingTestCase.class)
    static class BuildsMismatchingTestCase
    {
    }

    /**
     * A built test case whose {@code postTest()} reports a row count mismatch that already has a
     * failure attached to it, as a close failure would be.
     */
    public static class MismatchingTestCase extends DefaultPrepAndExpectedTestCase
    {
        static UnexpectedRowCountException thrown;

        public MismatchingTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester)
        {
            super(dataFileLoader, tester);
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
        public void postTest(final boolean verifyData) throws Exception
        {
            thrown = new UnexpectedRowCountException(
                    Arrays.asList(new RowCountDifference("ACCOUNT", 0, 5)));
            thrown.addSuppressed(new IllegalStateException("close failed"));
            throw thrown;
        }
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitPrep("prep.xml")
    @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
    @DbUnitExpected("expected.xml")
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    @DbUnitRowCountCheck(exclude = "IGNORED_TABLE")
    static class WithEveryAnnotation
    {
    }

    /** Records the tables of every dataset it is run on and touches no database. */
    private static final class RecordingDatabaseOperation extends DatabaseOperation
    {
        private final List<List<String>> executedTableNames = new ArrayList<>();

        @Override
        public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
                throws DatabaseUnitException
        {
            executedTableNames.add(Arrays.asList(dataSet.getTableNames()));
        }

        private List<List<String>> getExecutedTableNames()
        {
            return executedTableNames;
        }
    }

    @DbUnitPrep("prep.xml")
    static class WithPrep
    {
    }

    @DbUnitPrep({"prep.xml", "prep-lowercase.xml"})
    static class WithTwoDifferentlyCasedPrepFiles
    {
    }

    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
    @DbUnitExpected("expected.xml")
    static class WithDataFileLoaderAndExpected
    {
    }

    @DbUnitSetUpOperation(DbUnitOperation.NONE)
    static class WithSetupNoPrep
    {
    }

    @DbUnitSetUpOperation
    static class WithBareSetupNoPrep
    {
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class WithTearDown
    {
    }

    @DbUnitTearDownOperation
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

    @DbUnitRowCountCheck(enabled = false)
    static class WithDisabledRowCountCheck
    {
    }

    @DbUnitExpected("expected.xml")
    static class WithExpected
    {
    }

    @DbUnitConfig(verifyDefinitions = RulesCatalog.class)
    @DbUnitExpected("expected.xml")
    static class WithCatalogAndExpected
    {
    }

    /** Public, so the resolver can read its constants whatever package the test runs in. */
    public static class RulesCatalog
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", new String[] {"UPDATED_AT"});
    }

    @DbUnitExpected("only-the-injected-loader-reads.custom")
    static class WithOnlyTheInjectedLoaderReadableExpected
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

    @DbUnitConfig(dataFileLoader = CountingFlatXmlDataFileLoader.class)
    @DbUnitExpected("expected.xml")
    static class WithCountingLoaderAndExpected
    {
    }

    /** Records every path it is asked to load, to prove how often a file is parsed. */
    public static class CountingFlatXmlDataFileLoader extends FlatXmlDataFileLoader
    {
        static final List<String> loadedPaths = new ArrayList<>();

        @Override
        public IDataSet load(final String filename)
        {
            loadedPaths.add(filename);
            return super.load(filename);
        }
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
    @DbUnitSetUpOperation(DbUnitOperation.NONE)
    static class WithPropertiesAndSetupNone
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndExpected
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = ThrowingPostTestAndUndoTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithThrowingPostTestAndUndoTestCase
    {
    }

    /** A {@link ThrowingUndoTestCase} whose {@code postTest()} fails as well. */
    public static class ThrowingPostTestAndUndoTestCase extends ThrowingUndoTestCase
    {
        public ThrowingPostTestAndUndoTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester) throws Exception
        {
            super(dataFileLoader, tester);
        }

        @Override
        public void postTest(final boolean verifyData)
        {
            throw new IllegalStateException("postTest failed on purpose.");
        }
    }

    @DbUnitConfig(prepAndExpectedTestCase = ThrowingUndoTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithThrowingUndoTestCase
    {
    }

    /**
     * A constructible test case reporting and setting its properties, whose setter fails: the
     * undo step the configurer records for it throws.
     */
    public static class ThrowingUndoTestCase extends CachingConnectionPrepAndExpectedTestCase
    {
        public ThrowingUndoTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester) throws Exception
        {
            super(dataFileLoader, tester);
        }

        @Override
        public Properties getDatabaseConfigProperties()
        {
            return null;
        }

        @Override
        public void setDatabaseConfigProperties(final Properties databaseConfigProperties)
        {
            throw new IllegalStateException("Undo failed on purpose.");
        }
    }

    @DbUnitConfig(prepAndExpectedTestCase = CachingConnectionPrepAndExpectedTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithConstructedCachingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = FailingToTakeItsOperationTestCase.class)
    @DbUnitExpected("expected.xml")
    static class WithFailingToTakeItsOperationTestCase
    {
    }

    /** A constructible test case that fails when it is given its setup operation. */
    public static class FailingToTakeItsOperationTestCase
            extends CachingConnectionPrepAndExpectedTestCase
    {
        public FailingToTakeItsOperationTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester) throws Exception
        {
            super(dataFileLoader, tester);
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation)
        {
            throw new IllegalStateException("Set up failed on purpose.");
        }
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class,
            failureHandler = DiffCollectingFailureHandler.class)
    @DbUnitExpected("expected.xml")
    static class WithFailureHandlerAndRecordingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = RecordingPrepAndExpectedTestCase.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndRecordingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = CachingConnectionPrepAndExpectedTestCase.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndCachingTestCase
    {
    }

    @DbUnitConfig(prepAndExpectedTestCase = OverridesSetUpDatabaseConfigTestCase.class,
            properties = @DbUnitProperty(name = "batchSize", value = "50"))
    @DbUnitExpected("expected.xml")
    static class WithPropertiesAndOverridingTestCase
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
        public OverridesSetUpDatabaseConfigTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester)
        {
            super(dataFileLoader, tester);
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
     * Records the constructor arguments and lifecycle calls it received, standing in for a real
     * {@link org.dbunit.DefaultPrepAndExpectedTestCase} so these tests do not need a real
     * database connection.
     */
    public static class RecordingPrepAndExpectedTestCase extends DefaultPrepAndExpectedTestCase
    {
        static RecordingPrepAndExpectedTestCase instance;
        static IDatabaseTester lastTester;
        static boolean throwFromConstructor;

        boolean preTestCalled;
        boolean postTestCalled;

        DatabaseOperation setUpOperationInEffect() throws Exception
        {
            return getSetUpOperation();
        }

        DatabaseOperation tearDownOperationInEffect() throws Exception
        {
            return getTearDownOperation();
        }

        public RecordingPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester)
        {
            super(dataFileLoader, tester);
            if (throwFromConstructor)
            {
                throw new RuntimeException("simulated constructor failure");
            }
            instance = this;
            lastTester = tester;
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
        public void postTest(final boolean verifyData)
        {
            postTestCalled = true;
        }
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} with only a no-arg constructor, lacking the
     * {@code (DataFileLoader, IDatabaseTester)} constructor shape that
     * {@link AnnotatedTestExecutor} requires when it constructs the configured class.
     */
    public static class NoMatchingConstructorTestCase extends DefaultPrepAndExpectedTestCase
    {
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} that caches its {@code getReusableConnection()}
     * result the way a real one reused across test methods would. Two constructors: one for
     * direct injection in a test, one {@code (DataFileLoader, IDatabaseTester)} so
     * {@code AnnotatedTestExecutor} can construct it from
     * {@code @DbUnitConfig.prepAndExpectedTestCase}.
     */
    public static class CachingConnectionPrepAndExpectedTestCase
            extends RecordingPrepAndExpectedTestCase
    {
        private final IDatabaseConnection cached;
        boolean configureTestCalled;

        CachingConnectionPrepAndExpectedTestCase(final IDatabaseConnection cached,
                final IDatabaseTester tester)
        {
            super(new FlatXmlDataFileLoader(), tester);
            this.cached = cached;
        }

        CachingConnectionPrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
                final IDatabaseTester tester) throws Exception
        {
            super(dataFileLoader, tester);
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
     * An {@link IDatabaseTester} type that overrides {@code getOperationListener()}, as
     * {@link AbstractDatabaseTester} does, so a mock of it stands in for a tester that reports
     * the listener it carries. A mock of {@link IDatabaseTester} itself does not: Mockito leaves
     * the interface's default method where it is, so the mock's type does not override it.
     */
    abstract static class ListenerReportingTester implements IDatabaseTester
    {
        @Override
        public abstract IOperationListener getOperationListener();
    }

    /**
     * An {@link IDatabaseTester} written before {@code getOperationListener()} existed: it
     * stores the listener it is given and notifies it the way
     * {@link AbstractDatabaseTester#onSetup()} and {@code onTearDown()} do for an operation that
     * is not {@code NONE}, but, not overriding {@code getOperationListener()}, reports none.
     * Records every listener it is given, and runs no database operation.
     */
    static class TesterNotReportingItsListener implements IDatabaseTester
    {
        final List<IOperationListener> listenersSet = new ArrayList<>();
        Runnable onSetupAction = () ->
        {
        };
        int onSetupCalls;
        int onTearDownCalls;

        private final Supplier<IDatabaseConnection> connections;
        private IOperationListener listener;
        private IDataSet dataSet;
        private DatabaseOperation setUpOperation = DatabaseOperation.CLEAN_INSERT;
        private DatabaseOperation tearDownOperation = DatabaseOperation.NONE;

        TesterNotReportingItsListener(final Supplier<IDatabaseConnection> connections)
        {
            this.connections = connections;
        }

        @Override
        public void closeConnection(final IDatabaseConnection connection) throws Exception
        {
            connection.close();
        }

        @Override
        public IDatabaseConnection getConnection()
        {
            return connections.get();
        }

        @Override
        public IDataSet getDataSet()
        {
            return dataSet;
        }

        @Override
        public DatabaseOperation getSetUpOperation()
        {
            return setUpOperation;
        }

        @Override
        public DatabaseOperation getTearDownOperation()
        {
            return tearDownOperation;
        }

        @Override
        public void setDataSet(final IDataSet dataSet)
        {
            this.dataSet = dataSet;
        }

        @Override
        public void setSchema(final String schema)
        {
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation)
        {
            this.setUpOperation = setUpOperation;
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation)
        {
            this.tearDownOperation = tearDownOperation;
        }

        @Override
        public void onSetup() throws Exception
        {
            onSetupCalls++;
            onSetupAction.run();
            runOperationNotifications(setUpOperation, true);
        }

        @Override
        public void onTearDown() throws Exception
        {
            onTearDownCalls++;
            runOperationNotifications(tearDownOperation, false);
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener)
        {
            listenersSet.add(operationListener);
            listener = operationListener;
        }

        private void runOperationNotifications(final DatabaseOperation operation,
                final boolean setUp) throws Exception
        {
            if (operation == DatabaseOperation.NONE)
            {
                return;
            }
            if (listener == null)
            {
                listener = new DefaultOperationListener();
            }
            final IDatabaseConnection retrieved = getConnection();
            try
            {
                listener.connectionRetrieved(retrieved);
            } finally
            {
                if (setUp)
                {
                    listener.operationSetUpFinished(retrieved);
                } else
                {
                    listener.operationTearDownFinished(retrieved);
                }
            }
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
        final List<DatabaseOperation> setUpOperationsRun = new ArrayList<>();
        final List<DatabaseOperation> tearDownOperationsRun = new ArrayList<>();

        /** The name of every setter called on this tester, in order. */
        final List<String> writes = new ArrayList<>();

        /** The connection to hand out; none unless a test gives one. */
        IDatabaseConnection connection;

        @Override
        public IDatabaseConnection getConnection()
        {
            return connection;
        }

        @Override
        public void setDataSet(final IDataSet dataSet)
        {
            writes.add("setDataSet");
            super.setDataSet(dataSet);
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation)
        {
            writes.add("setSetUpOperation");
            super.setSetUpOperation(setUpOperation);
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation)
        {
            writes.add("setTearDownOperation");
            super.setTearDownOperation(tearDownOperation);
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener)
        {
            writes.add("setOperationListener");
            super.setOperationListener(operationListener);
        }

        @Override
        public void onSetup()
        {
            setUpOperationsRun.add(getSetUpOperation());
        }

        @Override
        public void onTearDown()
        {
            tearDownOperationsRun.add(getTearDownOperation());
        }
    }
}
