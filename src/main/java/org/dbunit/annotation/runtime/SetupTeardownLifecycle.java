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

import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.TestScopedConnection;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The setup/teardown path of one test's dbUnit lifecycle, driven by
 * {@link AnnotatedTestExecutor} when {@link AnnotatedTestConfiguration#isExpected()} is false:
 * apply the {@code @DbUnitPrep} dataset (if any) and the {@code @DbUnitSetup}/
 * {@code @DbUnitTearDown} operations to the tester, run its {@code onSetup()}/{@code onTearDown()},
 * and around them capture and verify the {@link AnnotatedRowCountCheck} baseline.
 *
 * <p>Operations are applied only for a declared annotation: with no {@code @DbUnitPrep} and no
 * {@code @DbUnitSetup}, {@code onSetup()} runs with whatever dataset and setup operation the
 * tester already carries (e.g. from a {@code @BeforeEach} method); with no
 * {@code @DbUnitTearDown}, {@code onTearDown()} runs with the tester's current teardown
 * operation. The dataset and both operations {@link #tester} carries coming into the test are
 * snapshotted at the start and restored at the end (see {@link TesterStateSnapshot}), so a
 * tester shared across methods carries no per-method state onto the next method.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class SetupTeardownLifecycle
{
    private static final Logger log = LoggerFactory.getLogger(SetupTeardownLifecycle.class);

    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final TestScopedConnection connection;
    private final AnnotatedRowCountCheck rowCountCheck;

    private boolean reachedOnSetup;
    private TesterStateSnapshot incomingState;

    /**
     * Creates the setup/teardown lifecycle for one test.
     *
     * @param configuration The resolved configuration.
     * @param tester The tester to drive.
     * @param connection The shared per-test connection holder.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family - false on the classic path, which never piggybacks the row count
     *            check baseline.
     */
    SetupTeardownLifecycle(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester, final TestScopedConnection connection,
            final boolean annotationDriven)
    {
        this.configuration = configuration;
        this.tester = tester;
        this.connection = connection;
        this.rowCountCheck = new AnnotatedRowCountCheck(configuration, tester, connection,
                annotationDriven);
    }

    /**
     * Offers {@code retrieved} - a connection the tester's own {@code onSetup()}/
     * {@code onTearDown()} just retrieved - to {@link AnnotatedRowCountCheck} for the
     * piggybacked baseline capture. Called by {@link AnnotatedTestExecutor} from the installed
     * {@link ExecutorOperationListener}.
     *
     * @param retrieved The connection the tester just retrieved.
     */
    void adoptAndCaptureBaselineOnFirstConnection(final IDatabaseConnection retrieved)
    {
        rowCountCheck.adoptAndCaptureBaselineOnFirstConnection(retrieved);
    }

    /**
     * Applies the prep dataset and setup operation, decides the row count check baseline, and
     * runs {@code onSetup()}.
     *
     * @throws Exception If any step fails.
     */
    void before() throws Exception
    {
        incomingState = TesterStateSnapshot.capture(tester);
        final DatabaseOperation resolvedSetUpOperation = applySetUpOperation();
        if (!rowCountCheck.canPiggybackBaselineOn(resolvedSetUpOperation))
        {
            rowCountCheck.captureBaselineEagerly();
        }
        applyPrepDataset();
        reachedOnSetup = true;
        tester.onSetup();
        if (!rowCountCheck.isBaselineAttempted())
        {
            // The predicted piggyback never actually happened - e.g. a tester whose onSetup()
            // does not end up calling executeOperation() the way AbstractDatabaseTester's does.
            // Always leave a baseline decided one way or another before the test method runs,
            // rather than silently skip the check because a prediction about a tester's
            // internals turned out wrong. canPiggybackBaselineOn() only trusts a piggyback
            // when onSetup() is AbstractDatabaseTester's own, so reaching here means that
            // contract was not kept - the baseline captured now may already include the prep
            // dataset's rows.
            rowCountCheck.captureBaselineEagerly();
        }
    }

    /**
     * Resolves the setup operation {@code onSetup()} will run and applies it to the tester.
     * Reads only the configuration and the tester's own current operation, never a connection,
     * so {@link #before()} can settle the row count check baseline route - see
     * {@link AnnotatedRowCountCheck#canPiggybackBaselineOn(DatabaseOperation)} - <em>before</em>
     * {@link #applyPrepDataset()} runs: combining a multi-file {@code @DbUnitPrep} dataset
     * resolves the shared connection to read its case-sensitivity feature, which would rule the
     * piggyback out (and cost a fresh-connection tester an extra connection) if it ran first.
     *
     * @return The setup operation {@code onSetup()} will run.
     */
    private DatabaseOperation applySetUpOperation()
    {
        if (configuration.getPrepDataFiles().length > 0 || configuration.isSetupDeclared())
        {
            // With @DbUnitSetup but no @DbUnitPrep, there is no dataset to apply the operation
            // to, but the operation itself - most usefully NONE - still applies to whatever
            // dataset the tester already has; see AnnotatedTestConfiguration#isSetupDeclared().
            final DatabaseOperation setUpOperation = configuration.getSetUpOperation();
            tester.setSetUpOperation(setUpOperation);
            return setUpOperation;
        }
        log.debug("No @DbUnitPrep data files and no @DbUnitSetup declared; running with the"
                + " tester's current dataset and setup operation, restored afterward.");
        return tester.getSetUpOperation();
    }

    /**
     * Applies the {@code @DbUnitPrep} dataset to the tester: a single file loaded directly, or
     * multiple files combined (see {@link #loadCombined}). With no {@code @DbUnitPrep} data
     * files this only warns, when {@code @DbUnitSetup} declares a non-NONE operation with no
     * dataset for it to act on - {@link #applySetUpOperation()} has already applied the
     * operation itself.
     *
     * @throws Exception If loading or combining a prep dataset fails.
     */
    private void applyPrepDataset() throws Exception
    {
        if (configuration.getPrepDataFiles().length > 0)
        {
            tester.setDataSet(loadCombined(configuration.getDataFileLoader(),
                    configuration.getPrepDataFiles()));
            return;
        }
        if (configuration.isSetupDeclared())
        {
            final DatabaseOperation setUpOperation = configuration.getSetUpOperation();
            warnIfNonNoneOperationHasNoDataset("@DbUnitSetup", "onSetup()", setUpOperation);
            log.debug("No @DbUnitPrep data files declared; applied @DbUnitSetup's operation"
                    + " {} without changing the tester's dataset.", setUpOperation);
        }
    }

    /**
     * Applies the teardown operation (when {@code @DbUnitTearDown} was declared), runs
     * {@code onTearDown()}, then verifies the row count check baseline unless {@code testFailed}
     * - all skipped when {@link #before()} threw before {@code onSetup()} ran, since
     * {@code onTearDown()} would then act on state {@code onSetup()} never established and throw
     * a second exception masking the first. Either way the incoming tester state is restored
     * afterward - on a failure, with any restore failure attached as suppressed rather than
     * masking the primary.
     *
     * @param testFailed Whether the test method itself already threw; when {@code true},
     *            verification is skipped so a difference does not mask the real failure.
     * @throws Exception If any step fails.
     */
    void after(final boolean testFailed) throws Exception
    {
        try
        {
            if (reachedOnSetup)
            {
                if (configuration.isTearDownDeclared())
                {
                    final DatabaseOperation tearDownOperation =
                            configuration.getTearDownOperation();
                    tester.setTearDownOperation(tearDownOperation);
                    warnIfNonNoneOperationHasNoDataset("@DbUnitTearDown", "onTearDown()",
                            tearDownOperation);
                } else
                {
                    log.debug("No @DbUnitTearDown declared; running onTearDown() with the"
                            + " tester's current teardown operation, restored afterward.");
                }
                tester.onTearDown();
                if (!testFailed)
                {
                    rowCountCheck.verify();
                }
            }
        } catch (final Throwable primaryFailure)
        {
            TesterStateSnapshot.restoreSuppressing(tester, incomingState, primaryFailure);
            throw primaryFailure;
        }
        TesterStateSnapshot.restore(tester, incomingState);
    }

    /**
     * Warns when {@code annotationName} ({@code @DbUnitSetup} or {@code @DbUnitTearDown})
     * declares a non-{@link DatabaseOperation#NONE} operation with no {@code @DbUnitPrep} data
     * files and no dataset already on the tester - every such operation ({@code CLEAN_INSERT},
     * {@code INSERT}, {@code REFRESH}, {@code UPDATE}, {@code DELETE}, {@code DELETE_ALL},
     * {@code TRUNCATE_TABLE}) needs a dataset to act on, so {@code AbstractDatabaseTester}'s
     * {@code testerMethodName} would run the operation against a {@code null} dataset. A bare
     * {@code @DbUnitSetup} resolves to {@code CLEAN_INSERT}, so that is the likely shape of the
     * mistake on the setup side; a bare {@code @DbUnitTearDown} resolves to {@code NONE}, so on
     * the teardown side reaching this warning means the {@code operation} member was set
     * explicitly.
     *
     * <p>A warning rather than an {@link IllegalStateException}: a custom {@link IDatabaseTester}
     * whose {@code onSetup()}/{@code onTearDown()} sources its dataset some other way, or a
     * test double, legitimately has {@code getDataSet()} return {@code null} here and handles
     * the operation itself.
     *
     * @param annotationName The declaring annotation, {@code "@DbUnitSetup"} or
     *            {@code "@DbUnitTearDown"}.
     * @param testerMethodName The tester lifecycle method that would run the operation,
     *            {@code "onSetup()"} or {@code "onTearDown()"}.
     * @param resolvedOperation The operation the annotation resolved to.
     */
    private void warnIfNonNoneOperationHasNoDataset(final String annotationName,
            final String testerMethodName, final DatabaseOperation resolvedOperation)
    {
        if (resolvedOperation != DatabaseOperation.NONE && tester.getDataSet() == null)
        {
            log.warn("{} declares a non-NONE operation, but there is nothing for it to act on:"
                    + " no @DbUnitPrep data files were declared, and the tester holds no"
                    + " dataset. A built-in tester will fail in {}. Add @DbUnitPrep, change the"
                    + " operation to DbUnitOperation.NONE, or set a dataset on the tester in a"
                    + " @BeforeEach method.", annotationName, testerMethodName);
        }
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
     * Returns whether an <em>already-resolved</em> connection reports
     * {@code DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES} - the same feature
     * {@code DefaultPrepAndExpectedTestCase#configureTest()} resolves for its own multi-file
     * combining, so multiple {@code @DbUnitPrep} files combine the same way rather than always
     * case-insensitively.
     *
     * <p>Reads only a connection {@link #before()} has already resolved
     * ({@link TestScopedConnection#peekConnection()}); it never resolves one itself. On the row
     * count check's piggyback route - see
     * {@link AnnotatedRowCountCheck#canPiggybackBaselineOn(DatabaseOperation)} - no connection
     * is resolved until {@code onSetup()} retrieves it, so this returns false and a multi-file
     * prep dataset combines case-insensitively: the pre-annotation default, and identical to a
     * case-sensitive combine unless two prep files hold tables whose names differ only in case.
     * Resolving one here purely to read this feature would rule that route out and cost a
     * fresh-connection tester (a plain {@code JdbcDatabaseTester}) a second physical connection
     * every test. Every other route - a custom tester, a {@link DatabaseOperation#NONE} setup
     * operation, a connection already in hand from {@code @BeforeEach} parameter injection -
     * has resolved a connection before this runs, so the feature is honored.
     *
     * @return True when an already-resolved connection reports case-sensitive table names.
     */
    private boolean isCaseSensitiveTableNames()
    {
        final IDatabaseConnection resolved = connection.peekConnection();
        return resolved != null && resolved.getConfig()
                .getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
    }
}
