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
package org.dbunit;

import java.util.function.Supplier;

import org.dbunit.database.IDatabaseConnection;

/**
 * An {@link IOperationListener} that forwards every callback to a {@code delegate} except a
 * close of a connection whose lifecycle is owned elsewhere: it drops
 * {@code operationSetUpFinished}/{@code operationTearDownFinished} for the connection (or
 * connections) it protects, so the {@code delegate}'s {@link DefaultOperationListener}-style
 * close cannot run on one still in use for a later step - a row count check verify, a
 * connection injected into the test method, or the shared connection
 * {@link DefaultPrepAndExpectedTestCase} drives its whole lifecycle on.
 *
 * <p>Two shapes: a {@linkplain #ConnectionPreservingOperationListener(IOperationListener)
 * blanket} listener protects every connection (the caller owns them all), and a
 * {@linkplain #ConnectionPreservingOperationListener(IOperationListener, Supplier) scoped} one
 * protects the single connection its {@link Supplier} names, re-evaluated on each callback
 * since that connection may not be resolved yet when the listener is installed; any other
 * connection has its callbacks forwarded unchanged, so a tester handing out a fresh connection
 * per operation still has each of those closed right after its own operation.
 *
 * <p>The shield suppresses the <em>whole</em> {@code operationSetUpFinished}/
 * {@code operationTearDownFinished} callback for a protected connection, not only its
 * {@code connection.close()}: {@link IOperationListener} has no way to signal "do your other
 * work but skip the close". This is exactly right for the delegates dbUnit ships -
 * {@link DefaultOperationListener} (closes, nothing else) and
 * {@link IOperationListener#NO_OP_OPERATION_LISTENER} (does nothing). A custom delegate that
 * <em>also</em> does non-close work in those callbacks (commit a transaction, release a lock,
 * record a metric) does not see them for a protected connection; do that work from
 * {@code connectionRetrieved} instead, which is always forwarded, or manage the connection
 * entirely in the delegate and do not protect it here.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class ConnectionPreservingOperationListener implements IOperationListener
{
    private final IOperationListener delegate;
    private final Supplier<IDatabaseConnection> protectedConnection;
    private final boolean protectEveryConnection;

    /**
     * Creates a blanket listener that protects every connection from a close by the delegate,
     * forwarding only {@code connectionRetrieved}.
     *
     * @param delegate The listener to forward to.
     */
    public ConnectionPreservingOperationListener(final IOperationListener delegate)
    {
        this.delegate = delegate;
        this.protectedConnection = null;
        this.protectEveryConnection = true;
    }

    /**
     * Creates a listener that protects the one connection {@code protectedConnection} supplies,
     * forwarding every callback for any other connection.
     *
     * @param delegate The listener to forward to.
     * @param protectedConnection Supplies the connection to protect, re-read on each callback;
     *            may return {@code null} before that connection is resolved, protecting nothing
     *            until it is.
     */
    public ConnectionPreservingOperationListener(final IOperationListener delegate,
            final Supplier<IDatabaseConnection> protectedConnection)
    {
        this.delegate = delegate;
        this.protectedConnection = protectedConnection;
        this.protectEveryConnection = false;
    }

    /**
     * Returns the listener this one forwards to.
     *
     * @return The delegate listener.
     */
    public final IOperationListener getDelegate()
    {
        return delegate;
    }

    @Override
    public void connectionRetrieved(final IDatabaseConnection connection)
    {
        delegate.connectionRetrieved(connection);
    }

    @Override
    public void operationSetUpFinished(final IDatabaseConnection connection)
    {
        if (!protects(connection))
        {
            delegate.operationSetUpFinished(connection);
        }
    }

    @Override
    public void operationTearDownFinished(final IDatabaseConnection connection)
    {
        if (!protects(connection))
        {
            delegate.operationTearDownFinished(connection);
        }
    }

    private boolean protects(final IDatabaseConnection connection)
    {
        return protectEveryConnection || connection == protectedConnection.get();
    }

    /**
     * Returns the listener a newly-installed wrapper should delegate to, so re-wrapping a
     * tester shared across tests does not nest one wrapper layer per test: an existing
     * {@link ConnectionPreservingOperationListener}'s own delegate, the tester's existing
     * listener as-is, or a fresh {@link DefaultOperationListener} when it had none.
     *
     * <p>"Had none" is read from a {@code null} {@link IDatabaseTester#getOperationListener()},
     * the interface default. {@link AbstractDatabaseTester} lazily creates a
     * {@link DefaultOperationListener} on first use, so a {@code null} there genuinely means
     * "none yet" and the fresh one substituted here matches. A custom {@link IDatabaseTester}
     * that manages an {@link IOperationListener} internally without exposing it through
     * {@code getOperationListener()} will have that internal listener bypassed - such a tester
     * must override {@code getOperationListener()} for it to be preserved.
     *
     * @param existingListener The tester's current listener, possibly {@code null}.
     * @return The listener a new wrapper should delegate to.
     */
    public static IOperationListener unwrap(final IOperationListener existingListener)
    {
        if (existingListener instanceof ConnectionPreservingOperationListener)
        {
            return ((ConnectionPreservingOperationListener) existingListener).delegate;
        }
        return existingListener != null ? existingListener : new DefaultOperationListener();
    }

    /**
     * Returns whether {@code listener}, after unwrapping any
     * {@link ConnectionPreservingOperationListener} layers, is
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} - the established, pre-existing signal
     * that a tester's connection is managed elsewhere and must not be closed by whatever is
     * driving the tester's lifecycle.
     *
     * @param listener The listener to inspect, possibly a wrapper or {@code null}.
     * @return {@code true} when it unwraps to the no-op listener.
     */
    public static boolean unwrapsToNoOp(final IOperationListener listener)
    {
        if (listener == IOperationListener.NO_OP_OPERATION_LISTENER)
        {
            return true;
        }
        if (listener instanceof ConnectionPreservingOperationListener)
        {
            return unwrapsToNoOp(((ConnectionPreservingOperationListener) listener).delegate);
        }
        return false;
    }
}
