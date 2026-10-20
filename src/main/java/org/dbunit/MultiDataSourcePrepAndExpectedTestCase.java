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
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

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
 * The data source name to {@link PrepAndExpectedTestCase} wiring is complete and fixed when the
 * instance is created, with {@link #builder()} or the
 * {@link #MultiDataSourcePrepAndExpectedTestCase(Map) Map constructor}:
 *
 * <pre>
 * MultiDataSourcePrepAndExpectedTestCase.builder()
 *         .dataFileLoader(loader)
 *         .add("orders", ordersTester)
 *         .add("audit", auditTestCase)
 *         .build();
 * </pre>
 *
 * The {@code {dataSourceName -> PrepAndExpectedTestData}} map each test run passes to
 * {@link #preTest(Map)} / {@link #runTest(Map, PrepAndExpectedTestCaseSteps)} varies per call; a
 * wired data source absent from that map, or mapped to the exact
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
    private final Map<String, PrepAndExpectedTestCase> testCasesByDataSourceName;

    private Set<String> involvedDataSourceNames = Collections.emptySet();

    /**
     * Creates an instance wired to the given delegates, in the given map's iteration order. Use
     * {@link #builder()} to wire testers, or to set the loader and the connection policy of the
     * test cases that wrap them.
     *
     * @param testCasesByDataSourceName
     *            The delegates to wire, keyed by data source name; not {@code null} or empty,
     *            and not containing a {@code null} or blank key or a {@code null} value.
     *            Defensively copied; mutating this map afterward does not affect this
     *            instance.
     * @throws IllegalArgumentException
     *             If testCasesByDataSourceName is {@code null} or empty, or contains a
     *             {@code null} or blank key, or a {@code null} value.
     */
    public MultiDataSourcePrepAndExpectedTestCase(
            final Map<String, ? extends PrepAndExpectedTestCase> testCasesByDataSourceName)
    {
        this(builder().addAll(testCasesByDataSourceName));
    }

    private MultiDataSourcePrepAndExpectedTestCase(final Builder builder)
    {
        this.testCasesByDataSourceName = Collections.unmodifiableMap(builder.wire());
    }

    /**
     * Creates a builder, the way to wire testers and delegates in any order.
     *
     * @return A new, empty builder.
     */
    public static Builder builder()
    {
        return new Builder();
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
     * attempting the self-suppression {@link Throwable#addSuppressed(Throwable)} forbids. The run
     * is over then, as after {@link #postTest(boolean)}, so a further {@code postTest} call, such
     * as one in a caller's {@code finally} block, has nothing to tear down.
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
            final List<String> teardownOrder = reverseInvolvedOrder();
            involvedDataSourceNames = Collections.emptySet();
            tearDownSuppressing(teardownOrder, stepFailure);
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
     * @throws IllegalArgumentException
     *             If dataByDataSourceName is {@code null}, contains an unknown data source name,
     *             or maps a wired data source name to {@code null}.
     * @throws Exception
     *             If an involved delegate's own {@code preTest} fails.
     */
    public void preTest(final Map<String, PrepAndExpectedTestData> dataByDataSourceName)
            throws Exception
    {
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

        involvedDataSourceNames = Collections.emptySet();
        final List<String> setUpSoFar = new ArrayList<>();
        for (final String dataSourceName : involved)
        {
            final PrepAndExpectedTestData data = dataByDataSourceName.get(dataSourceName);
            final PrepAndExpectedTestCase testCase = testCasesByDataSourceName.get(dataSourceName);
            try
            {
                testCase.preTest(data);
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
     * <p>
     * The run is over once this is called, whether it returns or throws: a further call without
     * a new {@code preTest} in between, such as one in a caller's {@code finally} block after a
     * rejected {@code preTest}, has nothing to tear down.
     *
     * @param verifyData
     *            True to verify each involved delegate's data before cleaning it up; false to
     *            skip verification (the test steps already failed) and just clean up.
     * @throws MultiDataSourceAssertionError
     *             If one or more involved delegates failed their own {@code postTest}.
     */
    public void postTest(final boolean verifyData) throws Exception
    {
        final List<String> teardownOrder = reverseInvolvedOrder();
        involvedDataSourceNames = Collections.emptySet();
        final Map<String, Throwable> failuresByDataSourceName = new LinkedHashMap<>();
        for (final String dataSourceName : teardownOrder)
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

    /**
     * Wires the data sources of a {@link MultiDataSourcePrepAndExpectedTestCase}, in the order
     * they are added, and builds it. Each data source gets either a delegate of its own or a
     * tester, which {@link #build()} wraps in a {@link DefaultPrepAndExpectedTestCase} that uses
     * the loader and the connection policy of this builder, whichever order they were set in.
     *
     * <p>Not thread-safe. An instance it builds is not changed by anything done to the builder
     * afterward.
     *
     * @author Jeff Jensen
     * @since 3.6.0
     */
    public static class Builder
    {
        private final Map<String, Wiring> wiringByDataSourceName = new LinkedHashMap<>();

        private DataFileLoader dataFileLoader;

        private boolean closeConnectionAfterTest = true;

        private Builder()
        {
        }

        /**
         * Sets the loader that every test case wrapping a tester added to this builder uses.
         *
         * @param dataFileLoader
         *            The loader; {@code null}, the default, chooses a
         *            {@link FileExtensionDataFileLoader}, which picks the format from the file
         *            extension.
         * @return This builder, so calls can chain.
         */
        public Builder dataFileLoader(final DataFileLoader dataFileLoader)
        {
            this.dataFileLoader = dataFileLoader;
            return this;
        }

        /**
         * Sets whether every test case wrapping a tester added to this builder closes its
         * connection after each test.
         *
         * @param closeConnectionAfterTest
         *            {@code true}, the default, to close each connection after each test;
         *            {@code false} to reuse it across {@code @ParameterizedTest} rows, as for a
         *            lone {@link DefaultPrepAndExpectedTestCase}.
         * @return This builder, so calls can chain.
         */
        public Builder closeConnectionAfterTest(final boolean closeConnectionAfterTest)
        {
            this.closeConnectionAfterTest = closeConnectionAfterTest;
            return this;
        }

        /**
         * Wires one data source to the given delegate.
         *
         * @param dataSourceName
         *            The data source name; not {@code null} or blank, and not already wired.
         * @param testCase
         *            The delegate for that data source; not {@code null}.
         * @return This builder, so calls can chain.
         * @throws IllegalArgumentException
         *             If dataSourceName is {@code null}, blank, or already wired, or if testCase
         *             is {@code null}.
         */
        public Builder add(final String dataSourceName, final PrepAndExpectedTestCase testCase)
        {
            requireDataSourceName(dataSourceName);
            if (testCase == null)
            {
                throw new IllegalArgumentException("testCase must not be null.");
            }
            return register(dataSourceName, new Wiring(testCase, null));
        }

        /**
         * Wires one data source to the given tester, which {@link #build()} wraps in a new
         * {@link DefaultPrepAndExpectedTestCase} that uses this builder's loader and connection
         * policy.
         *
         * @param dataSourceName
         *            The data source name; not {@code null} or blank, and not already wired.
         * @param tester
         *            The tester for that data source; not {@code null}.
         * @return This builder, so calls can chain.
         * @throws IllegalArgumentException
         *             If dataSourceName is {@code null}, blank, or already wired, or if tester
         *             is {@code null}.
         */
        public Builder add(final String dataSourceName, final IDatabaseTester tester)
        {
            requireDataSourceName(dataSourceName);
            if (tester == null)
            {
                throw new IllegalArgumentException("tester must not be null.");
            }
            return register(dataSourceName, new Wiring(null, tester));
        }

        /**
         * Wires every entry of the given map to its delegate, in the map's iteration order.
         *
         * @param testCasesByDataSourceName
         *            The delegates to wire, keyed by data source name; not {@code null}.
         * @return This builder, so calls can chain.
         * @throws IllegalArgumentException
         *             If testCasesByDataSourceName is {@code null}, or contains a {@code null},
         *             blank or already wired key, or a {@code null} value.
         */
        public Builder addAll(
                final Map<String, ? extends PrepAndExpectedTestCase> testCasesByDataSourceName)
        {
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
         * Builds an instance wired to the data sources added so far. Each call builds a new
         * instance, with new test cases wrapping the testers.
         *
         * @return The new instance.
         * @throws IllegalArgumentException
         *             If no data source was added.
         */
        public MultiDataSourcePrepAndExpectedTestCase build()
        {
            return new MultiDataSourcePrepAndExpectedTestCase(this);
        }

        private Map<String, PrepAndExpectedTestCase> wire()
        {
            if (wiringByDataSourceName.isEmpty())
            {
                throw new IllegalArgumentException("At least one data source is required.");
            }

            final DataFileLoader loader = resolveDataFileLoader();
            final Map<String, PrepAndExpectedTestCase> wired = new LinkedHashMap<>();
            for (final Map.Entry<String, Wiring> entry : wiringByDataSourceName.entrySet())
            {
                final Wiring wiring = entry.getValue();
                wired.put(entry.getKey(), wiring.create(loader, closeConnectionAfterTest));
            }
            return wired;
        }

        private DataFileLoader resolveDataFileLoader()
        {
            if (dataFileLoader == null)
            {
                return new FileExtensionDataFileLoader();
            }
            return dataFileLoader;
        }

        private void requireDataSourceName(final String dataSourceName)
        {
            if (dataSourceName == null || dataSourceName.trim().isEmpty())
            {
                throw new IllegalArgumentException("dataSourceName must not be null or blank.");
            }
        }

        private Builder register(final String dataSourceName, final Wiring wiring)
        {
            if (wiringByDataSourceName.containsKey(dataSourceName))
            {
                throw new IllegalArgumentException(
                        "A data source named '" + dataSourceName + "' is already wired.");
            }

            wiringByDataSourceName.put(dataSourceName, wiring);
            return this;
        }
    }

    /**
     * What one data source was given: a delegate of its own, or a tester that is wrapped in a
     * test case when the instance is built.
     */
    private static class Wiring
    {
        private final PrepAndExpectedTestCase testCase;

        private final IDatabaseTester tester;

        private Wiring(final PrepAndExpectedTestCase testCase, final IDatabaseTester tester)
        {
            this.testCase = testCase;
            this.tester = tester;
        }

        private PrepAndExpectedTestCase create(final DataFileLoader loader,
                final boolean closeConnectionAfterTest)
        {
            if (testCase == null)
            {
                return new DefaultPrepAndExpectedTestCase(loader, tester,
                        closeConnectionAfterTest);
            }
            return testCase;
        }
    }
}
