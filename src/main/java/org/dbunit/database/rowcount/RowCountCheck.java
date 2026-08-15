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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.filter.ExcludeTableFilter;

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
        if (!configuration.isEnabled())
        {
            return null;
        }
        return snapshot(connection);
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
}
