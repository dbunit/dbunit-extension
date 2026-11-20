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
 * Logs one warning, the first time it sees a connection whose autocommit is disabled, for a
 * connection dbUnit's own setup and teardown operations will run on. Those operations
 * ({@code CLEAN_INSERT}, {@code DELETE_ALL}, ...) do not manage a transaction of their own, and
 * {@link org.dbunit.operation.TransactionOperation} refuses a connection whose autocommit is
 * already off, so on such a connection their writes are never committed: invisible to other
 * connections, and - when the connection is kept across test methods - held as locks a
 * database's idle-in-transaction timeout may terminate mid-run.
 *
 * <p>Used as the {@code onAcquired} hook of a {@link TestScopedConnection} so the check runs
 * once per acquired connection at the single acquisition point, whichever lifecycle owner
 * borrowed it, such as {@link org.dbunit.DefaultPrepAndExpectedTestCase}. One instance per
 * lifecycle owner; it warns at most once regardless of how many times the connection is
 * (re)acquired, and once for a tester however many lifecycle owners it serves, as it is the
 * tester that hands out connections with autocommit disabled. Without a tester to name, an
 * instance warns once for itself.
 *
 * <p>This type is internal to dbUnit's bindings; it is public only for use across
 * dbUnit's packages and may change in a minor release.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class AutoCommitOffWarning implements Consumer<IDatabaseConnection>
{
    private static final Logger log = LoggerFactory.getLogger(AutoCommitOffWarning.class);

    private static final WarnedTesters TESTERS_WARNED_IN_THE_JVM = new WarnedTesters();

    private final Supplier<IDatabaseTester> tester;

    private boolean isAlreadyWarned;

    /**
     * Creates the warning for one lifecycle owner.
     *
     * @param tester Supplies the database tester the connection comes from, read fresh each
     *            time as it can change between tests; may supply {@code null}, for which the
     *            warning is given once for this instance.
     */
    public AutoCommitOffWarning(final Supplier<IDatabaseTester> tester)
    {
        this.tester = tester;
    }

    /**
     * Warns when {@code connection}'s autocommit is off, unless a warning has already been
     * logged by this instance or for its tester, or the autocommit state cannot be read.
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
        final IDatabaseTester databaseTester = tester.get();
        if (databaseTester != null)
        {
            final boolean isFirstWarning =
                    TESTERS_WARNED_IN_THE_JVM.isFirstWarningAbout(databaseTester);
            if (!isFirstWarning)
            {
                return;
            }
        }
        log.warn("The connection this test's dbUnit setup/teardown operations run on has"
                + " autocommit disabled. Those operations (CLEAN_INSERT, DELETE_ALL, ...) do"
                + " not manage a transaction, and TransactionOperation refuses a non-autocommit"
                + " connection, so the prep and teardown writes are never committed - invisible"
                + " to other connections, and (for a connection shared across test methods)"
                + " held as locks the database's idle-in-transaction timeout may terminate"
                + " mid-run. Use a connection with autocommit enabled.");
    }
}
