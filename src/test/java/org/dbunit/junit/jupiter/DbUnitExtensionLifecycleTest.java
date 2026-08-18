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
import static org.assertj.core.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCaseSteps;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.opentest4j.TestAbortedException;

/**
 * Lifecycle integration tests for {@link DbUnitExtension} that verify execution
 * ordering and teardown behavior under failure using {@link EngineTestKit}.
 */
class DbUnitExtensionLifecycleTest {
    @Test
    void testBeforeAndAfterTestExecution_testMethodSucceeds_callbacksRunInOrder() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(LifecycleOrderSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testAfterTestExecution_testMethodFails_onTearDownCalled() {
        FailingTestSample.CALL_LOG.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingTestSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(FailingTestSample.CALL_LOG)
                .as("onTearDown() must be called even when the test method fails.")
                .contains("onTearDown");
    }

    @Test
    void testBeforeTestExecution_onSetupFails_onTearDownCalledAndTestMethodSkipped() {
        FailingSetupSample.CALL_LOG.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingSetupSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(FailingSetupSample.CALL_LOG)
                .as("The test method must not run and onTearDown() must still be called"
                        + " when onSetup() throws.")
                .containsExactly("onTearDown");
    }

    @Test
    void testBeforeTestExecution_nestedTestClass_resolvesTesterFromEnclosingInstance() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(EnclosingWithNested.NestedSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTesterField_shadowsEnclosingField() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(EnclosingWithNestedTesterOverride.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTestCaseField_notAmbiguousWithEnclosingTesterField() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        EnclosingWithDbUnitTesterAndNestedDbUnitTestCase.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTesterField_notAmbiguousWithEnclosingTestCaseField() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        EnclosingWithDbUnitTestCaseAndNestedDbUnitTester.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_composedDbUnitTestAnnotation_appliesSetup() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ComposedAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_metaAnnotationCarryingConfigAndPrep_appliesBoth() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(MetaAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testAfterTestExecution_expectedAnnotationAndPassingTest_runsVerification() {
        ExpectedPathSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ExpectedPathSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(ExpectedPathSample.testCase.postTestCalls)
                .as("A passing test must run verification (postTest(true)).")
                .containsExactly(true);
    }

    @Test
    void testAfterTestExecution_expectedAnnotationAndFailingTest_skipsVerificationAndReportsOriginalFailure() {
        FailingExpectedPathSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingExpectedPathSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(FailingExpectedPathSample.testCase.postTestCalls)
                .as("A failing test must skip verification (postTest(false)).")
                .containsExactly(false);
    }

    @Test
    void testAfterTestExecution_cleanupThrowsAfterTestFailure_attachesCleanupAsSuppressed() {
        SuppressedCleanupSample.testCase = new RecordingPrepAndExpectedTestCase();
        SuppressedCleanupSample.testCase.postTestFailure =
                new RuntimeException("cleanup failure");

        final EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(SuppressedCleanupSample.class)).execute();

        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
        final Event failedEvent = results.testEvents().failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));
        final Throwable reported = failedEvent
                .getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported)
                .as("The original test failure must still be the reported failure.")
                .hasMessageContaining("intentional test failure");
        assertThat(reported.getSuppressed())
                .as("A cleanup failure after the test already failed must be attached as"
                        + " suppressed, not replace the original failure.")
                .anyMatch(t -> "cleanup failure".equals(t.getMessage()));
    }

