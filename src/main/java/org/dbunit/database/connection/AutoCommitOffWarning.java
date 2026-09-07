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

import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs one warning, the first time it sees a connection whose autocommit is disabled, for a
 * connection dbUnit's own setup and teardown operations will run on. Those operations
 * ({@code CLEAN_INSERT}, {@code DELETE_ALL}, ...) do not manage a transaction of their own, and
 * {@link org.dbunit.operation.TransactionOperation} refuses a connection whose autocommit is
 * already off, so on such a connection their writes are never committed: invisible to other
 * connections, and - when the connection is kept across test methods - held as locks a
 * database's idle-in-transaction timeout may terminate mid-run.
 *
 * <p>Used as the {@code onAcquired} hook of a {@link TestScopedConnection} so the check runs
 * once per acquired connection at the single acquisition point, whichever path - the annotation
 * runtime's setup/teardown handling, or {@link org.dbunit.DefaultPrepAndExpectedTestCase} -
 * borrowed it. One instance per lifecycle owner; it warns at most once regardless of how many
 * times the connection is (re)acquired.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AutoCommitOffWarning implements Consumer<IDatabaseConnection>
{
    private final Logger log = LoggerFactory.getLogger(AutoCommitOffWarning.class);

    private boolean isAlreadyWarned;

    /**
     * Warns when {@code connection}'s autocommit is off, unless a warning has already been
     * logged by this instance or the autocommit state cannot be read.
     *
     * @param connection The connection just acquired.
     */
    @Override
    public void accept(final IDatabaseConnection connection)
    {
        if (isAlreadyWarned)
        {
            return;
        }
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            if (jdbcConnection == null || jdbcConnection.getAutoCommit())
            {
                return;
            }
        } catch (final SQLException e)
        {
            log.debug("AutoCommitOffWarning: could not read the connection's autocommit state",
                    e);
            return;
        }

        isAlreadyWarned = true;
        log.warn("The connection this test's dbUnit setup/teardown operations run on has"
                + " autocommit disabled. Those operations (CLEAN_INSERT, DELETE_ALL, ...) do"
                + " not manage a transaction, and TransactionOperation refuses a non-autocommit"
                + " connection, so the prep and teardown writes are never committed - invisible"
                + " to other connections, and (for a connection shared across test methods)"
                + " held as locks the database's idle-in-transaction timeout may terminate"
                + " mid-run. Use a connection with autocommit enabled.");
    }
}
