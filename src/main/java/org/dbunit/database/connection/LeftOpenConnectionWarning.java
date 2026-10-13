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

import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs one warning, the first time it sees a connection that is going to be left open while
 * the {@link JdbcDatabaseTester} that supplied it creates a new connection on every call. A
 * connection is left open for its real owner to close, as
 * {@link ConnectionOwnership#isLeftOpenByConfiguration()} decides: the
 * {@code closeConnectionAfterTest} flag is {@code false}, or an operation listener is the
 * no-op one. A {@link JdbcDatabaseTester} built without a
 * {@link org.dbunit.database.CachingConnectionProvider} has no such owner: nothing else holds
 * the connection it just created, and the next call creates another. A test case used for one
 * test leaves one open after it, and one reused across tests keeps one and leaves another each
 * time a step fails, so a long run can exhaust the database's connections while its tests
 * pass.
 *
 * <p>Only a {@link JdbcDatabaseTester} is judged, through
 * {@link JdbcDatabaseTester#isReusingConnection()}. Another tester, such as a
 * {@link org.dbunit.DataSourceDatabaseTester}, cannot be told apart from one whose data source
 * hands back the same connection, as a transaction-aware one does, so it is left alone.
 *
 * <p>Used as an {@code onAcquired} hook of a {@link TestScopedConnection}, next to
 * {@link AutoCommitOffWarning}, so the check runs where a connection arrives. One instance per
 * lifecycle owner; it warns at most once regardless of how many times the connection is
 * (re)acquired. The tester is checked before the ownership is asked anything, so a tester that
 * is not a {@link JdbcDatabaseTester} sees no extra calls.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class LeftOpenConnectionWarning implements Consumer<IDatabaseConnection>
{
    private static final Logger log = LoggerFactory.getLogger(LeftOpenConnectionWarning.class);

    private final ConnectionOwnership ownership;
    private final Supplier<IDatabaseTester> tester;

    private boolean isAlreadyWarned;

    /**
     * Creates the warning for one lifecycle owner.
     *
     * @param ownership Decides whether the configuration leaves the connection open.
     * @param tester Supplies the database tester the connection comes from, read fresh each
     *            time as it can change between tests; may supply {@code null}.
     */
    public LeftOpenConnectionWarning(final ConnectionOwnership ownership,
            final Supplier<IDatabaseTester> tester)
    {
        this.ownership = ownership;
        this.tester = tester;
    }

    /**
     * Warns when the connection is going to be left open and its tester creates a new
     * connection on every call, unless a warning has already been logged by this instance.
     *
     * @param connection The connection just acquired; not inspected, as it is the tester and
     *            the ownership that decide.
     */
    @Override
    public void accept(final IDatabaseConnection connection)
    {
        if (isAlreadyWarned)
        {
            return;
        }
        final IDatabaseTester databaseTester = tester.get();
        if (!(databaseTester instanceof JdbcDatabaseTester))
        {
            return;
        }
        final JdbcDatabaseTester jdbcTester = (JdbcDatabaseTester) databaseTester;
        final boolean isReusingConnection = jdbcTester.isReusingConnection();
        if (isReusingConnection)
        {
            return;
        }
        final boolean isLeftOpen = ownership.isLeftOpenByConfiguration();
        if (!isLeftOpen)
        {
            return;
        }

        isAlreadyWarned = true;
        log.warn("The connection this test's dbUnit steps share is left open after the test,"
                + " because closeConnectionAfterTest is false or an operation listener is the"
                + " no-op listener. The database tester, {}, was built without a"
                + " CachingConnectionProvider, so it creates a new connection on every call and"
                + " nothing closes the one left open: a test case used for one test leaves a"
                + " connection open after it, and one reused across tests keeps one and leaves"
                + " another open each time a step fails, so a long run can exhaust the"
                + " database's connections. Build the tester with a CachingConnectionProvider,"
                + " so it hands out one connection that its owner closes when the run ends, or"
                + " let dbUnit close the connection: leave closeConnectionAfterTest true and do"
                + " not use the no-op listener.", jdbcTester.getClass().getName());
    }
}
