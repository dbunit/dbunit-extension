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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCaseSteps;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.junit.jupiter.inheritance.ClassLevelExpectedBase;
import org.dbunit.junit.jupiter.inheritance.ClassLevelPrepBase;
import org.dbunit.junit.jupiter.inheritance.MethodLevelExpectedBase;
import org.dbunit.junit.jupiter.inheritance.MethodLevelPrepBase;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
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
class DbUnitExtensionLifecycleTest
{
    @Test
    void testBeforeAndAfterTestExecution_testMethodSucceeds_callbacksRunInOrder()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(LifecycleOrderSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testAfterTestExecution_testMethodFails_onTearDownCalled()
    {
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
    void testBeforeTestExecution_onSetupFails_onTearDownCalledAndTestMethodSkipped()
    {
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
    void testBeforeAndAfterTestExecution_classicPathTesterWithCustomListener_listenerWrappedForTheTestAndPutBack()
    {
        final CallLoggingTester tester = new CallLoggingTester(new ArrayList<>());
        final IOperationListener original = IOperationListener.NO_OP_OPERATION_LISTENER;
        tester.setOperationListener(original);
        ClassicCustomListenerSample.tester = tester;
        tester.setOperationListenerCalls = 0;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ClassicCustomListenerSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(tester.setOperationListenerCalls)
                .as("A bare @ExtendWith(DbUnitExtension.class) test - the 3.5.0 style, opting into"
                        + " no @DbUnit* annotation or marker field - has its tester's"
                        + " IOperationListener wrapped for the test, and put back, nothing more.")
                .isEqualTo(2);
        assertThat(tester.getOperationListener())
                .as("The tester's original listener must be the one it carries after the test.")
                .isSameAs(original);
    }

    @Test
    void testBeforeTestExecution_nestedTestClass_resolvesTesterFromEnclosingInstance()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(EnclosingWithNested.NestedSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassMethodUsesEnclosingClassLevelAnnotation_appliesOperation()
    {
        EnclosingWithClassLevelSetupAnnotation.databaseTesterInstance = new ConnectedTester();
        SetUpOperationReadingTestCase.setUpOperationAtPreTest = null;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        EnclosingWithClassLevelSetupAnnotation.NestedInheritsEnclosingAnnotation.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(SetUpOperationReadingTestCase.setUpOperationAtPreTest)
                .as("A @DbUnitSetUpOperation declared on the enclosing class must be found and"
                        + " applied"
                        + " for a @Nested inner class's test method - the same enclosing-class"
                        + " annotation fallback findAnnotation() documents for field discovery -"
                        + " not silently skipped in favor of the tester's own operation.")
                .isEqualTo(DatabaseOperation.REFRESH);
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTesterField_shadowsEnclosingField()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(EnclosingWithNestedTesterOverride.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTestCaseField_notAmbiguousWithEnclosingTesterField()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        EnclosingWithDbUnitTesterAndNestedDbUnitTestCase.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_nestedTestClassOwnDbUnitTesterField_notAmbiguousWithEnclosingTestCaseField()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        EnclosingWithDbUnitTestCaseAndNestedDbUnitTester.NestedOverride.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_composedDbUnitTestAnnotation_appliesSetup()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ComposedAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_metaAnnotationCarryingConfigAndPrep_appliesBoth()
    {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(MetaAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testBeforeTestExecution_methodPrepOnComposedClassLevelPrep_methodPrepOverridesIt() throws Exception
    {
        final ConnectedTester tester = new ConnectedTester();
        MethodPrepOverridesComposedPrepSample.tester = tester;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(MethodPrepOverridesComposedPrepSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(tester.dataSetAtOnSetup.getTableNames())
                .as("A method-level @DbUnitPrep must override a class-level @DbUnitPrep carried"
                        + " by a composed annotation, exactly as it overrides a directly-declared"
                        + " class-level one - here the method's ACCOUNT dataset, not the"
                        + " composed annotation's TEST_TABLE one.")
                .containsExactly("ACCOUNT");
    }

    @Test
    void testAfterTestExecution_nestedClassExpectedAnnotation_runsVerificationViaEnclosingTestCaseField()
    {
        NestedExpectedAnnotationEnclosing.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(
                        NestedExpectedAnnotationEnclosing.NestedExpectedSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(NestedExpectedAnnotationEnclosing.testCase.postTestCalls)
                .as("Verification must run for a @Nested test method whose"
                        + " @DbUnitExpected is method-level and whose @DbUnitTestCase and tester"
                        + " fields live on the enclosing test instance.")
                .containsExactly(true);
    }

    @Test
    void testBeforeTestExecution_expectedAnnotationWithOperationsSetInBeforeEach_keepsThoseOperations()
    {
        final OperationHoldingTester tester = new OperationHoldingTester();
        final OperationReadingPrepAndExpectedTestCase testCase =
                new OperationReadingPrepAndExpectedTestCase(tester);
        BeforeEachOperationsExpectedAnnotationSample.tester = tester;
        BeforeEachOperationsExpectedAnnotationSample.testCase = testCase;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(BeforeEachOperationsExpectedAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        final List<DatabaseOperation> setUpThenTearDown =
                Arrays.asList(DatabaseOperation.REFRESH, DatabaseOperation.DELETE_ALL);
        assertThat(testCase.operationsAtPreTest)
                .as("The setup and teardown operations a @BeforeEach method set must still be"
                        + " the tester's when preTest() runs, as no annotation declares them.")
                .isEqualTo(setUpThenTearDown);
        assertThat(testCase.operationsAtPostTest)
                .as("They must still be the tester's when postTest() cleans up, so the"
                        + " teardown operation the test asked for is the one that runs.")
                .isEqualTo(setUpThenTearDown);
    }

    @Test
    void testBeforeTestExecution_classLevelPrepInheritedFromAnotherPackage_resolvesRelativeToTheDeclaringClass()
            throws Exception
    {
        final ConnectedTester tester = new ConnectedTester();
        InheritedClassLevelPrepSample.tester = tester;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InheritedClassLevelPrepSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(tester.dataSetAtOnSetup.getTableNames())
                .as("A relative @DbUnitPrep path inherited from a base class in another package"
                        + " names a file next to that base class, not next to the subclass.")
                .containsExactly("BASE_CLASS_TABLE");
    }

    @Test
    void testBeforeTestExecution_methodLevelPrepInheritedFromAnotherPackage_resolvesRelativeToTheDeclaringClass()
            throws Exception
    {
        final ConnectedTester tester = new ConnectedTester();
        InheritedMethodLevelPrepSample.tester = tester;

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InheritedMethodLevelPrepSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(tester.dataSetAtOnSetup.getTableNames())
                .as("A relative @DbUnitPrep path on an inherited test method names a file next"
                        + " to the class that declares the method.")
                .containsExactly("BASE_METHOD_TABLE");
    }

    @Test
    void testBeforeTestExecution_classLevelExpectedInheritedFromAnotherPackage_resolvesRelativeToTheDeclaringClass()
    {
        InheritedClassLevelExpectedSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InheritedClassLevelExpectedSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(InheritedClassLevelExpectedSample.testCase.configuredExpectedDataFiles)
                .as("A relative @DbUnitExpected path inherited from a base class in another"
                        + " package names a file next to that base class.")
                .containsExactly("/org/dbunit/junit/jupiter/inheritance/class-level-expected.xml");
    }

    @Test
    void testBeforeTestExecution_methodLevelExpectedInheritedFromAnotherPackage_resolvesRelativeToTheDeclaringClass()
    {
        InheritedMethodLevelExpectedSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InheritedMethodLevelExpectedSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(InheritedMethodLevelExpectedSample.testCase.configuredExpectedDataFiles)
                .as("A relative @DbUnitExpected path on an inherited test method names a file"
                        + " next to the class that declares the method.")
                .containsExactly(
                        "/org/dbunit/junit/jupiter/inheritance/method-level-expected.xml");
    }

    @Test
    void testAfterTestExecution_expectedAnnotationAndPassingTest_runsVerification()
    {
        PassingExpectedAnnotationSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(PassingExpectedAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(PassingExpectedAnnotationSample.testCase.postTestCalls)
                .as("A passing test must run verification (postTest(true)).")
                .containsExactly(true);
    }

    @Test
    void testBeforeTestExecution_expectedAnnotationAndInjectedTestCase_runsOnTheTestCasesOwnTester()
    {
        final CallLoggingTester ownTester = new CallLoggingTester(new ArrayList<>());
        OwnTesterSample.testCase = new TesterRecordingPrepAndExpectedTestCase(ownTester);
        OwnTesterSample.otherTester = new CallLoggingTester(new ArrayList<>());

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(OwnTesterSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(OwnTesterSample.testCase.databaseTesterAtPreTest)
                .as("An injected test case is used as it was built: dbUnit does not give it the"
                        + " tester of another field of the test.")
                .isSameAs(ownTester);
    }

    @Test
    void testAfterTestExecution_expectedAnnotationAndFailingTest_skipsVerificationAndReportsOriginalFailure()
    {
        FailingExpectedAnnotationSample.testCase = new RecordingPrepAndExpectedTestCase();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingExpectedAnnotationSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(FailingExpectedAnnotationSample.testCase.postTestCalls)
                .as("A failing test must skip verification (postTest(false)).")
                .containsExactly(false);
    }

    @Test
    void testAfterTestExecution_cleanupThrowsAfterTestFailure_attachesCleanupAsSuppressed()
    {
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
    void testAfterTestExecution_cleanupThrowsAfterTestAborted_promotesCleanupFailureOverAbort()
    {
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
    static class LifecycleOrderSample
    {
        private final List<String> callLog = new ArrayList<>();
        IDatabaseTester databaseTester = new CallLoggingTester(callLog);

        @BeforeEach
        void beforeEach()
        {
            callLog.add("beforeEach");
        }

        @AfterEach
        void afterEach()
        {
            assertThat(callLog)
                    .as("Execution order must be: beforeEach, onSetup, test, onTearDown.")
                    .containsExactly("beforeEach", "onSetup", "test", "onTearDown");
        }

        @Test
        void testOrderVerification()
        {
            assertThat(callLog)
                    .as("onSetup() must run after @BeforeEach and before the test method.")
                    .containsExactly("beforeEach", "onSetup");
            callLog.add("test");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class FailingTestSample
    {
        static final List<String> CALL_LOG = Collections.synchronizedList(new ArrayList<>());
        IDatabaseTester databaseTester = new CallLoggingTester(CALL_LOG);

        @Test
        void testThatFails()
        {
            CALL_LOG.add("test");
            fail("intentional failure to verify onTearDown still runs");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class FailingSetupSample
    {
        static final List<String> CALL_LOG = Collections.synchronizedList(new ArrayList<>());
        IDatabaseTester databaseTester = new CallLoggingTester(CALL_LOG, true);

        @Test
        void testThatNeverRuns()
        {
            CALL_LOG.add("test");
            fail("test method must not run when onSetup() throws");
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithNested
    {
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Nested
        class NestedSample
        {
            @Test
            void testUsesEnclosingTesterField()
            {
                // resolution succeeding without throwing is the assertion
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class ClassicCustomListenerSample
    {
        static CallLoggingTester tester;

        IDatabaseTester databaseTester = tester;

        @Test
        void testClassicPathPutsTheListenerBack()
        {
            // the assertion is in the outer test method, against tester.setOperationListenerCalls
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
    @DbUnitConfig(prepAndExpectedTestCase = SetUpOperationReadingTestCase.class)
    static class EnclosingWithClassLevelSetupAnnotation
    {
        static ConnectedTester databaseTesterInstance;

        IDatabaseTester databaseTester = databaseTesterInstance;

        @Nested
        class NestedInheritsEnclosingAnnotation
        {
            @Test
            void testNestedMethodAppliesEnclosingClassLevelDbUnitSetup()
            {
                // the assertion is in the outer test method, against
                // SetUpOperationReadingTestCase.setUpOperationAtPreTest
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithNestedTesterOverride
    {
        @DbUnitTester
        IDatabaseTester outerTester = new UnusableTester();

        @Nested
        class NestedOverride
        {
            @DbUnitTester
            IDatabaseTester innerTester = new ConnectedTester();

            @Test
            void testInnerFieldShadowsOuterField()
            {
                // the outer tester fails when asked for a connection; succeeding proves the
                // inner @DbUnitTester field - not the outer one - was resolved.
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithDbUnitTesterAndNestedDbUnitTestCase
    {
        @DbUnitTester
        IDatabaseTester outerTester = new CallLoggingTester(new ArrayList<>());

        @Nested
        class NestedOverride
        {
            @DbUnitTestCase
            PrepAndExpectedTestCase testCase = new RecordingPrepAndExpectedTestCase();

            @Test
            void testInnerTestCaseFieldIsUsed()
            {
                // the enclosing class's @DbUnitTester field and this class's own
                // @DbUnitTestCase field live on two different instances, so they must not be
                // rejected as an ambiguous "both declared on one class" conflict.
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class EnclosingWithDbUnitTestCaseAndNestedDbUnitTester
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase outerTestCase = new RecordingPrepAndExpectedTestCase();

        @Nested
        class NestedOverride
        {
            @DbUnitTester
            IDatabaseTester innerTester = new ConnectedTester();

            @Test
            void testInnerTesterFieldIsUsed()
            {
                // the mirror image: the enclosing class's @DbUnitTestCase field and this
                // class's own @DbUnitTester field live on two different instances too.
            }
        }
    }

    @DbUnitTest
    @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
    static class ComposedAnnotationSample
    {
        IDatabaseTester databaseTester = new ConnectedTester();

        @Test
        void testComposedAnnotationApplies()
        {
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @DbUnitTest
    @DbUnitConfig(dataSetBaseDir = "/org/dbunit/junit/jupiter/")
    @interface AppDatabaseTest
    {
    }

    @AppDatabaseTest
    static class MetaAnnotationSample
    {
        IDatabaseTester databaseTester = new ConnectedTester();

        @Test
        @DbUnitPrep("loader-test.xml")
        void testMetaAnnotationConfigAndPrepBothApply()
        {
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @DbUnitTest
    @DbUnitPrep("loader-test.xml")
    @interface AppDatabaseTestSeedingTestTable
    {
    }

    @AppDatabaseTestSeedingTestTable
    static class MethodPrepOverridesComposedPrepSample
    {
        static ConnectedTester tester;

        IDatabaseTester databaseTester = tester;

        @Test
        @DbUnitPrep("expected.xml")
        void testMethodPrepWins()
        {
        }
    }

    static class InheritedClassLevelPrepSample extends ClassLevelPrepBase
    {
        static ConnectedTester tester;

        IDatabaseTester databaseTester = tester;

        @Test
        void testInheritedClassLevelPrep()
        {
        }
    }

    @DbUnitTest
    static class InheritedMethodLevelPrepSample extends MethodLevelPrepBase
    {
        static ConnectedTester tester;

        IDatabaseTester databaseTester = tester;
    }

    static class InheritedClassLevelExpectedSample extends ClassLevelExpectedBase
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        void testInheritedClassLevelExpected()
        {
        }
    }

    @DbUnitTest
    static class InheritedMethodLevelExpectedSample extends MethodLevelExpectedBase
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());
    }

    @DbUnitTest
    static class NestedExpectedAnnotationEnclosing
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Nested
        class NestedExpectedSample
        {
            @Test
            @DbUnitExpected("expected.xml")
            void testExpectedAnnotationResolvesFromEnclosingInstance()
            {
            }
        }
    }

    @DbUnitTest
    static class PassingExpectedAnnotationSample
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testPassingExpectedAnnotation()
        {
        }
    }

    @DbUnitTest
    static class BeforeEachOperationsExpectedAnnotationSample
    {
        static OperationHoldingTester tester;
        static OperationReadingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = tester;

        @BeforeEach
        void configureOperations()
        {
            databaseTester.setSetUpOperation(DatabaseOperation.REFRESH);
            databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        }

        @Test
        @DbUnitExpected("expected.xml")
        void testExpectedAnnotation()
        {
        }
    }

    @DbUnitTest
    static class OwnTesterSample
    {
        static TesterRecordingPrepAndExpectedTestCase testCase;
        static IDatabaseTester otherTester;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = otherTester;

        @Test
        @DbUnitExpected("expected.xml")
        void testExpectedAnnotation()
        {
        }
    }

    @DbUnitTest
    static class FailingExpectedAnnotationSample
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testFailingExpectedAnnotation()
        {
            fail("intentional test failure to verify verification is skipped");
        }
    }

    @DbUnitTest
    static class SuppressedCleanupSample
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testFailsThenCleanupAlsoFails()
        {
            fail("intentional test failure");
        }
    }

    @DbUnitTest
    static class AbortedTestThenCleanupFailsSample
    {
        static RecordingPrepAndExpectedTestCase testCase;

        @DbUnitTestCase
        PrepAndExpectedTestCase injected = testCase;
        IDatabaseTester databaseTester = new CallLoggingTester(new ArrayList<>());

        @Test
        @DbUnitExpected("expected.xml")
        void testAbortsThenCleanupFails()
        {
            Assumptions.assumeTrue(false, "intentional abort");
        }
    }

    private static class CallLoggingTester implements IDatabaseTester
    {
        private final List<String> callLog;
        private final boolean failOnSetup;
        DatabaseOperation lastSetUpOperation;
        IDataSet lastDataSet;
        // Snapshotted when onSetup() runs, so an assertion still sees what the executor applied
        // for the test even after it restores the tester's state afterward.
        DatabaseOperation setUpOperationAtOnSetup;
        IDataSet dataSetAtOnSetup;
        IOperationListener operationListener;
        int setOperationListenerCalls;

        CallLoggingTester(final List<String> callLog)
        {
            this(callLog, false);
        }

        CallLoggingTester(final List<String> callLog, final boolean failOnSetup)
        {
            this.callLog = callLog;
            this.failOnSetup = failOnSetup;
        }

        @Override
        public void onSetup() throws Exception
        {
            if (failOnSetup)
            {
                throw new Exception("intentional setup failure to verify onTearDown still runs");
            }
            setUpOperationAtOnSetup = lastSetUpOperation;
            dataSetAtOnSetup = lastDataSet;
            callLog.add("onSetup");
        }

        @Override
        public void onTearDown() throws Exception
        {
            callLog.add("onTearDown");
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception
        {
            return null;
        }

        @Override
        public IDataSet getDataSet()
        {
            return null;
        }

        @Override
        public void setDataSet(final IDataSet dataSet)
        {
            lastDataSet = dataSet;
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
            lastSetUpOperation = setUpOperation;
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation)
        {
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener)
        {
            setOperationListenerCalls++;
            this.operationListener = operationListener;
        }

        @Override
        public IOperationListener getOperationListener()
        {
            return operationListener;
        }

        @Override
        @Deprecated
        public void closeConnection(final IDatabaseConnection connection) throws Exception
        {
        }

        @Override
        @Deprecated
        public void setSchema(final String schema)
        {
        }
    }

    /**
     * A {@link CallLoggingTester} an annotation-driven test can run on: it hands out an in-memory
     * connection, and its setup operation records the dataset it runs on in
     * {@link CallLoggingTester#dataSetAtOnSetup}, as the test case of the extension runs it on
     * the prep dataset.
     */
    private static class ConnectedTester extends CallLoggingTester
    {
        private final DatabaseOperation recordingSetUpOperation = new DatabaseOperation()
        {
            @Override
            public void execute(final IDatabaseConnection connection, final IDataSet dataSet)
            {
                dataSetAtOnSetup = dataSet;
            }
        };

        ConnectedTester()
        {
            super(new ArrayList<>());
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception
        {
            return InMemoryDatabaseConnection.create();
        }

        @Override
        public DatabaseOperation getSetUpOperation()
        {
            return recordingSetUpOperation;
        }

        @Override
        public DatabaseOperation getTearDownOperation()
        {
            return DatabaseOperation.NONE;
        }
    }

    /**
     * A {@link CallLoggingTester} that fails the test when anything asks it for a connection, for
     * a test that proves another tester than this one was resolved.
     */
    private static class UnusableTester extends CallLoggingTester
    {
        UnusableTester()
        {
            super(new ArrayList<>());
        }

        @Override
        public IDatabaseConnection getConnection()
        {
            throw new IllegalStateException("This tester must not have been resolved.");
        }
    }

    /**
     * A {@link DefaultPrepAndExpectedTestCase} the extension builds, which records the setup
     * operation it would run when {@code preTest()} is called, and touches no database.
     */
    public static class SetUpOperationReadingTestCase extends DefaultPrepAndExpectedTestCase
    {
        static volatile DatabaseOperation setUpOperationAtPreTest;

        public SetUpOperationReadingTestCase(final DataFileLoader dataFileLoader,
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
        public void preTest() throws Exception
        {
            setUpOperationAtPreTest = getSetUpOperation();
        }

        @Override
        public void postTest(final boolean verifyData)
        {
        }
    }

    /**
     * A {@link CallLoggingTester} that keeps the setup and teardown operations it is given, as a
     * real tester does, so a test can see which ones are in force.
     */
    private static class OperationHoldingTester extends CallLoggingTester
    {
        private DatabaseOperation setUpOperation = DatabaseOperation.CLEAN_INSERT;
        private DatabaseOperation tearDownOperation = DatabaseOperation.NONE;

        OperationHoldingTester()
        {
            super(new ArrayList<>());
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
        public void setSetUpOperation(final DatabaseOperation setUpOperation)
        {
            this.setUpOperation = setUpOperation;
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation)
        {
            this.tearDownOperation = tearDownOperation;
        }
    }

    /**
     * Records the setup and teardown operations of the tester it was given, as they are when
     * {@code preTest()} and {@code postTest(boolean)} run: setup first, then teardown.
     */
    private static class OperationReadingPrepAndExpectedTestCase
            extends RecordingPrepAndExpectedTestCase
    {
        final List<DatabaseOperation> operationsAtPreTest = new ArrayList<>();
        final List<DatabaseOperation> operationsAtPostTest = new ArrayList<>();

        OperationReadingPrepAndExpectedTestCase(final IDatabaseTester tester)
        {
            super(tester);
        }

        @Override
        public void preTest()
        {
            record(operationsAtPreTest);
            super.preTest();
        }

        @Override
        public void postTest(final boolean verifyData)
        {
            record(operationsAtPostTest);
            super.postTest(verifyData);
        }

        private void record(final List<DatabaseOperation> operations)
        {
            final IDatabaseTester tester = getDatabaseTester();
            operations.add(tester.getSetUpOperation());
            operations.add(tester.getTearDownOperation());
        }
    }

    /**
     * Records {@code postTest(boolean)} calls, optionally throwing from it, to verify
     * {@link DbUnitExtension}'s verification-skip and suppression behavior without a real
     * database.
     */
    private static class RecordingPrepAndExpectedTestCase extends DefaultPrepAndExpectedTestCase
    {
        final List<Boolean> postTestCalls = Collections.synchronizedList(new ArrayList<>());
        RuntimeException postTestFailure;
        String[] configuredExpectedDataFiles;

        RecordingPrepAndExpectedTestCase()
        {
            this(new CallLoggingTester(new ArrayList<>()));
        }

        RecordingPrepAndExpectedTestCase(final IDatabaseTester tester)
        {
            super(new FlatXmlDataFileLoader(), tester);
        }

        @Override
        public void configureTest(final VerifyTableDefinition[] verifyTableDefinitions,
                final String[] prepDataFiles, final String[] expectedDataFiles)
        {
            configuredExpectedDataFiles = expectedDataFiles;
        }

        @Override
        public void preTest()
        {
        }

        @Override
        public void postTest()
        {
            postTest(true);
        }

        @Override
        public void postTest(final boolean verifyData)
        {
            postTestCalls.add(verifyData);
            if (postTestFailure != null)
            {
                throw postTestFailure;
            }
        }
    }

    /**
     * Extends {@link RecordingPrepAndExpectedTestCase} to also record the tester it holds when
     * {@code preTest()} runs - proving {@code DbUnitExtension} leaves this test case's tester as
     * its owner built it.
     */
    private static class TesterRecordingPrepAndExpectedTestCase
            extends RecordingPrepAndExpectedTestCase
    {
        IDatabaseTester databaseTesterAtPreTest;

        TesterRecordingPrepAndExpectedTestCase(final IDatabaseTester tester)
        {
            super(tester);
        }

        @Override
        public void preTest()
        {
            databaseTesterAtPreTest = getDatabaseTester();
            super.preTest();
        }
    }
}
