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

import java.util.Properties;

import org.dbunit.ConnectionPreservingOperationListener;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.AutoCommitOffWarning;
import org.dbunit.database.connection.ConnectionOwnership;
import org.dbunit.database.connection.TestScopedConnection;

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
 * {@code DbUnitExtension}, which resolves the {@link IDatabaseTester} and any injected
 * {@link PrepAndExpectedTestCase} - field discovery is binding-specific - and hands them here
 * already resolved.
 *
 * <p>The binding tells this executor, through the constructor's {@code annotationDriven} flag,
 * whether the test opted into the {@code org.dbunit.annotation} family at all - any
 * {@code @DbUnit*} annotation, or a {@code @DbUnitTester}/{@code @DbUnitTestCase} field. When it
 * did not - a bare {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
 * {@link IDatabaseTester} field, the 3.5.0 lifecycle-only style - this executor takes the
 * <em>classic path</em>: {@link #installOperationListener()} does not run, so the tester's
 * {@link IOperationListener} is left untouched, and {@link AnnotatedRowCountCheck} never
 * piggybacks (it captures its baseline eagerly and {@link SetupTeardownLifecycle} lets
 * {@code onSetup()}/{@code onTearDown()} manage their own connections) - exactly as
 * {@code DbUnitExtension} did before this class existed. The prep/expected path is always
 * annotation-driven (it needs {@code @DbUnitExpected}).
 *
 * <p>The connection {@link #getConnection()} memoizes - the tester's own on the setup/teardown
 * path, {@link PrepAndExpectedTestCase#getReusableConnection()}'s on the prep/expected path so a
 * {@code Connection}/{@code IDatabaseConnection} parameter shares the test case's own - is used
 * by the row count check and by a binding's parameter injection, and closed in
 * {@link #afterTest(boolean)} when {@link ConnectionOwnership#mayClose()} allows it. On an
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
    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final boolean annotationDriven;
    private final AutoCommitOffWarning autoCommitOffWarning = new AutoCommitOffWarning();
    private final TestScopedConnection testScopedConnection;
    private final ExpectedLifecycle expectedLifecycle;
    private final SetupTeardownLifecycle setupTeardownLifecycle;

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
     * {@link ConnectionPreservingOperationListener#unwrap(IOperationListener)} unwraps the prior
     * wrapper first, so the layers do not stack - and the last test's wrapper stays installed
     * on the tester after the class
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
        this.annotationDriven = annotationDriven;
        this.expectedLifecycle =
                new ExpectedLifecycle(configuration, tester, prepAndExpectedTestCase);
        final ConnectionOwnership ownership = new ConnectionOwnership(
                configuration::isCloseConnectionAfterTest, tester::getOperationListener,
                this::borrowingLifecycleRan);
        this.testScopedConnection = new TestScopedConnection(this::acquireConnection, ownership,
                this::onConnectionAcquired);
        this.setupTeardownLifecycle = new SetupTeardownLifecycle(configuration, tester,
                testScopedConnection, annotationDriven);
        if (annotationDriven)
        {
            installOperationListener();
        }
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
                ? expectedLifecycle.ensureTestCase().getReusableConnection()
                : tester.getConnection();
    }

    /**
     * Runs on each connection {@link #testScopedConnection} acquires: applies
     * {@code @DbUnitProperty} values, and - on the annotation-driven setup/teardown path, where
     * this executor's prep and teardown operations run through the tester rather than a
     * transaction-managed {@link org.dbunit.operation.TransactionOperation} - warns once if
     * that connection has autocommit off, the same check {@link DefaultPrepAndExpectedTestCase}
     * makes for the prep/expected path. The classic path resolves this connection only for the
     * read-only row count check baseline, so an autocommit-off connection there is not a
     * problem and is not warned about; the prep/expected path warns via
     * {@link DefaultPrepAndExpectedTestCase}'s own acquisition of the same connection.
     */
    private void onConnectionAcquired(final IDatabaseConnection connection)
    {
        applyProperties(connection, configuration.getDatabaseConfigProperties());
        if (annotationDriven && !configuration.isExpected())
        {
            autoCommitOffWarning.accept(connection);
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
     * Wraps the tester's operation listener in an {@link ExecutorOperationListener}, so that
     * regardless of whether {@code @DbUnitProperty} is configured, this executor's own memoized
     * connection (see the class Javadoc) is protected from a premature close by the tester's own
     * setup/teardown machinery, and so the row count check baseline can be captured from
     * whatever connection {@code onSetup()} retrieves on its own - see
     * {@link #onListenerFirstConnectionRetrieved(IDatabaseConnection)}. Called from the
     * constructor only for an {@code annotationDriven} test; the classic path leaves the
     * tester's listener untouched (see the class Javadoc).
     * {@link ConnectionPreservingOperationListener#unwrap(IOperationListener)} un-nests a prior
     * wrapper so re-wrapping a tester shared across tests does not stack layers.
     */
    private void installOperationListener()
    {
        final IOperationListener delegate =
                ConnectionPreservingOperationListener.unwrap(tester.getOperationListener());
        tester.setOperationListener(new ExecutorOperationListener(
                configuration.getDatabaseConfigProperties(), this::peekResolvedConnection,
                delegate, this::onListenerFirstConnectionRetrieved));
    }

    /**
     * Runs when the tester's own {@code onSetup()}/{@code onTearDown()} retrieves a connection
     * and notifies the installed {@link ExecutorOperationListener}: on the setup/teardown path,
     * warns once on an autocommit-off connection (the single point every connection a prep or
     * teardown operation runs on passes through - see {@link AutoCommitOffWarning}), then offers
     * it to {@link AnnotatedRowCountCheck} for the piggybacked baseline capture. A no-op on the
     * prep/expected path: the row count check there is the {@link PrepAndExpectedTestCase}'s own,
     * and the connection identity is {@link PrepAndExpectedTestCase#getReusableConnection()}'s -
     * adopting a directly-retrieved one here would reintroduce, for a
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
        autoCommitOffWarning.accept(connection);
        setupTeardownLifecycle.adoptAndCaptureBaselineOnFirstConnection(connection);
    }

    /**
     * Applies {@code @DbUnitProperty} values to {@code connection}'s {@link DatabaseConfig}; a
     * no-op when {@code properties} is empty. Called from both {@link #getConnection()}, so the
     * row count check's baseline/verify/override calls always see property state already
     * applied before they read it, and from {@link ExecutorOperationListener}, since a tester's
     * {@code onSetup()}/{@code onTearDown()} may resolve their own connection independently of
     * {@link #getConnection()}'s memoized one.
     *
     * @param connection The connection whose {@link DatabaseConfig} to apply the values to.
     * @param properties The {@code @DbUnitProperty} values; empty applies none.
     */
    static void applyProperties(final IDatabaseConnection connection,
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
                testScopedConnection.release();
            } catch (final Exception closeFailure)
            {
                primaryFailure.addSuppressed(closeFailure);
            }
            throw primaryFailure;
        }
        testScopedConnection.release();
    }
}
