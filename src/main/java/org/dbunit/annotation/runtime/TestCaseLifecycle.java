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
import java.util.HashSet;
import java.util.Set;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestData;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.RowCountDifference;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The lifecycle of one annotation-driven test, run by {@link AnnotatedTestExecutor} for every
 * test that opted into the {@code org.dbunit.annotation} family, with or without
 * {@code @DbUnitExpected}: resolve or construct the {@link PrepAndExpectedTestCase}, apply the
 * values the test declares that a {@link DefaultPrepAndExpectedTestCase} can only receive
 * through its own API (via {@link TestCaseConfigurer}), give it the setup/teardown operations
 * the annotations declare, then run its {@code configureTest()}/{@code preTest()} before the
 * test and {@code postTest()} after. A test without {@code @DbUnitExpected} is the same
 * lifecycle with no expected files and no tables to verify. An injected test case is used as its
 * owner built it, with its tester and so its operations as they are: see
 * {@link InjectedTestCaseDeclarationCheck}.
 *
 * <p>The tester is only read, never written: the test case takes its connection from it, and its
 * setup and teardown operations unless the annotations gave the test case ones of its own. Its
 * dataset is never read: a test sets up the {@code @DbUnitPrep} files it declares, and one that
 * declares none sets up no data, as an injected test case does, whatever dataset the tester
 * holds, such as one a {@code @BeforeEach} method gave it in the 3.5.0 style or an earlier test
 * left on a tester shared across tests. {@link #before()} warns of a dataset it ignores. So a
 * tester shared across tests - a {@code static @DbUnitTester} field, a field under
 * {@code @TestInstance(Lifecycle.PER_CLASS)}, a Spring singleton bean - carries no per-test
 * state onto the next test. The tester's own {@code onSetup()}/{@code onTearDown()} do not run,
 * so an override of either in a tester subclass has no effect on an annotated test.
 *
 * <p>The row count check is the {@link PrepAndExpectedTestCase}'s own -
 * {@code DefaultPrepAndExpectedTestCase} captures and verifies it around its own steps - so this
 * class never touches it; {@code @DbUnitRowCountCheck} reaches it as an override through
 * {@link TestCaseConfigurer}.
 *
 * <p>An injected {@link DefaultPrepAndExpectedTestCase} is driven by this class and by nothing
 * else. A test method that configures it again, with {@code runTest()} or
 * {@code configureTest()}, would run the setup, the verification and the cleanup a second
 * time on the datasets of its own call, against a database that call already cleaned, so
 * {@link #after(boolean)} cleans up without verifying and fails the test instead. It notices
 * by the two datasets, which every {@code configureTest()} replaces and which
 * {@link #before()} remembers once it has set the test case up, so a test method that never
 * ran, because a step of {@code before()} failed, is never blamed.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class TestCaseLifecycle
{
    private static final Logger log = LoggerFactory.getLogger(TestCaseLifecycle.class);

    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;

    private final boolean injected;

    private PrepAndExpectedTestCase testCase;
    private boolean configured;
    private IDataSet prepDatasetOfTheTest;
    private IDataSet expectedDatasetOfTheTest;
    private boolean datasetsRemembered;
    private TestCaseRestoration testCaseRestoration;

    /**
     * Creates the lifecycle for one test.
     *
     * @param configuration The resolved configuration.
     * @param tester The tester to construct a {@link PrepAndExpectedTestCase} around when one is
     *            not injected, and to check for a dataset to ignore when no prep files are
     *            declared; {@code null} for an injected test case that tells dbUnit of none.
     * @param injectedTestCase An already-injected {@code @DbUnitTestCase} instance to drive, or
     *            {@code null} to construct one from
     *            {@link AnnotatedTestConfiguration#getPrepAndExpectedTestCaseClass()}.
     * @throws IllegalStateException If the test declares anything the injected test case cannot
     *             be given - see {@link InjectedTestCaseDeclarationCheck}.
     */
    TestCaseLifecycle(final AnnotatedTestConfiguration configuration,
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
     * {@link #undoConfiguration()} when {@code after()} never runs. An injected test case is not
     * checked for its loader here, as a connection needs none: {@link #before()} checks it
     * before the step that needs it.
     *
     * @return The configured test case this lifecycle drives.
     * @throws Exception If constructing or configuring it fails.
     */
    PrepAndExpectedTestCase ensureConfiguredTestCase() throws Exception
    {
        ensureTestCase();
        if (testCaseRestoration == null && testCase instanceof DefaultPrepAndExpectedTestCase)
        {
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
     * annotations declare are given to a test case this class constructed only: an injected test
     * case runs as its owner built it, so it must have the loader its {@code configureTest()}
     * needs by now, see
     * {@link InjectedTestCaseDeclarationCheck#requireDataFileLoader(PrepAndExpectedTestCase)}.
     * The check runs directly before that step, not when a connection is resolved, so under a
     * binding that calls this method after the {@code @BeforeEach} methods, one of them can
     * still set the loader.
     *
     * @throws IllegalStateException If an injected test case has no loader.
     * @throws Exception If any step fails.
     */
    void before() throws Exception
    {
        ensureConfiguredTestCase();
        if (injected)
        {
            new InjectedTestCaseDeclarationCheck(configuration).requireDataFileLoader(testCase);
        } else
        {
            applyDeclaredOperations();
        }
        configureTestCase();
        configured = true;
        if (!injected)
        {
            warnIfTestersDataSetIsIgnored();
            warnIfDeclaredOperationsHaveNothingToActOn();
        }
        testCase.preTest();
        rememberTheDatasetsOfTheInjectedTestCase();
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
     * stale state instead of nothing. Either way what the configurer applied is undone
     * afterward - on a failure, with any undo failure attached as suppressed rather than
     * masking the primary.
     *
     * <p>A row count difference gets a hint when it is the classic row count check +
     * {@code @DbUnitPrep} + no-teardown shape, see {@link #withHintForUnclearedPrep}.
     *
     * <p>An injected {@link DefaultPrepAndExpectedTestCase} that the test method configured
     * again is cleaned up without verifying, and the test fails: see
     * {@link #failIfTheTestMethodConfiguredTheInjectedTestCaseAgain()}.
     *
     * @param testFailed Whether the test method itself already threw.
     * @throws IllegalStateException If the test method configured an injected
     *             {@link DefaultPrepAndExpectedTestCase} again.
     * @throws Exception If {@code postTest()} fails.
     */
    void after(final boolean testFailed) throws Exception
    {
        try
        {
            if (configured)
            {
                failIfTheTestMethodConfiguredTheInjectedTestCaseAgain();
                testCase.postTest(!testFailed);
            }
        } catch (final UnexpectedRowCountException rowCountFailure)
        {
            final UnexpectedRowCountException hinted = withHintForUnclearedPrep(rowCountFailure);
            undoConfigurationSuppressing(hinted);
            throw hinted;
        } catch (final Throwable primaryFailure)
        {
            undoConfigurationSuppressing(primaryFailure);
            throw primaryFailure;
        }
        undoConfiguration();
    }

    /**
     * Remembers the prep and the expected dataset that {@code configureTest()} gave an injected
     * {@link DefaultPrepAndExpectedTestCase}, which returns the very objects it keeps, so
     * {@link #failIfTheTestMethodConfiguredTheInjectedTestCaseAgain()} can tell whether the test
     * method configured it again. Called when {@link #before()} has set the test case up, as the
     * test method does not run otherwise, and a step that failed may have reset the datasets.
     * Another test case, such as a hand-written one, may build a new dataset for every call, so
     * it is not watched; nor is one this class constructed, which nothing but this class can
     * reach.
     */
    private void rememberTheDatasetsOfTheInjectedTestCase()
    {
        if (!watchesTheDatasetsOfTheTestCase())
        {
            return;
        }

        prepDatasetOfTheTest = testCase.getPrepDataset();
        expectedDatasetOfTheTest = testCase.getExpectedDataset();
        datasetsRemembered = true;
    }

    /**
     * Cleans up without verifying, and fails, when the test method configured the injected test
     * case again. {@code runTest()} and {@code configureTest()} replace both datasets of the
     * instance, so a dataset that is no longer the one {@link #before()} saw means the test
     * method drove the instance itself, after this class had set it up for the test. Its own
     * lifecycle then ran inside this one: verifying now would compare a database it already
     * cleaned up with the expectations of its call, and fail with a difference that is no fault
     * of the code under test. The cleanup runs once, so a test method that called
     * {@code configureTest()} and {@code preTest()} but not {@code postTest()} does not leave its
     * prep rows behind; a failure of it is attached to the one thrown.
     *
     * <p>A no-op unless {@link #before()} remembered the datasets.
     *
     * @throws IllegalStateException If the test method configured the test case again.
     */
    private void failIfTheTestMethodConfiguredTheInjectedTestCaseAgain()
    {
        if (!datasetsRemembered)
        {
            return;
        }

        final boolean prepDatasetReplaced = testCase.getPrepDataset() != prepDatasetOfTheTest;
        final boolean expectedDatasetReplaced =
                testCase.getExpectedDataset() != expectedDatasetOfTheTest;
        if (!prepDatasetReplaced && !expectedDatasetReplaced)
        {
            return;
        }

        final IllegalStateException misuse = new IllegalStateException(
                "The test method configured the @DbUnitTestCase instance of "
                        + testCase.getClass().getName() + " again, as runTest() and"
                        + " configureTest() do, after dbUnit had configured and set it up for"
                        + " this test, so two lifecycles would drive the instance. dbUnit"
                        + " cleaned up without verifying. Let dbUnit or the test"
                        + " method drive the instance, not both: remove @DbUnitTestCase from the"
                        + " field, and DbUnitExtension or DbUnitTestExecutionListener from the"
                        + " class, for a test method that drives it itself, or leave the calls"
                        + " out of a test that the annotations drive.");
        try
        {
            testCase.postTest(false);
        } catch (final Throwable cleanupFailure)
        {
            misuse.addSuppressed(cleanupFailure);
        }
        throw misuse;
    }

    private boolean watchesTheDatasetsOfTheTestCase()
    {
        return injected && testCase instanceof DefaultPrepAndExpectedTestCase;
    }

    private void undoConfigurationSuppressing(final Throwable primaryFailure)
    {
        final TestCaseRestoration restoration = testCaseRestoration;
        testCaseRestoration = null;
        TestCaseRestoration.undoSuppressing(restoration, primaryFailure);
    }

    /**
     * Gives the test case {@code @DbUnitSetUpOperation}'s operation and
     * {@code @DbUnitTearDownOperation}'s when the annotations declare them. Otherwise the test
     * case runs the tester's own, even with {@code @DbUnitPrep} files, such as one a
     * {@code @BeforeEach} method set, which can be one {@code DbUnitOperation} cannot express.
     * Nothing is written to the tester, so there is nothing to put back.
     */
    private void applyDeclaredOperations()
    {
        final DefaultPrepAndExpectedTestCase built = builtTestCase();
        if (configuration.isSetupDeclared())
        {
            built.setSetUpOperation(configuration.getSetUpOperation());
        }
        if (configuration.isTearDownDeclared())
        {
            built.setTearDownOperation(configuration.getTearDownOperation());
        }
    }

    /**
     * Warns when the test declares no {@code @DbUnitPrep} files and the tester holds a dataset,
     * such as one a {@code @BeforeEach} method gave it in the 3.5.0 style: an annotated test sets
     * up only the files it names and never reads a tester's dataset, so that one is ignored. A
     * test that names its files chose its dataset, so a dataset the tester happens to hold, which
     * a shared tester can keep from an earlier test, is not worth a warning then, so this method
     * does not ask the tester.
     *
     * <p>Asking the tester only feeds this warning, so a tester whose {@code getDataSet()} throws
     * a {@link RuntimeException}, as one that builds its dataset lazily from a file that is
     * missing or malformed does, does not fail the test: the failure is logged at debug level and
     * no warning is given.
     */
    private void warnIfTestersDataSetIsIgnored()
    {
        final boolean prepFilesDeclared = configuration.getPrepDataFiles().length > 0;
        if (prepFilesDeclared)
        {
            return;
        }

        final boolean testerHoldsDataSet = testerHoldsDataSet();
        if (!testerHoldsDataSet)
        {
            return;
        }

        log.warn("The tester holds a dataset, but this test declares no @DbUnitPrep files, so"
                + " dbUnit sets up no data and ignores it: an annotation-driven test sets up only"
                + " the files it names. Name the dataset's file with @DbUnitPrep.");
    }

    /**
     * Returns whether the tester holds a dataset: {@code false} when its {@code getDataSet()}
     * throws a {@link RuntimeException}, as it is then not known to hold one.
     */
    private boolean testerHoldsDataSet()
    {
        try
        {
            return tester.getDataSet() != null;
        } catch (final RuntimeException e)
        {
            log.debug("The tester's getDataSet() threw, so it is not known whether it holds a"
                    + " dataset this test ignores; not warning of one.", e);
            return false;
        }
    }

    /**
     * Warns when a declared, non-{@link DatabaseOperation#NONE} operation has no dataset to act
     * on: every such operation ({@code CLEAN_INSERT}, {@code INSERT}, {@code REFRESH},
     * {@code UPDATE}, {@code DELETE}, {@code DELETE_ALL}, {@code TRUNCATE_TABLE}) needs one, and
     * a bare {@code @DbUnitSetUpOperation} resolves to {@code CLEAN_INSERT}, so that is the
     * likely shape of the mistake on the setup side; a bare {@code @DbUnitTearDownOperation}
     * resolves to {@code NONE}, so on the teardown side reaching this warning means the
     * operation was set explicitly. The teardown operation also acts on the expected datasets.
     * The dataset a tester holds is not one to act on, see
     * {@link #warnIfTestersDataSetIsIgnored()}.
     *
     * <p>A warning rather than a failure: the operation runs against an empty dataset and
     * changes nothing, which is all a custom operation that sources its data elsewhere needs.
     */
    private void warnIfDeclaredOperationsHaveNothingToActOn()
    {
        final boolean prepFilesDeclared = configuration.getPrepDataFiles().length > 0;
        if (prepFilesDeclared)
        {
            return;
        }

        if (configuration.isSetupDeclared()
                && configuration.getSetUpOperation() != DatabaseOperation.NONE)
        {
            log.warn("@DbUnitSetUpOperation declares a non-NONE operation, but there is nothing"
                    + " for it to act on: no @DbUnitPrep data files were declared. Add"
                    + " @DbUnitPrep, or change the operation to DbUnitOperation.NONE.");
        }

        final boolean expectedFilesDeclared = configuration.getExpectedDataFiles().length > 0;
        if (configuration.isTearDownDeclared()
                && configuration.getTearDownOperation() != DatabaseOperation.NONE
                && !expectedFilesDeclared)
        {
            log.warn("@DbUnitTearDownOperation declares a non-NONE operation, but there is"
                    + " nothing for it to act on: no @DbUnitPrep or @DbUnitExpected data files"
                    + " were declared. Add @DbUnitPrep or @DbUnitExpected, or change the"
                    + " operation to DbUnitOperation.NONE.");
        }
    }

    /**
     * Returns {@code failure} with an extra hint when it is the classic row count check +
     * {@code @DbUnitPrep} + no-teardown shape: the baseline is captured <em>before</em>
     * {@code @DbUnitPrep} loads, so a prep dataset that is never torn down reads as leaked rows
     * and fails the check - a common first surprise. Whatever turned the check on gets the hint:
     * {@code @DbUnitRowCountCheck}, the {@code -Ddbunit.rowCountCheck} system property that a
     * suite-wide run sets, or {@link org.dbunit.database.DatabaseConfig#FEATURE_ROW_COUNT_CHECK}.
     *
     * <p>The hint is for a test case this class constructed, whose teardown operation is the
     * declared one or else the tester's ({@link DatabaseOperation#NONE}), so an explicit
     * {@code @DbUnitTearDownOperation(NONE)} is covered too, and only when a table of the
     * difference is a table of the prep dataset, as the leak is then plausibly the prep rows. An
     * injected test case gets none: it runs the operation its owner set on it, which dbUnit
     * cannot read, and the annotation the hint names is rejected next to it. Otherwise returns
     * {@code failure} unchanged.
     */
    private UnexpectedRowCountException withHintForUnclearedPrep(
            final UnexpectedRowCountException failure)
    {
        if (injected)
        {
            return failure;
        }

        final boolean prepFilesDeclared = configuration.getPrepDataFiles().length > 0;
        if (!prepFilesDeclared || effectiveTearDownOperation() != DatabaseOperation.NONE)
        {
            return failure;
        }

        final boolean differsInAPrepTable = differsInAPrepTable(failure);
        if (!differsInAPrepTable)
        {
            return failure;
        }

        final UnexpectedRowCountException hinted = new UnexpectedRowCountException(
                failure.getDifferences(),
                "The row count check's baseline is captured before @DbUnitPrep loads, so a"
                        + " prep dataset that is never torn down reads as leaked rows. Add"
                        + " @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL), or exclude these"
                        + " tables from the check.");
        hinted.setStackTrace(failure.getStackTrace());
        for (final Throwable suppressed : failure.getSuppressed())
        {
            hinted.addSuppressed(suppressed);
        }
        return hinted;
    }

    /**
     * Returns whether a table of the row count difference is a table of the prep dataset the
     * test case loaded, compared ignoring case as dbUnit finds a table in a dataset. The prep
     * dataset is loaded by now, so this reads its table names and parses nothing. A prep dataset
     * that cannot list its tables counts as holding none: the hint is advice, and must not hide
     * the failure it explains.
     */
    private boolean differsInAPrepTable(final UnexpectedRowCountException failure)
    {
        final Set<String> prepTableKeys = prepTableKeys();
        for (final RowCountDifference difference : failure.getDifferences())
        {
            final String differenceKey = TableNameKey.of(difference.getTableName());
            if (prepTableKeys.contains(differenceKey))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the table names of the prep dataset of {@link #testCase} in the form
     * {@link TableNameKey} compares them by; empty when the test case reports no prep dataset or
     * the dataset cannot list its tables.
     */
    private Set<String> prepTableKeys()
    {
        final Set<String> prepTableKeys = new HashSet<>();
        try
        {
            final IDataSet prepDataset = testCase.getPrepDataset();
            if (prepDataset == null)
            {
                return prepTableKeys;
            }
            for (final String tableName : prepDataset.getTableNames())
            {
                prepTableKeys.add(TableNameKey.of(tableName));
            }
        } catch (final DataSetException | RuntimeException e)
        {
            log.debug("The prep dataset could not list its tables, so a row count difference is"
                    + " not traced to prep that is never torn down.", e);
            prepTableKeys.clear();
        }
        return prepTableKeys;
    }

    /**
     * Returns the teardown operation a test case this class constructed runs: the one the
     * annotations declared, else the tester's own; {@code null} when the annotations declare none
     * and no tester is known.
     */
    private DatabaseOperation effectiveTearDownOperation()
    {
        if (configuration.isTearDownDeclared())
        {
            return configuration.getTearDownOperation();
        }
        if (tester == null)
        {
            return null;
        }
        return tester.getTearDownOperation();
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

    /**
     * Returns the test case this class constructed, which is always a
     * {@link DefaultPrepAndExpectedTestCase}: the operations and the other values are given to a
     * built test case only.
     */
    private DefaultPrepAndExpectedTestCase builtTestCase()
    {
        return (DefaultPrepAndExpectedTestCase) testCase;
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
