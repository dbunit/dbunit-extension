/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

import java.util.concurrent.Callable;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;

/**
 * Default implementation of AbstractDatabaseTester, which does not know how
 * to get a connection by itself.
 *
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since 2.2
 */
public class DefaultDatabaseTester extends AbstractDatabaseTester
{
    private final IDatabaseConnection connection;
    private final CachingConnectionProvider connectionProvider;
    private final Callable<IDatabaseConnection> connectionFactory;

    /**
     * Creates a new DefaultDatabaseTester with the supplied connection.<br>
     * The same connection instance is returned by every {@link #getConnection()}
     * call, with no liveness check. Pair this with a non-closing
     * {@link IOperationListener} (e.g. {@link IOperationListener#NO_OP_OPERATION_LISTENER})
     * to keep it open across test methods.
     *
     * @param connection the connection to return from every {@link #getConnection()} call
     */
    public DefaultDatabaseTester(final IDatabaseConnection connection)
    {
        this.connection = connection;
        this.connectionProvider = null;
        this.connectionFactory = null;
    }

    /**
     * Creates a new DefaultDatabaseTester that reuses one connection - created
     * with the given factory - across calls via the given
     * {@link CachingConnectionProvider}, transparently replacing it if it is no
     * longer alive.<br>
     * Share the same <code>connectionProvider</code> instance across the testers
     * created for each test to get reuse across test methods; pair it with
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER} (or an equivalent
     * non-closing listener) so the cached connection is not closed after every
     * {@link #onSetup()}/{@link #onTearDown()} call.
     *
     * @param connectionProvider caches and validates the connection across calls
     * @param connectionFactory creates a new connection; only invoked by
     *            <code>connectionProvider</code> when there is no live cached
     *            connection to reuse
     * @since 3.4.0
     */
    public DefaultDatabaseTester(final CachingConnectionProvider connectionProvider,
            final Callable<IDatabaseConnection> connectionFactory)
    {
        if (connectionProvider == null)
        {
            throw new NullPointerException(
                    "The parameter 'connectionProvider' must not be null");
        }
        if (connectionFactory == null)
        {
            throw new NullPointerException(
                    "The parameter 'connectionFactory' must not be null");
        }
        this.connection = null;
        this.connectionProvider = connectionProvider;
        this.connectionFactory = connectionFactory;
    }

    public IDatabaseConnection getConnection() throws Exception
    {
        if (connectionProvider != null)
        {
            return connectionProvider.getConnection(connectionFactory);
        }
        return this.connection;
    }
}
