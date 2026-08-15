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
package org.dbunit.database.rowcount;

import java.sql.SQLException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;

/**
 * Manages a {@link RowCountCheck} baseline across one caller's test lifecycle: lazily
 * resolves a {@link RowCountCheck} from a connection's
 * {@link org.dbunit.database.DatabaseConfig} on first use (unless one is supplied),
 * captures a baseline, verifies it later, and lets the baseline be discarded when a
 * verify would be noise - e.g. the caller's own test steps already failed, so the
 * database is in an unknown state and a count difference is not a finding worth its own
 * report.
 * <p>
 * Holds no connection of its own; every method takes the connection to use, leaving
 * acquisition and closing entirely to the caller.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class RowCountChecker
{
    private RowCountCheck rowCountCheck;
    private RowCountSnapshot baseline;

    /**
     * Captures the baseline using the given connection, resolving a {@link RowCountCheck}
     * from its {@link org.dbunit.database.DatabaseConfig} first if none has been resolved
     * or set yet.
     *
     * @param connection the connection to capture the baseline from.
     * @throws DatabaseUnitException if enumerating or filtering the tables fails.
     * @throws SQLException if counting a table's rows fails.
     */
    public void capture(final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        baseline = resolve(connection).capture(connection);
    }

    /**
     * Verifies the captured baseline against the given connection's current row counts.
     * A no-op that never queries the connection when no baseline was captured - the check
     * is disabled, {@link #capture(IDatabaseConnection)} was never called, or
     * {@link #discardBaseline()} was.
     *
     * @param connection the connection to read the current row counts from.
     * @throws DatabaseUnitException if enumerating or filtering the tables fails, or if any
     *             table's row count no longer matches the baseline
     *             ({@link UnexpectedRowCountException}).
     * @throws SQLException if counting a table's rows fails.
     */
    public void verify(final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        if (baseline == null)
        {
            return;
        }
        resolve(connection).verify(baseline, connection);
    }

    /**
     * Discards the captured baseline, so a later {@link #verify(IDatabaseConnection)} call
     * skips silently instead of comparing against it.
     */
    public void discardBaseline()
    {
        baseline = null;
    }

    /**
     * Returns whether a baseline is currently held, so a caller can tell there is nothing to
     * verify - e.g. to skip acquiring a connection for {@link #verify(IDatabaseConnection)}
     * entirely - without needing one just to ask.
     *
     * @return {@code true} when a baseline was captured and neither consumed by
     *         {@link #discardBaseline()} nor left uncaptured because the check was disabled.
     */
    public boolean hasBaseline()
    {
        return baseline != null;
    }

    private RowCountCheck resolve(final IDatabaseConnection connection)
    {
        if (rowCountCheck == null)
        {
            rowCountCheck =
                    new RowCountCheck(new RowCountCheckConfiguration(connection.getConfig()));
        }
        return rowCountCheck;
    }

    /**
     * Returns the {@link RowCountCheck} in use.
     *
     * @return the row count check, or {@code null} if none has been resolved or set yet.
     */
    public RowCountCheck getRowCountCheck()
    {
        return rowCountCheck;
    }

    /**
     * Sets the {@link RowCountCheck} to use, overriding the one otherwise lazily built from
     * a connection's DatabaseConfig on first use.
     *
     * @param rowCountCheck the row count check to use.
     */
    public void setRowCountCheck(final RowCountCheck rowCountCheck)
    {
        this.rowCountCheck = rowCountCheck;
    }
}
