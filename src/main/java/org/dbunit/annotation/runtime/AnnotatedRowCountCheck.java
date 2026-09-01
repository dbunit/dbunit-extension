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

import java.sql.SQLException;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.TestScopedConnection;
import org.dbunit.database.rowcount.RowCountChecker;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.operation.DatabaseOperation;

/**
 * The row count check for {@link SetupTeardownLifecycle}: captures a baseline before
 * {@code onSetup()} and verifies it after {@code onTearDown()}, so a table the test left
 * dirty - one it should have cleaned and did not, or a reference table it wrongly cleaned -
 * fails the test.
 *
 * <p>Capturing that baseline needs a connection - to read whether the check is even enabled -
 * but resolving one just for that would cost a plain {@code JdbcDatabaseTester} an extra
 * physical connection every test, even when the check turns out disabled. So the baseline is
 * captured from whichever connection ends up cheapest:
 *
 * <ul>
 * <li>when {@link #canPiggybackBaselineOn(DatabaseOperation)} can prove {@code onSetup()} is
 * about to retrieve one on its own, {@link #adoptAndCaptureBaselineOnFirstConnection(IDatabaseConnection)}
 * piggybacks on that connection - via the {@link ExecutorOperationListener} - so no connection
 * is resolved here at all;</li>
 * <li>otherwise - a custom {@link IDatabaseTester}, a {@link DatabaseOperation#NONE} setup
 * operation, a connection already memoized from a {@code @BeforeEach} parameter, or the classic
 * path where there is no listener to piggyback on - {@link #captureBaselineEagerly()} resolves
 * one eagerly, <em>before</em> {@code onSetup()}.</li>
 * </ul>
 *
 * <p>Either way a baseline is always decided - captured, or found disabled - before the test
 * method runs; which route got there differs only in cost. {@link SetupTeardownLifecycle}
 * re-runs {@link #captureBaselineEagerly()} after {@code onSetup()} as a last-resort net for a
 * piggyback it trusted that still did not fire ({@link #isBaselineAttempted()} reports whether
 * that is needed).
 *
 * <p>Holds no connection of its own; every route takes the shared {@link TestScopedConnection}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class AnnotatedRowCountCheck
{
    private final AnnotatedTestConfiguration configuration;
    private final IDatabaseTester tester;
    private final TestScopedConnection connection;
    private final boolean annotationDriven;
    private final RowCountChecker checker = new RowCountChecker();

    private boolean baselineAttempted;

    /**
     * Creates the row count check for one test.
     *
     * @param configuration The resolved configuration.
     * @param tester The tester being driven.
     * @param connection The shared per-test connection holder.
     * @param annotationDriven Whether the test opted into the {@code org.dbunit.annotation}
     *            family - false on the classic path, which never piggybacks.
     */
    AnnotatedRowCountCheck(final AnnotatedTestConfiguration configuration,
            final IDatabaseTester tester, final TestScopedConnection connection,
            final boolean annotationDriven)
    {
        this.configuration = configuration;
        this.tester = tester;
        this.connection = connection;
        this.annotationDriven = annotationDriven;
    }

    /**
     * Returns whether {@code onSetup()} can be trusted to retrieve a connection and notify
     * {@link ExecutorOperationListener#connectionRetrieved(IDatabaseConnection)} on its own, so
     * the baseline can be captured from that connection via
     * {@link #adoptAndCaptureBaselineOnFirstConnection(IDatabaseConnection)} instead of
     * {@link #captureBaselineEagerly()} resolving a separate one just to find out whether the
     * check is even enabled.
     *
     * <p>True only when this is provably safe: the test is {@code annotationDriven}, so an
     * {@link ExecutorOperationListener} is actually installed to piggyback on; {@code tester} is
     * dbUnit's own {@link AbstractDatabaseTester} <em>and does not override {@code onSetup()}</em>,
     * so {@code executeOperation()} - known by reading its source, not by assuming it - runs and
     * calls {@code getConnection()} and notifies the listener <em>before</em> the operation
     * touches any data, whenever the operation is not {@link DatabaseOperation#NONE}; and no
     * connection is already memoized (e.g. by an earlier {@code @BeforeEach} parameter
     * injection), since piggybacking on a second, different connection than one already in hand
     * would orphan the first rather than reuse it. {@link SetupTeardownLifecycle} consults this
     * before it combines a multi-file {@code @DbUnitPrep} dataset, precisely so that combine's
     * case-sensitivity read does not resolve a connection first and rule the piggyback out
     * here; on this route the combine falls back to combining case-insensitively. A custom
     * {@link IDatabaseTester}, or an
     * {@link AbstractDatabaseTester} subclass that overrides {@code onSetup()} and might apply
     * the setup operation some other way, is never assumed to behave the same way -
     * {@link #captureBaselineEagerly()} runs for it instead, <em>before</em> {@code onSetup()},
     * so the baseline never picks up rows {@code onSetup()} inserts.
     *
     * @param resolvedSetUpOperation The setup operation {@code onSetup()} is about to run.
     * @return True when a piggyback is provably safe.
     */
    boolean canPiggybackBaselineOn(final DatabaseOperation resolvedSetUpOperation)
    {
        return annotationDriven && !connection.isResolved()
                && tester instanceof AbstractDatabaseTester && !testerOverridesOnSetup()
                && resolvedSetUpOperation != DatabaseOperation.NONE;
    }

    /**
     * Returns whether {@link #tester}'s runtime type overrides
     * {@link AbstractDatabaseTester#onSetup()} somewhere below {@link AbstractDatabaseTester}
     * itself, rather than inheriting it - in which case {@code onSetup()} is not guaranteed to
     * route through {@code executeOperation()} and notify the operation listener, so
     * {@link #canPiggybackBaselineOn(DatabaseOperation)} cannot trust it. Only reached once
     * {@link #canPiggybackBaselineOn(DatabaseOperation)} has established {@link #tester} is an
     * {@link AbstractDatabaseTester}, so the walk always terminates at that ceiling.
     */
    private boolean testerOverridesOnSetup()
    {
        return DefaultMethodOverrideCheck.declaresBelow(tester.getClass(),
                AbstractDatabaseTester.class, "onSetup");
    }

    /**
     * Eagerly resolves the shared connection and captures the baseline from it. The route taken
     * when {@link #canPiggybackBaselineOn(DatabaseOperation)} said no connection is coming from
     * {@code onSetup()} on its own, and, run again by {@link SetupTeardownLifecycle} after
     * {@code onSetup()}, the last-resort net for a piggyback that was trusted but never fired.
     *
     * @throws Exception If resolving the connection or capturing the baseline fails.
     */
    void captureBaselineEagerly() throws Exception
    {
        captureBaseline(connection.getConnection());
    }

    /**
     * Captures the baseline from {@code retrieved} - the tester's own, just retrieved by its
     * {@code onSetup()} - the first time this is called for the current test; a no-op on any
     * later call (e.g. {@code onTearDown()} later notifying the same listener). Memoizes
     * {@code retrieved} into the shared holder the same way {@link TestScopedConnection#getConnection()}
     * would, so every other caller this test - the later verify, a parameter injection, the
     * end-of-test close - reuses this exact connection.
     *
     * <p>Called only from {@link ExecutorOperationListener}, which declares no checked
     * exceptions, so a failure here is wrapped in a {@link DatabaseUnitRuntimeException} rather
     * than a checked one.
     *
     * @param retrieved The connection the tester just retrieved.
     */
    void adoptAndCaptureBaselineOnFirstConnection(final IDatabaseConnection retrieved)
    {
        if (baselineAttempted)
        {
            return;
        }
        connection.adopt(retrieved);
        try
        {
            captureBaseline(retrieved);
        } catch (final DatabaseUnitException | SQLException e)
        {
            throw new DatabaseUnitRuntimeException(e);
        }
    }

    /**
     * Captures the baseline from {@code from}, marking a baseline as having been attempted
     * either way - including when {@code from} is {@code null} (e.g. a test double) or the
     * check turns out disabled - so a piggyback prediction is never retried nor second-guessed
     * later in the same test.
     */
    private void captureBaseline(final IDatabaseConnection from)
            throws DatabaseUnitException, SQLException
    {
        baselineAttempted = true;
        if (from == null)
        {
            return;
        }
        if (configuration.isRowCountCheckDeclared())
        {
            checker.setEnabledOverride(configuration.isRowCountCheckEnabled(),
                    configuration.getRowCountCheckExclude());
        }
        checker.capture(from);
    }

    /**
     * Returns whether a baseline capture has been attempted this test - captured, found
     * disabled, or found to have no connection. {@link SetupTeardownLifecycle} checks this
     * after {@code onSetup()} to decide whether the last-resort eager capture is still needed.
     *
     * @return True once a capture has been attempted.
     */
    boolean isBaselineAttempted()
    {
        return baselineAttempted;
    }

    /**
     * Verifies the captured baseline against the shared connection's current row counts, unless
     * no baseline was captured (the check is disabled, or there was no connection). A mismatch
     * is rethrown with an extra hint when it is the classic {@code @DbUnitRowCountCheck} +
     * {@code @DbUnitPrep} + no-teardown shape.
     *
     * @throws Exception If resolving the connection or verifying fails, including
     *             {@link UnexpectedRowCountException} on a mismatch.
     */
    void verify() throws Exception
    {
        if (!checker.hasBaseline())
        {
            return;
        }
        final IDatabaseConnection against = connection.getConnection();
        if (against == null)
        {
            return;
        }
        try
        {
            checker.verify(against);
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
     * returns {@code e} unchanged. Keyed on the teardown operation the tester actually just ran
     * ({@link DatabaseOperation#NONE}), so an explicit {@code @DbUnitTearDown(operation = NONE)}
     * is covered too.
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
}
