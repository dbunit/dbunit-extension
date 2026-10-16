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

import java.util.function.Supplier;

import org.dbunit.ConnectionPreservingOperationListener;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DefaultOperationListener;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfigOverrides;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.AutoCommitOffWarning;
import org.dbunit.database.connection.ConnectionOwnership;
import org.dbunit.database.connection.LeftOpenConnectionWarning;
import org.dbunit.database.connection.TestScopedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one test's dbUnit lifecycle from a resolved {@link AnnotatedTestConfiguration},
 * dispatching {@link #beforeTest()}/{@link #afterTest(boolean)} to one of two path objects -
 * {@link SetupTeardownLifecycle} when {@link AnnotatedTestConfiguration#isExpected()} is false,
 * {@link ExpectedLifecycle} when it is true - and owning what both share: the one
 * {@link TestScopedConnection} this test's steps reuse, its {@link ConnectionOwnership}
 * decision, the {@code @DbUnitProperty} application, and (for an annotation-driven test) the
 * {@link ExecutorOperationListener} installed on the tester.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension} or the Spring {@code DbUnitTestExecutionListener}, which resolves the
 * {@link IDatabaseTester} and any injected {@link PrepAndExpectedTestCase} - through
 * {@link TestInstanceTesterResolver}'s shared field rules, from the test instances only the
 * binding knows how to find - and hands them here already resolved.
 *
 * <p>The binding tells this executor, through the constructor's {@code annotationDriven} flag,
 * whether the test opted into the {@code org.dbunit.annotation} family at all - any
 * {@code @DbUnit*} annotation, or a {@code @DbUnitTester}/{@code @DbUnitTestCase} field. When it
 * did not - a bare {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
 * {@link IDatabaseTester} field, the 3.5.0 lifecycle-only style - this executor takes the
 * <em>classic path</em>: {@link SetupTeardownLifecycle} lets {@code onSetup()}/
 * {@code onTearDown()} manage their own connections, as {@code DbUnitExtension} did before this
 * class existed, and the tester's {@link IOperationListener} is wrapped for the test by an
 * {@link ExecutorOperationListener} that forwards every callback to it, shields no connection
 * from the closes it makes, and only takes the row count check baseline from the connection
 * {@code onSetup()} retrieves, so the test opens no connection the 3.5.0 lifecycle did not. The
 * listener is put back when the test is over. The connection {@code onSetup()} retrieved stays
 * the tester's listener's to close or to keep open, as in the 3.5.0 lifecycle: that listener was
 * told the operation finished on it, and one that left it open keeps it for another owner, such
 * as the fixed connection of a {@link org.dbunit.DefaultDatabaseTester} that later tests reuse.
 * A connection this executor has to resolve itself, because {@code onSetup()} cannot be trusted
 * to retrieve one, is first offered to the tester's own listener's
 * {@link IOperationListener#connectionRetrieved(IDatabaseConnection)}, as every connection
 * retrieved from the tester is, so a {@code DatabaseConfig} customization made there - such as
 * enabling the row count check - applies to it, and is the only one this executor closes. The
 * prep/expected path is always annotation-driven (it needs {@code @DbUnitExpected}).
 *
 * <p>Any test leaves the tester's {@link IOperationListener} untouched when its tester does not
 * report the listener it carries - an {@link IDatabaseTester} that does not override
 * {@link IDatabaseTester#getOperationListener()}, as {@link org.dbunit.AbstractDatabaseTester}
 * does. Wrapping a listener takes reading it, and putting it back takes comparing it, so
 * replacing one that cannot be read would discard it for good - for example the no-op listener
 * that says the tester's connections are managed elsewhere. For such a test the row count check
 * baseline is always captured from a connection this executor resolves itself, and an
 * annotation-driven one has its {@code @DbUnitProperty} values reach only that connection, not
 * one the tester's own {@code onSetup()}/{@code onTearDown()} retrieves, and the connection this
 * executor holds is not shielded from a close by the tester's own listener.
 *
 * <p>The connection {@link #getConnection()} memoizes - the tester's own on the setup/teardown
 * path, {@link PrepAndExpectedTestCase#getReusableConnection()}'s on the prep/expected path so a
 * {@code Connection}/{@code IDatabaseConnection} parameter shares the test case's own - is used
 * by the row count check and by a binding's parameter injection, and closed in
 * {@link #afterTest(boolean)} when {@link ConnectionOwnership#mayClose()} allows it, unless the
 * classic path took it over from {@code onSetup()}. On an
 * annotation-driven test the {@link ExecutorOperationListener} additionally shields that one
 * connection, by identity, from a premature close by the tester's own {@code onSetup()}/
 * {@code onTearDown()} machinery while still forwarding a close for any other connection the
 * tester hands out.
 *
 * <p>The row count check (see {@code org.dbunit.database.rowcount.RowCountCheck}) runs, on the
 * setup/teardown path, through {@link AnnotatedRowCountCheck}; the prep/expected path already
 * has its own via {@link DefaultPrepAndExpectedTestCase#preTest()} and {@code cleanupData()}.
 * Either way, when {@code @DbUnitRowCountCheck} is declared its resolved {@code RowCountCheck}
 * overrides whichever of the two would otherwise resolve one from the connection's own
 * {@link DatabaseConfig} - for the prep/expected path via
 * {@link DefaultPrepAndExpectedTestCase#setRowCountCheckOverride(boolean, String[])} (applied
 * by {@link InjectedTestCaseConfigurer}), which resolves that connection lazily on its own so
 * this executor needs none just to build the override.
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
    private final AutoCommitOffWarning autoCommitOffWarning = new AutoCommitOffWarning();
    private final LeftOpenConnectionWarning leftOpenConnectionWarning;
    private final TestScopedConnection testScopedConnection;
    private final ExpectedLifecycle expectedLifecycle;
    private final SetupTeardownLifecycle setupTeardownLifecycle;
    private final DatabaseConfigOverrides databaseConfigOverrides;

    private boolean afterTestRan;
    private ExecutorOperationListener installedListener;
    private IOperationListener listenerToRestore;
    private PrepAndExpectedTestCase testCaseToUnbindTesterFrom;

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
     * <p>Mutates {@code tester}'s listener only when {@code tester} reports the listener it
     * carries (it overrides {@link IDatabaseTester#getOperationListener()}):
     * {@link #installOperationListener()} runs here, replacing {@code tester}'s
     * {@link IOperationListener} with an {@code ExecutorOperationListener} wrapping the previous
     * one, which {@link #afterTest(boolean)} - or {@link #releaseIfAfterTestDidNotRun()} - puts
     * back, so a {@code tester} shared across methods (e.g. a {@code static @DbUnitTester}
     * field) carries nothing onto the next test or the next user of the tester. A
     * {@code tester} shared beyond one test class - a bean in a cached application context - is
     * left as it was found the same way, but is still not safe to share between tests running at
     * the same time: each installs its own wrapper and sets its own dataset and operations on
     * the one tester. When {@code annotationDriven} is false - the classic path - the wrapper
     * shields no connection from a close by the tester's own listener. When {@code tester}
     * cannot report its listener, the tester's listener is left untouched; see the class
     * Javadoc.
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
     *            are then left as in the 3.5.0 lifecycle, apart from the tester's listener being
     *            wrapped for the test to take the row count check baseline from the connection
     *            {@code onSetup()} retrieves. Always true for the prep/expected path, which
     *            needs {@code @DbUnitExpected}.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase,
            final boolean annotationDriven)
    {
        this(configuration, tester, prepAndExpectedTestCase, annotationDriven, false);
    }

    /**
     * Creates an executor for one test from what a binding resolved, taking the tester, the
     * injected {@link PrepAndExpectedTestCase} if any, and whether the resolution gave that test
     * case its tester. When it did, {@link #unbindTesterFromInjectedTestCase()} gives the test
     * case no tester again once the whole test is over, so an injected test case shared across
     * tests, such as a {@code static} {@code @DbUnitTestCase} field, does not keep the first
     * test's tester for all the others. Otherwise the same as
     * {@link #AnnotatedTestExecutor(AnnotatedTestConfiguration, IDatabaseTester, PrepAndExpectedTestCase, boolean)}.
     *
     * @param configuration The resolved configuration to execute.
     * @param resolution The tester, and any injected test case, a binding resolved for this test.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family, as for the other constructor.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final TestInstanceTesterResolver.Resolution resolution, final boolean annotationDriven)
    {
        this(configuration, resolution.getTester(), resolution.getTestCase(), annotationDriven,
                resolution.isTesterBoundToTestCase());
    }

    private AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase,
            final boolean annotationDriven, final boolean testerBoundToTestCase)
    {
        this.configuration = configuration;
        this.tester = tester;
        this.annotationDriven = annotationDriven;
        if (testerBoundToTestCase)
        {
            this.testCaseToUnbindTesterFrom = prepAndExpectedTestCase;
        }
        this.databaseConfigOverrides =
                new DatabaseConfigOverrides(configuration.getDatabaseConfigProperties());
        this.expectedLifecycle =
                new ExpectedLifecycle(configuration, tester, prepAndExpectedTestCase);
        final ConnectionOwnership ownership = new ConnectionOwnership(
                this::mayCloseConnectionAfterTest, tester::getOperationListener,
                this::borrowingLifecycleRan);
        this.leftOpenConnectionWarning = new LeftOpenConnectionWarning(ownership, () -> tester);
        this.testScopedConnection = new TestScopedConnection(this::acquireConnection, ownership,
                this::onConnectionAcquired);
        final boolean installsOperationListener = exposesOperationListener(tester);
        this.setupTeardownLifecycle = new SetupTeardownLifecycle(configuration, tester,
                testScopedConnection, this::isInstalledListenerInPlace);
        if (installsOperationListener)
        {
            installOperationListener();
        } else
        {
            log.debug("{} does not override IDatabaseTester.getOperationListener(), so its"
                    + " operation listener cannot be read to wrap and put back; leaving it as it"
                    + " is.", tester.getClass().getName());
        }
    }

    /**
     * Returns whether {@code tester} reports the listener it carries, which
     * {@link #installOperationListener()} needs to wrap it and {@link #restoreOperationListener()}
     * to put it back: its type overrides {@link IDatabaseTester#getOperationListener()}, whose
     * default reports none. Replacing the listener of a tester that does not would discard the
     * one it has, and leave the replacement on it for good.
     */
    private static boolean exposesOperationListener(final IDatabaseTester tester)
    {
        return DefaultMethodOverrideCheck.overridesDefaultMethod(tester.getClass(),
                IDatabaseTester.class, "getOperationListener");
    }

    /**
     * Returns whether the {@link ExecutorOperationListener} {@link #installOperationListener()}
     * put on the tester still hears its callbacks: the tester carries it, directly or beneath
     * {@link ConnectionPreservingOperationListener} layers, which forward every
     * {@code connectionRetrieved()}. False when none was installed, and when something has
     * replaced it since - a {@code @BeforeEach} method given the tester as a parameter runs
     * after this executor was built and may set a listener of its own - as the tester's
     * {@code onSetup()} would then never pass its connection on for the row count check
     * baseline.
     */
    private boolean isInstalledListenerInPlace()
    {
        IOperationListener layer = tester.getOperationListener();
        while (layer != null)
        {
            if (layer == installedListener)
            {
                return true;
            }
            if (!(layer instanceof ConnectionPreservingOperationListener))
            {
                return false;
            }
            layer = ((ConnectionPreservingOperationListener) layer).getDelegate();
        }
        return false;
    }

    /**
     * The {@code closeConnectionAfterTest} input to this executor's {@link ConnectionOwnership}:
     * the {@code @DbUnitConfig} flag, and on the prep/expected path also the test case's own
     * {@link PrepAndExpectedTestCase#isCloseConnectionAfterTest()}. The connection a binding
     * injects there is the test case's {@link PrepAndExpectedTestCase#getReusableConnection()},
     * so an injected instance built to keep its connection open for its real owner must not
     * have it closed here just because {@code @DbUnitConfig} says nothing about it.
     */
    private boolean mayCloseConnectionAfterTest()
    {
        final boolean configuredToClose = configuration.isCloseConnectionAfterTest();
        final PrepAndExpectedTestCase testCase = expectedLifecycle.getPrepAndExpectedTestCase();
        final boolean testCaseCloses = !configuration.isExpected() || testCase == null
                || testCase.isCloseConnectionAfterTest();
        return configuredToClose && testCaseCloses;
    }

    /**
     * The {@code borrowingLifecycleRan} input to this executor's {@link ConnectionOwnership}:
     * true unless the connection is held by a reused injected {@code @DbUnitTestCase} instance
     * whose {@link ExpectedLifecycle#before()} failed before {@code configureTest()} - see
     * {@link ExpectedLifecycle#isConnectionOwnedByUnconfiguredInjectedTestCase()}.
     */
    private boolean borrowingLifecycleRan()
    {
        return !(configuration.isExpected()
                && expectedLifecycle.isConnectionOwnedByUnconfiguredInjectedTestCase());
    }

    private IDatabaseConnection acquireConnection() throws Exception
    {
        return configuration.isExpected()
                ? expectedLifecycle.ensureConfiguredTestCase().getReusableConnection()
                : tester.getConnection();
    }

    /**
     * Runs on each connection {@link #testScopedConnection} acquires: applies
     * {@code @DbUnitProperty} values, and - on the annotation-driven setup/teardown path, where
     * this executor's prep and teardown operations run through the tester rather than a
     * transaction-managed {@link org.dbunit.operation.TransactionOperation} - warns once if
     * that connection has autocommit off, and once if it is going to be left open with a tester
     * that opens a new connection on every call (see {@link LeftOpenConnectionWarning}), the
     * same checks {@link DefaultPrepAndExpectedTestCase} makes for the prep/expected path. The
     * classic path resolves a connection here only for the read-only row count check baseline,
     * when {@code onSetup()} cannot be trusted to retrieve one, so an autocommit-off connection
     * there is not a problem and neither warning is made; the prep/expected path warns via
     * {@link DefaultPrepAndExpectedTestCase}'s own acquisition of the same connection.
     *
     * <p>On the setup/teardown path it first tells the tester's own listener about the
     * connection - see {@link #notifyTesterListenerOfRetrieval}.
     */
    private void onConnectionAcquired(final IDatabaseConnection connection)
    {
        if (!configuration.isExpected())
        {
            notifyTesterListenerOfRetrieval(connection);
        }
        applyDatabaseConfigProperties(connection);
        if (annotationDriven && !configuration.isExpected())
        {
            autoCommitOffWarning.accept(connection);
            leftOpenConnectionWarning.accept(connection);
        }
    }

    /**
     * Calls {@link IOperationListener#connectionRetrieved(IDatabaseConnection)} on the tester's
     * own listener for a connection this executor resolved itself rather than through the
     * tester's {@code onSetup()}/{@code onTearDown()}, which would have notified it. That
     * callback is the documented place to configure a connection's {@code DatabaseConfig}, for
     * example to enable the row count check, so skipping it here would leave a check enabled
     * that way running on one route and silently off on another. The executor's own listener
     * is bypassed: it would offer the connection to the baseline capture again, and
     * {@link #onConnectionAcquired} applies the {@code @DbUnitProperty} values itself, after
     * the tester's listener, as the executor's listener does. Not used on the prep/expected
     * path, where {@link org.dbunit.DefaultPrepAndExpectedTestCase} tells the tester's own
     * listener about the connection each of its set up and tear down operations retrieves.
     */
    private void notifyTesterListenerOfRetrieval(final IDatabaseConnection connection)
    {
        final IOperationListener testersOwnListener =
                withoutExecutorListener(tester.getOperationListener());
        if (testersOwnListener == null)
        {
            return;
        }
        testersOwnListener.connectionRetrieved(connection);
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
     * triggered {@link ExpectedLifecycle#ensureTestCase()} (e.g. a {@code Connection} parameter
     * resolved via {@link #getConnection()}).
     *
     * @return The test case this executor drives, or {@code null} if none exists yet.
     */
    public PrepAndExpectedTestCase getPrepAndExpectedTestCase()
    {
        return expectedLifecycle.getPrepAndExpectedTestCase();
    }

    /**
     * Returns the connection this test's steps use, resolved at most once per test and reused
     * by every caller instead of each asking independently - the row count check's own
     * baseline/verify calls on the setup/teardown path, and a binding injecting an
     * {@code IDatabaseConnection}/{@code Connection} parameter on either path. On the
     * prep/expected path ({@link AnnotatedTestConfiguration#isExpected()} true), delegates to
     * {@link PrepAndExpectedTestCase#getReusableConnection()} - constructing the test case first
     * via {@link ExpectedLifecycle#ensureTestCase()} if it does not exist yet - instead of
     * asking {@link #tester} directly, so a parameter injection shares that test case's own
     * connection; on the setup/teardown path, asks {@link #tester} directly. For a tester with
     * no connection caching of its own (e.g. a plain {@code JdbcDatabaseTester}), asking
     * independently on either path would otherwise open one new physical connection per call.
     * Closed in {@link #afterTest(boolean)} - see the class Javadoc.
     *
     * @return The connection to reuse, or {@code null} if none is available (e.g. a test
     *         double).
     * @throws Exception If resolving the connection fails.
     */
    public IDatabaseConnection getConnection() throws Exception
    {
        return testScopedConnection.getConnection();
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
        return testScopedConnection.peekConnection();
    }

    /**
     * Wraps the tester's operation listener in an {@link ExecutorOperationListener}, so that the
     * row count check baseline can be captured from whatever connection {@code onSetup()}
     * retrieves on its own - see {@link #onListenerFirstConnectionRetrieved(IDatabaseConnection)}
     * - and, for an {@code annotationDriven} test, so that this executor's own memoized
     * connection (see the class Javadoc) is protected from a premature close by the tester's own
     * setup/teardown machinery, whether or not {@code @DbUnitProperty} is configured. The
     * classic path protects no connection: the tester's own listener keeps every callback, and
     * so every close, it had in the 3.5.0 lifecycle. Called from the constructor for a tester
     * that reports its listener; one that cannot is left untouched (see the class Javadoc).
     * {@link #restoreOperationListener()} puts the tester's own listener back once the test is
     * over.
     */
    private void installOperationListener()
    {
        listenerToRestore = withoutExecutorListener(tester.getOperationListener());
        final IOperationListener delegate = listenerToRestore == null
                ? new DefaultOperationListener()
                : listenerToRestore;
        final Supplier<IDatabaseConnection> protectedConnection =
                annotationDriven ? this::peekResolvedConnection : () -> null;
        installedListener = new ExecutorOperationListener(this::applyDatabaseConfigProperties,
                protectedConnection, delegate, this::onListenerFirstConnectionRetrieved);
        tester.setOperationListener(installedListener);
    }

    /**
     * Returns {@code listener} with a wrapper an executor installed on an earlier test peeled
     * off, so re-wrapping a tester shared across tests does not nest one layer per test.
     * Anything else is returned as it is - including a caller's own
     * {@link org.dbunit.ConnectionPreservingOperationListener}, which expresses who owns the
     * connections and must stay in force beneath this executor's wrapper.
     */
    private static IOperationListener withoutExecutorListener(final IOperationListener listener)
    {
        if (listener instanceof ExecutorOperationListener)
        {
            return ((ExecutorOperationListener) listener).getDelegate();
        }
        return listener;
    }

    /**
     * Puts back the listener the tester carried before {@link #installOperationListener()},
     * unless something else has replaced the executor's since - a test that installs a listener
     * of its own mid-test owns it, and the executor's listener, left in its chain, is
     * deactivated. A no-op when nothing was installed, or it was already put back.
     */
    private void restoreOperationListener()
    {
        if (installedListener == null)
        {
            return;
        }
        installedListener.deactivate();
        if (tester.getOperationListener() == installedListener)
        {
            tester.setOperationListener(listenerToRestore);
        }
        installedListener = null;
    }

    /**
     * Runs when the tester's own {@code onSetup()}/{@code onTearDown()} retrieves a connection
     * and notifies the installed {@link ExecutorOperationListener}: on the annotation-driven
     * setup/teardown path, warns once on an autocommit-off connection (the single point every
     * connection a prep or teardown operation runs on passes through - see
     * {@link AutoCommitOffWarning}) and once on one that is going to be left open with nothing
     * to close it (see {@link LeftOpenConnectionWarning}), then offers it to
     * {@link AnnotatedRowCountCheck} for the piggybacked baseline capture. The classic path
     * makes no warning, as it never did. A no-op on the prep/expected path: the row count check
     * there is the {@link PrepAndExpectedTestCase}'s own, and the connection identity is
     * {@link PrepAndExpectedTestCase#getReusableConnection()}'s - adopting a
     * directly-retrieved one here would reintroduce, for a
     * {@link PrepAndExpectedTestCase} implementation that drives {@code tester.onSetup()}
     * itself, the connection-identity split {@link #getConnection()}'s path split exists to
     * prevent.
     */
    private void onListenerFirstConnectionRetrieved(final IDatabaseConnection connection)
    {
        if (configuration.isExpected())
        {
            return;
        }
        if (annotationDriven)
        {
            autoCommitOffWarning.accept(connection);
            leftOpenConnectionWarning.accept(connection);
        }
        setupTeardownLifecycle.adoptAndCaptureBaselineOnFirstConnection(connection);
    }

    /**
     * Applies {@code @DbUnitProperty} values to {@code connection}'s {@link DatabaseConfig}; a
     * no-op when there are none. Called from both {@link #getConnection()}, so the row count
     * check's baseline/verify/override calls always see property state already applied before
     * they read it, and from {@link ExecutorOperationListener}, since a tester's
     * {@code onSetup()}/{@code onTearDown()} may resolve their own connection independently of
     * {@link #getConnection()}'s memoized one. {@link #restoreWhatThisExecutorChanged()} puts
     * back what this replaced, so a connection kept past the test carries none of the values
     * forward.
     *
     * <p>A no-op on the prep/expected path, where the {@link PrepAndExpectedTestCase} applies the
     * values to the connection it owns and puts them back itself: this executor applying them as
     * well, after it, would take the test case's values for the ones to restore.
     *
     * @param connection The connection whose {@link DatabaseConfig} to apply the values to.
     */
    private void applyDatabaseConfigProperties(final IDatabaseConnection connection)
    {
        if (configuration.isExpected())
        {
            return;
        }

        try
        {
            databaseConfigOverrides.applyTo(connection.getConfig());
        } catch (final DatabaseUnitException e)
        {
            throw new IllegalStateException("Failed to apply @DbUnitProperty values.", e);
        }
    }

    /**
     * Puts back what {@link #applyDatabaseConfigProperties(IDatabaseConnection)} replaced, then
     * the tester's own listener - see {@link #restoreOperationListener()} - so the one failing
     * cannot leave the other undone.
     */
    private void restoreWhatThisExecutorChanged()
    {
        try
        {
            databaseConfigOverrides.restore();
        } finally
        {
            restoreOperationListener();
        }
    }

    /**
     * Runs every before-test step by delegating to the path {@link AnnotatedTestConfiguration#isExpected()}
     * selects: {@link SetupTeardownLifecycle#before()} or {@link ExpectedLifecycle#before()}.
     *
     * @throws Exception If any step fails.
     */
    public void beforeTest() throws Exception
    {
        if (configuration.isExpected())
        {
            expectedLifecycle.before();
        } else
        {
            setupTeardownLifecycle.before();
        }
    }

    /**
     * Runs every after-test step by delegating to the path {@link AnnotatedTestConfiguration#isExpected()}
     * selects - {@link SetupTeardownLifecycle#after(boolean)} or
     * {@link ExpectedLifecycle#after(boolean)} - then closes the connection
     * {@link #testScopedConnection} memoized, if one was ever resolved, so a difference
     * reported by the row count check still leaves it closed.
     *
     * <p>When both the step and closing the connection fail, the step's failure is the one
     * thrown, with the close failure attached to it via
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
        afterTestRan = true;
        try
        {
            runAfterStepThenRelease(testFailed);
        } finally
        {
            restoreWhatThisExecutorChanged();
        }
    }

    private void runAfterStepThenRelease(final boolean testFailed) throws Exception
    {
        try
        {
            if (configuration.isExpected())
            {
                expectedLifecycle.after(testFailed);
            } else
            {
                setupTeardownLifecycle.after(testFailed);
            }
        } catch (final Throwable primaryFailure)
        {
            try
            {
                releaseConnection();
            } catch (final Exception closeFailure)
            {
                primaryFailure.addSuppressed(closeFailure);
            }
            throw primaryFailure;
        }
        releaseConnection();
    }

    /**
     * Releases the connection {@link #testScopedConnection} memoized. On the classic path one
     * it took over from the tester's own {@code onSetup()} is only forgotten, as the tester's
     * listener already decided whether to close it; see the class Javadoc.
     *
     * @throws Exception If closing the connection fails.
     */
    private void releaseConnection() throws Exception
    {
        if (annotationDriven)
        {
            testScopedConnection.release();
        } else
        {
            testScopedConnection.releaseUnlessAdopted();
        }
    }

    /**
     * Closes the connection {@link #testScopedConnection} memoized, when {@link #afterTest(boolean)}
     * never ran. A binding calls this once the test method's whole lifecycle is over, to cover
     * the one path {@link #afterTest(boolean)} cannot: a {@code @BeforeEach} method that fails
     * after a {@code Connection}/{@code IDatabaseConnection} parameter already resolved this
     * executor's connection, so the test method - and with it the binding's after-test callback -
     * never runs. A no-op when {@link #afterTest(boolean)} already ran, which released the
     * connection itself, and when no connection was ever resolved, which it never acquires.
     * Closes under the same {@link ConnectionOwnership#mayClose()} rules as
     * {@link #afterTest(boolean)}.
     *
     * @throws Exception If closing the connection fails.
     */
    public void releaseIfAfterTestDidNotRun() throws Exception
    {
        if (afterTestRan)
        {
            return;
        }
        Exception failure = null;
        try
        {
            releaseConnection();
        } catch (final Exception releaseFailure)
        {
            failure = releaseFailure;
        }
        try
        {
            expectedLifecycle.undoConfiguration();
        } catch (final RuntimeException undoFailure)
        {
            failure = withSuppressed(failure, undoFailure);
        }
        try
        {
            restoreWhatThisExecutorChanged();
        } catch (final RuntimeException restoreFailure)
        {
            failure = withSuppressed(failure, restoreFailure);
        }
        if (failure != null)
        {
            throw failure;
        }
    }

    /**
     * Gives the injected test case no tester again, when the resolution gave it this one because
     * it carried none: the tester was chosen for this test, by a {@code databaseTesterFactory}, an
     * unmarked field or a Spring bean, and the test case, shared across tests as a
     * {@code static @DbUnitTestCase} field is, would otherwise keep it - never asking for the
     * next test's own.
     *
     * <p>A binding calls this once the whole test is over, after {@link #afterTest(boolean)} or
     * {@link #releaseIfAfterTestDidNotRun()} and after any {@code @AfterEach} method, which may
     * still use the tester through the test case.
     * {@link PrepAndExpectedTestCase#setDatabaseTester(IDatabaseTester)} with {@code null}
     * releases the connection it holds, as {@code DefaultPrepAndExpectedTestCase} does, closing
     * it only if its {@code closeConnectionAfterTest} says to. That is the instance's own value
     * by now: a {@code false} the test declared was put back when the test's configuration was
     * undone. So when the test declared {@code false}, the connection belongs to its real owner,
     * and the instance is held to that while the tester comes off. A no-op when nothing was
     * bound, or it was already taken off.
     */
    public void unbindTesterFromInjectedTestCase()
    {
        final PrepAndExpectedTestCase testCase = testCaseToUnbindTesterFrom;
        testCaseToUnbindTesterFrom = null;
        if (testCase == null)
        {
            return;
        }

        if (configuration.isCloseConnectionAfterTest())
        {
            testCase.setDatabaseTester(null);
            return;
        }

        final boolean ownCloseConnectionAfterTest = testCase.isCloseConnectionAfterTest();
        testCase.setCloseConnectionAfterTest(false);
        try
        {
            testCase.setDatabaseTester(null);
        } finally
        {
            testCase.setCloseConnectionAfterTest(ownCloseConnectionAfterTest);
        }
    }

    /**
     * Returns {@code primary}, with {@code later} attached to it as suppressed, or {@code later}
     * itself when nothing has failed yet - so the first failure is the one that surfaces and a
     * cleanup step after it cannot replace it.
     */
    private static Exception withSuppressed(final Exception primary, final Exception later)
    {
        if (primary == null)
        {
            return later;
        }
        primary.addSuppressed(later);
        return primary;
    }
}