    @Test
    void testAfterTestExecution_cleanupThrowsAfterTestAborted_promotesCleanupFailureOverAbort() {
        AbortedTestThenCleanupFailsSample.testCase = new RecordingPrepAndExpectedTestCase();
        AbortedTestThenCleanupFailsSample.testCase.postTestFailure =
                new RuntimeException("cleanup failure after abort");

        final EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AbortedTestThenCleanupFailsSample.class)).execute();

        results.testEvents().assertStatistics(stats -> stats.started(1).aborted(0).failed(1));
        final Event failedEvent = results.testEvents().failed().stream().findFirst()
                .orElseThrow(() -> new AssertionError("Expected one failed test event."));
        final Throwable reported = failedEvent
                .getRequiredPayload(TestExecutionResult.class)
                .getThrowable()
                .orElseThrow(() -> new AssertionError("Expected a reported throwable."));
        assertThat(reported)
                .as("A genuine failure from afterTest() must be promoted over a mere test"
                        + " abort, not swallowed as a suppressed exception on the abort - which"
                        + " would report the test as ABORTED instead of FAILED, hiding the"
                        + " regression dbUnit itself caught.")
                .hasMessageContaining("cleanup failure after abort");
        assertThat(reported.getSuppressed())
                .as("The original abort must still be visible, attached as suppressed on the"
                        + " promoted failure.")
                .anyMatch(t -> t instanceof TestAbortedException);
    }

    // ---- sample fixture classes ----

    @ExtendWith(DbUnitExtension.class)
    static class LifecycleOrderSample {
        private final List<String> callLog = new ArrayList<>();
        IDatabaseTester databaseTester = new CallLoggingTester(callLog);

        @BeforeEach
        void beforeEach() {
            callLog.add("beforeEach");
        }

        @AfterEach
        void afterEach() {
            assertThat(callLog)
                    .as("Execution order must be: beforeEach, onSetup, test, onTearDown.")
                    .containsExactly("beforeEach", "onSetup", "test", "onTearDown");
        }

        @Test
        void testOrderVerification() {
            assertThat(callLog)
                    .as("onSetup() must run after @BeforeEach and before the test method.")
                    .containsExactly("beforeEach", "onSetup");
            callLog.add("test");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class FailingTestSample {
        static final List<String> CALL_LOG = Collections.synchronizedList(new ArrayList<>());
        IDatabaseTester databaseTester = new CallLoggingTester(CALL_LOG);

        @Test
        void testThatFails() {
            CALL_LOG.add("test");
            fail("intentional failure to verify onTearDown still runs");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class FailingSetupSample {
        static final List<String> CALL_LOG = Collections.synchronizedList(new ArrayList<>());
        IDatabaseTester databaseTester = new CallLoggingTester(CALL_LOG, true);

        @Test
        void testThatNeverRuns() {
            CALL_LOG.add("test");
            fail("test method must not run when onSetup() throws");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithNested {
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Nested
        class NestedSample {
            @Test
            void testUsesEnclosingTesterField() {
                // resolution succeeding without throwing is the assertion
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithNestedTesterOverride {
        @DbUnitTester
        IDatabaseTester outerTester = new CallLoggingTester(new ArrayList<>(), true);

        @Nested
        class NestedOverride {
            @DbUnitTester
            IDatabaseTester innerTester = new CallLoggingTester(new ArrayList<>());

            @Test
            void testInnerFieldShadowsOuterField() {
                // the outer tester's onSetup() throws; succeeding proves the inner @DbUnitTester
                // field - not the outer one - was resolved.
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithDbUnitTesterAndNestedDbUnitTestCase {
        @DbUnitTester
        IDatabaseTester outerTester = new CallLoggingTester(new ArrayList<>());

        @Nested
        class NestedOverride {
            @DbUnitTestCase
            PrepAndExpectedTestCase testCase = new RecordingPrepAndExpectedTestCase();

            @Test
            void testInnerTestCaseFieldIsUsed() {
                // the enclosing class's @DbUnitTester field and this class's own
                // @DbUnitTestCase field live on two different instances, so they must not be
                // rejected as an ambiguous "both declared on one class" conflict.
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithDbUnitTestCaseAndNestedDbUnitTester {
        @DbUnitTestCase
        PrepAndExpectedTestCase outerTestCase = new RecordingPrepAndExpectedTestCase();

        @Nested
        class NestedOverride {
            @DbUnitTester
            IDatabaseTester innerTester = new CallLoggingTester(new ArrayList<>());

            @Test
            void testInnerTesterFieldIsUsed() {
                // the mirror image: the enclosing class's @DbUnitTestCase field and this
                // class's own @DbUnitTester field live on two different instances too.
            }
        }
    }

    @DbUnitTest
    @DbUnitSetup(operation = DbUnitOperation.REFRESH)
    static class ComposedAnnotationSample {
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        void testComposedAnnotationApplies() {
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @DbUnitTest
    @DbUnitConfig(dataSetBaseDir = "/org/dbunit/junit/jupiter/")
    @interface AppDatabaseTest {
    }

    @AppDatabaseTest
    static class MetaAnnotationSample {
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitPrep("loader-test.xml")
        void testMetaAnnotationConfigAndPrepBothApply() {
        }
    }

    @DbUnitTest
    static class ExpectedPathSample {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testPassingExpectedPath() {
        }
    }

    @DbUnitTest
    static class FailingExpectedPathSample {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testFailingExpectedPath() {
            fail("intentional test failure to verify verification is skipped");
        }
    }

    @DbUnitTest
    static class SuppressedCleanupSample {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testFailsThenCleanupAlsoFails() {
            fail("intentional test failure");
        }
    }

    @DbUnitTest
    static class AbortedTestThenCleanupFailsSample {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testAbortsThenCleanupFails() {
            Assumptions.assumeTrue(false, "intentional abort");
        }
    }

    private static class CallLoggingTester implements IDatabaseTester {
        private final List<String> callLog;
        private final boolean failOnSetup;

        CallLoggingTester(final List<String> callLog) {
            this(callLog, false);
        }

        CallLoggingTester(final List<String> callLog, final boolean failOnSetup) {
            this.callLog = callLog;
            this.failOnSetup = failOnSetup;
        }

        @Override
        public void onSetup() throws Exception {
            if (failOnSetup) {
                throw new Exception("intentional setup failure to verify onTearDown still runs");
            }
            callLog.add("onSetup");
        }

        @Override
        public void onTearDown() throws Exception {
            callLog.add("onTearDown");
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception {
            return null;
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
            return null;
        }

        @Override
        public DatabaseOperation getTearDownOperation() {
            return null;
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
        public void closeConnection(final IDatabaseConnection connection) throws Exception {
        }

        @Override
        @Deprecated
        public void setSchema(final String schema) {
        }
    }

    /**
     * Records {@code postTest(boolean)} calls, optionally throwing from it, to verify
     * {@link DbUnitExtension}'s verification-skip and suppression behavior without a real
     * database.
     */
    private static class RecordingPrepAndExpectedTestCase implements PrepAndExpectedTestCase {
        final List<Boolean> postTestCalls = Collections.synchronizedList(new ArrayList<>());
        RuntimeException postTestFailure;

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
                final PrepAndExpectedTestCaseSteps testSteps) {
            return null;
        }

        @Override
        public void postTest() {
            postTest(true);
        }

        @Override
        public void postTest(final boolean verifyData) {
            postTestCalls.add(verifyData);
            if (postTestFailure != null) {
                throw postTestFailure;
            }
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
