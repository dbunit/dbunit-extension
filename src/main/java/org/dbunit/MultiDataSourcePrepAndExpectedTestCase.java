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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.util.fileloader.DataFileLoader;

/**
 * Preps and verifies tables across multiple {@link org.dbunit.database.IDatabaseConnection}s
 * around one run of the code under test, by fanning the {@link PrepAndExpectedTestCase} lifecycle
 * out to one delegate per data source.
 * <p>
 * Holds an ordered {@code {dataSourceName -> PrepAndExpectedTestCase}} map, one delegate per data
 * source - normally a {@link DefaultPrepAndExpectedTestCase} bound to its own
 * {@link IDatabaseTester}. {@link #preTest(Map)} sets each involved delegate up in the map's
 * declared order; {@link #postTest(boolean)} verifies and cleans each up in reverse order,
 * collecting every delegate's failure into one {@link MultiDataSourceAssertionError} instead of
 * stopping at the first; {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} runs the test steps
 * exactly once in between. This class does not implement {@link PrepAndExpectedTestCase} itself -
 * that interface's singular accessors and lifecycle methods have no honest answer for N data
 * sources - and it drives each delegate only through that interface's own public
 * {@code preTest}/{@code postTest} methods, never a reimplemented verify or cleanup.
 * <p>
 * The data source name to {@link PrepAndExpectedTestCase} wiring is fixed for this instance's
 * life: assemble it with the constructors, {@link #from(String, PrepAndExpectedTestCase)}, the
 * {@code forTesters(...)} factories, and {@link #add(String, PrepAndExpectedTestCase)} /
 * {@link #add(String, IDatabaseTester)} / {@link #addAll(Map)}, all of which return this instance
 * so they chain; the wiring is then frozen on the first {@link #preTest(Map)} /
 * {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} call, and a further {@code add} throws
 * {@link IllegalStateException}. The {@code {dataSourceName -> PrepAndExpectedTestData}} map each
 * test run passes to {@link #preTest(Map)} / {@link #runTest(Map, PrepAndExpectedTestCaseSteps)}
 * varies per call; a wired data source absent from that map, or mapped to the exact
 * {@link PrepAndExpectedTestData#NONE} instance, sits that run out entirely - its connection is
 * never opened.
 * <p>
 * This instance is not thread-safe, but is safe to reuse sequentially - across
 * {@code @ParameterizedTest} rows, or as a longer-lived singleton reused across test classes -
 * since each {@link #preTest(Map)} / {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} /
 * {@link #postTest(boolean)} call, including its own teardown, completes before the next begins.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class MultiDataSourcePrepAndExpectedTestCase
{
    private final Map<String, PrepAndExpectedTestCase> testCasesByDataSourceName =
            new LinkedHashMap<>();

    private final DataFileLoader dataFileLoader;

    private final boolean closeConnectionAfterTest;

    private final boolean testerConstructionAllowed;

    private boolean frozen;

    private Set<String> involvedDataSourceNames = Collections.emptySet();

    /**
     * Creates an empty instance for the delegate currency: assemble it with
     * {@link #add(String, PrepAndExpectedTestCase)} / {@link #addAll(Map)}.
     */
    public MultiDataSourcePrepAndExpectedTestCase()
    {
        this(null, true, false);
    }

    /**
     * Creates an instance seeded with the given delegates, in the given map's iteration order.
     *
     * @param testCasesByDataSourceName
     *            The delegates to seed this instance with, keyed by data source name; not
     *            {@code null}, and not containing a {@code null} or blank key or a {@code null}
     *            value. Defensively copied; mutating this map afterward does not affect this
     *            instance.
     * @throws IllegalArgumentException
     *             If testCasesByDataSourceName is {@code null}, or contains a {@code null} or
     *             blank key, or a {@code null} value.
     */
    public MultiDataSourcePrepAndExpectedTestCase(
            final Map<String, ? extends PrepAndExpectedTestCase> testCasesByDataSourceName)
    {
        this();
        addAll(testCasesByDataSourceName);
    }

    private MultiDataSourcePrepAndExpectedTestCase(final DataFileLoader dataFileLoader,
            final boolean closeConnectionAfterTest, final boolean testerConstructionAllowed)
    {
        this.dataFileLoader = dataFileLoader;
        this.closeConnectionAfterTest = closeConnectionAfterTest;
        this.testerConstructionAllowed = testerConstructionAllowed;
    }

    /**
     * Creates an instance seeded with one data source, for the delegate currency.
     *
     * @param dataSourceName
     *            The data source name; not {@code null} or blank.
     * @param testCase
     *            The delegate for that data source; not {@code null}.
     * @return The new instance, so further {@code add}/{@code addAll} calls can chain off it.
     * @throws IllegalArgumentException
     *             If dataSourceName is {@code null} or blank, or if testCase is {@code null}.
     */
    public static MultiDataSourcePrepAndExpectedTestCase from(final String dataSourceName,
            final PrepAndExpectedTestCase testCase)
    {
        return new MultiDataSourcePrepAndExpectedTestCase().add(dataSourceName, testCase);
    }

    /**
     * Creates an empty instance for the tester currency - {@link #add(String, IDatabaseTester)}
     * wraps each tester in a {@link DefaultPrepAndExpectedTestCase} sharing the given loader, with
     * {@code closeConnectionAfterTest} true.
     *
     * @param dataFileLoader
     *            The loader every {@link #add(String, IDatabaseTester)}-wrapped delegate shares.
     * @return The new instance.
     */
    public static MultiDataSourcePrepAndExpectedTestCase forTesters(
            final DataFileLoader dataFileLoader)
    {
        return forTesters(dataFileLoader, true);
    }

    /**
     * Creates an empty instance for the tester currency, as {@link #forTesters(DataFileLoader)}
     * does, with the given {@code closeConnectionAfterTest} applied to every
     * {@link #add(String, IDatabaseTester)}-wrapped delegate.
     *
     * @param dataFileLoader
     *            The loader every {@link #add(String, IDatabaseTester)}-wrapped delegate shares.
     * @param closeConnectionAfterTest
     *            Whether each {@link #add(String, IDatabaseTester)}-wrapped delegate closes its
     *            connection after each test; false to reuse each delegate's connection across
     *            {@code @ParameterizedTest} rows, as for a lone {@link DefaultPrepAndExpectedTestCase}.
     * @return The new instance.
     */
    public static MultiDataSourcePrepAndExpectedTestCase forTesters(
            final DataFileLoader dataFileLoader, final boolean closeConnectionAfterTest)
    {
        return new MultiDataSourcePrepAndExpectedTestCase(dataFileLoader,
                closeConnectionAfterTest, true);
    }

    /**
     * Creates an instance for the tester currency, seeded with the given testers - as
     * {@link #forTesters(DataFileLoader)} does, then {@link #add(String, IDatabaseTester)} for
     * each entry, in the given map's iteration order.
     *
     * @param dataFileLoader
     *            The loader every wrapped delegate shares.
     * @param testersByDataSourceName
     *            The testers to seed this instance with, keyed by data source name; not
     *            {@code null}.
     * @return The new instance.
     * @throws IllegalArgumentException
     *             If testersByDataSourceName is {@code null}, or contains a {@code null} or
     *             blank key, or a {@code null} value.
     */
    public static MultiDataSourcePrepAndExpectedTestCase forTesters(
            final DataFileLoader dataFileLoader,
            final Map<String, ? extends IDatabaseTester> testersByDataSourceName)
    {
        return forTesters(dataFileLoader, testersByDataSourceName, true);
    }

    /**
     * Creates an instance for the tester currency, seeded with the given testers, as
     * {@link #forTesters(DataFileLoader, Map)} does, with the given
     * {@code closeConnectionAfterTest} applied to every wrapped delegate.
     *
     * @param dataFileLoader
     *            The loader every wrapped delegate shares.
     * @param testersByDataSourceName
     *            The testers to seed this instance with, keyed by data source name; not
     *            {@code null}.
     * @param closeConnectionAfterTest
     *            Whether each wrapped delegate closes its connection after each test.
     * @return The new instance.
     * @throws IllegalArgumentException
     *             If testersByDataSourceName is {@code null}, or contains a {@code null} or
     *             blank key, or a {@code null} value.
     */
    public static MultiDataSourcePrepAndExpectedTestCase forTesters(
            final DataFileLoader dataFileLoader,
            final Map<String, ? extends IDatabaseTester> testersByDataSourceName,
            final boolean closeConnectionAfterTest)
    {
        if (testersByDataSourceName == null)
        {
            throw new IllegalArgumentException("testersByDataSourceName must not be null.");
        }

        final MultiDataSourcePrepAndExpectedTestCase instance =
                forTesters(dataFileLoader, closeConnectionAfterTest);
        for (final Map.Entry<String, ? extends IDatabaseTester> entry : testersByDataSourceName
                .entrySet())
        {
            instance.add(entry.getKey(), entry.getValue());
        }
        return instance;
    }

    /**
     * Wires one data source to the given delegate.
     *
     * @param dataSourceName
     *            The data source name; not {@code null} or blank, and not already wired.
     * @param testCase
     *            The delegate for that data source; not {@code null}.
     * @return This instance, so calls can chain.
     * @throws IllegalArgumentException
     *             If dataSourceName is {@code null}, blank, or already wired, or if testCase is
     *             {@code null}.
     * @throws IllegalStateException
     *             If the wiring is already frozen by an earlier {@link #preTest(Map)} /
     *             {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} call.
     */
    public MultiDataSourcePrepAndExpectedTestCase add(final String dataSourceName,
            final PrepAndExpectedTestCase testCase)
    {
        checkNotFrozen();
        if (dataSourceName == null || dataSourceName.trim().isEmpty())
        {
            throw new IllegalArgumentException("dataSourceName must not be null or blank.");
        }
        if (testCase == null)
        {
            throw new IllegalArgumentException("testCase must not be null.");
        }
        if (testCasesByDataSourceName.containsKey(dataSourceName))
        {
            throw new IllegalArgumentException(
                    "A data source named '" + dataSourceName + "' is already wired.");
        }

        testCasesByDataSourceName.put(dataSourceName, testCase);
        return this;
    }

    /**
     * Wires one data source to a new {@link DefaultPrepAndExpectedTestCase} bound to the given
     * tester, sharing this instance's loader and {@code closeConnectionAfterTest}.
     *
     * @param dataSourceName
     *            The data source name; not {@code null} or blank, and not already wired.
     * @param tester
     *            The tester for that data source; not {@code null}.
     * @return This instance, so calls can chain.
     * @throws IllegalArgumentException
     *             If dataSourceName is {@code null}, blank, or already wired, or if tester is
     *             {@code null}.
     * @throws IllegalStateException
     *             If this instance was not created via one of the {@code forTesters(...)}
     *             factories, or if the wiring is already frozen.
     */
    public MultiDataSourcePrepAndExpectedTestCase add(final String dataSourceName,
            final IDatabaseTester tester)
    {
        checkNotFrozen();
        if (!testerConstructionAllowed)
        {
            throw new IllegalStateException("add(String, IDatabaseTester) requires an instance"
                    + " created via one of the forTesters(...) factories.");
        }
        if (tester == null)
        {
            throw new IllegalArgumentException("tester must not be null.");
        }

        return add(dataSourceName,
                new DefaultPrepAndExpectedTestCase(dataFileLoader, tester,
                        closeConnectionAfterTest));
    }

    /**
     * Wires every entry of the given map, in its iteration order.
     *
     * @param testCasesByDataSourceName
     *            The delegates to add, keyed by data source name; not {@code null}.
     * @return This instance, so calls can chain.
     * @throws IllegalArgumentException
     *             If testCasesByDataSourceName is {@code null}, or contains a {@code null} or
     *             blank key, an already-wired key, or a {@code null} value.
     * @throws IllegalStateException
     *             If the wiring is already frozen by an earlier {@link #preTest(Map)} /
     *             {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} call.
     */
    public MultiDataSourcePrepAndExpectedTestCase addAll(
            final Map<String, ? extends PrepAndExpectedTestCase> testCasesByDataSourceName)
    {
        checkNotFrozen();
        if (testCasesByDataSourceName == null)
        {
            throw new IllegalArgumentException("testCasesByDataSourceName must not be null.");
        }

        for (final Map.Entry<String, ? extends PrepAndExpectedTestCase> entry
                : testCasesByDataSourceName.entrySet())
        {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Runs the full lifecycle once: {@link #preTest(Map)}, then the given test steps exactly
     * once, then {@link #postTest(boolean) postTest(true)}.
     * <p>
     * A throwable from the test steps is rethrown as-is - not wrapped - with every involved
     * delegate torn down first via {@link PrepAndExpectedTestCase#postTest(boolean)
     * postTest(false)} in reverse declared order, each teardown failure attached to the step
     * throwable via {@link Throwable#addSuppressed(Throwable)} - except a teardown failure that
     * is the exact same instance as the step throwable itself, which is dropped rather than
     * attempting the self-suppression {@link Throwable#addSuppressed(Throwable)} forbids.
     *
     * @param dataByDataSourceName
     *            The prep/expected/verify data for this run, keyed by data source name; a wired
     *            data source absent from this map, or mapped to {@link PrepAndExpectedTestData#NONE},
     *            sits this run out entirely.
     * @param testSteps
     *            The test steps to run, exactly once.
     * @return The user-defined object the test steps returned.
     * @throws Exception
     *             If {@link #preTest(Map)} fails, if the test steps fail, or if
     *             {@link #postTest(boolean)} fails.
     */
    public Object runTest(final Map<String, PrepAndExpectedTestData> dataByDataSourceName,
            final PrepAndExpectedTestCaseSteps testSteps) throws Exception
    {
        preTest(dataByDataSourceName);

        final Object result;
        try
        {
            result = testSteps.run();
        } catch (final Throwable stepFailure)
        {
            tearDownSuppressing(reverseInvolvedOrder(), stepFailure);
            throw stepFailure;
        }

        postTest(true);
        return result;
    }

    /**
     * Sets up every involved data source's prep data, in declared order.
     * <p>
     * A wired data source absent from {@code dataByDataSourceName}, or mapped to the exact
     * {@link PrepAndExpectedTestData#NONE} instance, is skipped entirely this run - its
     * {@code preTest} is never called and its connection is never opened. An unknown key in
     * {@code dataByDataSourceName} - one not wired to this instance - is rejected before any
     * delegate is touched.
     * <p>
     * If an involved delegate's own {@code preTest} fails, every involved delegate set up so far -
     * including the one that just failed - is rolled back, via
     * {@link PrepAndExpectedTestCase#postTest(boolean) postTest(false)}, in reverse order, never a
     * bare {@code cleanupData()} - with each rollback failure attached to the original failure via
     * {@link Throwable#addSuppressed(Throwable)}, except a rollback failure that is the exact same
     * instance as the original failure, which is dropped rather than attempting the
     * self-suppression {@link Throwable#addSuppressed(Throwable)} forbids; the original failure is
     * then rethrown as-is.
     *
     * @param dataByDataSourceName
     *            The prep/expected/verify data for this run, keyed by data source name; not
     *            {@code null}, and containing no key this instance has no delegate wired for.
     * @throws IllegalStateException
     *             If no data source is wired yet.
     * @throws IllegalArgumentException
     *             If dataByDataSourceName is {@code null}, contains an unknown data source name,
     *             or maps a wired data source name to {@code null}.
     * @throws Exception
     *             If an involved delegate's own {@code preTest} fails.
     */
    public void preTest(final Map<String, PrepAndExpectedTestData> dataByDataSourceName)
            throws Exception
    {
        if (testCasesByDataSourceName.isEmpty())
        {
            throw new IllegalStateException(
                    "No data source is wired; call add(...) before preTest(...).");
        }
        if (dataByDataSourceName == null)
        {
            throw new IllegalArgumentException("dataByDataSourceName must not be null.");
        }

        final Set<String> unknownDataSourceNames = new LinkedHashSet<>(
                dataByDataSourceName.keySet());
        unknownDataSourceNames.removeAll(testCasesByDataSourceName.keySet());
        if (!unknownDataSourceNames.isEmpty())
        {
            throw new IllegalArgumentException("Unknown data source name(s) "
                    + unknownDataSourceNames + "; wired data source(s) are "
                    + testCasesByDataSourceName.keySet() + ".");
        }

        final Set<String> involved = new LinkedHashSet<>();
        for (final String dataSourceName : testCasesByDataSourceName.keySet())
        {
            final boolean hasEntry = dataByDataSourceName.containsKey(dataSourceName);
            final PrepAndExpectedTestData data = dataByDataSourceName.get(dataSourceName);
            if (hasEntry && data == null)
            {
                throw new IllegalArgumentException("dataByDataSourceName must not map \""
                        + dataSourceName + "\" to null; omit the key or map it to"
                        + " PrepAndExpectedTestData.NONE to skip that data source.");
            }
            if (data != null && data != PrepAndExpectedTestData.NONE)
            {
                involved.add(dataSourceName);
            }
        }

        frozen = true;
        final List<String> setUpSoFar = new ArrayList<>();
        for (final String dataSourceName : involved)
        {
            final PrepAndExpectedTestData data = dataByDataSourceName.get(dataSourceName);
            final PrepAndExpectedTestCase testCase = testCasesByDataSourceName.get(dataSourceName);
            try
            {
                testCase.preTest(data.getVerifyTableDefinitions(), data.getPrepDataFiles(),
                        data.getExpectedDataFiles());
            } catch (final Throwable setupFailure)
            {
                setUpSoFar.add(dataSourceName);
                rollBackSuppressing(setUpSoFar, setupFailure);
                involvedDataSourceNames = Collections.emptySet();
                throw setupFailure;
            }
            setUpSoFar.add(dataSourceName);
        }

        involvedDataSourceNames = involved;
    }

    private void rollBackSuppressing(final List<String> setUpSoFar, final Throwable primaryFailure)
    {
        final List<String> reverseOrder = new ArrayList<>(setUpSoFar);
        Collections.reverse(reverseOrder);
        tearDownSuppressing(reverseOrder, primaryFailure);
    }

    private void tearDownSuppressing(final List<String> dataSourceNamesInTeardownOrder,
            final Throwable primaryFailure)
    {
        for (final String dataSourceName : dataSourceNamesInTeardownOrder)
        {
            try
            {
                testCasesByDataSourceName.get(dataSourceName).postTest(false);
            } catch (final Throwable teardownFailure)
            {
                // Throwable.addSuppressed() throws IllegalArgumentException for
                // self-suppression; skip it rather than let that mask the real failure.
                if (teardownFailure != primaryFailure)
                {
                    primaryFailure.addSuppressed(teardownFailure);
                }
            }
        }
    }

    /**
     * Verifies and cleans up every involved data source, in reverse declared order.
     * <p>
     * Every involved delegate's own {@link PrepAndExpectedTestCase#postTest(boolean)} runs -
     * verify (or, when {@code verifyData} is false, its own row count baseline discard) and
     * cleanup - even if an earlier one in this reverse order failed; every collected failure is
     * then aggregated into one {@link MultiDataSourceAssertionError} rather than the first one
     * stopping the rest from being torn down.
     *
     * @param verifyData
     *            True to verify each involved delegate's data before cleaning it up; false to
     *            skip verification (the test steps already failed) and just clean up.
     * @throws MultiDataSourceAssertionError
     *             If one or more involved delegates failed their own {@code postTest}.
     */
    public void postTest(final boolean verifyData) throws Exception
    {
        final Map<String, Throwable> failuresByDataSourceName = new LinkedHashMap<>();
        for (final String dataSourceName : reverseInvolvedOrder())
        {
            try
            {
                testCasesByDataSourceName.get(dataSourceName).postTest(verifyData);
            } catch (final Throwable failure)
            {
                failuresByDataSourceName.put(dataSourceName, failure);
            }
        }

        if (failuresByDataSourceName.isEmpty())
        {
            return;
        }

        final List<MultiDataSourceAssertionError.NamedFailure> namedFailures = new ArrayList<>();
        for (final String dataSourceName : testCasesByDataSourceName.keySet())
        {
            final Throwable failure = failuresByDataSourceName.get(dataSourceName);
            if (failure != null)
            {
                namedFailures.add(new MultiDataSourceAssertionError.NamedFailure(dataSourceName,
                        phaseOf(verifyData, failure), failure));
            }
        }
        throw MultiDataSourceAssertionError.aggregate(namedFailures);
    }

    // Only an AssertionError reliably means the verify step failed - a checked exception (e.g. a
    // DataSetException from a malformed dataset) could equally have come from cleanupData(), so
    // it is labelled generically rather than guessed wrong.
    private static String phaseOf(final boolean verifyData, final Throwable failure)
    {
        if (!verifyData)
        {
            return "cleanupData";
        }
        return failure instanceof AssertionError ? "verifyData" : "postTest";
    }

    private List<String> reverseInvolvedOrder()
    {
        final List<String> reverseOrder = new ArrayList<>(involvedDataSourceNames);
        Collections.reverse(reverseOrder);
        return reverseOrder;
    }

    private void checkNotFrozen()
    {
        if (frozen)
        {
            throw new IllegalStateException(
                    "The data source wiring is frozen after the first preTest(...)/runTest(...)"
                            + " call; assemble it fully before running a test.");
        }
    }

    /**
     * Returns the wired data source names, in declared order.
     *
     * @return An unmodifiable view of the wired data source names, in declared order.
     */
    public Set<String> getDataSourceNames()
    {
        return Collections.unmodifiableSet(testCasesByDataSourceName.keySet());
    }

    /**
     * Returns the delegate wired to one data source, for a test that needs to reach one directly -
     * e.g. to assert something extra on it, or to inspect it after a failure.
     *
     * @param dataSourceName
     *            The data source name.
     * @return The delegate wired to that data source.
     * @throws IllegalArgumentException
     *             If no data source with that name is wired.
     */
    public PrepAndExpectedTestCase getTestCase(final String dataSourceName)
    {
        final PrepAndExpectedTestCase testCase = testCasesByDataSourceName.get(dataSourceName);
        if (testCase == null)
        {
            throw new IllegalArgumentException(
                    "No data source named '" + dataSourceName + "' is wired.");
        }
        return testCase;
    }
}
