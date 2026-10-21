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

import java.util.function.Consumer;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.ClosedConnectionHandedOutWarning;
import org.dbunit.database.connection.ConnectionOwnership;
import org.dbunit.database.connection.TestScopedConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one test's dbUnit lifecycle from a resolved {@link AnnotatedTestConfiguration},
 * dispatching {@link #beforeTest()}/{@link #afterTest(boolean)} to {@link TestCaseLifecycle} for
 * an annotation-driven test and to {@link ClassicLifecycle} for a classic one, and owning what
 * both share: the one {@link TestScopedConnection} this test's steps reuse and its
 * {@link ConnectionOwnership} decision.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension} or the Spring {@code DbUnitTestExecutionListener}, which resolves the
 * {@link IDatabaseTester} and any injected {@link PrepAndExpectedTestCase} - through
 * {@link TestInstanceTesterResolver}'s shared field rules, from the test instances only the
 * binding knows how to find - and hands them here already resolved.
 *
 * <p>The binding tells this executor, through the constructor's {@code annotationDriven} flag,
 * whether the test opted into the {@code org.dbunit.annotation} family at all - any
 * {@code @DbUnit*} annotation, or a {@code @DbUnitTester}/{@code @DbUnitTestCase} field.
 *
 * <p>An <em>annotation-driven</em> test runs through a {@link PrepAndExpectedTestCase}, built
 * around the tester or injected, whether or not it declares {@code @DbUnitExpected}: see
 * {@link TestCaseLifecycle}. The tester is only read, never written, and nothing is installed on
 * it, so its own {@code onSetup()}/{@code onTearDown()} do not run and a tester shared across
 * tests carries nothing from one to the next. The connection {@link #getConnection()} memoizes
 * is {@link DefaultPrepAndExpectedTestCase#getReusableConnection()}'s, so a binding's
 * {@code IDatabaseConnection} parameter shares the test case's own; it is closed by the test
 * case when its lifecycle ends and, for a connection a parameter resolved before that, by
 * {@link #afterTest(boolean)} or {@link #releaseIfAfterTestDidNotRun()} when
 * {@link ConnectionOwnership#mayClose()} allows it.
 *
 * <p>A <em>classic</em> test - a bare {@code @ExtendWith(DbUnitExtension.class)} class with one
 * plain, unannotated {@link IDatabaseTester} field, the 3.5.0 lifecycle-only style - keeps that
 * lifecycle, run by {@link ClassicLifecycle}, with the tester's {@link IOperationListener}
 * wrapped for the test by a {@link ClassicOperationListener} and put back when the test is over.
 * That wrapper sits on the tester object, so tests that run at the same time cannot share a
 * tester on this path: each would see the others' wrapper and connections. Such a tester
 * belongs on the annotation-driven path, which installs nothing on it.
 * A tester that does not report its listener is left untouched. A connection this executor
 * resolves itself, which the 3.5.0 lifecycle never did, is closed after the test unless the
 * tester is known to hand out one fixed connection, see {@link FixedConnectionCheck}, or its
 * listener is the no-op listener: either way the connection is the one the next test needs.
 * Both classes document how the row count check baseline and the connections are handled. A
 * connection the tester hands out already closed, as it hands out one fixed connection once
 * something closed it, is warned about, see {@link ClosedConnectionHandedOutWarning}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AnnotatedTestExecutor
{
    private static final Logger log = LoggerFactory.getLogger(AnnotatedTestExecutor.class);

    private final IDatabaseTester tester;
    private final boolean annotationDriven;
    private final TestScopedConnection testScopedConnection;
    private final TestCaseLifecycle testCaseLifecycle;
    private final ClassicLifecycle classicLifecycle;
    private final ClosedConnectionHandedOutWarning closedConnectionWarning;

    private boolean afterTestRan;
    private ClassicOperationListener installedListener;
    private IOperationListener listenerToRestore;

    /**
     * Creates an executor for an annotation-driven test - equivalent to
     * {@link #AnnotatedTestExecutor(AnnotatedTestConfiguration, IDatabaseTester, PrepAndExpectedTestCase, boolean)}
     * with {@code annotationDriven} {@code true}.
     *
     * @param configuration The resolved configuration to execute.
     * @param tester The tester to construct a {@link PrepAndExpectedTestCase} around when
     *            {@code prepAndExpectedTestCase} is {@code null}.
     * @param prepAndExpectedTestCase An already-injected test case to drive instead of
     *            constructing one, or {@code null} to have this executor construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     */
    AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase)
    {
        this(configuration, tester, prepAndExpectedTestCase, true);
    }

    /**
     * Creates an executor for one test, as
     * {@link #AnnotatedTestExecutor(AnnotatedTestConfiguration, TestInstanceTesterResolver.Resolution, boolean)}
     * does for a tester and test case that are not in a resolution.
     *
     * @param configuration The resolved configuration to execute.
     * @param tester The tester to drive on the classic path, or to construct a
     *            {@link PrepAndExpectedTestCase} around when {@code prepAndExpectedTestCase}
     *            is {@code null} and the test is annotation-driven.
     * @param prepAndExpectedTestCase An already-injected test case to drive instead of
     *            constructing one, or {@code null} to have this executor construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family.
     */
    AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase,
            final boolean annotationDriven)
    {
        this.tester = tester;
        this.annotationDriven = annotationDriven;
        if (annotationDriven)
        {
            this.testCaseLifecycle =
                    new TestCaseLifecycle(configuration, tester, prepAndExpectedTestCase);
            this.classicLifecycle = null;
            this.closedConnectionWarning = null;
            final ConnectionOwnership ownership = new ConnectionOwnership(
                    this::testCaseClosesItsConnection, this::testersOperationListener);
            this.testScopedConnection = new TestScopedConnection(
                    testCaseLifecycle::getReusableConnection, ownership, null);
        } else
        {
            this.testCaseLifecycle = null;
            this.closedConnectionWarning = new ClosedConnectionHandedOutWarning(() -> tester);
            final FixedConnectionCheck handsOutOneFixedConnection = new FixedConnectionCheck(tester);
            final ConnectionOwnership ownership = new ConnectionOwnership(
                    () -> !handsOutOneFixedConnection.getAsBoolean(),
                    this::testersOperationListener);
            final Consumer<IDatabaseConnection> onAcquired =
                    closedConnectionWarning.andThen(this::notifyTesterListenerOfRetrieval);
            this.testScopedConnection = new TestScopedConnection(() -> tester.getConnection(),
                    ownership, onAcquired);
            this.classicLifecycle = new ClassicLifecycle(tester, testScopedConnection,
                    this::isInstalledListenerInPlace);
            if (exposesOperationListener(tester))
            {
                installOperationListener();
            } else if (tester != null)
            {
                log.debug("{} reports no operation listener, so its operation listener cannot be"
                        + " read to wrap and put back; leaving it as it is.",
                        tester.getClass().getName());
            }
        }
    }

    /**
     * Creates an executor for one test from what a binding resolved: the tester, and the
     * injected {@link PrepAndExpectedTestCase} if any, which is used as its owner built it.
     *
     * <p>An annotation-driven test installs nothing on the tester. A classic one mutates the
     * tester's listener only when the tester reports the listener it carries
     * ({@link IDatabaseTester#getOperationListener()} returns one): the listener is replaced here
     * with a {@code ClassicOperationListener} wrapping the previous one, which
     * {@link #afterTest(boolean)} - or {@link #releaseIfAfterTestDidNotRun()} - puts back, so a
     * tester shared across methods (e.g. a {@code static @DbUnitTester} field) carries nothing
     * onto the next test or the next user of the tester. When the tester cannot report its
     * listener, its listener is left untouched; see the class Javadoc.
     *
     * @param configuration The resolved configuration to execute.
     * @param resolution The tester, and any injected test case, a binding resolved for this
     *            test. The tester is driven on the classic path, or a
     *            {@link PrepAndExpectedTestCase} is constructed around it when no test case was
     *            injected and the test is annotation-driven.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family - any {@code @DbUnit*} annotation, or a
     *            {@code @DbUnitTester}/{@code @DbUnitTestCase} field. False for a bare
     *            {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
     *            {@link IDatabaseTester} field, which keeps the 3.5.0 lifecycle, apart from the
     *            tester's listener being wrapped for the test to take the row count check
     *            baseline from the connection {@code onSetup()} retrieves.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final TestInstanceTesterResolver.Resolution resolution, final boolean annotationDriven)
    {
        this(configuration, resolution.getTester(), resolution.getTestCase(), annotationDriven);
    }

    /**
     * Returns whether {@code tester} reports the listener it carries, which
     * {@link #installOperationListener()} needs to wrap it and {@link #restoreOperationListener()}
     * to put it back: {@link IDatabaseTester#getOperationListener()} returns one, as
     * {@link org.dbunit.AbstractDatabaseTester} always does; the default method reports none.
     * Replacing the listener of a tester that reports none would discard the one it has, and
     * leave the replacement on it for good.
     */
    private static boolean exposesOperationListener(final IDatabaseTester tester)
    {
        return tester != null && tester.getOperationListener() != null;
    }

    /**
     * Returns the tester's own listener: the one this executor's wrapper forwards to on the
     * classic path, so {@link ConnectionOwnership} sees the no-op listener a tester was given
     * through it.
     */
    private IOperationListener testersOperationListener()
    {
        if (tester == null)
        {
            return null;
        }
        return withoutExecutorListener(tester.getOperationListener());
    }

    /**
     * Returns whether the {@link ClassicOperationListener} {@link #installOperationListener()}
     * put on the tester still hears its callbacks: the tester still carries it. False when none
     * was installed, and when the tester does not carry it - one whose
     * {@code setOperationListener()} ignores it, say - as the tester's {@code onSetup()} would
     * then never pass its connection on for the row count check baseline.
     */
    private boolean isInstalledListenerInPlace()
    {
        return installedListener != null && tester.getOperationListener() == installedListener;
    }

    /**
     * The {@code closeConnectionAfterTest} input to the {@link ConnectionOwnership} of an
     * annotation-driven test: whether the test case closes its own connection when its lifecycle
     * ends. For a {@link DefaultPrepAndExpectedTestCase} that is the opposite of
     * {@link DefaultPrepAndExpectedTestCase#isConnectionLeftOpen()}, which counts the no-op
     * listener the test case reports itself as well as its {@code closeConnectionAfterTest}
     * flag. It is {@code true} for a test case that is not a
     * {@link DefaultPrepAndExpectedTestCase}, where the tester's listener is the only signal
     * that someone else owns the connection. The connection a binding injects is the test
     * case's {@link DefaultPrepAndExpectedTestCase#getReusableConnection()}, so an injected
     * instance built to keep its connection open for its real owner must not have it closed
     * here.
     */
    private boolean testCaseClosesItsConnection()
    {
        final PrepAndExpectedTestCase testCase = testCaseLifecycle.getPrepAndExpectedTestCase();
        if (testCase instanceof DefaultPrepAndExpectedTestCase)
        {
            return !((DefaultPrepAndExpectedTestCase) testCase).isConnectionLeftOpen();
        }
        return true;
    }

    /**
     * Calls {@link IOperationListener#connectionRetrieved(IDatabaseConnection)} on the tester's
     * own listener for a connection this executor resolved itself rather than through the
     * tester's {@code onSetup()}/{@code onTearDown()}, which would have notified it. That
     * callback is the documented place to configure a connection's {@code DatabaseConfig}, for
     * example to enable the row count check, so skipping it here would leave a check enabled
     * that way running on one route and silently off on another. The executor's own listener
     * is bypassed: it would offer the connection to the baseline capture again. Used on the
     * classic path only; an annotation-driven test's {@link DefaultPrepAndExpectedTestCase}
     * tells the tester's own listener about its shared connection when it acquires it, and again
     * before each of its set up and tear down operations.
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
     * Returns the {@link PrepAndExpectedTestCase} an annotation-driven test runs through, or
     * {@code null} for a classic test, or when nothing has constructed one yet - a binding
     * resolving a parameter before {@link #beforeTest()} runs (e.g. a {@code @BeforeEach}
     * parameter) sees {@code null} unless one was already injected through the constructor, or
     * an earlier parameter resolution this same test already triggered
     * {@link TestCaseLifecycle#ensureTestCase()} (e.g. an {@code IDatabaseConnection} parameter
     * resolved via {@link #getConnection()}).
     *
     * @return The test case this executor drives, or {@code null} if none exists yet.
     */
    public PrepAndExpectedTestCase getPrepAndExpectedTestCase()
    {
        if (testCaseLifecycle == null)
        {
            return null;
        }
        return testCaseLifecycle.getPrepAndExpectedTestCase();
    }

    /**
     * Returns the connection this test's steps use, resolved at most once per test and reused
     * by every caller instead of each asking independently - the row count check's own
     * baseline/verify calls on the classic path, and a binding injecting an
     * {@code IDatabaseConnection} parameter on either. For an annotation-driven test, delegates
     * to {@link DefaultPrepAndExpectedTestCase#getReusableConnection()} - constructing the test
     * case first via {@link TestCaseLifecycle#ensureTestCase()} if it does not exist yet - so a
     * parameter injection shares that test case's own connection; on the classic path, asks
     * {@link #tester} directly. For a tester with no connection caching of its own (e.g. a
     * plain {@code JdbcDatabaseTester}), asking independently would otherwise open one new
     * physical connection per call. Closed in {@link #afterTest(boolean)} - see the class
     * Javadoc.
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
     * Wraps the tester's operation listener in a {@link ClassicOperationListener}, so that the
     * row count check baseline can be captured from whatever connection {@code onSetup()}
     * retrieves on its own - see {@link #onListenerFirstConnectionRetrieved(IDatabaseConnection)}.
     * The tester's own listener keeps every callback, and so every close, it had in the 3.5.0
     * lifecycle. Called from the constructor of a classic executor for a tester that reports its
     * listener; one that cannot is left untouched (see the class Javadoc).
     * {@link #restoreOperationListener()} puts the tester's own listener back once the test is
     * over.
     */
    private void installOperationListener()
    {
        listenerToRestore = withoutExecutorListener(tester.getOperationListener());
        installedListener = new ClassicOperationListener(listenerToRestore,
                this::onListenerFirstConnectionRetrieved);
        tester.setOperationListener(installedListener);
    }

    /**
     * Returns {@code listener} with a wrapper an executor installed on an earlier test peeled
     * off, so re-wrapping a tester shared across tests does not nest one layer per test.
     * Anything else is returned as it is.
     */
    private static IOperationListener withoutExecutorListener(final IOperationListener listener)
    {
        if (listener instanceof ClassicOperationListener)
        {
            return ((ClassicOperationListener) listener).getDelegate();
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
     * and notifies the installed {@link ClassicOperationListener}: warns when the connection is
     * already closed, and offers it to {@link ClassicRowCountCheck} for the piggybacked baseline
     * capture.
     */
    private void onListenerFirstConnectionRetrieved(final IDatabaseConnection connection)
    {
        closedConnectionWarning.accept(connection);
        classicLifecycle.adoptAndCaptureBaselineOnFirstConnection(connection);
    }

    /**
     * Runs every before-test step by delegating to the lifecycle the test runs:
     * {@link TestCaseLifecycle#before()} for an annotation-driven test,
     * {@link ClassicLifecycle#before()} for a classic one.
     *
     * @throws Exception If any step fails.
     */
    public void beforeTest() throws Exception
    {
        if (annotationDriven)
        {
            testCaseLifecycle.before();
        } else
        {
            classicLifecycle.before();
        }
    }

    /**
     * Runs every after-test step by delegating to the lifecycle the test runs -
     * {@link TestCaseLifecycle#after(boolean)} or {@link ClassicLifecycle#after(boolean)} - then
     * closes the connection {@link #testScopedConnection} memoized, if one was ever resolved, so
     * a difference reported by the row count check still leaves it closed.
     *
     * <p>When both the step and closing the connection fail, the step's failure is the one
     * thrown, with the close failure attached to it via
     * {@link Throwable#addSuppressed(Throwable)} rather than replacing it - a plain {@code
     * finally} block would otherwise let the close failure silently discard the more useful
     * diagnostic (e.g. which table the row count check found unexpectedly changed). The step's
     * failure is caught as {@link Throwable}, not just {@link Exception}, and rethrown with its
     * original static type preserved: a comparison mismatch on an annotation-driven test fails
     * via {@link org.dbunit.assertion.DbComparisonFailure}, an {@link Error} subclass, not an
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
            restoreOperationListener();
        }
    }

    private void runAfterStepThenRelease(final boolean testFailed) throws Exception
    {
        try
        {
            if (annotationDriven)
            {
                testCaseLifecycle.after(testFailed);
            } else
            {
                classicLifecycle.after(testFailed);
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
     * listener already decided whether to close it, and one it resolved itself is closed unless
     * the tester is known to hand out one fixed connection or its listener is the no-op
     * listener; see the class Javadoc.
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
     * after an {@code IDatabaseConnection} parameter already resolved this
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
        if (testCaseLifecycle != null)
        {
            try
            {
                testCaseLifecycle.undoConfiguration();
            } catch (final RuntimeException undoFailure)
            {
                failure = withSuppressed(failure, undoFailure);
            }
        }
        try
        {
            restoreOperationListener();
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
