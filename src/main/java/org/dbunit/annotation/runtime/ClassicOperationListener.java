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

import java.util.function.Consumer;

import org.dbunit.IOperationListener;
import org.dbunit.database.IDatabaseConnection;

/**
 * The {@link IOperationListener} {@link AnnotatedTestExecutor} installs on the tester for a
 * classic test, one that opted into none of the {@code org.dbunit.annotation} family. It
 * forwards every callback to its delegate - the tester's own listener, and the documented place
 * for it to configure the connection's {@code DatabaseConfig} (e.g. to enable the row count
 * check or set a custom {@code RowCounter}) - and, after the delegate's
 * {@code connectionRetrieved}, offers the connection to {@code onConnectionRetrieved} for the
 * row count check baseline capture (a no-op after the first call). Reading
 * {@code DatabaseConfig} last this way means the row count check always sees the delegate's
 * configuration already applied, never a stale value from before it ran. It shields no
 * connection from a close the delegate makes: the connection {@code onSetup()} retrieved stays
 * the tester's listener's to close or to keep open, as in the 3.5.0 lifecycle.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class ClassicOperationListener implements IOperationListener
{
    private final IOperationListener delegate;
    private final Consumer<IDatabaseConnection> onConnectionRetrieved;

    private boolean active = true;

    /**
     * Creates the listener.
     *
     * @param delegate The tester's own listener to forward every callback to.
     * @param onConnectionRetrieved Offered each retrieved connection, after the delegate has
     *            run, until {@link #deactivate()}.
     */
    ClassicOperationListener(final IOperationListener delegate,
            final Consumer<IDatabaseConnection> onConnectionRetrieved)
    {
        this.delegate = delegate;
        this.onConnectionRetrieved = onConnectionRetrieved;
    }

    /**
     * Returns the listener this one forwards to.
     *
     * @return The tester's own listener.
     */
    IOperationListener getDelegate()
    {
        return delegate;
    }

    /**
     * Makes this listener only forward every callback to its delegate from now on, as the
     * executor that installed it is finished. A test can wrap the tester's listener mid-test,
     * so this one stays in the chain after the executor puts back what it can; left active it
     * would offer the next test's connection to a baseline capture nobody will verify.
     */
    void deactivate()
    {
        active = false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate, then offers the connection to the {@code onConnectionRetrieved}
     * callback. After {@link #deactivate()} it forwards only.
     */
    @Override
    public void connectionRetrieved(final IDatabaseConnection connection)
    {
        delegate.connectionRetrieved(connection);
        if (active)
        {
            onConnectionRetrieved.accept(connection);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate only.
     */
    @Override
    public void operationSetUpFinished(final IDatabaseConnection connection)
    {
        delegate.operationSetUpFinished(connection);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to the delegate only.
     */
    @Override
    public void operationTearDownFinished(final IDatabaseConnection connection)
    {
        delegate.operationTearDownFinished(connection);
    }
}
