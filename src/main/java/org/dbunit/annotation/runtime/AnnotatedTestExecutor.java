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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.DefaultOperationListener;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.RowCountChecker;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one test's dbUnit lifecycle from a resolved {@link AnnotatedTestConfiguration}: the
 * setup/teardown path when {@link AnnotatedTestConfiguration#isExpected()} is false, or
 * the {@link PrepAndExpectedTestCase} prep/expected path when it is true.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension}, which resolves the {@link IDatabaseTester} and any injected
 * {@link PrepAndExpectedTestCase} - field discovery is binding-specific - and hands them here
 * already resolved.
 *
 * <p>The binding also tells this executor, through the constructor's {@code annotationDriven}
 * flag, whether the test opted into the {@code org.dbunit.annotation} family at all - any
 * {@code @DbUnit*} annotation, or a {@code @DbUnitTester}/{@code @DbUnitTestCase} field. When it
 * did not - a bare {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
 * {@link IDatabaseTester} field, the 3.5.0 lifecycle-only style - this executor takes the
 * <em>classic path</em>: it does not touch the tester's {@link IOperationListener}, and it does
 * not hold the tester's connection past {@code onSetup()}. It captures the row count check
 * baseline from a connection of its own, closes that connection again right after
 * {@code onSetup()}, and lets {@code onSetup()}/{@code onTearDown()} manage their own
 * connections through the tester's own, unchanged listener - exactly as {@code DbUnitExtension}
 * did before this class existed, so a 3.5.0 test that opts into nothing sees no lifecycle
 * change. The {@code ExecutorOperationListener} wrap, the baseline piggyback, and the
 * held-until-end-of-test connection described below all apply only when {@code annotationDriven}
 * is true; the prep/expected path is always annotation-driven (it needs {@code @DbUnitExpected}).
 *
 * <p>The row count check (see {@code org.dbunit.database.rowcount.RowCountCheck}) is captured
 * and verified by this class only for the setup/teardown path; the prep/expected path already has its
 * own, via {@link DefaultPrepAndExpectedTestCase#preTest()} and {@code cleanupData()} -
 * wrapping it a second time here would just repeat the same table counts. Either way, when
 * {@code @DbUnitRowCountCheck} is declared, its resolved {@code RowCountCheck} overrides
 * whichever of the two would otherwise resolve one from the connection's own
 * {@link DatabaseConfig} - for the prep/expected path via
 * {@link DefaultPrepAndExpectedTestCase#setRowCountCheckOverride(boolean, String[])}, which
 * resolves that connection lazily on its own rather than this executor needing one just to
 * build the override.
 *
 * <p>Capturing that baseline needs a connection - to read whether the check is even enabled
 * before anything else can happen - but resolving one just for that would cost a plain
 * {@code JdbcDatabaseTester} an extra physical connection every test, on top of whatever
 * {@code onSetup()} itself opens, even when the check turns out disabled. So, on the
 * annotation-driven paths, {@link #beforeSetupTeardownTest()} captures the baseline from
 * whichever connection ends up cheapest: when
 * {@link #canPiggybackRowCountBaseline(DatabaseOperation)} can prove {@code onSetup()} is
 * about to retrieve one on its own - {@code tester} is an {@link AbstractDatabaseTester} and its
 * resolved setup operation is not {@link DatabaseOperation#NONE} - capture piggybacks on that
 * connection via {@link #captureRowCountBaselineOnFirstConnectionRetrieved(IDatabaseConnection)},
 * so this executor never resolves a connection of its own at all; otherwise - a custom
 * {@link IDatabaseTester}, a {@link DatabaseOperation#NONE} setup operation, or a connection
 * already memoized from earlier - {@link #captureRowCountBaseline()} resolves one eagerly, the
 * same way every version of this class before the optimization always did. The classic path
 * always takes that eager route - there is no listener to piggyback on. Either way a baseline
 * is always decided - captured, or found disabled - before the test method runs; which path
 * got there differs only in cost, never in outcome.
 *
 * <p>On the annotation-driven paths, the connection {@link IDatabaseTester#getConnection()}
 * returns for the row count check and for parameter injection is memoized by
 * {@link #getConnection()} and closed in {@link #afterTest(boolean)} when
 * {@code @DbUnitConfig#closeConnectionAfterTest()} (default {@code true}) allows it - the same
 * flag {@link DefaultPrepAndExpectedTestCase} already uses for the connection it resolves on
 * its own - and the tester's own {@link IOperationListener} is not
 * {@link IOperationListener#NO_OP_OPERATION_LISTENER} (nor an {@code ExecutorOperationListener}
 * wrapping it), the established signal a tester's connection is shared beyond this one test -
 * e.g. a {@code JdbcDatabaseTester} built with a
 * {@link org.dbunit.database.CachingConnectionProvider}, or a {@code DefaultDatabaseTester}
 * built from one fixed, externally-owned connection - so this executor leaves closing it to
 * that connection's actual owner. Set {@code closeConnectionAfterTest} to {@code false}
 * explicitly for a shared connection protected by some other, non-{@code NO_OP} listener. The
 * classic path resolves its baseline connection from {@link IDatabaseTester#getConnection()}
 * the same way and closes it only in {@link #afterTest(boolean)}, honoring the same
 * {@code NO_OP}/{@code closeConnectionAfterTest} checks - it never closes that connection
 * before {@code onSetup()}, since a tester that returns one fixed connection from every call
 * (e.g. a {@code DefaultDatabaseTester} built from a fixed connection) would then run
 * {@code onSetup()} against a closed one. A connection resolved only to discover the check is
 * disabled is still held until {@link #afterTest(boolean)} rather than released right away;
 * for a fresh-connection tester such as a plain {@code JdbcDatabaseTester} that is one idle
 * connection for the test's duration, the same as the annotation-driven paths when the check
 * is off and no {@code Connection}/{@code IDatabaseConnection} parameter is injected.
 *
 * <p>On the annotation-driven paths that memoized connection is also protected from a premature
 * close by the tester's <em>own</em> setup/teardown machinery: for a tester whose
 * {@link IDatabaseTester#getConnection()} returns the same connection object on every call (e.g.
 * the two examples above) and carries no listener of its own, {@code onSetup()} would otherwise
 * install a plain {@code DefaultOperationListener} that closes the connection the instant setup
 * finishes - the same object this executor is still holding onto for the row count check's later
 * verify, or for a parameter injected into the test method.
 * {@link #installOperationListener()} wraps the tester's listener in an
 * {@code ExecutorOperationListener} that recognizes this executor's own memoized connection by
 * identity and lets only {@link #afterTest(boolean)} decide when to close it, while still
 * forwarding a close notification for any other connection object exactly as before - so a
 * tester that hands out a fresh connection per call (e.g. a plain {@code JdbcDatabaseTester})
 * still has each of those closed right after its own operation, same as always.
 *
 * <p>On the prep/expected path, {@link #getConnection()} resolves a parameter injection through
 * {@link PrepAndExpectedTestCase#getReusableConnection()} instead of asking {@link #tester}
 * directly, so the connection a test method's {@code Connection}/{@code IDatabaseConnection}
 * parameter receives is the same one that test case's own setup/verify/cleanup steps use,
 * rather than a second one this executor would otherwise resolve independently. The
 * close-protection described above is specific to the setup/teardown path's tester-sourced connection;
 * the prep/expected path's connection - shared with the test case - is closed by whichever of
 * this executor or the test case gets there first, the other's close call finding it already
 * closed.
 *
 * <p>{@link #installOperationListener()} runs from the constructor only when
 * {@code annotationDriven} is true - so the {@code ExecutorOperationListener} wrap covers every
 * annotation-driven test regardless of which path it takes, and no classic test at all. On the
 * prep/expected path, {@link DefaultPrepAndExpectedTestCase} never actually triggers it: it
 * calls {@code IDatabaseTester#getConnection()} directly (no listener notification - that only
 * fires from inside {@code AbstractDatabaseTester#executeOperation()}), and drives its own
 * internally-built tester for the actual setup/teardown operations, which carries its own,
 * separate listener rather than this one. A different {@link PrepAndExpectedTestCase}
 * implementation that instead calls {@code tester.onSetup()}/{@code onTearDown()} directly
 * would trigger it; {@link #captureRowCountBaselineOnFirstConnectionRetrieved(IDatabaseConnection)}
 * guards against that specifically, rather than assuming - as {@link DefaultPrepAndExpectedTestCase}'s
 * behavior might otherwise suggest - that this listener is simply unreachable on that path for
 * every implementation.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AnnotatedTestExecutor
{
    private static final Logger log = LoggerFactory.getLogger(AnnotatedTestExecutor.class);

    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final boolean annotationDriven;
    private final boolean prepAndExpectedTestCaseInjected;
    private final RowCountChecker rowCountChecker = new RowCountChecker();

    private PrepAndExpectedTestCase prepAndExpectedTestCase;
    private IDatabaseConnection resolvedConnection;
    private boolean connectionResolved;
    private boolean rowCountBaselineAttempted;
    private boolean prepAndExpectedTestCaseConfigured;
    private boolean reachedOnSetup;
    private boolean incomingTesterStateCaptured;
    private IDataSet incomingDataSet;
    private DatabaseOperation incomingSetUpOperation;
    private DatabaseOperation incomingTearDownOperation;

    /**
     * Creates an executor for an annotation-driven test - equivalent to
     * {@link #AnnotatedTestExecutor(AnnotatedTestConfiguration, IDatabaseTester, PrepAndExpectedTestCase, boolean)}
     * with {@code annotationDriven} {@code true}.
     *
     * @param configuration The resolved configuration to execute.
     * @param tester The tester to drive the setup/teardown path with, or to construct a
     *            {@link PrepAndExpectedTestCase} around when {@code prepAndExpectedTestCase}
     *            is {@code null} and the prep/expected path is configured.
     * @param prepAndExpectedTestCase An already-injected test case to drive instead of
     *            constructing one, or {@code null} to have this executor construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase)
    {
        this(configuration, tester, prepAndExpectedTestCase, true);
    }

    /**
     * Creates an executor for one test.
     *
     * <p>Mutates {@code tester} only when {@code annotationDriven} is true:
     * {@link #installOperationListener()} runs here, replacing {@code tester}'s
     * {@link IOperationListener} with an {@code ExecutorOperationListener} wrapping the previous
     * one. A binding constructs one executor per test method, so a {@code tester} shared across
     * methods (e.g. a {@code static @DbUnitTester} field) is re-wrapped each time -
     * {@link #unwrapExistingDelegate()} unwraps the prior wrapper first, so the layers do not
     * stack - and the last test's wrapper stays installed on the tester after the class
     * finishes, holding a reference to that last executor until the tester is itself discarded
     * or given a new listener. When {@code annotationDriven} is false - the classic path - the
     * tester's listener is left untouched; see the class Javadoc.
     *
     * @param configuration The resolved configuration to execute.
     * @param tester The tester to drive the setup/teardown path with, or to construct a
     *            {@link PrepAndExpectedTestCase} around when {@code prepAndExpectedTestCase}
     *            is {@code null} and the prep/expected path is configured.
     * @param prepAndExpectedTestCase An already-injected test case to drive instead of
     *            constructing one, or {@code null} to have this executor construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family - any {@code @DbUnit*} annotation, or a
     *            {@code @DbUnitTester}/{@code @DbUnitTestCase} field. False for a bare
     *            {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
     *            {@link IDatabaseTester} field, whose tester listener and connection lifecycle
     *            are then left exactly as the 3.5.0 lifecycle. Always true for the prep/expected
     *            path, which needs {@code @DbUnitExpected}.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase,
            final boolean annotationDriven)
    {
        this.configuration = configuration;
        this.tester = tester;
        this.prepAndExpectedTestCase = prepAndExpectedTestCase;
        this.prepAndExpectedTestCaseInjected = prepAndExpectedTestCase != null;
        this.annotationDriven = annotationDriven;
        if (annotationDriven)
        {
            installOperationListener();
        }
    }

    /**
     * Returns the tester this executor drives, so a binding can inject it as a parameter
     * without resolving a second, independent instance.
     *
     * @return The tester passed to the constructor.
     */
    public IDatabaseTester getTester()
    {
        return tester;
    }

    /**
     * Returns the {@link PrepAndExpectedTestCase} this executor drives, or {@code null} when
     * {@link AnnotatedTestConfiguration#isExpected()} is false, or it is true but nothing has
     * constructed one yet - a binding resolving a parameter before {@link #beforeTest()} runs
     * (e.g. a {@code @BeforeEach} parameter) sees {@code null} unless one was already injected
     * through the constructor, or an earlier parameter resolution this same test already
     * triggered {@link #ensurePrepAndExpectedTestCase()} (e.g. a {@code Connection} parameter
     * resolved via {@link #getConnection()}).
     *
     * @return The test case this executor drives, or {@code null} if none exists yet.
     */
    public PrepAndExpectedTestCase getPrepAndExpectedTestCase()
    {
        return prepAndExpectedTestCase;
    }

    /**
     * Returns the connection this test's steps use, resolved at most once per test and reused
     * by every caller within this executor instead of each asking independently - the row count
     * check's own baseline/verify/override calls on the setup/teardown path, and a binding injecting an
     * {@code IDatabaseConnection}/{@code Connection} parameter on either path. On the
     * prep/expected path ({@link AnnotatedTestConfiguration#isExpected()} true), delegates to
     * {@link PrepAndExpectedTestCase#getReusableConnection()} - constructing
     * {@link #prepAndExpectedTestCase} first via {@link #ensurePrepAndExpectedTestCase()} if it
     * does not exist yet - instead of asking {@link #tester} directly, so a parameter injection
     * shares that test case's own connection; on the setup/teardown path, asks {@link #tester} directly
     * as before. For a tester with no connection caching of its own (e.g. a plain
     * {@code JdbcDatabaseTester}), asking independently on either path would otherwise open one
     * new physical connection per call. Closed in {@link #afterTest(boolean)} - see the class
     * Javadoc.
     *
     * @return The connection to reuse, or {@code null} if none is available (e.g. a test
     *         double).
     * @throws Exception If resolving the connection fails.
     */
    public IDatabaseConnection getConnection() throws Exception
    {
        if (!connectionResolved)
        {
            resolvedConnection = configuration.isExpected()
                    ? ensurePrepAndExpectedTestCase().getReusableConnection()
                    : tester.getConnection();
            connectionResolved = true;
            if (resolvedConnection != null)
            {
                applyProperties(resolvedConnection, configuration.getDatabaseConfigProperties());
            }
        }
        return resolvedConnection;
    }

    /**
     * Returns the connection {@link #getConnection()} has already resolved this test, without
     * triggering resolution - {@code null} both before {@link #getConnection()} first runs and
     * when the tester has no connection to offer. Used only by
     * {@link ExecutorOperationListener} to recognize, at whatever later moment the tester's own
     * {@code onSetup()}/{@code onTearDown()} happens to ask, whether the connection they are
     * about to close is the one this executor is still holding onto.
     */
    private IDatabaseConnection peekResolvedConnection()
    {
        return connectionResolved ? resolvedConnection : null;
    }

    /**
     * Wraps the tester's operation listener in an {@link ExecutorOperationListener}, so that
     * regardless of whether {@code @DbUnitProperty} is configured, this executor's own memoized
     * connection (see the class Javadoc) is protected from a premature close by the tester's own
     * setup/teardown machinery, and so the row count check baseline can be captured from
     * whatever connection {@code onSetup()} retrieves on its own - see
     * {@link #captureRowCountBaselineOnFirstConnectionRetrieved(IDatabaseConnection)}. Called
     * from the constructor only for an {@code annotationDriven} test; the classic path leaves
     * the tester's listener untouched (see the class Javadoc).
     */
    private void installOperationListener()
    {
        final IOperationListener delegate = unwrapExistingDelegate();
        tester.setOperationListener(new ExecutorOperationListener(
                configuration.getDatabaseConfigProperties(), this::peekResolvedConnection,
                delegate, this::captureRowCountBaselineOnFirstConnectionRetrieved));
    }

    /**
     * Returns the listener a newly-installed {@link ExecutorOperationListener} should delegate
     * to: when the tester already carries one - e.g. a shared static tester field that an
     * earlier test in the same class already wrapped - that wrapper's own delegate, so
     * constructing one executor per test does not nest one wrapper layer per test; otherwise
     * the tester's existing listener as-is, or a fresh {@link DefaultOperationListener} when it
     * had none.
     *
     * <p>"Had none" is read from {@link IDatabaseTester#getOperationListener()}, whose default
     * is {@code null}. {@link AbstractDatabaseTester} overrides it, and a {@code null} there
     * genuinely means "none yet" (it lazily creates a {@link DefaultOperationListener} on first
     * use), so the fresh {@link DefaultOperationListener} substituted here matches. A custom
     * {@link IDatabaseTester} that does <em>not</em> extend {@link AbstractDatabaseTester},
     * manages an {@link IOperationListener} internally, and does not override
     * {@code getOperationListener()} to expose it will have that internal listener bypassed -
     * such a tester must override {@code getOperationListener()} for the annotation runtime to
     * preserve it.
     */
    private IOperationListener unwrapExistingDelegate()
    {
        final IOperationListener existingListener = tester.getOperationListener();
        if (existingListener instanceof ExecutorOperationListener)
        {
            return ((ExecutorOperationListener) existingListener).delegate;
        }
        return existingListener != null ? existingListener : new DefaultOperationListener();
    }

    /**
     * Applies {@code @DbUnitProperty} values to {@code connection}'s {@link DatabaseConfig}; a
     * no-op when {@code properties} is empty. Called from both {@link #getConnection()}, so the
     * row count check's baseline/verify/override calls always see property state already
     * applied before they read it, and from
     * {@link ExecutorOperationListener#connectionRetrieved(IDatabaseConnection)}, since
     * a tester's {@code onSetup()}/{@code onTearDown()} may resolve their own connection
     * independently of {@link #getConnection()}'s memoized one.
     */
    private static void applyProperties(final IDatabaseConnection connection,
            final Properties properties)
    {
        if (properties.isEmpty())
        {
            return;
        }
        try
        {
            connection.getConfig().setPropertiesByString(properties);
        } catch (final DatabaseUnitException e)
        {
            throw new IllegalStateException("Failed to apply @DbUnitProperty values.", e);
        }
    }

    /**
     * Runs every before-test step: for the setup/teardown path, applies the prep dataset (if any) and
     * the setup operation, then calls {@code onSetup()}; for the prep/expected path, resolves
     * or constructs the {@link PrepAndExpectedTestCase} and calls {@code configureTest()} then
     * {@code preTest()}.
     *
     * @throws Exception If any step fails.
     */
    public void beforeTest() throws Exception
    {
        if (configuration.isExpected())
        {
            beforeExpectedTest();
        } else
        {
            beforeSetupTeardownTest();
        }
    }

    private void beforeSetupTeardownTest() throws Exception
    {
        captureIncomingTesterState();
        final DatabaseOperation resolvedSetUpOperation;
        if (configuration.getPrepDataFiles().length > 0)
        {
            tester.setDataSet(
                    loadCombined(configuration.getDataFileLoader(), configuration.getPrepDataFiles()));
            resolvedSetUpOperation = configuration.getSetUpOperation();
            tester.setSetUpOperation(resolvedSetUpOperation);
        } else if (configuration.isSetupDeclared())
        {
            // no dataset to apply it to, but the operation itself - most usefully NONE -
            // still applies to whatever dataset the tester already has; see
            // AnnotatedTestConfiguration#isSetupDeclared().
            resolvedSetUpOperation = configuration.getSetUpOperation();
            warnIfNonNoneSetupOperationHasNoDataset(resolvedSetUpOperation);
            tester.setSetUpOperation(resolvedSetUpOperation);
            log.debug("No @DbUnitPrep data files declared; applied @DbUnitSetup's operation"
                    + " {} without changing the tester's dataset.", resolvedSetUpOperation);
        } else
        {
            resolvedSetUpOperation = tester.getSetUpOperation();
            log.debug("No @DbUnitPrep data files declared; running with the tester's current"
                    + " dataset and setup operation, restored afterward.");
        }
        if (!canPiggybackRowCountBaseline(resolvedSetUpOperation))
        {
            captureRowCountBaseline();
        }
        reachedOnSetup = true;
        tester.onSetup();
        if (!rowCountBaselineAttempted)
        {
            // The predicted piggyback never actually happened - e.g. a tester whose onSetup()
            // does not end up calling executeOperation() the way AbstractDatabaseTester's does.
            // Always leave a baseline decided one way or another before the test method runs,
            // rather than silently skip the check because a prediction about a tester's
            // internals turned out wrong. canPiggybackRowCountBaseline() only trusts a piggyback
            // when onSetup() is AbstractDatabaseTester's own, so reaching here means that
            // contract was not kept - the baseline captured now may already include the prep
            // dataset's rows.
            captureRowCountBaseline();
        }
    }

    /**
     * Snapshots the dataset and setup/teardown operations {@link #tester} carries coming into
     * this test - after any {@code @BeforeEach} method has run, before this executor applies a
     * thing - so {@link #restoreIncomingTesterState()} can put them back afterward. Keeps a
     * per-method {@code @DbUnitPrep}/{@code @DbUnitSetup}/{@code @DbUnitTearDown} from leaking
     * onto a later method that shares the tester: a {@code static} {@code @DbUnitTester} field,
     * or any tester field under {@code @TestInstance(Lifecycle.PER_CLASS)}. A class-level or
     * inherited annotation is unaffected - it resolves for every method in scope, so every
     * method's config declares it and this executor re-applies it each time. A
     * {@code @BeforeEach}-set value is the captured snapshot and is restored unchanged, so
     * configuring the tester in {@code @BeforeEach} still works.
     */
    private void captureIncomingTesterState()
    {
        incomingDataSet = tester.getDataSet();
        incomingSetUpOperation = tester.getSetUpOperation();
        incomingTearDownOperation = tester.getTearDownOperation();
        incomingTesterStateCaptured = true;
    }

    /**
     * Restores the dataset and setup/teardown operations {@link #captureIncomingTesterState()}
     * snapshotted, so a shared tester carries no per-method state forward. A no-op when this
     * test never reached {@link #beforeSetupTeardownTest()} (the prep/expected path, or an
     * {@link #afterTest(boolean)} call in a unit test with no preceding {@link #beforeTest()}).
     */
    private void restoreIncomingTesterState()
    {
        if (!incomingTesterStateCaptured)
        {
            return;
        }
        tester.setDataSet(incomingDataSet);
        tester.setSetUpOperation(incomingSetUpOperation);
        tester.setTearDownOperation(incomingTearDownOperation);
    }

    /**
     * Warns when {@code @DbUnitSetup} declares a non-{@link DatabaseOperation#NONE} operation
     * with no {@code @DbUnitPrep} data files and no dataset already on the tester - every such
     * operation ({@code CLEAN_INSERT}, {@code INSERT}, {@code REFRESH}, {@code UPDATE},
     * {@code DELETE}, {@code DELETE_ALL}, {@code TRUNCATE_TABLE}) needs a dataset to act on, so
     * {@code AbstractDatabaseTester}'s {@code onSetup()} would run the operation against a
     * {@code null} dataset. A bare {@code @DbUnitSetup} resolves to {@code CLEAN_INSERT}, not
     * {@code NONE}, so this is the likely shape of that mistake.
     *
     * <p>A warning rather than an {@link IllegalStateException}: a custom {@link IDatabaseTester}
     * whose {@code onSetup()} sources its dataset some other way, or a test double, legitimately
     * has {@code getDataSet()} return {@code null} here and handles the operation itself.
     *
     * @param resolvedSetUpOperation The setup operation {@code @DbUnitSetup} resolved to.
     */
    private void warnIfNonNoneSetupOperationHasNoDataset(
            final DatabaseOperation resolvedSetUpOperation)
    {
        if (resolvedSetUpOperation != DatabaseOperation.NONE && tester.getDataSet() == null)
        {
            log.warn("@DbUnitSetup declares a non-NONE operation, but there is nothing for it"
                    + " to act on: no @DbUnitPrep data files were declared, and the tester holds"
                    + " no dataset. A built-in tester will fail in onSetup(). Add @DbUnitPrep,"
                    + " change the operation to DbUnitOperation.NONE, or set a dataset on the"
                    + " tester in a @BeforeEach method.");
        }
    }

    /**
     * Warns when {@code @DbUnitTearDown} declares a non-{@link DatabaseOperation#NONE} operation
     * with no {@code @DbUnitPrep} data files and no dataset already on the tester - every such
     * operation ({@code CLEAN_INSERT}, {@code INSERT}, {@code REFRESH}, {@code UPDATE},
     * {@code DELETE}, {@code DELETE_ALL}, {@code TRUNCATE_TABLE}) needs a dataset to act on, so
     * {@code AbstractDatabaseTester}'s {@code onTearDown()} would run the operation against a
     * {@code null} dataset. Mirrors {@link #warnIfNonNoneSetupOperationHasNoDataset(DatabaseOperation)}
     * for the teardown side; unlike {@code @DbUnitSetup}, a bare {@code @DbUnitTearDown}
     * resolves to {@code NONE}, so reaching this warning means the {@code operation} member was
     * set explicitly.
     *
     * <p>A warning rather than an {@link IllegalStateException}: a custom {@link IDatabaseTester}
     * whose {@code onTearDown()} sources its dataset some other way, or a test double,
     * legitimately has {@code getDataSet()} return {@code null} here and handles the operation
     * itself.
     *
     * @param resolvedTearDownOperation The teardown operation {@code @DbUnitTearDown} resolved
     *            to.
     */
    private void warnIfNonNoneTearDownOperationHasNoDataset(
            final DatabaseOperation resolvedTearDownOperation)
    {
        if (resolvedTearDownOperation != DatabaseOperation.NONE && tester.getDataSet() == null)
        {
            log.warn("@DbUnitTearDown declares a non-NONE operation, but there is nothing for"
                    + " it to act on: no @DbUnitPrep data files were declared, and the tester"
                    + " holds no dataset. A built-in tester will fail in onTearDown(). Add"
                    + " @DbUnitPrep, change the operation to DbUnitOperation.NONE, or set a"
                    + " dataset on the tester in a @BeforeEach method.");
        }
    }

    /**
     * Returns whether {@code onSetup()} can be trusted to retrieve a connection and notify
     * {@link ExecutorOperationListener#connectionRetrieved(IDatabaseConnection)} on its own, so
     * the row count check baseline can be captured from that connection - see
     * {@link #captureRowCountBaselineOnFirstConnectionRetrieved(IDatabaseConnection)} - instead
     * of this executor eagerly resolving a separate one just to find out whether the check is
     * even enabled, the way {@link #captureRowCountBaseline()} always did before this
     * optimization existed.
     *
     * <p>True only when this is provably safe: the test is {@code annotationDriven}, so
     * {@link #installOperationListener()} has actually installed the
     * {@link ExecutorOperationListener} there is to piggyback on; {@code tester} is dbUnit's own
     * {@link AbstractDatabaseTester} <em>and does not override {@code onSetup()}</em>, so
     * {@code executeOperation()} - known by reading its source, not by assuming it - runs and
     * calls {@code getConnection()} and notifies the listener <em>before</em> the operation
     * touches any data, whenever the operation is not {@link DatabaseOperation#NONE}; and no
     * connection is already memoized (e.g. by an earlier {@code @BeforeEach} parameter
     * injection), since piggybacking on a second, different connection than one already in hand
     * would orphan the first rather than reuse it. A custom {@link IDatabaseTester}
     * implementation, or an {@link AbstractDatabaseTester} subclass that overrides
     * {@code onSetup()} and might apply the setup operation some other way, is never assumed to
     * behave the same way - {@link #captureRowCountBaseline()} runs eagerly for it instead,
     * <em>before</em> {@code onSetup()}, so the baseline never picks up rows {@code onSetup()}
     * inserts. The after-the-fact check in {@link #beforeSetupTeardownTest()} is a last-resort
     * net for a piggyback this method did trust that still did not fire. The classic path always
     * captures eagerly too - there is no listener there.
     *
     * @param resolvedSetUpOperation The setup operation {@code onSetup()} is about to run.
     */
    private boolean canPiggybackRowCountBaseline(final DatabaseOperation resolvedSetUpOperation)
    {
        return annotationDriven && !connectionResolved
                && tester instanceof AbstractDatabaseTester && !testerOverridesOnSetup()
                && resolvedSetUpOperation != DatabaseOperation.NONE;
    }

    /**
     * Returns whether {@link #tester}'s runtime type overrides
     * {@link AbstractDatabaseTester#onSetup()} rather than inheriting it - in which case
     * {@code onSetup()} is not guaranteed to route through {@code executeOperation()} and notify
     * the operation listener, so {@link #canPiggybackRowCountBaseline(DatabaseOperation)} cannot
     * trust it and the baseline is captured eagerly instead. Conservatively returns {@code true}
     * if the method cannot be resolved by reflection.
     */
    private boolean testerOverridesOnSetup()
    {
        try
        {
            return tester.getClass().getMethod("onSetup").getDeclaringClass()
                    != AbstractDatabaseTester.class;
        } catch (final NoSuchMethodException e)
        {
            return true;
        }
    }

    private void beforeExpectedTest() throws Exception
    {
        ensurePrepAndExpectedTestCase();
        applyDataFileLoader();
        applyFailureHandler();
        applyDatabaseConfigProperties();
        applyCloseConnectionAfterTest();
        applySetUpOperation();
        applyTearDownOperation();
        applyRowCountCheckOverride();
        prepAndExpectedTestCase.configureTest(configuration.getVerifyTableDefinitions(),
                configuration.getPrepDataFiles(), configuration.getExpectedDataFiles());
        prepAndExpectedTestCaseConfigured = true;
        prepAndExpectedTestCase.preTest();
    }

    /**
     * Returns {@link #prepAndExpectedTestCase}, constructing it via
     * {@link #newPrepAndExpectedTestCase()} first when it does not already exist (no
     * {@code @DbUnitTestCase} field supplied one, and nothing has constructed one yet). Safe to
     * call more than once - construction only happens once - and safe to call before
     * {@link #beforeTest()} does, e.g. from {@link #getConnection()} resolving an early
     * {@code @BeforeEach} parameter, since construction has no side effect beyond reflectively
     * instantiating the instance.
     *
     * @return The test case this executor drives.
     * @throws Exception If constructing it fails.
     */
    private PrepAndExpectedTestCase ensurePrepAndExpectedTestCase() throws Exception
    {
        if (prepAndExpectedTestCase == null)
        {
            prepAndExpectedTestCase = newPrepAndExpectedTestCase();
        }
        return prepAndExpectedTestCase;
    }

    /**
     * Applies {@code @DbUnitConfig.dataFileLoader()} to {@link #prepAndExpectedTestCase}, so a
     * {@link org.dbunit.annotation.DbUnitTestCase}-injected instance's own dataset loading
     * matches the configured value the same way a freshly-constructed instance already
     * receives it through its constructor - see {@link #newPrepAndExpectedTestCase()}.
     * Re-applying it to a freshly-constructed instance is harmless, since it is the same value
     * that instance's constructor already received.
     *
     * @throws IllegalStateException If {@code dataFileLoader()} names a non-default loader and
     *             {@link #prepAndExpectedTestCase}'s type does not override
     *             {@link PrepAndExpectedTestCase#setDataFileLoader(org.dbunit.util.fileloader.DataFileLoader)}
     *             - {@link DefaultPrepAndExpectedTestCase} does - since it would otherwise
     *             silently keep loading with whatever loader it was already constructed with.
     */
    private void applyDataFileLoader()
    {
        final DataFileLoader dataFileLoader = configuration.getDataFileLoader();
        if (dataFileLoader.getClass() != FileExtensionDataFileLoader.class
                && !overridesDefaultMethod("setDataFileLoader", DataFileLoader.class))
        {
            throw new IllegalStateException("DbUnitConfig.dataFileLoader() names "
                    + dataFileLoader.getClass().getName() + ", but the injected DbUnitTestCase "
                    + prepAndExpectedTestCase.getClass().getName() + " does not override"
                    + " setDataFileLoader(); it would silently keep loading with whatever"
                    + " loader it was already constructed with. Override setDataFileLoader(),"
                    + " or drop dataFileLoader() from @DbUnitConfig for this test.");
        }
        prepAndExpectedTestCase.setDataFileLoader(dataFileLoader);
    }

    /**
     * Applies {@code @DbUnitConfig.failureHandler()} to {@link #prepAndExpectedTestCase} -
     * {@code null} when not set, resetting {@link #prepAndExpectedTestCase} to dbUnit's own
     * default handler rather than leaving an earlier test's handler in place, for a test case
     * reused across several tests (e.g. one held by a {@code @DbUnitTestCase} static field).
     *
     * @throws IllegalStateException If {@code failureHandler()} is set and
     *             {@link #prepAndExpectedTestCase}'s type does not override
     *             {@link PrepAndExpectedTestCase#setFailureHandler(org.dbunit.assertion.FailureHandler)}
     *             - {@link DefaultPrepAndExpectedTestCase} does - since the configured handler
     *             would otherwise silently never take effect, with no other route to it.
     */
    private void applyFailureHandler()
    {
        final FailureHandler failureHandler = configuration.getFailureHandler();
        if (failureHandler != null
                && !overridesDefaultMethod("setFailureHandler", FailureHandler.class))
        {
            throw new IllegalStateException("DbUnitConfig.failureHandler() names "
                    + failureHandler.getClass().getName() + ", but the injected DbUnitTestCase "
                    + prepAndExpectedTestCase.getClass().getName() + " does not override"
                    + " setFailureHandler(); it would silently keep dbUnit's own default"
                    + " handler instead. Override setFailureHandler(), or drop"
                    + " failureHandler() from @DbUnitConfig for this test.");
        }
        prepAndExpectedTestCase.setFailureHandler(failureHandler);
    }

    /**
     * Returns whether {@link #prepAndExpectedTestCase}'s runtime type overrides the named
     * {@link PrepAndExpectedTestCase} default method, rather than inheriting the interface's
     * own no-op body - the same distinction {@code getDatabaseTester()}/
     * {@code setDatabaseTester()} round-tripping already makes for tester resolution (see
     * {@code DbUnitExtension#resolve}), generalized here to every other
     * {@code @DbUnitConfig}-driven setter this executor calls on an already-injected instance.
     * Used to decide whether a configured, non-default attribute that setter cannot actually
     * apply should fail fast or merely log, rather than silently doing nothing. See
     * {@link DefaultMethodOverrideCheck#overridesDefaultMethod} for this check's own known
     * limitation.
     *
     * @param methodName The setter's name.
     * @param parameterTypes The setter's parameter types.
     * @return True when {@link #prepAndExpectedTestCase}'s type overrides the method.
     */
    private boolean overridesDefaultMethod(final String methodName,
            final Class<?>... parameterTypes)
    {
        return DefaultMethodOverrideCheck.overridesDefaultMethod(
                prepAndExpectedTestCase.getClass(), PrepAndExpectedTestCase.class, methodName,
                parameterTypes);
    }

    /**
     * Applies {@code @DbUnitConfig.closeConnectionAfterTest()} to
     * {@link #prepAndExpectedTestCase}, so a {@link org.dbunit.annotation.DbUnitTestCase}-injected
     * instance's own connection-closing behaviour matches the configured value the same way a
     * freshly-constructed instance already receives it through its constructor - see
     * {@link #newPrepAndExpectedTestCase()}. Re-applying it to a freshly-constructed instance
     * is harmless, since it is the same value that instance's constructor already received.
     *
     * <p>Unlike the other {@code @DbUnitConfig}-driven setters this executor calls on an
     * injected instance, a non-overriding implementation only logs a warning here rather than
     * failing fast: this executor's own connection (for the row count check or parameter
     * injection) still honors {@code closeConnectionAfterTest} regardless - only the injected
     * instance's own connection handling might not - so this attribute is never a complete
     * no-op the way the others are.
     */
    private void applyCloseConnectionAfterTest()
    {
        final boolean closeConnectionAfterTest = configuration.isCloseConnectionAfterTest();
        if (!closeConnectionAfterTest
                && !overridesDefaultMethod("setCloseConnectionAfterTest", boolean.class))
        {
            log.warn("DbUnitConfig.closeConnectionAfterTest() is false, but the injected"
                    + " DbUnitTestCase {} does not override setCloseConnectionAfterTest(); it"
                    + " may still close a connection this test's tester shares with other"
                    + " tests. This executor's own connection - for the row count check or"
                    + " parameter injection - still honors the false value regardless.",
                    prepAndExpectedTestCase.getClass().getName());
        }
        prepAndExpectedTestCase.setCloseConnectionAfterTest(closeConnectionAfterTest);
    }

    /**
     * Applies {@code @DbUnitProperty}/{@code propertiesProvider()} values to
     * {@link #prepAndExpectedTestCase} - empty when none are configured, resetting
     * {@link #prepAndExpectedTestCase} to apply no properties rather than leaving an earlier
     * test's values in place, for a test case reused across several tests (e.g. one held by a
     * {@code @DbUnitTestCase} static field). The setup/teardown path applies the same values
     * through {@link #installOperationListener()} instead, which this path's {@code setupData()}/
     * {@code verifyData()}/{@code cleanupData()} never triggers.
     *
     * @throws IllegalStateException If any {@code @DbUnitProperty}/{@code propertiesProvider()}
     *             value is configured and {@link #prepAndExpectedTestCase}'s type does not
     *             override
     *             {@link PrepAndExpectedTestCase#setDatabaseConfigProperties(java.util.Properties)}
     *             - {@link DefaultPrepAndExpectedTestCase} does - since this path has no other
     *             route to the connection at all, and the values would otherwise silently
     *             never reach it.
     */
    private void applyDatabaseConfigProperties()
    {
        final Properties properties = configuration.getDatabaseConfigProperties();
        if (!properties.isEmpty()
                && !overridesDefaultMethod("setDatabaseConfigProperties", Properties.class))
        {
            throw new IllegalStateException("DbUnitConfig declares " + properties.size()
                    + " @DbUnitProperty value(s), but the injected DbUnitTestCase "
                    + prepAndExpectedTestCase.getClass().getName() + " does not override"
                    + " setDatabaseConfigProperties(); this path never reaches the connection"
                    + " any other way - the setup/teardown path's IOperationListener-based application"
                    + " never triggers here. Override setDatabaseConfigProperties(), or drop"
                    + " properties()/propertiesProvider() from @DbUnitConfig for this test.");
        }
        if (!properties.isEmpty() && overridesSetUpDatabaseConfig())
        {
            log.warn("DbUnitConfig declares {} @DbUnitProperty value(s) and the injected"
                    + " DbUnitTestCase {} overrides setUpDatabaseConfig(DatabaseConfig); those"
                    + " values reach the connection only if that override calls"
                    + " super.setUpDatabaseConfig(config). Add the super call, or drop"
                    + " properties()/propertiesProvider() and configure the DatabaseConfig"
                    + " entirely in the override.", properties.size(),
                    prepAndExpectedTestCase.getClass().getName());
        }
        prepAndExpectedTestCase.setDatabaseConfigProperties(properties);
    }

    /**
     * Returns whether {@link #prepAndExpectedTestCase} is a {@link DefaultPrepAndExpectedTestCase}
     * subclass that redeclares {@code setUpDatabaseConfig(DatabaseConfig)} somewhere below
     * {@link DefaultPrepAndExpectedTestCase} itself - the pre-3.6.0 way to configure a
     * {@link DatabaseConfig}, and a silent trap for {@code @DbUnitProperty}: an override that
     * does not call {@code super.setUpDatabaseConfig(config)} drops the values
     * {@link #applyDatabaseConfigProperties()} just handed to
     * {@link DefaultPrepAndExpectedTestCase#setDatabaseConfigProperties(Properties)}, since
     * that same hook is where they would otherwise be applied. Reflection cannot tell whether
     * the override calls {@code super}, only that one exists - so the caller warns rather than
     * fails. Returns false for a non-{@link DefaultPrepAndExpectedTestCase} implementation,
     * whose own {@code setDatabaseConfigProperties()} override (already required by
     * {@link #applyDatabaseConfigProperties()}) is its own business.
     */
    private boolean overridesSetUpDatabaseConfig()
    {
        Class<?> type = prepAndExpectedTestCase.getClass();
        while (type != null && type != DefaultPrepAndExpectedTestCase.class
                && type != Object.class)
        {
            try
            {
                type.getDeclaredMethod("setUpDatabaseConfig", DatabaseConfig.class);
                return true;
            } catch (final NoSuchMethodException e)
            {
                type = type.getSuperclass();
            }
        }
        return false;
    }

    /**
     * Applies {@code @DbUnitSetup}'s operation to {@link #tester}. Called only from
     * {@link #beforeExpectedTest()}, unconditionally:
     * {@link AnnotatedTestConfiguration#getSetUpOperation()} already defaults to
     * {@link org.dbunit.operation.DatabaseOperation#CLEAN_INSERT} - the same default the
     * setup/teardown path applies whenever it has a dataset to set up - and {@code setupData()}
     * always runs against a real, if possibly empty, prep dataset on the prep/expected path, so
     * there is no null-dataset case here to avoid touching. The setup/teardown path resolves
     * and applies the setup operation inline in {@link #beforeSetupTeardownTest()} instead, and
     * only when {@code @DbUnitPrep} or {@code @DbUnitSetup} is declared.
     */
    private void applySetUpOperation()
    {
        tester.setSetUpOperation(configuration.getSetUpOperation());
    }

    /**
     * Applies {@code @DbUnitTearDown}'s operation to {@link #tester}. Called unconditionally
     * from {@link #beforeExpectedTest()} on the prep/expected path - where {@code cleanupData()}
     * always runs and reads its teardown operation from the tester lazily, so the default of
     * {@link org.dbunit.operation.DatabaseOperation#NONE} still has to be set explicitly to
     * override whatever a reused test case's tester already carries - but only when
     * {@link AnnotatedTestConfiguration#isTearDownDeclared()} from
     * {@link #afterSetupTeardownTest()} on the setup/teardown path. That mirrors how
     * {@link #beforeSetupTeardownTest()} writes the setup operation only when
     * {@code @DbUnitPrep} or {@code @DbUnitSetup} is declared: with no {@code @DbUnitTearDown},
     * the setup/teardown path runs {@code onTearDown()} with whatever teardown operation the
     * tester already carries - e.g. one a {@code @BeforeEach} method set. Whatever this or
     * {@link #beforeSetupTeardownTest()} does apply on the setup/teardown path is undone by
     * {@link #restoreIncomingTesterState()} once the test finishes, so a shared tester carries
     * no per-method operation or dataset forward to the next method.
     */
    private void applyTearDownOperation()
    {
        tester.setTearDownOperation(configuration.getTearDownOperation());
    }

    /**
     * Overrides the prep/expected path's own {@code RowCountCheck} resolution with the
     * enabled flag and excluded table patterns {@code @DbUnitRowCountCheck} declares, when
     * declared; clears any such override when not declared, so
     * {@code DefaultPrepAndExpectedTestCase}'s own connection-{@link DatabaseConfig}-based
     * resolution takes over instead of an earlier test's override silently carrying over onto
     * this one, for a test case reused across several tests (e.g. one held by a
     * {@code @DbUnitTestCase} static field). Clearing is unconditional and never checked
     * against {@link #overridesDefaultMethod}: nothing was explicitly requested when
     * {@code @DbUnitRowCountCheck} is absent, so there is nothing to fail loud about - it is
     * purely defensive hygiene for a reused test case. Needs no connection of this executor's
     * own either way - see
     * {@link DefaultPrepAndExpectedTestCase#setRowCountCheckOverride(boolean, String[])}.
     *
     * @throws IllegalStateException If {@code @DbUnitRowCountCheck} is declared and
     *             {@link #prepAndExpectedTestCase}'s type does not override
     *             {@link PrepAndExpectedTestCase#setRowCountCheckOverride(boolean, String[])} -
     *             {@link DefaultPrepAndExpectedTestCase} does - since the check would
     *             otherwise silently never run for this test at all.
     */
    private void applyRowCountCheckOverride()
    {
        if (configuration.isRowCountCheckDeclared())
        {
            if (!overridesDefaultMethod("setRowCountCheckOverride", boolean.class,
                    String[].class))
            {
                throw new IllegalStateException("@DbUnitRowCountCheck is declared, but the"
                        + " injected DbUnitTestCase "
                        + prepAndExpectedTestCase.getClass().getName() + " does not override"
                        + " setRowCountCheckOverride(); the check would silently never run for"
                        + " this test. Override setRowCountCheckOverride()/"
                        + "clearRowCountCheckOverride(), or drop @DbUnitRowCountCheck for this"
                        + " test.");
            }
            prepAndExpectedTestCase.setRowCountCheckOverride(
                    configuration.isRowCountCheckEnabled(),
                    configuration.getRowCountCheckExclude());
        } else
        {
            prepAndExpectedTestCase.clearRowCountCheckOverride();
        }
    }

    private PrepAndExpectedTestCase newPrepAndExpectedTestCase() throws Exception
    {
        final Class<? extends PrepAndExpectedTestCase> testCaseClass =
                configuration.getPrepAndExpectedTestCaseClass();
        final Constructor<? extends PrepAndExpectedTestCase> constructor;
        try
        {
            constructor = testCaseClass.getDeclaredConstructor(DataFileLoader.class,
                    IDatabaseTester.class, boolean.class);
        } catch (final NoSuchMethodException e)
        {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " has no (DataFileLoader, IDatabaseTester,"
                    + " boolean) constructor.", e);
        }
        constructor.setAccessible(true);
        try
        {
            return constructor.newInstance(configuration.getDataFileLoader(), tester,
                    configuration.isCloseConnectionAfterTest());
        } catch (final InvocationTargetException e)
        {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " threw from its (DataFileLoader,"
                    + " IDatabaseTester, boolean) constructor.", e.getCause());
        }
    }

    /**
     * Runs every after-test step: for the setup/teardown path, applies the teardown operation (when
     * {@code @DbUnitTearDown} was declared) then calls {@code onTearDown()}, then verifies the
     * row count check baseline unless {@code testFailed}; for the prep/expected path, calls
     * {@code postTest(!testFailed)}. Either way, closes the connection {@link #getConnection()}
     * memoized - if one was ever resolved - so a difference reported by the row count check
     * still leaves it closed. When both the step above and closing the connection fail, the
     * step's failure is the one thrown, with the close failure attached to it via
     * {@link Throwable#addSuppressed(Throwable)} rather than replacing it - a plain {@code
     * finally} block would otherwise let the close failure silently discard the more useful
     * diagnostic (e.g. which table the row count check found unexpectedly changed). The step's
     * failure is caught as {@link Throwable}, not just {@link Exception}, and rethrown with its
     * original static type preserved: a comparison mismatch on the prep/expected path fails via
     * {@link org.dbunit.assertion.DbComparisonFailure}, an {@link Error} subclass, not an
     * {@code Exception} - catching only {@code Exception} would skip closing the connection on
     * every ordinary verification failure, the single most common way this method's step throws
     * at all.
     *
     * @param testFailed Whether the test method itself already threw; when {@code true},
     *            verification is skipped so a difference does not mask the real failure.
     * @throws Exception If any step fails with a checked exception.
     * @throws Error If any step fails with an {@code Error}, e.g. a comparison mismatch.
     */
    public void afterTest(final boolean testFailed) throws Exception
    {
        try
        {
            if (configuration.isExpected())
            {
                afterExpectedTest(testFailed);
            } else
            {
                afterSetupTeardownTest(testFailed);
            }
        } catch (final Throwable primaryFailure)
        {
            try
            {
                closeResolvedConnectionIfOwned();
            } catch (final Exception closeFailure)
            {
                primaryFailure.addSuppressed(closeFailure);
            }
            throw primaryFailure;
        }
        closeResolvedConnectionIfOwned();
    }

    /**
     * Closes the connection {@link #getConnection()} memoized, when one was resolved,
     * {@code @DbUnitConfig#closeConnectionAfterTest()} (default {@code true}) allows it, the
     * tester's listener does not mark the connection as shared - see the class Javadoc - and it
     * is not already closed. A no-op when {@link #getConnection()} was never called this test.
     * Called from {@link #afterTest(boolean)} on every path - including the classic path, whose
     * eagerly-resolved baseline connection is held until here rather than closed before
     * {@code onSetup()}.
     *
     * <p>The already-closed check matters on the {@code @DbUnitExpected} path: when
     * {@code tester.getConnection()} returns the same connection object on every call (e.g. a
     * {@code DefaultDatabaseTester} built from one fixed connection), the connection this
     * memoizes for {@code @DbUnitRowCountCheck}'s override or a parameter injection is the same
     * one {@link DefaultPrepAndExpectedTestCase} independently resolves and closes for its own
     * setup/verify/cleanup steps - without this check, this method would close it a second time.
     *
     * <p>Skipped entirely for a connection that belongs to an injected {@code @DbUnitTestCase}
     * instance whose {@code configureTest()} never ran - see
     * {@link #isExpectedPathConnectionOwnedByUnconfiguredInjectedTestCase()}.
     *
     * @throws Exception If closing the connection fails.
     */
    private void closeResolvedConnectionIfOwned() throws Exception
    {
        if (isExpectedPathConnectionOwnedByUnconfiguredInjectedTestCase())
        {
            return;
        }
        if (connectionResolved && resolvedConnection != null
                && configuration.isCloseConnectionAfterTest()
                && !isNonClosingListener(tester.getOperationListener())
                && !resolvedConnection.getConnection().isClosed())
        {
            resolvedConnection.close();
        }
    }

    /**
     * Returns whether the resolved connection came from an injected {@code @DbUnitTestCase}
     * instance's {@link PrepAndExpectedTestCase#getReusableConnection()} for a
     * {@code @BeforeEach} parameter, and {@link #beforeExpectedTest()} then failed before
     * {@code configureTest()} - so {@code postTest()}/{@code cleanupData()} never ran to close
     * that connection the test case's own way. Closing it here would strand a reused instance
     * (e.g. a {@code static @DbUnitTestCase} field, or any instance field under
     * {@code @TestInstance(PER_CLASS)}) holding a closed connection for the next test method,
     * which would then fail cryptically. The instance keeps ownership; its own next lifecycle
     * run, or the caller discarding it, releases the connection.
     *
     * <p>Only for an <em>injected</em> instance: a test case this executor constructed itself is
     * single-use and discarded after this test, so the executor still closes its connection
     * rather than leak it.
     */
    private boolean isExpectedPathConnectionOwnedByUnconfiguredInjectedTestCase()
    {
        return configuration.isExpected() && prepAndExpectedTestCaseInjected
                && !prepAndExpectedTestCaseConfigured;
    }

    /**
     * Returns whether {@code listener} - or, when it is an {@link ExecutorOperationListener}
     * installed by {@link #installOperationListener()}, the delegate it wraps - is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER}: the established, pre-existing signal
     * that a tester's connection is managed elsewhere and must not be closed by whatever is
     * driving the tester's lifecycle.
     */
    private static boolean isNonClosingListener(final IOperationListener listener)
    {
        if (listener == IOperationListener.NO_OP_OPERATION_LISTENER)
        {
            return true;
        }
        if (listener instanceof ExecutorOperationListener)
        {
            return isNonClosingListener(((ExecutorOperationListener) listener).delegate);
        }
        return false;
    }

    private void afterSetupTeardownTest(final boolean testFailed) throws Exception
    {
        try
        {
            if (reachedOnSetup)
            {
                if (configuration.isTearDownDeclared())
                {
                    applyTearDownOperation();
                    warnIfNonNoneTearDownOperationHasNoDataset(
                            configuration.getTearDownOperation());
                } else
                {
                    log.debug("No @DbUnitTearDown declared; running onTearDown() with the"
                            + " tester's current teardown operation, restored afterward.");
                }
                tester.onTearDown();
                if (!testFailed)
                {
                    verifyRowCountUnchanged();
                }
            }
            // else: beforeTest() threw before onSetup() ran - e.g. a prep dataset failed to
            // load. Running onTearDown() here (especially a declared teardown operation) would
            // act on state onSetup() never established and throw a second exception that masks
            // the first. Mirrors afterExpectedTest()'s !prepAndExpectedTestCaseConfigured guard.
            // The restore below still runs, undoing anything beforeSetupTeardownTest() applied
            // before it threw.
        } catch (final Throwable primaryFailure)
        {
            try
            {
                restoreIncomingTesterState();
            } catch (final RuntimeException restoreFailure)
            {
                primaryFailure.addSuppressed(restoreFailure);
            }
            throw primaryFailure;
        }
        restoreIncomingTesterState();
    }

    private void afterExpectedTest(final boolean testFailed) throws Exception
    {
        if (!prepAndExpectedTestCaseConfigured)
        {
            // Either beforeTest() never ran, or it failed before configureTest() - e.g. an
            // @DbUnitConfig-driven setter's fail-fast check - so prepAndExpectedTestCase, when
            // non-null, is either uninitialized or (for one reused across tests, e.g. a
            // @DbUnitTestCase static field) still holding an earlier test's prep/expected state.
            // postTest() would run cleanup against that stale state instead of nothing.
            // afterTest()'s connection close is skipped for an injected instance here too - see
            // isExpectedPathConnectionOwnedByUnconfiguredInjectedTestCase().
            return;
        }
        prepAndExpectedTestCase.postTest(!testFailed);
    }

    /**
     * Eagerly resolves this executor's own connection (see {@link #getConnection()}) and
     * captures the row count check baseline from it, <em>before</em> {@code onSetup()} runs so
     * the baseline reflects the pre-test database. The route taken when
     * {@link #canPiggybackRowCountBaseline(DatabaseOperation)} said no connection is coming from
     * {@code onSetup()} on its own - a custom {@link IDatabaseTester}, an
     * {@link AbstractDatabaseTester} that overrides {@code onSetup()}, a
     * {@link DatabaseOperation#NONE} setup operation, or a connection already memoized from
     * earlier - and, from {@link #beforeSetupTeardownTest()} after {@code onSetup()}, the
     * last-resort net for a piggyback that was trusted but still never fired.
     */
    private void captureRowCountBaseline() throws Exception
    {
        captureRowCountBaseline(getConnection());
    }

    /**
     * Captures the row count check baseline from {@code connection}, marking a baseline as
     * having been attempted either way - including when {@code connection} is {@code null}
     * (e.g. a test double) or the check turns out disabled - so
     * {@link #canPiggybackRowCountBaseline(DatabaseOperation)}'s prediction is never retried
     * nor second-guessed later in the same test.
     */
    private void captureRowCountBaseline(final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        rowCountBaselineAttempted = true;
        if (connection == null)
        {
            return;
        }
        if (configuration.isRowCountCheckDeclared())
        {
            rowCountChecker.setEnabledOverride(configuration.isRowCountCheckEnabled(),
                    configuration.getRowCountCheckExclude());
        }
        rowCountChecker.capture(connection);
    }

    /**
     * Captures the row count check baseline from {@code connection} - the tester's own, just
     * retrieved by its {@code onSetup()} - the first time this is called for the current test;
     * a no-op on any later call this same test (e.g. {@code onTearDown()} later notifying this
     * same listener with a different connection). Memoizes {@code connection} the same way
     * {@link #getConnection()} would, so every other caller this test (the row count check's
     * later verify, a parameter injection, the close at the end of the test) reuses this exact
     * connection instead of the tester resolving yet another one.
     *
     * <p>Called only from {@link ExecutorOperationListener#connectionRetrieved(IDatabaseConnection)},
     * which declares no checked exceptions - so unlike {@link #captureRowCountBaseline()}, a
     * failure here is wrapped in a {@link DatabaseUnitRuntimeException} instead of a plain
     * {@code Exception}.
     *
     * <p>A no-op on the prep/expected path ({@link AnnotatedTestConfiguration#isExpected()}
     * true): the row count check there is captured and verified by the
     * {@link PrepAndExpectedTestCase} itself, never by this executor - see the class Javadoc -
     * so there is no baseline for this executor to capture. {@link DefaultPrepAndExpectedTestCase}
     * never triggers this listener at all on that path (it calls {@code IDatabaseTester#getConnection()}
     * directly, or drives its own internally-built tester with its own, separate listener,
     * neither of which notifies this one), so this guard changes nothing observable for it; it
     * exists for a {@link PrepAndExpectedTestCase} implementation that - unlike
     * {@link DefaultPrepAndExpectedTestCase} - calls {@code tester.onSetup()}/{@code onTearDown()}
     * directly, which otherwise would have reached here and memoized {@link #resolvedConnection}
     * from {@code connection} directly, bypassing {@link PrepAndExpectedTestCase#getReusableConnection()}
     * - silently reintroducing, for that implementation, the same connection-identity split
     * {@link #getConnection()}'s path split exists to prevent.
     */
    private void captureRowCountBaselineOnFirstConnectionRetrieved(
            final IDatabaseConnection connection)
    {
        if (configuration.isExpected() || rowCountBaselineAttempted)
        {
            return;
        }
        resolvedConnection = connection;
        connectionResolved = true;
        try
        {
            captureRowCountBaseline(connection);
        } catch (final DatabaseUnitException | SQLException e)
        {
            throw new DatabaseUnitRuntimeException(e);
        }
    }

    private void verifyRowCountUnchanged() throws Exception
    {
        if (!rowCountChecker.hasBaseline())
        {
            return;
        }
        final IDatabaseConnection connection = getConnection();
        if (connection == null)
        {
            return;
        }
        try
        {
            rowCountChecker.verify(connection);
        } catch (final UnexpectedRowCountException e)
        {
            throw augmentForUnclearedPrep(e);
        }
    }

    /**
     * Rethrows {@code e} with an extra hint when the mismatch is the classic
     * {@code @DbUnitRowCountCheck} + {@code @DbUnitPrep} + no-teardown shape: the baseline is
     * captured <em>before</em> {@code @DbUnitPrep} loads, so a prep dataset that is never torn
     * down reads as leaked rows and fails the check - a common first surprise. Otherwise
     * returns {@code e} unchanged. Keyed on the teardown operation the tester actually just
     * ran ({@link DatabaseOperation#NONE}), so an explicit
     * {@code @DbUnitTearDown(operation = NONE)} is covered too.
     */
    private UnexpectedRowCountException augmentForUnclearedPrep(
            final UnexpectedRowCountException e)
    {
        if (configuration.isRowCountCheckDeclared()
                && configuration.getPrepDataFiles().length > 0
                && tester.getTearDownOperation() == DatabaseOperation.NONE)
        {
            return new UnexpectedRowCountException(e.getDifferences(),
                    "The @DbUnitRowCountCheck baseline is captured before @DbUnitPrep loads, so"
                            + " a prep dataset that is never torn down reads as leaked rows. Add"
                            + " @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL) (or"
                            + " CLEAN_INSERT), or list these tables in"
                            + " @DbUnitRowCountCheck(exclude = ...).");
        }
        return e;
    }

    private IDataSet loadCombined(final DataFileLoader dataFileLoader, final String[] paths)
            throws Exception
    {
        if (paths.length == 1)
        {
            return dataFileLoader.load(paths[0]);
        }
        final IDataSet[] dataSets = new IDataSet[paths.length];
        for (int i = 0; i < paths.length; i++)
        {
            dataSets[i] = dataFileLoader.load(paths[i]);
        }
        try
        {
            return new CompositeDataSet(dataSets, true, isCaseSensitiveTableNames());
        } catch (final DataSetException e)
        {
            throw new IllegalStateException("Failed to combine prep datasets.", e);
        }
    }

    /**
     * Returns the connection's {@code DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES}, the
     * same feature {@code DefaultPrepAndExpectedTestCase#configureTest()} resolves for its own
     * multi-file combining - so multiple {@code @DbUnitPrep} files combine the same way instead
     * of always case-insensitively. False when the tester has no connection to ask (e.g. a test
     * double).
     */
    private boolean isCaseSensitiveTableNames() throws Exception
    {
        final IDatabaseConnection connection = getConnection();
        return connection != null && connection.getConfig()
                .getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
    }

    /**
     * Forwards {@link #connectionRetrieved} to {@code delegate} first - the tester's own,
     * previously-configured {@link IOperationListener}, and the documented place for it to
     * configure the connection's {@code DatabaseConfig} (e.g. to enable the row count check or
     * set a custom {@code RowCounter}) - then applies {@code @DbUnitProperty} values on top,
     * letting them override whatever {@code delegate} set, then offers the connection to
     * {@code onFirstConnectionRetrieved} for the row count check baseline capture (a no-op
     * after the first call - see
     * {@link AnnotatedTestExecutor#captureRowCountBaselineOnFirstConnectionRetrieved(IDatabaseConnection)}).
     * Reading {@code DatabaseConfig} last this way means the row count check always sees
     * {@code delegate}'s and {@code @DbUnitProperty}'s configuration already applied, never a
     * stale value from before either ran.
     *
     * <p>Also shields the enclosing {@link AnnotatedTestExecutor}'s own memoized connection -
     * identified by {@code protectedConnection}, evaluated fresh on every call since the
     * memoized connection may not be resolved yet when this listener is installed - from a
     * close notification the tester's own setup/teardown machinery would otherwise deliver for
     * it; any other connection object still gets that notification forwarded to {@code delegate}
     * exactly as before, so a tester that hands out a fresh connection per call is unaffected.
     *
     * <p>The shield suppresses the whole {@code operationSetUpFinished}/
     * {@code operationTearDownFinished} callback for the protected connection, not only its
     * {@code connection.close()}: {@link IOperationListener} has no way to signal "do your
     * other work but skip the close". This is exactly right for the delegates dbUnit ships -
     * {@link DefaultOperationListener} (closes, nothing else) and
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} (does nothing). A custom delegate that
     * <em>also</em> does non-close work in those callbacks (commit a transaction, release a
     * lock, record a metric) does not see them for this one connection; do that work from
     * {@code connectionRetrieved} instead, which is always forwarded, or set
     * {@code closeConnectionAfterTest = false} and manage the connection entirely in the
     * delegate.
     */
    private static final class ExecutorOperationListener implements IOperationListener
    {
        private final Properties properties;
        private final Supplier<IDatabaseConnection> protectedConnection;
        private final IOperationListener delegate;
        private final Consumer<IDatabaseConnection> onFirstConnectionRetrieved;

        private ExecutorOperationListener(final Properties properties,
                final Supplier<IDatabaseConnection> protectedConnection,
                final IOperationListener delegate,
                final Consumer<IDatabaseConnection> onFirstConnectionRetrieved)
        {
            this.properties = properties;
            this.protectedConnection = protectedConnection;
            this.delegate = delegate;
            this.onFirstConnectionRetrieved = onFirstConnectionRetrieved;
        }

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            delegate.connectionRetrieved(connection);
            applyProperties(connection, properties);
            onFirstConnectionRetrieved.accept(connection);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
            if (connection == protectedConnection.get())
            {
                return;
            }
            delegate.operationSetUpFinished(connection);
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
            if (connection == protectedConnection.get())
            {
                return;
            }
            delegate.operationTearDownFinished(connection);
        }
    }
}
