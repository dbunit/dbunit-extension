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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One connection acquired to serve a single test's dbUnit lifecycle: acquired at most once
 * from a supplier, memoized so every step of that test reuses it rather than opening its own,
 * and released at the end only when this lifecycle - not some external owner - is the one that
 * should close it, as its {@link ConnectionOwnership} decides.
 *
 * <p>Not thread-safe; a test's steps run sequentially on one thread.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class TestScopedConnection
{
    private static final Logger log = LoggerFactory.getLogger(TestScopedConnection.class);

    private final Callable<IDatabaseConnection> source;
    private final ConnectionOwnership ownership;
    private final Consumer<IDatabaseConnection> onAcquired;

    private IDatabaseConnection connection;
    private boolean resolved;

    /**
     * Creates a test-scoped connection.
     *
     * @param source Acquires the connection on first use; may return {@code null} (e.g. a test
     *            double with no connection to offer).
     * @param ownership Decides, at {@link #release()} time, whether this lifecycle may close
     *            the connection.
     * @param onAcquired Run once on each connection {@code source} returns, immediately after
     *            acquisition (e.g. to apply {@code @DbUnitProperty} values, or warn on an
     *            autocommit-off connection); not run for a {@code null} connection, nor for one
     *            {@link #adopt(IDatabaseConnection)} takes over. May be {@code null}.
     */
    public TestScopedConnection(final Callable<IDatabaseConnection> source,
            final ConnectionOwnership ownership,
            final Consumer<IDatabaseConnection> onAcquired)
    {
        this.source = source;
        this.ownership = ownership;
        this.onAcquired = onAcquired;
    }

    /**
     * Returns the connection for this test, acquiring it from the supplier on first use and
     * returning the same one thereafter. A memoized connection that has since been closed - by
     * a pool max-lifetime, a server reap, a
     * {@link org.dbunit.database.CachingConnectionProvider#close()} between reused test methods
     * - is dropped and re-acquired rather than handed back dead.
     *
     * @return The connection, or {@code null} when the supplier has none to offer.
     * @throws Exception If the supplier fails.
     */
    public IDatabaseConnection getConnection() throws Exception
    {
        if (resolved && connection != null && isClosedOrUnreadable(connection))
        {
            connection = null;
            resolved = false;
        }
        if (!resolved)
        {
            connection = source.call();
            resolved = true;
            if (connection != null && onAcquired != null)
            {
                onAcquired.accept(connection);
            }
        }
        return connection;
    }

    /**
     * Returns the connection already acquired this test, or {@code null} both before
     * {@link #getConnection()} first runs and when the supplier had none - never triggers
     * acquisition.
     *
     * @return The memoized connection, or {@code null}.
     */
    public IDatabaseConnection peekConnection()
    {
        return resolved ? connection : null;
    }

    /**
     * Returns whether {@link #getConnection()} or {@link #adopt(IDatabaseConnection)} has run
     * this test, regardless of whether the resulting connection is {@code null}.
     *
     * @return {@code true} once a connection (possibly {@code null}) has been resolved.
     */
    public boolean isResolved()
    {
        return resolved;
    }

    /**
     * Takes over a connection acquired elsewhere - the executor's listener piggyback, which
     * memoizes whatever connection the tester's own {@code onSetup()} retrieved - as if
     * {@link #getConnection()} had returned it. {@code onAcquired} is not run: the caller doing the
     * piggyback has already done that connection's post-acquisition setup.
     *
     * @param connection The connection to adopt.
     */
    public void adopt(final IDatabaseConnection connection)
    {
        this.connection = connection;
        this.resolved = true;
    }

    /**
     * Closes the connection when {@link ConnectionOwnership#mayClose()} allows it and it is not
     * already closed; a no-op when no connection was ever acquired. Leaves the memo in place -
     * a single-use holder is discarded with its owner, and a reused one relies on
     * {@code revalidateOnReacquire} to drop the now-closed connection on the next
     * {@link #getConnection()}.
     *
     * <p>When it does close, it forgets the connection too, so the next {@link #getConnection()}
     * on a reused holder acquires a fresh one. When ownership says leave it - {@code
     * closeConnectionAfterTest=false}, a no-op listener - the memo is kept for its real owner.
     *
     * <p>The already-closed guard matters when this holder's connection is the same object
     * another owner also closes - the {@code @DbUnitExpected} path, where the executor's
     * borrowed connection and {@link org.dbunit.DefaultPrepAndExpectedTestCase}'s are one and
     * the same. A {@code null} underlying JDBC connection is treated as nothing to close, the
     * same way {@link #getConnection()}'s liveness check does; a {@link SQLException} from the
     * check is left to propagate rather than swallowed: a connection whose {@code isClosed()}
     * throws has failed in a way worth surfacing (as a suppressed exception, via
     * {@link #releaseSuppressing(Throwable)}).
     *
     * <p>An uncommitted transaction is rolled back first when the connection's autocommit is
     * off - see {@link #rollBackOpenTransaction(Connection)}.
     *
     * @throws Exception If closing the connection, or checking whether it is already closed,
     *             fails.
     */
    public void release() throws Exception
    {
        if (!resolved || connection == null || !ownership.mayClose())
        {
            return;
        }
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            if (jdbcConnection != null && !jdbcConnection.isClosed())
            {
                rollBackOpenTransaction(jdbcConnection);
                connection.close();
            }
        } finally
        {
            connection = null;
            resolved = false;
        }
    }

    /**
     * Rolls back an uncommitted transaction on {@code jdbcConnection}, best-effort, before
     * {@link #release()} closes it, when its autocommit is off. The dbUnit setup/teardown
     * operations that run on a borrowed connection manage no transaction of their own, so on an
     * autocommit-off connection their writes sit here uncommitted - already warned about (see
     * {@link AutoCommitOffWarning}) and about to be discarded by the close regardless. Derby and
     * DB2 refuse to close a connection while a transaction is still open - Derby raises SQLState
     * 25001 - turning an ordinary end-of-test close into a failure; every other supported
     * database rolls the transaction back on close on its own. Doing it explicitly makes the
     * close portable and the discard of those never-committed writes intentional. A rollback,
     * not a commit: this lifecycle's operations were never meant to persist here, and a rollback
     * cannot disturb work a caller committed before handing the connection over. A connection
     * with autocommit on has no transaction to end and is left untouched.
     *
     * @param jdbcConnection The JDBC connection {@link #release()} is about to close.
     */
    private static void rollBackOpenTransaction(final Connection jdbcConnection)
    {
        try
        {
            if (!jdbcConnection.getAutoCommit())
            {
                jdbcConnection.rollback();
            }
        } catch (final SQLException e)
        {
            log.debug("rollBackOpenTransaction: could not roll back before closing the"
                    + " connection", e);
        }
    }

    /**
     * Releases the connection, attaching any close failure to {@code primary} via
     * {@link Throwable#addSuppressed(Throwable)} rather than letting it replace the more useful
     * failure already in flight, then {@linkplain #discardOnFailure() discards} the memo.
     *
     * @param primary The failure already being thrown, to attach a close failure to.
     */
    public void releaseSuppressing(final Throwable primary)
    {
        try
        {
            release();
        } catch (final Exception closeFailure)
        {
            primary.addSuppressed(closeFailure);
        }
        discardOnFailure();
    }

    /**
     * Forgets the memoized connection so the next {@link #getConnection()} acquires a fresh one.
     * For use after a lifecycle step failed: the step that just threw may have left the
     * connection broken, so the next step should not reuse it.
     */
    public void discardOnFailure()
    {
        connection = null;
        resolved = false;
    }

    /**
     * Returns whether {@code connection} is closed or can no longer report its state - the
     * cheap, side-effect-free check for a memoized connection a pool or server may have closed
     * between reused test methods. A {@link SQLException} while asking is treated as "unusable",
     * so a re-acquire follows.
     */
    private static boolean isClosedOrUnreadable(final IDatabaseConnection connection)
    {
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            return jdbcConnection == null || jdbcConnection.isClosed();
        } catch (final SQLException e)
        {
            return true;
        }
    }
}
