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

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * The prep/expected path of one test's dbUnit lifecycle, driven by
 * {@link AnnotatedTestExecutor} when {@link AnnotatedTestConfiguration#isExpected()} is true:
 * resolve or construct the {@link PrepAndExpectedTestCase}, push the {@code @DbUnitConfig}
 * values it can only receive through its own API onto it (via {@link InjectedTestCaseConfigurer}),
 * set the setup/teardown operations, then run its {@code configureTest()}/{@code preTest()}
 * before the test and {@code postTest()} after.
 *
 * <p>The row count check on this path is the {@link PrepAndExpectedTestCase}'s own -
 * {@code DefaultPrepAndExpectedTestCase} captures and verifies it around its own steps - so
 * this class never touches it; {@code @DbUnitRowCountCheck} reaches it as an override through
 * {@link InjectedTestCaseConfigurer}.
 *
 * <p>The dataset and setup/teardown operations {@link #tester} carries coming into the test are
 * snapshotted at the start and restored at the end (see {@link TesterStateSnapshot}), so a
 * tester shared across methods - a {@code static @DbUnitTester} field, or one under
 * {@code @TestInstance(Lifecycle.PER_CLASS)} - carries no per-method state onto the next
 * method.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class ExpectedLifecycle
{
    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final boolean testCaseInjected;

    private PrepAndExpectedTestCase testCase;
    private boolean configured;
    private TesterStateSnapshot incomingState;

    /**
     * Creates the prep/expected lifecycle for one test.
     *
     * @param configuration The resolved configuration.
     * @param tester The tester to construct a {@link PrepAndExpectedTestCase} around when one
     *            is not injected, and whose setup/teardown operations to set.
     * @param injectedTestCase An already-injected {@code @DbUnitTestCase} instance to drive, or
     *            {@code null} to construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     */
    ExpectedLifecycle(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester, final PrepAndExpectedTestCase injectedTestCase)
    {
        this.configuration = configuration;
        this.tester = tester;
        this.testCase = injectedTestCase;
        this.testCaseInjected = injectedTestCase != null;
    }

    /**
     * Returns the {@link PrepAndExpectedTestCase} this lifecycle drives, or {@code null} when
     * nothing has constructed one yet - a binding resolving a parameter before {@link #before()}
     * runs sees {@code null} unless one was injected through the constructor, or an earlier
     * parameter resolution this same test already triggered {@link #ensureTestCase()}.
     *
     * @return The test case, or {@code null} if none exists yet.
     */
    PrepAndExpectedTestCase getPrepAndExpectedTestCase()
    {
        return testCase;
    }

    /**
     * Returns {@link #testCase}, constructing it via {@link #newTestCase()} first when it does
     * not already exist (no {@code @DbUnitTestCase} field supplied one, and nothing has
     * constructed one yet). Safe to call more than once - construction only happens once - and
     * safe to call before {@link #before()} does, e.g. from a binding resolving an early
     * {@code @BeforeEach} {@code Connection} parameter through
     * {@link PrepAndExpectedTestCase#getReusableConnection()}, since construction has no side
     * effect beyond reflectively instantiating the instance.
     *
     * @return The test case this lifecycle drives.
     * @throws Exception If constructing it fails.
     */
    PrepAndExpectedTestCase ensureTestCase() throws Exception
    {
        if (testCase == null)
        {
            testCase = newTestCase();
        }
        return testCase;
    }

    /**
     * Returns whether the connection came from an injected {@code @DbUnitTestCase} instance's
     * {@link PrepAndExpectedTestCase#getReusableConnection()} for a {@code @BeforeEach}
     * parameter, and {@link #before()} then failed before {@code configureTest()} - so
     * {@code postTest()}/{@code cleanupData()} never ran to close that connection the test
     * case's own way. It is the {@code borrowingLifecycleRan} input to
     * {@link AnnotatedTestExecutor}'s {@link org.dbunit.database.connection.ConnectionOwnership}:
     * {@code false} here means the executor's
     * {@link org.dbunit.database.connection.TestScopedConnection#release()} leaves the
     * connection alone, so a reused instance (e.g. a {@code static @DbUnitTestCase} field, or
     * any instance field under {@code @TestInstance(PER_CLASS)}) is not stranded holding a
     * closed connection for the next test method. The instance keeps ownership; its own next
     * lifecycle run, or the caller discarding it, releases the connection.
     *
     * <p>Only for an <em>injected</em> instance: a test case constructed here is single-use
     * and discarded after this test, so the executor still closes its connection rather than
     * leak it.
     *
     * @return True when a reused injected instance still owns the connection.
     */
    boolean isConnectionOwnedByUnconfiguredInjectedTestCase()
    {
        return testCaseInjected && !configured;
    }

    /**
     * Resolves or constructs the {@link PrepAndExpectedTestCase}, configures it, and runs its
     * {@code configureTest()} then {@code preTest()}.
     *
     * @throws Exception If any step fails.
     */
    void before() throws Exception
    {
        ensureTestCase();
        incomingState = TesterStateSnapshot.capture(tester);
        new InjectedTestCaseConfigurer(configuration, testCase).applyAll();
        applySetUpOperation();
        applyTearDownOperation();
        testCase.configureTest(configuration.getVerifyTableDefinitions(),
                configuration.getPrepDataFiles(), configuration.getExpectedDataFiles());
        configured = true;
        testCase.preTest();
    }

    /**
     * Calls {@code postTest(!testFailed)} on the test case, unless {@link #before()} never ran
     * or failed before {@code configureTest()} completed - in which case the instance, when
     * non-null, is either uninitialized or (for one reused across tests) still holding an
     * earlier test's prep/expected state, and {@code postTest()} would run cleanup against that
     * stale state instead of nothing. Either way the incoming tester state is restored
     * afterward - on a failure, with any restore failure attached as suppressed rather than
     * masking the primary.
     *
     * @param testFailed Whether the test method itself already threw.
     * @throws Exception If {@code postTest()} fails.
     */
    void after(final boolean testFailed) throws Exception
    {
        try
        {
            if (configured)
            {
                testCase.postTest(!testFailed);
            }
        } catch (final Throwable primaryFailure)
        {
            TesterStateSnapshot.restoreSuppressing(tester, incomingState, primaryFailure);
            throw primaryFailure;
        }
        TesterStateSnapshot.restore(tester, incomingState);
    }

    /**
     * Applies {@code @DbUnitSetup}'s operation to {@link #tester}, unconditionally:
     * {@link AnnotatedTestConfiguration#getSetUpOperation()} already defaults to
     * {@link org.dbunit.operation.DatabaseOperation#CLEAN_INSERT} - the same default the
     * setup/teardown path applies whenever it has a dataset to set up - and {@code setupData()}
     * always runs against a real, if possibly empty, prep dataset on this path, so there is no
     * null-dataset case to avoid touching.
     */
    private void applySetUpOperation()
    {
        tester.setSetUpOperation(configuration.getSetUpOperation());
    }

    /**
     * Applies {@code @DbUnitTearDown}'s operation to {@link #tester}, unconditionally:
     * {@code cleanupData()} always runs on this path and reads its teardown operation from the
     * tester lazily, so the default of {@link org.dbunit.operation.DatabaseOperation#NONE}
     * still has to be set explicitly to override whatever a reused test case's tester already
     * carries. Whatever this applies is undone by {@link #after(boolean)} restoring
     * {@link #incomingState} once the test finishes, so a shared tester carries no per-method
     * operation forward.
     */
    private void applyTearDownOperation()
    {
        tester.setTearDownOperation(configuration.getTearDownOperation());
    }

    private PrepAndExpectedTestCase newTestCase() throws Exception
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
}
