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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.filter.ExcludeTableFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares a database's table row counts before and after a test, to catch a table the test
 * left dirty - either one it should have cleaned up and did not, or a reference table it wrongly
 * cleaned. Read-only: never modifies data.
 * <p>
 * Every method is a no-op, and never queries the connection, when
 * {@link RowCountCheckConfiguration#isEnabled()} is {@code false} - the check costs nothing
 * when a caller has not opted in.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class RowCountCheck
{
    private final Logger log = LoggerFactory.getLogger(RowCountCheck.class);

    private final RowCountCheckConfiguration configuration;

    /**
     * Creates a check using the given configuration.
     *
     * @param configuration resolves whether the check is enabled, the excluded table patterns,
     *            and the {@link RowCounter} to use.
     */
    public RowCountCheck(final RowCountCheckConfiguration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * Captures the baseline row counts to later {@link #verify(RowCountSnapshot, IDatabaseConnection)}
     * against, of every table {@code connection} exposes that survives the configured exclude
     * patterns.
     *
     * @param connection the connection to capture the baseline from.
     * @return the baseline snapshot, or {@code null} when the check is disabled.
     * @throws DatabaseUnitException if enumerating or filtering the tables fails.
     * @throws SQLException if counting a table's rows fails.
     */
    public RowCountSnapshot capture(final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        return configuration.isEnabled() ? snapshot(connection) : null;
    }

    /**
     * Verifies that {@code connection}'s current row counts still match {@code baseline}.
     * A no-op that never queries the connection when the check is disabled or when
     * {@code baseline} is {@code null} - the latter meaning no baseline was captured, e.g.
     * because the caller intentionally skipped {@link #capture(IDatabaseConnection)}.
     *
     * @param baseline the baseline to compare against; {@code null} to skip the check silently.
     * @param connection the connection to read the current row counts from.
     * @throws DatabaseUnitException if enumerating or filtering the tables fails, or if any
     *             table's row count no longer matches the baseline
     *             ({@link UnexpectedRowCountException}).
     * @throws SQLException if counting a table's rows fails.
     */
    public void verify(final RowCountSnapshot baseline, final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        if (!configuration.isEnabled() || baseline == null)
        {
            return;
        }

        final RowCountSnapshot current = snapshot(connection);
        final List<RowCountDifference> differences = baseline.difference(current);
        if (!differences.isEmpty())
        {
            throw new UnexpectedRowCountException(differences);
        }
    }

    private RowCountSnapshot snapshot(final IDatabaseConnection connection)
            throws DatabaseUnitException, SQLException
    {
        try
        {
            final String[] allTableNames = connection.createDataSet().getTableNames();
            final ExcludeTableFilter excludeTableFilter = configuration.getExcludeTableFilter();

            final List<String> tableNames = new ArrayList<>();
            for (final String tableName : allTableNames)
            {
                if (excludeTableFilter.isValidName(tableName))
                {
                    tableNames.add(tableName);
                }
            }

            final Map<String, Integer> rowCounts =
                    configuration.getRowCounter().countRows(connection, tableNames);
            return new RowCountSnapshot(rowCounts);
        }
        finally
        {
            endReadTransaction(connection);
        }
    }

    /**
     * Ends the transaction the read-only snapshot queries (the {@code SELECT COUNT(*)}s and the
     * metadata reads behind {@link IDatabaseConnection#createDataSet()}) opened, when the
     * connection's autocommit is off, so a check that modified nothing does not leave that
     * connection sitting idle in a transaction - holding locks, and a candidate for the
     * database's {@code idle_in_transaction_session_timeout}. This matters when the connection
     * outlives the check: shared through a {@link org.dbunit.database.CachingConnectionProvider}
     * and not closed after each test.
     * <p>
     * A rollback rather than a commit: the snapshot wrote nothing of its own, so there is
     * nothing to keep, and a rollback cannot disturb work a caller committed before calling in.
     * A best-effort attempt - a snapshot that already failed, or a connection the database has
     * dropped, is the caller's to handle.
     *
     * @param connection the connection the snapshot queried.
     */
    private void endReadTransaction(final IDatabaseConnection connection)
    {
        try
        {
            final Connection jdbcConnection = connection.getConnection();
            if (jdbcConnection != null && !jdbcConnection.getAutoCommit())
            {
                jdbcConnection.rollback();
            }
        }
        catch (final SQLException e)
        {
            log.debug("endReadTransaction: could not roll back the row count snapshot's"
                    + " read transaction", e);
        }
    }
}
