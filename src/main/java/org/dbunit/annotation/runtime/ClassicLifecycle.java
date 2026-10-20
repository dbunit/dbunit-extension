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

import java.util.function.BooleanSupplier;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.TestScopedConnection;

/**
 * The classic lifecycle of one test, run by {@link AnnotatedTestExecutor} for a test that opted
 * into none of the {@code org.dbunit.annotation} family: the tester's own {@code onSetup()}
 * before the test method and {@code onTearDown()} after it, as {@code DbUnitExtension} ran them
 * in 3.5.0, and around them the capture and verification of the {@link ClassicRowCountCheck}
 * baseline.
 *
 * <p>The tester carries everything the test needs - its dataset and its setup and teardown
 * operations, as the test class configured them, such as in a {@code @BeforeEach} method - and
 * is not written to. Nothing here applies a dataset, an operation or a {@code DatabaseConfig}
 * value, as no annotation declares one.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class ClassicLifecycle
{
    private final IDatabaseTester tester;
    private final ClassicRowCountCheck rowCountCheck;

    private boolean reachedOnSetup;

    /**
     * Creates the classic lifecycle for one test.
     *
     * @param tester The tester to drive.
     * @param connection The shared per-test connection holder.
     * @param operationListenerInPlace Supplies whether the {@link ClassicOperationListener}
     *            installed for this test still hears the tester's callbacks - false for a tester
     *            that cannot report its listener, where there is nothing to piggyback the row
     *            count check baseline on, and for one that does not carry it.
     */
    ClassicLifecycle(final IDatabaseTester tester, final TestScopedConnection connection,
            final BooleanSupplier operationListenerInPlace)
    {
        this.tester = tester;
        this.rowCountCheck =
                new ClassicRowCountCheck(tester, connection, operationListenerInPlace);
    }

    /**
     * Offers {@link ClassicRowCountCheck} the connection the tester's own
     * {@code onSetup()}/{@code onTearDown()} just retrieved, for the piggybacked baseline
     * capture. Called by {@link AnnotatedTestExecutor} from the installed
     * {@link ClassicOperationListener}, which the tester notifies before its operation reads the
     * dataset.
     *
     * @param retrieved The connection the tester just retrieved.
     */
    void adoptAndCaptureBaselineOnFirstConnection(final IDatabaseConnection retrieved)
    {
        rowCountCheck.adoptAndCaptureBaselineOnFirstConnection(retrieved);
    }

    /**
     * Decides the row count check baseline and runs {@code onSetup()}.
     *
     * @throws Exception If any step fails.
     */
    void before() throws Exception
    {
        final boolean connectionComesFromOnSetup =
                rowCountCheck.canPiggybackBaselineOn(tester.getSetUpOperation());
        if (!connectionComesFromOnSetup)
        {
            rowCountCheck.captureBaselineEagerly();
        }
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
            // contract was not kept - the baseline captured now may already include the rows
            // onSetup() inserted.
            rowCountCheck.captureBaselineEagerly();
        }
    }

    /**
     * Runs {@code onTearDown()}, then verifies the row count check baseline unless
     * {@code testFailed} - all skipped when {@link #before()} threw before {@code onSetup()}
     * ran, since {@code onTearDown()} would then act on state {@code onSetup()} never
     * established and throw a second exception masking the first.
     *
     * @param testFailed Whether the test method itself already threw; when {@code true},
     *            verification is skipped so a difference does not mask the real failure.
     * @throws Exception If any step fails.
     */
    void after(final boolean testFailed) throws Exception
    {
        if (!reachedOnSetup)
        {
            return;
        }

        tester.onTearDown();
        if (!testFailed)
        {
            rowCountCheck.verify();
        }
    }
}
