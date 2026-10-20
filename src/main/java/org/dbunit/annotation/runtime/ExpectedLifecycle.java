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

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestData;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * The prep/expected path of one test's dbUnit lifecycle, driven by
 * {@link AnnotatedTestExecutor} when {@link AnnotatedTestConfiguration#isExpected()} is true:
 * resolve or construct the {@link PrepAndExpectedTestCase}, apply the values the test declares
 * that a {@link DefaultPrepAndExpectedTestCase} can only receive through its own API (via
 * {@link TestCaseConfigurer}), set the setup/teardown operations the annotations declare, then
 * run its {@code configureTest()}/{@code preTest()} before the test and {@code postTest()}
 * after. An injected test case is used as its owner built it, with its tester and so its
 * operations as they are: see {@link InjectedTestCaseDeclarationCheck}.
 *
 * <p>The row count check on this path is the {@link PrepAndExpectedTestCase}'s own -
 * {@code DefaultPrepAndExpectedTestCase} captures and verifies it around its own steps - so
 * this class never touches it; {@code @DbUnitRowCountCheck} reaches it as an override through
 * {@link TestCaseConfigurer}.
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

    private final boolean injected;

    private PrepAndExpectedTestCase testCase;
    private boolean configured;
    private TesterStateSnapshot incomingState;
    private TestCaseRestoration testCaseRestoration;

    /**
     * Creates the prep/expected lifecycle for one test.
     *
     * @param configuration The resolved configuration.
     * @param tester The tester to construct a {@link PrepAndExpectedTestCase} around when one
     *            is not injected, and whose setup/teardown operations to set; {@code null} for
     *            an injected test case that tells dbUnit of none.
     * @param injectedTestCase An already-injected {@code @DbUnitTestCase} instance to drive, or
     *            {@code null} to construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     * @throws IllegalStateException If the test declares anything the injected test case cannot
     *             be given - see {@link InjectedTestCaseDeclarationCheck}.
     */
    ExpectedLifecycle(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester, final PrepAndExpectedTestCase injectedTestCase)
    {
        if (injectedTestCase != null)
        {
            new InjectedTestCaseDeclarationCheck(configuration).requireSupportedBy(
                    injectedTestCase);
        }
        this.configuration = configuration;
        this.tester = tester;
        this.injected = injectedTestCase != null;
        this.testCase = injectedTestCase;
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
     * {@code @BeforeEach} {@code IDatabaseConnection} parameter through
     * {@link DefaultPrepAndExpectedTestCase#getReusableConnection()}, since construction has no side
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
     * Returns {@link #testCase} after applying the declared values to it, once - see
     * {@link TestCaseConfigurer} - so a connection it hands out already carries them.
     * {@link #before()} calls this, and so does a binding resolving an early {@code @BeforeEach}
     * {@code IDatabaseConnection} parameter through {@link DefaultPrepAndExpectedTestCase#getReusableConnection()}
     * before {@code before()} runs: the connection is acquired with the test case's properties
     * in force at that moment. Whatever this applies is undone by {@link #after(boolean)}, or by
     * {@link #undoConfiguration()} when {@code after()} never runs. An injected test case is
     * first checked for the loader the test's files need, see
     * {@link InjectedTestCaseDeclarationCheck#requireDataFileLoaderForTheFiles(PrepAndExpectedTestCase)}.
     *
     * @return The configured test case this lifecycle drives.
     * @throws IllegalStateException If an injected test case lacks the loader the test's files
     *             need.
     * @throws Exception If constructing or configuring it fails.
     */
    PrepAndExpectedTestCase ensureConfiguredTestCase() throws Exception
    {
        ensureTestCase();
        if (testCaseRestoration == null && testCase instanceof DefaultPrepAndExpectedTestCase)
        {
            if (injected)
            {
                new InjectedTestCaseDeclarationCheck(configuration)
                        .requireDataFileLoaderForTheFiles(testCase);
            }
            testCaseRestoration = new TestCaseConfigurer(configuration,
                    (DefaultPrepAndExpectedTestCase) testCase).applyAll();
        }
        return testCase;
    }

    /**
     * Returns the connection the test case shares across its lifecycle, configuring it first as
     * {@link #ensureConfiguredTestCase()} does.
     *
     * @return The test case's connection.
     * @throws IllegalStateException If the test case is not a
     *             {@link DefaultPrepAndExpectedTestCase}, which tells dbUnit of no connection.
     * @throws Exception If constructing or configuring the test case, or resolving the
     *             connection, fails.
     */
    IDatabaseConnection getReusableConnection() throws Exception
    {
        final PrepAndExpectedTestCase configured = ensureConfiguredTestCase();
        if (configured instanceof DefaultPrepAndExpectedTestCase)
        {
            return ((DefaultPrepAndExpectedTestCase) configured).getReusableConnection();
        }
        throw new IllegalStateException("The @DbUnitTestCase field's test case "
                + configured.getClass().getName() + " is not a DefaultPrepAndExpectedTestCase,"
                + " so dbUnit has no connection of it to give a test method or to close.");
    }

    /**
     * Puts back what {@link #ensureConfiguredTestCase()} applied to the test case, including the
     * {@code DatabaseConfig} values on a connection it kept. A no-op when nothing was applied or
     * it was already undone. Called by {@link #after(boolean)}, and by the executor when the
     * after-test callback never runs, for example a {@code @BeforeEach} that fails.
     */
    void undoConfiguration()
    {
        final TestCaseRestoration restoration = testCaseRestoration;
        testCaseRestoration = null;
        TestCaseRestoration.undoIfPresent(restoration);
    }

    /**
     * Resolves or constructs the {@link PrepAndExpectedTestCase}, configures it, and runs its
     * {@code configureTest()} then {@code preTest()}. The setup and teardown operations the
     * annotations declare are set on the tester of a test case this class constructed only: an
     * injected test case runs with its tester as its owner configured it.
     *
     * @throws Exception If any step fails.
     */
    void before() throws Exception
    {
        ensureTestCase();
        if (tester != null)
        {
            incomingState = TesterStateSnapshot.capture(tester);
        }
        ensureConfiguredTestCase();
        if (!injected)
        {
            applySetUpOperation();
            applyTearDownOperation();
        }
        configureTestCase();
        configured = true;
        testCase.preTest();
    }

    /**
     * Configures {@link #testCase} with the files and the tables to verify the annotations
     * declare. When they declare no tables, a {@link DefaultPrepAndExpectedTestCase} takes them
     * from the expected datasets it loads anyway, with the rules of the class-level catalogs, with
     * {@link PrepAndExpectedTestData#verifyingExpectedTables(String[], String[],
     * VerifyTableDefinition[])}, instead of this class parsing the expected files a first time
     * just to list their tables; any other test case is given the tables derived here.
     */
    private void configureTestCase() throws Exception
    {
        final String[] prepDataFiles = configuration.getPrepDataFiles();
        final String[] expectedDataFiles = configuration.getExpectedDataFiles();
        if (configuration.derivesVerifyTableDefinitions()
                && testCase instanceof DefaultPrepAndExpectedTestCase)
        {
            testCase.configureTest(PrepAndExpectedTestData.verifyingExpectedTables(prepDataFiles,
                    expectedDataFiles, configuration.getCatalogDefinitions()));
            return;
        }
        testCase.configureTest(configuration.getVerifyTableDefinitions(effectiveDataFileLoader()),
                prepDataFiles, expectedDataFiles);
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
            final TestCaseRestoration restoration = testCaseRestoration;
            testCaseRestoration = null;
            TestCaseRestoration.undoSuppressing(restoration, primaryFailure);
            throw primaryFailure;
        }
        try
        {
            TesterStateSnapshot.restore(tester, incomingState);
        } finally
        {
            undoConfiguration();
        }
    }

    /**
     * Applies {@code @DbUnitSetUpOperation}'s operation to {@link #tester} when the annotations
     * declare one - see {@link AnnotatedTestConfiguration#isSetupDeclared()}. Otherwise the
     * tester's own operation stays, even with {@code @DbUnitPrep} files, such as one a
     * {@code @BeforeEach} method set, which can be one {@code DbUnitOperation} cannot express.
     * Whatever this applies is undone by {@link #after(boolean)} restoring
     * {@link #incomingState} once the test finishes, so a shared tester carries no per-method
     * operation forward.
     */
    private void applySetUpOperation()
    {
        if (tester != null && configuration.isSetupDeclared())
        {
            tester.setSetUpOperation(configuration.getSetUpOperation());
        }
    }

    /**
     * Applies {@code @DbUnitTearDownOperation}'s operation to {@link #tester} when the annotations
     * declare one, as the setup/teardown path does. Otherwise the tester's own operation stays,
     * which {@code cleanupData()} reads from the tester lazily. Whatever this applies is undone
     * by {@link #after(boolean)} restoring {@link #incomingState} once the test finishes, so a
     * shared tester carries no per-method operation forward.
     */
    private void applyTearDownOperation()
    {
        if (tester != null && configuration.isTearDownDeclared())
        {
            tester.setTearDownOperation(configuration.getTearDownOperation());
        }
    }

    /**
     * Returns the loader {@link #testCase} will load the expected datasets with: its own, or
     * {@code @DbUnitConfig}'s when the test case does not report one.
     */
    private DataFileLoader effectiveDataFileLoader()
    {
        if (testCase instanceof DefaultPrepAndExpectedTestCase)
        {
            final DataFileLoader testCaseLoader =
                    ((DefaultPrepAndExpectedTestCase) testCase).getDataFileLoader();
            if (testCaseLoader != null)
            {
                return testCaseLoader;
            }
        }
        return configuration.getDataFileLoader();
    }

    private PrepAndExpectedTestCase newTestCase() throws Exception
    {
        final Class<? extends DefaultPrepAndExpectedTestCase> testCaseClass =
                configuration.getPrepAndExpectedTestCaseClass();
        final Constructor<? extends DefaultPrepAndExpectedTestCase> constructor;
        try
        {
            constructor = testCaseClass.getDeclaredConstructor(DataFileLoader.class,
                    IDatabaseTester.class);
        } catch (final NoSuchMethodException e)
        {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " has no (DataFileLoader, IDatabaseTester)"
                    + " constructor.", e);
        }
        constructor.setAccessible(true);
        try
        {
            return constructor.newInstance(configuration.getDataFileLoader(), tester);
        } catch (final InvocationTargetException e)
        {
            throw new IllegalStateException("DbUnitConfig.prepAndExpectedTestCase class "
                    + testCaseClass.getName() + " threw from its (DataFileLoader,"
                    + " IDatabaseTester) constructor.", e.getCause());
        }
    }
}
