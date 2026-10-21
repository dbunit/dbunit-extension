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
package org.dbunit.database.connection;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs one warning for a tester, the second time it sees a connection from it that is going to
 * be closed after the test while the tester may hand out the same connection again. The
 * connection is going to be closed when {@link ConnectionOwnership#mayClose()} says so: the
 * {@code closeConnectionAfterTest} flag of the test case is {@code true} and the operation
 * listener of the tester is not the no-op one. Two kinds of tester may hand it out again:
 *
 * <ul>
 * <li>A {@link DefaultDatabaseTester} returns the connection it holds from every call. One it
 * was built over is not replaced once closed, so every later test that uses the tester finds it
 * closed, and one that a {@link org.dbunit.database.CachingConnectionProvider} holds is replaced
 * at the cost of a new connection for each test.</li>
 * <li>A tester that reports no operation listener, such as one written before
 * {@link IDatabaseTester#getOperationListener()} existed, gives dbUnit no way to learn that it
 * keeps its connection open for another owner, as the no-op listener says.</li>
 * </ul>
 *
 * <p>Only a tester that serves a second test is warned about. Closing the connection is right
 * for a tester built for a single test, which never shows up a second time, and wrong for one
 * that hands the connection out again, where the second test is the one that finds it closed.
 * The tester is counted by identity, in a registry shared by the whole JVM that does not keep it
 * alive, once for each lifecycle of an owner that acquires a qualifying connection from it, so
 * the warning comes when the second test does, before the failure it explains. A lifecycle that
 * acquires the connection again, such as the clean up after a failed step, does not count twice:
 * the owner says when its lifecycle ends with {@link #lifecycleEnded()}. A lifecycle that ends
 * without the owner saying so, as an annotation-driven test's does when its
 * {@code configureTest()} fails, is counted with the next one of an owner that outlives a test,
 * so there the warning can come one test late, after the failure it explains.
 * {@link ClosedConnectionHandedOutWarning} names the closed connection in the test that is
 * handed it.
 *
 * <p>It is the counterpart of {@link LeftOpenConnectionWarning}, which warns about a connection
 * that is left open when nothing else closes it. Any other tester is not judged, as nothing says
 * it hands the connection out again.
 *
 * <p>Used as an {@code onAcquired} hook of a {@link TestScopedConnection}, next to
 * {@link AutoCommitOffWarning}, so the check runs where a connection arrives. A tester is warned
 * about at most once, whichever lifecycle owners acquire connections from it. The tester is
 * checked before the ownership is asked anything, so a tester that is not judged sees no calls
 * beyond its listener report.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class ClosedFixedConnectionWarning implements Consumer<IDatabaseConnection>
{
    private static final Logger log = LoggerFactory.getLogger(ClosedFixedConnectionWarning.class);

    private static final UsedTesters TESTERS_OF_THE_JVM = new UsedTesters();

    private final ConnectionOwnership ownership;
    private final Supplier<IDatabaseTester> tester;
    private final UsedTesters usedTesters;

    /** The tester this owner has counted a use of in its current lifecycle, if any. */
    private IDatabaseTester testerUsedInThisLifecycle;

    /**
     * Creates the warning for one lifecycle owner.
     *
     * @param ownership Decides whether the connection is going to be closed.
     * @param tester Supplies the database tester the connection comes from, read fresh each
     *            time as it can change between tests; may supply {@code null}.
     */
    public ClosedFixedConnectionWarning(final ConnectionOwnership ownership,
            final Supplier<IDatabaseTester> tester)
    {
        this(ownership, tester, TESTERS_OF_THE_JVM);
    }

    ClosedFixedConnectionWarning(final ConnectionOwnership ownership,
            final Supplier<IDatabaseTester> tester, final UsedTesters usedTesters)
    {
        this.ownership = ownership;
        this.tester = tester;
        this.usedTesters = usedTesters;
    }

    /**
     * Warns when the connection is going to be closed and its tester may hand it out again, and
     * the tester has served another test before, unless a warning has already been logged for
     * it. A connection this owner acquires again from the same tester before its lifecycle ends
     * is not another use.
     *
     * @param connection The connection just acquired; not inspected, as it is the tester and
     *            the ownership that decide.
     */
    @Override
    public void accept(final IDatabaseConnection connection)
    {
        final IDatabaseTester databaseTester = tester.get();
        if (databaseTester == null || databaseTester == testerUsedInThisLifecycle)
        {
            return;
        }
        final boolean isDefaultDatabaseTester = databaseTester instanceof DefaultDatabaseTester;
        final boolean reportsNoListener = databaseTester.getOperationListener() == null;
        final boolean mayHandOutTheConnectionAgain = isDefaultDatabaseTester || reportsNoListener;
        if (!mayHandOutTheConnectionAgain)
        {
            return;
        }
        final boolean isGoingToClose = ownership.mayClose();
        if (!isGoingToClose)
        {
            return;
        }

        testerUsedInThisLifecycle = databaseTester;
        final int uses = usedTesters.recordUse(databaseTester);
        final boolean isSecondUse = uses == 2;
        if (!isSecondUse)
        {
            return;
        }
        if (isDefaultDatabaseTester)
        {
            warnAboutADefaultDatabaseTester(databaseTester);
        } else
        {
            warnAboutATesterWithoutAListener(databaseTester);
        }
    }

    /**
     * Tells this warning that the lifecycle of its owner has ended, so that a connection the
     * owner acquires from the same tester for its next test counts as another use of the tester.
     * An owner reused across tests, such as a test case in a static field, calls it when each
     * test's lifecycle ends.
     */
    public void lifecycleEnded()
    {
        testerUsedInThisLifecycle = null;
    }

    private static void warnAboutADefaultDatabaseTester(final IDatabaseTester databaseTester)
    {
        log.warn("The connection this test's dbUnit steps share is going to be closed after the"
                + " test, and its database tester, {}, which has served an earlier test, hands"
                + " out the connection it holds again on its next call. One the tester was built"
                + " over is not replaced once it is closed, so every later test that uses the"
                + " tester finds it closed, and one a CachingConnectionProvider holds is"
                + " replaced at the cost of a new connection for each test. Keep the connection"
                + " open: give the tester IOperationListener.NO_OP_OPERATION_LISTENER, or call"
                + " setCloseConnectionAfterTest(false) on a test case you build yourself.",
                databaseTester.getClass().getName());
    }

    private static void warnAboutATesterWithoutAListener(final IDatabaseTester databaseTester)
    {
        log.warn("The connection this test's dbUnit steps share is going to be closed after the"
                + " test, and its database tester, {}, which has served an earlier test, does not"
                + " report its operation listener (it does not override"
                + " IDatabaseTester.getOperationListener()), so dbUnit cannot tell that the"
                + " tester keeps its connection open for another owner. If"
                + " the tester hands out one connection from every call, every later test that"
                + " uses it finds the connection closed: override getOperationListener() to"
                + " return IOperationListener.NO_OP_OPERATION_LISTENER to keep it open, or call"
                + " setCloseConnectionAfterTest(false) on a test case you build yourself. A"
                + " tester that opens a new connection on every call can ignore this warning.",
                databaseTester.getClass().getName());
    }
}
