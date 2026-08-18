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
import java.util.Properties;
import java.util.function.Supplier;

import org.dbunit.DatabaseUnitException;
import org.dbunit.DefaultOperationListener;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.RowCountChecker;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives one test's dbUnit lifecycle from a resolved {@link AnnotatedTestConfiguration}: the
 * simple setup/teardown path when {@link AnnotatedTestConfiguration#isExpected()} is false, or
 * the {@link PrepAndExpectedTestCase} prep/expected path when it is true.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by a binding such as
 * {@code DbUnitExtension}, which resolves the {@link IDatabaseTester} and any injected
 * {@link PrepAndExpectedTestCase} - field discovery is binding-specific - and hands them here
 * already resolved.
 *
 * <p>The row count check (see {@code org.dbunit.database.rowcount.RowCountCheck}) wraps only
 * the simple path: the prep/expected path already has its own, via
 * {@link DefaultPrepAndExpectedTestCase#preTest()} and {@code cleanupData()} - wrapping it a
 * second time here would just repeat the same table counts.
 *
 * <p>The connection {@link IDatabaseTester#getConnection()} returns for the row count check and
 * for parameter injection is memoized by {@link #getConnection()} and closed in
 * {@link #afterTest(boolean)} when {@code @DbUnitConfig#closeConnectionAfterTest()} (default
 * {@code true}) allows it - the same flag {@link DefaultPrepAndExpectedTestCase} already uses
 * for the connection it resolves on its own - and the tester's own {@link IOperationListener}
 * is not {@link IOperationListener#NO_OP_OPERATION_LISTENER} (nor an
 * {@code ExecutorOperationListener} wrapping it), the established signal a tester's
 * connection is shared beyond this one test - e.g. a {@code JdbcDatabaseTester} built with a
 * {@link org.dbunit.database.CachingConnectionProvider}, or a {@code DefaultDatabaseTester}
 * built from one fixed, externally-owned connection - so this executor leaves closing it to
 * that connection's actual owner. Set {@code closeConnectionAfterTest} to {@code false}
 * explicitly for a shared connection protected by some other, non-{@code NO_OP} listener.
 *
 * <p>That memoized connection is also protected from a premature close by the tester's
 * <em>own</em> setup/teardown machinery: for a tester whose {@link IDatabaseTester#getConnection()}
 * returns the same connection object on every call (e.g. the two examples above) and carries no
 * listener of its own, {@code onSetup()} would otherwise install a plain
 * {@code DefaultOperationListener} that closes the connection the instant setup finishes - the
 * same object this executor is still holding onto for the row count check's later verify, or for
 * a parameter injected into the test method. {@link #installOperationListener()} always wraps
 * the tester's listener in an {@code ExecutorOperationListener} that recognizes this executor's
 * own memoized connection by identity and lets only {@link #afterTest(boolean)} decide when to
 * close it, while still forwarding a close notification for any other connection object exactly
 * as before - so a tester that hands out a fresh connection per call (e.g. a plain
 * {@code JdbcDatabaseTester}) still has each of those closed right after its own operation, same
 * as always.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class AnnotatedTestExecutor {
    private static final Logger log = LoggerFactory.getLogger(AnnotatedTestExecutor.class);

    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final RowCountChecker rowCountChecker = new RowCountChecker();

    private PrepAndExpectedTestCase prepAndExpectedTestCase;
    private IDatabaseConnection resolvedConnection;
    private boolean connectionResolved;

    /**
     * Creates an executor for one test.
     *
     * @param configuration the resolved configuration to execute.
     * @param tester the tester to drive the simple path with, or to construct a
     *            {@link PrepAndExpectedTestCase} around when {@code prepAndExpectedTestCase}
     *            is {@code null} and the prep/expected path is configured.
     * @param prepAndExpectedTestCase an already-injected test case to drive instead of
     *            constructing one, or {@code null} to have this executor construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     */
    public AnnotatedTestExecutor(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester,
            final PrepAndExpectedTestCase prepAndExpectedTestCase) {
        this.configuration = configuration;
        this.tester = tester;
        this.prepAndExpectedTestCase = prepAndExpectedTestCase;
        installOperationListener();
    }

    /**
     * Returns the tester this executor drives, so a binding can inject it as a parameter
     * without resolving a second, independent instance.
     *
     * @return the tester passed to the constructor.
     */
    public IDatabaseTester getTester() {
        return tester;
    }

    /**
     * Returns the {@link PrepAndExpectedTestCase} this executor drives, or {@code null} when
     * {@link AnnotatedTestConfiguration#isExpected()} is false, or it is true but
     * {@link #beforeTest()} has not run yet - a binding resolving a parameter before
     * {@code beforeTest()} runs (e.g. a {@code @BeforeEach} parameter) sees {@code null} unless
     * one was already injected through the constructor.
     *
     * @return the test case this executor drives, or {@code null} if none exists yet.
     */
    public PrepAndExpectedTestCase getPrepAndExpectedTestCase() {
        return prepAndExpectedTestCase;
    }

    /**
     * Returns the tester's connection, resolved at most once per test and reused by every
     * caller within this executor instead of each asking the tester independently - the row
     * count check's own baseline/verify/override calls, and a binding injecting an
     * {@code IDatabaseConnection}/{@code Connection} parameter. For a tester with no
     * connection caching of its own (e.g. a plain {@code JdbcDatabaseTester}), asking
     * independently would otherwise open one new physical connection per call. Closed in
     * {@link #afterTest(boolean)} - see the class Javadoc.
     *
     * @return the tester's connection, or {@code null} if the tester has none to offer (e.g.
     *         a test double).
     * @throws Exception if resolving the connection fails.
     */
    public IDatabaseConnection getConnection() throws Exception {
        if (!connectionResolved) {
            resolvedConnection = tester.getConnection();
            connectionResolved = true;
            if (resolvedConnection != null) {
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
    private IDatabaseConnection peekResolvedConnection() {
        return connectionResolved ? resolvedConnection : null;
    }

    /**
     * Always wraps the tester's operation listener in an {@link ExecutorOperationListener}, so
     * that regardless of whether {@code @DbUnitProperty} is configured, this executor's own
     * memoized connection (see the class Javadoc) is protected from a premature close by the
     * tester's own setup/teardown machinery.
     */
    private void installOperationListener() {
        final IOperationListener delegate = unwrapExistingDelegate();
        tester.setOperationListener(new ExecutorOperationListener(
                configuration.getDatabaseConfigProperties(), this::peekResolvedConnection,
                delegate));
    }

    /**
     * Returns the listener a newly-installed {@link ExecutorOperationListener} should delegate
     * to: when the tester already carries one - e.g. a shared static tester field that an
     * earlier test in the same class already wrapped - that wrapper's own delegate, so
     * constructing one executor per test does not nest one wrapper layer per test; otherwise
     * the tester's existing listener as-is, or a fresh {@link DefaultOperationListener} when it
     * had none.
     */
    private IOperationListener unwrapExistingDelegate() {
        final IOperationListener existingListener = tester.getOperationListener();
        if (existingListener instanceof ExecutorOperationListener) {
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
            final Properties properties) {
        if (properties.isEmpty()) {
            return;
        }
        try {
            connection.getConfig().setPropertiesByString(properties);
        } catch (final DatabaseUnitException e) {
            throw new IllegalStateException("Failed to apply @DbUnitProperty values.", e);
        }
    }

    /**
     * Runs every before-test step: for the simple path, applies the prep dataset (if any) and
     * the setup operation, then calls {@code onSetup()}; for the prep/expected path, resolves
     * or constructs the {@link PrepAndExpectedTestCase} and calls {@code configureTest()} then
     * {@code preTest()}.
     *
     * @throws Exception if any step fails.
     */
    public void beforeTest() throws Exception {
        if (configuration.isExpected()) {
            beforeExpectedTest();
        } else {
            captureRowCountBaseline();
            beforeSimpleTest();
        }
    }

    private void beforeSimpleTest() throws Exception {
        if (configuration.getPrepDataFiles().length > 0) {
            tester.setDataSet(
                    loadCombined(configuration.getDataFileLoader(), configuration.getPrepDataFiles()));
            tester.setSetUpOperation(configuration.getSetUpOperation());
        } else {
            log.debug("No @DbUnitPrep data files declared; leaving the tester's dataset and"
                    + " setup operation untouched.");
        }
        tester.onSetup();
    }

    private void beforeExpectedTest() throws Exception {
        if (prepAndExpectedTestCase == null) {
            prepAndExpectedTestCase = newPrepAndExpectedTestCase();
        }
        applyFailureHandler();
        prepAndExpectedTestCase.configureTest(configuration.getVerifyTableDefinitions(),
                configuration.getPrepDataFiles(), configuration.getExpectedDataFiles());
        prepAndExpectedTestCase.preTest();
    }

    /**
     * Applies {@code @DbUnitConfig.failureHandler()} to {@link #prepAndExpectedTestCase}, when
     * set. A no-op when not set, or when the test case is not a
     * {@link DefaultPrepAndExpectedTestCase}, since that is the only implementation exposing a
     * setter for it.
     */
    private void applyFailureHandler() {
        if (configuration.getFailureHandler() != null
                && prepAndExpectedTestCase instanceof DefaultPrepAndExpectedTestCase) {
            ((DefaultPrepAndExpectedTestCase) prepAndExpectedTestCase)
                    .setFailureHandler(configuration.getFailureHandler());
        }
    }

    private PrepAndExpectedTestCase newPrepAndExpectedTestCase() throws Exception {
        final Class<? extends PrepAndExpectedTestCase> testCaseClass =
                configuration.getPrepAndExpectedTestCaseClass();
        final Constructor<? extends PrepAndExpectedTestCase> constructor;
        try {
            constructor = testCaseClass.getDeclaredConstructor(DataFileLoader.class,
                    IDatabaseTester.class, boolean.class);
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " has no (DataFileLoader, IDatabaseTester,"
                    + " boolean) constructor.", e);
        }
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(configuration.getDataFileLoader(), tester,
                    configuration.isCloseConnectionAfterTest());
        } catch (final InvocationTargetException e) {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " threw from its (DataFileLoader,"
                    + " IDatabaseTester, boolean) constructor.", e.getCause());
        }
    }

    /**
     * Runs every after-test step: for the simple path, applies the teardown operation (when
     * {@code @DbUnitTearDown} was declared) then calls {@code onTearDown()}, then verifies the
     * row count check baseline unless {@code testFailed}; for the prep/expected path, calls
     * {@code postTest(!testFailed)}. Either way, closes the connection {@link #getConnection()}
     * memoized - if one was ever resolved - so a difference reported by the row count check
     * still leaves it closed. When both the step above and closing the connection fail, the
     * step's exception is the one thrown, with the close failure attached to it via
     * {@link Throwable#addSuppressed(Throwable)} rather than replacing it - a plain {@code
     * finally} block would otherwise let the close failure silently discard the more useful
     * diagnostic (e.g. which table the row count check found unexpectedly changed).
     *
     * @param testFailed whether the test method itself already threw; when {@code true},
     *            verification is skipped so a difference does not mask the real failure.
     * @throws Exception if any step fails.
     */
    public void afterTest(final boolean testFailed) throws Exception {
        try {
            if (configuration.isExpected()) {
                afterExpectedTest(testFailed);
            } else {
                afterSimpleTest(testFailed);
            }
        } catch (final Exception primaryFailure) {
            try {
                closeResolvedConnectionIfOwned();
            } catch (final Exception closeFailure) {
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
     *
     * <p>The already-closed check matters on the {@code @DbUnitExpected} path: when
     * {@code tester.getConnection()} returns the same connection object on every call (e.g. a
     * {@code DefaultDatabaseTester} built from one fixed connection), the connection this
     * memoizes for {@code @DbUnitRowCountCheck}'s override or a parameter injection is the same
     * one {@link DefaultPrepAndExpectedTestCase} independently resolves and closes for its own
     * setup/verify/cleanup steps - without this check, this method would close it a second time.
     *
     * @throws Exception if closing the connection fails.
     */
    private void closeResolvedConnectionIfOwned() throws Exception {
        if (connectionResolved && resolvedConnection != null
                && configuration.isCloseConnectionAfterTest()
                && !isNonClosingListener(tester.getOperationListener())
                && !resolvedConnection.getConnection().isClosed()) {
            resolvedConnection.close();
        }
    }

    /**
     * Returns whether {@code listener} - or, when it is an {@link ExecutorOperationListener}
     * installed by {@link #installOperationListener()}, the delegate it wraps - is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER}: the established, pre-existing signal
     * that a tester's connection is managed elsewhere and must not be closed by whatever is
     * driving the tester's lifecycle.
     */
    private static boolean isNonClosingListener(final IOperationListener listener) {
        if (listener == IOperationListener.NO_OP_OPERATION_LISTENER) {
            return true;
        }
        if (listener instanceof ExecutorOperationListener) {
            return isNonClosingListener(((ExecutorOperationListener) listener).delegate);
        }
        return false;
    }

    private void afterSimpleTest(final boolean testFailed) throws Exception {
        applyTearDownOperation();
        tester.onTearDown();
        if (!testFailed) {
            verifyRowCountUnchanged();
        }
    }

    /**
     * Applies {@code @DbUnitTearDown}'s operation to {@link #tester}, when declared.
     * {@code cleanupData()} reads its teardown operation from the tester lazily, at cleanup
     * time, so setting it any time before then - here, alongside the rest of the pre-test
     * configuration - is sufficient. A no-op when not declared, leaving whatever teardown
     * operation the tester already had (typically
     * {@link org.dbunit.operation.DatabaseOperation#NONE}) in effect.
     */
    private void applyTearDownOperation() {
        if (configuration.isTearDownDeclared()) {
            tester.setTearDownOperation(configuration.getTearDownOperation());
        }
    }

    private void afterExpectedTest(final boolean testFailed) throws Exception {
        if (prepAndExpectedTestCase == null) {
            return;
        }
        prepAndExpectedTestCase.postTest(!testFailed);
    }

    private void captureRowCountBaseline() throws Exception {
        final IDatabaseConnection connection = getConnection();
        if (connection == null) {
            return;
        }
        rowCountChecker.capture(connection);
    }

    private void verifyRowCountUnchanged() throws Exception {
        if (!rowCountChecker.hasBaseline()) {
            return;
        }
        final IDatabaseConnection connection = getConnection();
        if (connection == null) {
            return;
        }
        rowCountChecker.verify(connection);
    }

    private IDataSet loadCombined(final DataFileLoader dataFileLoader, final String[] paths)
            throws Exception {
        if (paths.length == 1) {
            return dataFileLoader.load(paths[0]);
        }
        final IDataSet[] dataSets = new IDataSet[paths.length];
        for (int i = 0; i < paths.length; i++) {
            dataSets[i] = dataFileLoader.load(paths[i]);
        }
        try {
            return new CompositeDataSet(dataSets, true, isCaseSensitiveTableNames());
        } catch (final DataSetException e) {
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
    private boolean isCaseSensitiveTableNames() throws Exception {
        final IDatabaseConnection connection = getConnection();
        return connection != null && connection.getConfig()
                .getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
    }

    /**
     * Applies {@code @DbUnitProperty} values to a connection's {@code DatabaseConfig} on
     * {@link #connectionRetrieved}, and shields the enclosing {@link AnnotatedTestExecutor}'s
     * own memoized connection - identified by {@code protectedConnection}, evaluated fresh on
     * every call since the memoized connection may not be resolved yet when this listener is
     * installed - from a close notification the tester's own setup/teardown machinery would
     * otherwise deliver for it; any other connection object still gets that notification
     * forwarded to {@code delegate} exactly as before, so a tester that hands out a fresh
     * connection per call is unaffected.
     */
    private static final class ExecutorOperationListener implements IOperationListener {
        private final Properties properties;
        private final Supplier<IDatabaseConnection> protectedConnection;
        private final IOperationListener delegate;

        private ExecutorOperationListener(final Properties properties,
                final Supplier<IDatabaseConnection> protectedConnection,
                final IOperationListener delegate) {
            this.properties = properties;
            this.protectedConnection = protectedConnection;
            this.delegate = delegate;
        }

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection) {
            applyProperties(connection, properties);
            delegate.connectionRetrieved(connection);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection) {
            if (connection == protectedConnection.get()) {
                return;
            }
            delegate.operationSetUpFinished(connection);
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection) {
            if (connection == protectedConnection.get()) {
                return;
            }
            delegate.operationTearDownFinished(connection);
        }
    }
}
