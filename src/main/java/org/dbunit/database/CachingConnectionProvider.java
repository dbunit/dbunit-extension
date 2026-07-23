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

package org.dbunit.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches a single {@link IDatabaseConnection} so that it - and the table
 * metadata it accumulates - can be reused across many test methods instead of
 * being rebuilt on every call.
 *
 * <p>
 * A {@link Callable} supplies the connection-creation logic; the factory is
 * only invoked when there is no cached connection yet, or when the
 * previously cached one is no longer {@linkplain Connection#isValid(int)
 * alive}. A dead connection is closed and transparently replaced rather than
 * returned to the caller or left for the next call to fail on.
 *
 * <p>
 * <b>Usage.</b> Construct one instance per target database and share that
 * same instance across the {@link org.dbunit.JdbcDatabaseTester},
 * {@link org.dbunit.DataSourceDatabaseTester}, {@link org.dbunit.JndiDatabaseTester}
 * or {@link org.dbunit.DefaultDatabaseTester} instances that are created for
 * each test, for example via a {@code static} field on a common test base
 * class:
 *
 * <pre>
 * private static final CachingConnectionProvider CONNECTION_PROVIDER =
 *         new CachingConnectionProvider();
 *
 * &#64;BeforeEach
 * void setUp() throws Exception
 * {
 *     final IDatabaseTester tester = new JdbcDatabaseTester(driverClass,
 *             connectionUrl, username, password, schema, CONNECTION_PROVIDER);
 *     // The default IOperationListener closes the connection after every
 *     // onSetup()/onTearDown() call, which would defeat the cache. Pair it
 *     // with a listener that leaves the connection open, e.g.:
 *     tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
 *     ...
 * }
 * </pre>
 *
 * <p>
 * <b>Thread safety.</b> Access to the cached connection is synchronized, so
 * concurrent callers cannot create or replace it at the same time. The
 * returned {@link IDatabaseConnection} - and the underlying JDBC
 * {@link Connection} it wraps - is however not synchronized, so this class is
 * only appropriate for test suites that run sequentially, not for test
 * methods executing concurrently against the same cached connection.
 *
 * <p>
 * <b>Metadata staleness.</b> Because the whole point of reuse is to avoid
 * re-fetching table metadata, a cached connection's {@code DatabaseDataSet}
 * does not notice schema changes (new/dropped/altered tables) made after it
 * was first cached. Do not share a provider across tests that alter DDL
 * mid-run.
 *
 * @since 3.4.0
 */
public class CachingConnectionProvider
{
    private static final Logger logger =
            LoggerFactory.getLogger(CachingConnectionProvider.class);

    /**
     * Default number of seconds {@link #getConnection(Callable)} allows
     * {@link Connection#isValid(int)} to take when checking whether the
     * cached connection is still alive.
     */
    public static final int DEFAULT_VALIDATION_TIMEOUT_SECONDS = 5;

    private final int validationTimeoutSeconds;

    private IDatabaseConnection connection;

    /**
     * Creates a provider that validates the cached connection with the
     * {@link #DEFAULT_VALIDATION_TIMEOUT_SECONDS default validation timeout}.
     */
    public CachingConnectionProvider()
    {
        this(DEFAULT_VALIDATION_TIMEOUT_SECONDS);
    }

    /**
     * Creates a provider that validates the cached connection with the given
     * timeout.
     *
     * @param validationTimeoutSeconds
     *            The number of seconds {@link Connection#isValid(int)} is
     *            allowed to take when checking whether the cached connection
     *            is still alive. Zero means no timeout is applied.
     */
    public CachingConnectionProvider(final int validationTimeoutSeconds)
    {
        if (validationTimeoutSeconds < 0)
        {
            throw new IllegalArgumentException("The parameter "
                    + "'validationTimeoutSeconds' must not be negative");
        }
        this.validationTimeoutSeconds = validationTimeoutSeconds;
    }

    /**
     * Returns the cached connection, creating it with the given factory on
     * the first call and again whenever the previously cached connection is
     * no longer alive.
     *
     * @param connectionFactory
     *            Creates a new connection. Only invoked when there is no live
     *            cached connection to reuse.
     * @return The cached, live connection.
     * @throws Exception
     *             If {@code connectionFactory} throws while creating a new
     *             connection.
     */
    public synchronized IDatabaseConnection getConnection(
            final Callable<IDatabaseConnection> connectionFactory) throws Exception
    {
        if (connection != null && isAlive(connection))
        {
            logger.debug("getConnection() - reusing cached connection {}", connection);
            return connection;
        }

        if (connection != null)
        {
            logger.debug("getConnection() - cached connection {} is no longer"
                    + " alive, replacing it", connection);
            closeQuietly(connection);
            connection = null;
        }

        connection = connectionFactory.call();
        return connection;
    }

    /**
     * Closes and discards the cached connection, if any. The next call to
     * {@link #getConnection(Callable)} creates a fresh one.
     *
     * @throws SQLException
     *             If closing the cached connection fails.
     */
    public synchronized void close() throws SQLException
    {
        if (connection == null)
        {
            return;
        }

        try
        {
            connection.close();
        }
        finally
        {
            connection = null;
        }
    }

    private boolean isAlive(final IDatabaseConnection candidate)
    {
        try
        {
            final Connection jdbcConnection = candidate.getConnection();
            return !jdbcConnection.isClosed()
                    && jdbcConnection.isValid(validationTimeoutSeconds);
        } catch (final SQLException e)
        {
            logger.debug("isAlive() - liveness check failed for connection {}",
                    candidate, e);
            return false;
        }
    }

    private void closeQuietly(final IDatabaseConnection candidate)
    {
        try
        {
            candidate.close();
        } catch (final SQLException e)
        {
            logger.warn("closeQuietly() - exception while closing the stale"
                    + " cached connection", e);
        }
    }

    @Override
    public synchronized String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("validationTimeoutSeconds=").append(validationTimeoutSeconds);
        sb.append(", connection=").append(connection);
        sb.append("]");
        return sb.toString();
    }
}
