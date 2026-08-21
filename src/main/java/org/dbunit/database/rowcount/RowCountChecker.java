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
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

/**
 * Manages a {@link RowCountCheck} baseline across one caller's test lifecycle: lazily
 * resolves a {@link RowCountCheck} on first use - from a connection's
 * {@link org.dbunit.database.DatabaseConfig} by default, layering an enabled flag and
 * excluded table patterns onto it instead when {@link #setEnabledOverride(boolean, String[])}
 * was called, or bypassing resolution entirely when {@link #setRowCountCheck(RowCountCheck)}
 * was - captures a baseline, verifies it later, and lets the baseline be discarded when a
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
    private Boolean enabledOverride;
    private String[] excludeOverride;

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
            final DatabaseConfig databaseConfig = enabledOverride == null
                    ? connection.getConfig()
                    : overlayEnabledOverride(connection.getConfig());
            rowCountCheck = new RowCountCheck(new RowCountCheckConfiguration(databaseConfig));
        }
        return rowCountCheck;
    }

    /**
     * Builds a {@link DatabaseConfig} carrying {@link #enabledOverride}/{@link #excludeOverride}
     * instead of {@code real}'s own {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK}/
     * {@link DatabaseConfig#PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES}, while still carrying over
     * {@code real}'s configured {@link RowCounter} - so the override controls only what it
     * declares, and the resulting {@link RowCountCheckConfiguration} still lets
     * {@code -Ddbunit.rowCountCheck} win outright, exactly as it would resolving directly from
     * {@code real}.
     *
     * @param real the connection's own DatabaseConfig to carry the RowCounter over from.
     * @return the overlay DatabaseConfig to resolve a RowCountCheck from.
     */
    private DatabaseConfig overlayEnabledOverride(final DatabaseConfig real)
    {
        final DatabaseConfig overlay = new DatabaseConfig();
        overlay.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, enabledOverride);
        overlay.setProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                excludeOverride);
        overlay.setProperty(DatabaseConfig.PROPERTY_ROW_COUNTER,
                real.getProperty(DatabaseConfig.PROPERTY_ROW_COUNTER));
        return overlay;
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

    /**
     * Sets the enabled flag and excluded table patterns to resolve a {@link RowCountCheck}
     * from, instead of a connection's own {@link org.dbunit.database.DatabaseConfig} - the
     * values an annotation such as {@code @DbUnitRowCountCheck} declares, carried over onto
     * whatever connection this checker ends up resolving one from, rather than a caller having
     * to resolve a connection of its own just to read its {@link RowCounter}.
     *
     * @param enabled whether the check is enabled.
     * @param exclude the excluded table patterns.
     * @since 3.6.0
     */
    public void setEnabledOverride(final boolean enabled, final String[] exclude)
    {
        this.enabledOverride = enabled;
        this.excludeOverride = exclude;
    }
}
