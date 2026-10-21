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
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs one warning, the first time its tester hands a test a connection that is already closed.
 * A tester that hands out one fixed connection from every call hands it out closed once
 * something has closed it, and the test then fails on it with nothing that says why. Two closes
 * leave it closed:
 *
 * <ul>
 * <li>The tester's operation listener closes it after an operation, as the
 * {@link org.dbunit.DefaultOperationListener} does after each setup and teardown operation, the
 * same as in the 3.5.0 lifecycle.</li>
 * <li>dbUnit closes it after an earlier test. A {@link org.dbunit.DefaultPrepAndExpectedTestCase},
 * which every annotation-driven test runs through, closes the connection it shares unless the
 * tester's listener is the no-op one or its {@code closeConnectionAfterTest} is {@code false}.
 * The classic lifecycle of a bare {@code @ExtendWith(DbUnitExtension.class)} class closes a
 * connection it resolved itself unless the tester is known to hand out one fixed connection or
 * its listener is the no-op listener; a custom tester over one fixed connection is not known to,
 * as nothing tells it apart from one that opens a new connection on every call.</li>
 * </ul>
 *
 * <p>{@link org.dbunit.IOperationListener#NO_OP_OPERATION_LISTENER} keeps the connection open
 * from both, so the warning names it. The warning comes when a closed connection arrives, not
 * when it is closed: only then is it known that the tester hands out the same connection again,
 * and closing the connection of a tester that opens a new one on every call is right.
 *
 * <p>Offered every connection the tester hands the test, as an {@code onAcquired} hook of a
 * {@link TestScopedConnection} next to {@link AutoCommitOffWarning}, so the check runs where a
 * connection arrives, including one that is acquired again after the earlier one was found
 * closed. The classic lifecycle also offers it the connection the tester's own {@code onSetup()}
 * or {@code onTearDown()} retrieves. One instance for each lifecycle owner, which warns at most
 * once: a classic test, or a test case, which an annotation-driven test gets afresh for each test
 * unless one is injected.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class ClosedConnectionHandedOutWarning implements Consumer<IDatabaseConnection>
{
    private static final Logger log =
            LoggerFactory.getLogger(ClosedConnectionHandedOutWarning.class);

    private final Supplier<IDatabaseTester> tester;

    private boolean isAlreadyWarned;

    /**
     * Creates the warning for one lifecycle owner.
     *
     * @param tester Supplies the database tester that hands out the connections, named in the
     *            warning, read fresh each time as it can change between tests; may supply
     *            {@code null}.
     */
    public ClosedConnectionHandedOutWarning(final Supplier<IDatabaseTester> tester)
    {
        this.tester = tester;
    }

    /**
     * Warns when {@code connection} is already closed, unless this instance has warned already
     * or there is no tester to name.
     *
     * @param connection The connection the tester just handed out; may be {@code null}, as a
     *            test double may hand out none.
     */
    @Override
    public void accept(final IDatabaseConnection connection)
    {
        if (isAlreadyWarned || !isClosed(connection))
        {
            return;
        }
        final IDatabaseTester databaseTester = tester.get();
        if (databaseTester == null)
        {
            return;
        }

        isAlreadyWarned = true;
        log.warn("The database tester {} handed this test a connection that is already closed,"
                + " so each step of the test that uses it fails. A tester that hands out one"
                + " fixed connection from every call hands it out closed once something has"
                + " closed it: its operation listener, as the DefaultOperationListener closes it"
                + " after each setup and teardown operation, or dbUnit when an earlier test"
                + " ended, which closes the connection unless the tester's listener is the no-op"
                + " one. Keep the connection open: give the tester"
                + " IOperationListener.NO_OP_OPERATION_LISTENER, or return it from"
                + " getOperationListener() when the tester is not an AbstractDatabaseTester, or"
                + " call setCloseConnectionAfterTest(false) on a test case you build yourself.",
                databaseTester.getClass().getName());
    }

    /**
     * Returns whether the JDBC connection of {@code connection} reports that it is closed. A
     * connection that has no JDBC connection, or cannot report whether it is closed, is not
     * known to be.
     */
    private static boolean isClosed(final IDatabaseConnection connection)
    {
        if (connection == null)
        {
            return false;
        }
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            return jdbcConnection != null && jdbcConnection.isClosed();
        } catch (final SQLException e)
        {
            log.debug("ClosedConnectionHandedOutWarning: could not read whether the connection"
                    + " is closed", e);
            return false;
        }
    }
}
