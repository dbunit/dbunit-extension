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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.assertion.DbComparisonFailure;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link MultiDataSourcePrepAndExpectedTestCase}, using recording/stub
 * {@link PrepAndExpectedTestCase} delegates instead of real database connections - the
 * orchestration this class adds (wiring, fan-out, ordering, and failure aggregation) does not
 * depend on any one delegate's own database behavior, which is already covered at the
 * {@link DefaultPrepAndExpectedTestCase} level. {@link MultiDataSourcePrepAndExpectedTestCaseIT}
 * covers the same class end to end against real databases.
 *
 * @since 3.6.0
 */
class MultiDataSourcePrepAndExpectedTestCaseTest
{
    // ---- construction / assembly ----

    @Test
    void testFrom_thenAdd_wiresDataSourcesInCallOrder()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());

        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        assertThat(testCase.getDataSourceNames())
                .as("from(...).add(...) must wire the data sources in call order.")
                .containsExactly("catalog", "orders");
    }

    @Test
    void testConstructor_linkedHashMap_wiresInMapIterationOrder()
    {
        final Map<String, PrepAndExpectedTestCase> seed = new LinkedHashMap<>();
        seed.put("orders", new RecordingTestCase("orders", new ArrayList<>()));
        seed.put("catalog", new RecordingTestCase("catalog", new ArrayList<>()));

        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase(seed);

        assertThat(testCase.getDataSourceNames())
                .as("The Map constructor must wire data sources in the given map's iteration"
                        + " order.")
                .containsExactly("orders", "catalog");
    }

    @Test
    void testConstructor_mutatingTheSourceMapAfterwards_doesNotAffectTheWrapper()
    {
        final Map<String, PrepAndExpectedTestCase> seed = new LinkedHashMap<>();
        seed.put("catalog", new RecordingTestCase("catalog", new ArrayList<>()));

        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase(seed);
        seed.put("orders", new RecordingTestCase("orders", new ArrayList<>()));

        assertThat(testCase.getDataSourceNames())
                .as("The constructor must defensively copy the source map, so mutating it"
                        + " afterward does not affect this instance.")
                .containsExactly("catalog");
    }

    @Test
    void testConstructor_nullMapOrNullOrBlankKeyOrNullDelegate_throws()
    {
        assertThatThrownBy(() -> new MultiDataSourcePrepAndExpectedTestCase(null))
                .as("A null seed map must throw.").isInstanceOf(IllegalArgumentException.class);

        final Map<String, PrepAndExpectedTestCase> nullKey = new LinkedHashMap<>();
        nullKey.put(null, new RecordingTestCase("x", new ArrayList<>()));
        assertThatThrownBy(() -> new MultiDataSourcePrepAndExpectedTestCase(nullKey))
                .as("A null data source name in the seed map must throw.")
                .isInstanceOf(IllegalArgumentException.class);

        final Map<String, PrepAndExpectedTestCase> blankKey = new LinkedHashMap<>();
        blankKey.put("   ", new RecordingTestCase("x", new ArrayList<>()));
        assertThatThrownBy(() -> new MultiDataSourcePrepAndExpectedTestCase(blankKey))
                .as("A blank data source name in the seed map must throw.")
                .isInstanceOf(IllegalArgumentException.class);

        final Map<String, PrepAndExpectedTestCase> nullDelegate = new LinkedHashMap<>();
        nullDelegate.put("catalog", null);
        assertThatThrownBy(() -> new MultiDataSourcePrepAndExpectedTestCase(nullDelegate))
                .as("A null delegate in the seed map must throw.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAdd_duplicateDataSourceName_throwsIllegalArgumentException()
    {
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", new RecordingTestCase("catalog", new ArrayList<>()));

        assertThatThrownBy(() -> testCase.add("catalog",
                new RecordingTestCase("catalog-2", new ArrayList<>())))
                .as("Adding a second delegate under an already-wired data source name must"
                        + " throw.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAdd_afterFirstPreTest_throwsIllegalStateException() throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);

        testCase.preTest(map("catalog", someData()));

        assertThatThrownBy(
                () -> testCase.add("orders", new RecordingTestCase("orders", new ArrayList<>())))
                .as("add(...) after the wiring is frozen by the first preTest(...) call must"
                        + " throw.")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testPreTest_firstCallFailsValidation_wiringStaysUnfrozenSoAddStillWorks() throws Exception
    {
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase();

        assertThatThrownBy(() -> testCase.preTest(map("catalog", someData())))
                .as("preTest on an instance with nothing wired yet must throw.")
                .isInstanceOf(IllegalStateException.class);

        testCase.add("catalog", new RecordingTestCase("catalog", new ArrayList<>()));

        assertThat(testCase.getDataSourceNames())
                .as("A failed first preTest call - before any delegate was ever wired or"
                        + " touched - must not have frozen the wiring, so add(...) afterward"
                        + " must still work.")
                .containsExactly("catalog");
    }

    @Test
    void testAddTester_onInstanceNotCreatedViaForTesters_throwsIllegalStateException()
    {
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase();
        final IDatabaseTester tester = new DefaultDatabaseTester(new MockDatabaseConnection());

        assertThatThrownBy(() -> testCase.add("catalog", tester))
                .as("add(String, IDatabaseTester) on an instance not created via one of the"
                        + " forTesters(...) factories must throw.")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testPreTest_wiredViaMapOf_stillRunsEveryDataSource() throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        // a HashMap's iteration order is unspecified, standing in here for a Java 9+ caller's
        // Map.of(...) - this module's own sources build at Java 8 (pom.xml release=8), so this
        // test cannot literally call Map.of(...) itself
        final Map<String, PrepAndExpectedTestCase> unordered = new HashMap<>();
        unordered.put("catalog", catalog);
        unordered.put("orders", orders);
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase(unordered);

        testCase.preTest(map("catalog", someData(), "orders", someData()));
        testCase.postTest(true);

        assertThat(catalog.touched)
                .as("Every wired data source must run regardless of the source map's"
                        + " (unspecified) iteration order.")
                .isTrue();
        assertThat(orders.touched)
                .as("Every wired data source must run regardless of the source map's"
                        + " (unspecified) iteration order.")
                .isTrue();
    }

    @Test
    void testPreTest_zeroDataSources_throwsIllegalStateException()
    {
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                new MultiDataSourcePrepAndExpectedTestCase();

        assertThatThrownBy(() -> testCase.preTest(new LinkedHashMap<>()))
                .as("preTest(...) with zero data sources wired must throw.")
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- lifecycle ----

    @Test
    void testPreTest_mapHasUnknownDataSourceKey_throwsBeforeAnyDelegateTouched()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);

        assertThatThrownBy(
                () -> testCase.preTest(map("catalog", someData(), "bogus", someData())))
                .as("An unknown data source key must throw.")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.touched)
                .as("The wired (known) delegate must not be touched when the same call also"
                        + " contains an unknown data source key.")
                .isFalse();
    }

    @Test
    void testPreTest_dataSourceMappedToNull_throwsBeforeAnyDelegateTouched()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        assertThatThrownBy(
                () -> testCase.preTest(map("catalog", someData(), "orders", null)))
                .as("A wired data source explicitly mapped to null must throw, not be silently"
                        + " skipped like an absent key or NONE would be.")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.touched)
                .as("No delegate may be touched when the data map is rejected.")
                .isFalse();
    }

    @Test
    void testPreTest_dataSourceAbsentFromMap_thatDelegateIsNeverTouched() throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        testCase.preTest(map("catalog", someData()));

        assertThat(catalog.touched).as("A data source present in the data map must be touched.")
                .isTrue();
        assertThat(orders.touched)
                .as("A wired data source absent from the data map must never be touched.")
                .isFalse();
    }

    @Test
    void testPreTest_dataSourceMappedToNONE_thatDelegateIsNeverTouched() throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        testCase.preTest(map("catalog", someData(), "orders", PrepAndExpectedTestData.NONE));

        assertThat(orders.touched)
                .as("A data source mapped to the exact NONE instance must never be touched.")
                .isFalse();
    }

    @Test
    void testPreTest_dataSourceMappedToAnEmptyButDistinctInstance_thatDelegateIsStillTouched()
            throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);
        final PrepAndExpectedTestData emptyButDistinct = someData();
        assertThat(emptyButDistinct)
                .as("Sanity check: the empty bundle must be content-equal to NONE for this test"
                        + " to actually prove the == check rather than the equals() contract.")
                .isEqualTo(PrepAndExpectedTestData.NONE);

        testCase.preTest(map("catalog", emptyButDistinct));

        assertThat(catalog.touched)
                .as("A data source mapped to a content-equal-but-distinct empty bundle must"
                        + " still be touched; only the literal NONE instance means skip.")
                .isTrue();
    }

    @Test
    void testPreTest_threeInvolvedDataSources_setsUpEachInDeclaredOrder() throws Exception
    {
        final List<String> callLog = new ArrayList<>();
        final RecordingTestCase catalog = new RecordingTestCase("catalog", callLog);
        final RecordingTestCase orders = new RecordingTestCase("orders", callLog);
        final RecordingTestCase inventory = new RecordingTestCase("inventory", callLog);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders).add("inventory", inventory);

        testCase.preTest(map("catalog", someData(), "orders", someData(), "inventory",
                someData()));

        assertThat(callLog).as("The three involved data sources must be set up in declared"
                + " order.")
                .containsExactly("catalog:preTest", "orders:preTest", "inventory:preTest");
    }

    @Test
    void testPreTest_laterDataSourceSetupThrows_rollsBackEarlierAndFailingDelegateViaPostTestFalse()
    {
        final List<String> callLog = new ArrayList<>();
        final RecordingTestCase catalog = new RecordingTestCase("catalog", callLog);
        final RecordingTestCase orders = new RecordingTestCase("orders", callLog);
        orders.failOnPreTest(new RuntimeException("orders setup failed"));
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        assertThatThrownBy(
                () -> testCase.preTest(map("catalog", someData(), "orders", someData())))
                .isInstanceOf(RuntimeException.class);

        assertThat(callLog)
                .as("Every data source set up so far - the earlier one, and the one whose own"
                        + " preTest just failed - must be rolled back via postTest(false), in"
                        + " reverse order, not a bare cleanupData().")
                .containsExactly("catalog:preTest", "orders:preTest", "orders:postTest(false)",
                        "catalog:postTest(false)");
    }

    @Test
    void testPreTest_laterDataSourceSetupThrows_rethrowsTheSetupFailureItselfWithRollbackSuppressed()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RuntimeException rollbackFailure = new RuntimeException("catalog rollback failed");
        catalog.failOnPostTest(rollbackFailure);
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final RuntimeException setupFailure = new RuntimeException("orders setup failed");
        orders.failOnPreTest(setupFailure);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        final Throwable thrown = catchThrowable(
                () -> testCase.preTest(map("catalog", someData(), "orders", someData())));

        assertThat(thrown).as("preTest(...) must rethrow the real setup failure itself, not a"
                + " wrapper.").isSameAs(setupFailure);
        assertThat(thrown.getSuppressed())
                .as("A rollback failure must be attached as suppressed on the setup failure.")
                .containsExactly(rollbackFailure);
    }

    @Test
    void testPreTest_rollbackFailureIsTheSameInstanceAsTheSetupFailure_skipsItButStillSuppressesADistinctOne()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RuntimeException catalogRollbackFailure =
                new RuntimeException("catalog rollback failed");
        catalog.failOnPostTest(catalogRollbackFailure);
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final RuntimeException setupFailure = new RuntimeException("orders setup failed");
        orders.failOnPreTest(setupFailure);
        orders.failOnPostTest(setupFailure);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);

        final Throwable thrown = catchThrowable(
                () -> testCase.preTest(map("catalog", someData(), "orders", someData())));

        assertThat(thrown).as("preTest(...) must still rethrow the real setup failure itself.")
                .isSameAs(setupFailure);
        assertThat(thrown.getSuppressed())
                .as("orders' own rollback failure is the exact same instance as the setup"
                        + " failure and must be skipped rather than self-suppressed, but"
                        + " catalog's distinct rollback failure must still be attached - proving"
                        + " the self-suppression guard does not stop the rollback loop early.")
                .containsExactly(catalogRollbackFailure);
    }

    @Test
    void testRunTest_threeDataSources_runsTestStepsExactlyOnce() throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final RecordingTestCase inventory = new RecordingTestCase("inventory", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders).add("inventory", inventory);
        final AtomicInteger stepRunCount = new AtomicInteger();

        testCase.runTest(
                map("catalog", someData(), "orders", someData(), "inventory", someData()),
                () -> stepRunCount.incrementAndGet());

        assertThat(stepRunCount).as("The test steps must run exactly once.").hasValue(1);
    }

    @Test
    void testRunTest_testStepsThrow_rethrowsTheStepExceptionItselfNotAWrapper()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);
        final RuntimeException stepFailure = new RuntimeException("step failed");

        final Throwable thrown = catchThrowable(() -> testCase.runTest(map("catalog", someData()),
                () -> {
                    throw stepFailure;
                }));

        assertThat(thrown).as("runTest(...) must rethrow the step exception itself, not a"
                + " wrapper.").isSameAs(stepFailure);
    }

    @Test
    void testRunTest_testStepsThrow_tearsDownEveryInvolvedDataSourceWithFailuresSuppressedOnStepException()
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RuntimeException catalogTeardownFailure =
                new RuntimeException("catalog teardown failed");
        catalog.failOnPostTest(catalogTeardownFailure);
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);
        final RuntimeException stepFailure = new RuntimeException("step failed");

        final Throwable thrown = catchThrowable(() -> testCase
                .runTest(map("catalog", someData(), "orders", someData()), () -> {
                    throw stepFailure;
                }));

        assertThat(thrown).isSameAs(stepFailure);
        assertThat(thrown.getSuppressed())
                .as("Every involved data source must be torn down, with each teardown failure"
                        + " suppressed onto the step exception.")
                .containsExactly(catalogTeardownFailure);
        assertThat(orders.lastPostTestVerifyData)
                .as("Teardown after a step failure must call postTest(false) - not verify - on"
                        + " every involved data source.")
                .isFalse();
    }

    @Test
    void testRunTest_stepFailureIsTheSameInstanceAsATeardownFailure_stillRethrowsItWithoutSelfSuppressionError()
    {
        final RuntimeException sharedFailure = new RuntimeException("shared failure instance");
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        catalog.failOnPostTest(sharedFailure);
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);

        final Throwable thrown = catchThrowable(() -> testCase.runTest(map("catalog", someData()),
                () -> {
                    throw sharedFailure;
                }));

        assertThat(thrown)
                .as("The original failure must still be rethrown as-is even when a delegate's"
                        + " own teardown failure happens to be the exact same instance -"
                        + " addSuppressed() would otherwise throw for self-suppression and mask"
                        + " it.")
                .isSameAs(sharedFailure);
        assertThat(thrown.getSuppressed())
                .as("A teardown failure that is the primary failure itself must not be added as"
                        + " suppressed onto itself.")
                .isEmpty();
    }

    @Test
    void testPostTest_verifyTrueAndOneDataSourceFails_throwsAssertionErrorWithThatFailureAsCause()
            throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final DbComparisonFailure verifyFailure =
                new DbComparisonFailure("reason", "expected", "actual");
        catalog.failOnPostTest(verifyFailure);
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);
        testCase.preTest(map("catalog", someData()));

        final Throwable thrown = catchThrowable(() -> testCase.postTest(true));

        assertThat(thrown).as("A single involved data source's postTest failure must surface as"
                + " a MultiDataSourceAssertionError.")
                .isInstanceOf(MultiDataSourceAssertionError.class);
        assertThat(thrown.getCause())
                .as("That single failure must be this failure's cause, unwrapped, one Caused by"
                        + " hop down.")
                .isSameAs(verifyFailure);
    }

    @Test
    void testPostTest_verifyTrueAndFailureIsNotAnAssertionError_labelsThePhaseGenericallyAsPostTest()
            throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        catalog.failOnPostTest(new Exception("a checked failure that is not an AssertionError"));
        final MultiDataSourcePrepAndExpectedTestCase testCase =
                MultiDataSourcePrepAndExpectedTestCase.from("catalog", catalog);
        testCase.preTest(map("catalog", someData()));

        final Throwable aggregate = catchThrowable(() -> testCase.postTest(true));

        assertThat(aggregate.getMessage())
                .as("A postTest(true) failure that is not an AssertionError could equally have"
                        + " come from verifyData() (e.g. a checked DataSetException) or"
                        + " cleanupData(); it must not be confidently mislabelled as"
                        + " cleanupData.")
                .contains("catalog (postTest)");
    }

    @Test
    void testPostTest_verifyTrueAndTwoDataSourcesFail_aggregateNamesBothFirstAsCauseRestSuppressed()
            throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final RecordingTestCase inventory = new RecordingTestCase("inventory", new ArrayList<>());
        final DbComparisonFailure ordersFailure =
                new DbComparisonFailure("reason", "expected", "actual");
        orders.failOnPostTest(ordersFailure);
        final RuntimeException inventoryFailure = new RuntimeException("inventory cleanup failed");
        inventory.failOnPostTest(inventoryFailure);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders).add("inventory", inventory);
        testCase.preTest(map("catalog", someData(), "orders", someData(), "inventory",
                someData()));

        final Throwable aggregate = catchThrowable(() -> testCase.postTest(true));

        assertThat(aggregate)
                .as("Both postTest failures must be aggregated into one"
                        + " MultiDataSourceAssertionError.")
                .isInstanceOf(MultiDataSourceAssertionError.class);
        assertThat(aggregate.getMessage())
                .as("The aggregate message must name both failing data sources.")
                .contains("orders").contains("inventory");
        assertThat(aggregate.getCause())
                .as("The first-declared failing data source's own failure must be the cause,"
                        + " unwrapped, one Caused by hop down.")
                .isSameAs(ordersFailure);
        assertThat(aggregate.getSuppressed())
                .as("The second-declared failing data source's failure must be attached as"
                        + " suppressed.")
                .hasSize(1);
        assertThat(aggregate.getSuppressed()[0].getCause())
                .as("The suppressed entry must carry the real failure as its own cause, so it"
                        + " reads without unwrapping further.")
                .isSameAs(inventoryFailure);
    }

    @Test
    void testPostTest_reverseOrderTeardown_tearsDownLastDeclaredInvolvedDataSourceFirst()
            throws Exception
    {
        final List<String> callLog = new ArrayList<>();
        final RecordingTestCase catalog = new RecordingTestCase("catalog", callLog);
        final RecordingTestCase orders = new RecordingTestCase("orders", callLog);
        final RecordingTestCase inventory = new RecordingTestCase("inventory", callLog);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders).add("inventory", inventory);
        testCase.preTest(map("catalog", someData(), "orders", someData(), "inventory",
                someData()));
        callLog.clear();

        testCase.postTest(true);

        assertThat(callLog)
                .as("Teardown must run in reverse declared order: inventory, then orders, then"
                        + " catalog.")
                .containsExactly("inventory:postTest(true)", "orders:postTest(true)",
                        "catalog:postTest(true)");
    }

    @Test
    void testPostTest_verifyFalse_discardsRowCountBaselineOnEachInvolvedDelegate()
            throws Exception
    {
        final RecordingTestCase catalog = new RecordingTestCase("catalog", new ArrayList<>());
        final RecordingTestCase orders = new RecordingTestCase("orders", new ArrayList<>());
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders);
        testCase.preTest(map("catalog", someData(), "orders", someData()));

        testCase.postTest(false);

        assertThat(catalog.lastPostTestVerifyData)
                .as("postTest(false) must call postTest(false) on every involved delegate - what"
                        + " makes each delegate discard its own row count baseline instead of"
                        + " verifying.")
                .isFalse();
        assertThat(orders.lastPostTestVerifyData).isFalse();
    }

    @Test
    void testPostTest_oneDelegateCleanupThrows_stillTearsDownTheOthersAndAggregates()
            throws Exception
    {
        final List<String> callLog = new ArrayList<>();
        final RecordingTestCase catalog = new RecordingTestCase("catalog", callLog);
        final RecordingTestCase orders = new RecordingTestCase("orders", callLog);
        orders.failOnPostTest(new RuntimeException("orders cleanup failed"));
        final RecordingTestCase inventory = new RecordingTestCase("inventory", callLog);
        final MultiDataSourcePrepAndExpectedTestCase testCase = MultiDataSourcePrepAndExpectedTestCase
                .from("catalog", catalog).add("orders", orders).add("inventory", inventory);
        testCase.preTest(map("catalog", someData(), "orders", someData(), "inventory",
                someData()));
        callLog.clear();

        final Throwable aggregate = catchThrowable(() -> testCase.postTest(true));

        assertThat(aggregate).isInstanceOf(MultiDataSourceAssertionError.class);
        assertThat(callLog)
                .as("catalog and inventory must still be torn down, in reverse declared order,"
                        + " even though orders failed in between.")
                .containsExactly("inventory:postTest(true)", "orders:postTest(true)",
                        "catalog:postTest(true)");
    }

    private static PrepAndExpectedTestData someData()
    {
        return new PrepAndExpectedTestData(new VerifyTableDefinition[0], new String[0],
                new String[0]);
    }

    private static Map<String, PrepAndExpectedTestData> map(final String name,
            final PrepAndExpectedTestData data)
    {
        final Map<String, PrepAndExpectedTestData> result = new LinkedHashMap<>();
        result.put(name, data);
        return result;
    }

    private static Map<String, PrepAndExpectedTestData> map(final String name1,
            final PrepAndExpectedTestData data1, final String name2,
            final PrepAndExpectedTestData data2)
    {
        final Map<String, PrepAndExpectedTestData> result = map(name1, data1);
        result.put(name2, data2);
        return result;
    }

    private static Map<String, PrepAndExpectedTestData> map(final String name1,
            final PrepAndExpectedTestData data1, final String name2,
            final PrepAndExpectedTestData data2, final String name3,
            final PrepAndExpectedTestData data3)
    {
        final Map<String, PrepAndExpectedTestData> result = map(name1, data1, name2, data2);
        result.put(name3, data3);
        return result;
    }

    /**
     * Records the exact {@code preTest}/{@code postTest(boolean)} calls and their order via a
     * shared call log, and can be told to throw on demand from either.
     */
    private static final class RecordingTestCase implements PrepAndExpectedTestCase
    {
        private final String name;

        private final List<String> callLog;

        private boolean touched;

        private Boolean lastPostTestVerifyData;

        private Throwable preTestFailure;

        private Throwable postTestFailure;

        RecordingTestCase(final String name, final List<String> callLog)
        {
            this.name = name;
            this.callLog = callLog;
        }

        void failOnPreTest(final Throwable failure)
        {
            preTestFailure = failure;
        }

        void failOnPostTest(final Throwable failure)
        {
            postTestFailure = failure;
        }

        private static void throwIfSet(final Throwable failure) throws Exception
        {
            if (failure == null)
            {
                return;
            }
            if (failure instanceof RuntimeException)
            {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error)
            {
                throw (Error) failure;
            }
            throw (Exception) failure;
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
        public void preTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles) throws Exception
        {
            touched = true;
            callLog.add(name + ":preTest");
            throwIfSet(preTestFailure);
        }

        @Override
        public Object runTest(final VerifyTableDefinition[] verifyTables,
                final String[] prepDataFiles, final String[] expectedDataFiles,
                final PrepAndExpectedTestCaseSteps testSteps)
        {
            return null;
        }

        @Override
        public void postTest()
        {
        }

        @Override
        public void postTest(final boolean verifyData) throws Exception
        {
            touched = true;
            lastPostTestVerifyData = verifyData;
            callLog.add(name + ":postTest(" + verifyData + ")");
            throwIfSet(postTestFailure);
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
}
