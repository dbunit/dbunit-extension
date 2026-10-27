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
import org.dbunit.database.AbstractDatabaseConnection;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseDataSet;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.filter.ExcludeTableFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares a database's table row counts before and after a test, to catch a table the test
 * left dirty - either one it should have cleaned up and did not, or a reference table it wrongly
 * cleaned. Writes no data of its own, but on an autocommit-off connection it ends its read
 * transaction with a rollback, which discards uncommitted work.
 * <p>
 * Every method is a no-op, and never queries the connection, when
 * {@link RowCountCheckConfiguration#isEnabled()} is {@code false} - the check costs nothing
 * when a caller has not opted in.
 * <p>
 * It counts the tables its connection lists. A dbUnit connection, an
 * {@link AbstractDatabaseConnection}, keeps the dataset of its first listing for its lifetime, so
 * the check lists the tables of such a connection anew for each snapshot, through the metadata of
 * its JDBC connection. The check therefore counts a table the test creates or drops as empty in
 * the snapshot it is missing from, and lists a connection that narrows its dataset by overriding
 * {@link IDatabaseConnection#createDataSet()} in full, which leaves such tables to the exclude
 * patterns. It lists any other implementation of {@link IDatabaseConnection} through its own
 * {@code createDataSet()}. A connection that names no schema is counted through the current
 * schema of its JDBC connection when the driver reports one, so the check does not meet the
 * system schemas of a database such as H2, or the tables of other schemas, which it cannot count
 * by their unqualified names. When the driver reports none, the connection is counted as it is.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class RowCountCheck
{
    private static final Logger log = LoggerFactory.getLogger(RowCountCheck.class);

    private final RowCountCheckConfiguration configuration;

    /**
     * Creates a check using the given configuration.
     *
     * @param configuration Resolves whether the check is enabled, the excluded table patterns,
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
     * @param connection The connection to capture the baseline from.
     * @return The baseline snapshot, or {@code null} when the check is disabled.
     * @throws DatabaseUnitException If enumerating or filtering the tables fails.
     * @throws SQLException If counting a table's rows fails.
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
     * @param baseline The baseline to compare against; {@code null} to skip the check silently.
     * @param connection The connection to read the current row counts from.
     * @throws DatabaseUnitException If enumerating or filtering the tables fails, or if any
     *             table's row count no longer matches the baseline
     *             ({@link UnexpectedRowCountException}).
     * @throws SQLException If counting a table's rows fails.
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
            final IDatabaseConnection countingConnection = connectionToCountThrough(connection);
            final String[] allTableNames = tableNamesListedAfresh(countingConnection);
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
                    configuration.getRowCounter().countRows(countingConnection, tableNames);
            return new RowCountSnapshot(rowCounts);
        }
        finally
        {
            endReadTransaction(connection);
        }
    }

    /**
     * Returns the connection to list and count the tables through: {@code connection} itself,
     * unless it names no schema and its JDBC connection reports a current one, in which case a
     * view of it that names that schema. Without a schema a connection lists the tables of every
     * schema it can see, the system schemas of a database such as H2 among them, and counts them
     * by names that carry no schema, so a table of another schema cannot be counted.
     *
     * @param connection The connection the check was asked to count.
     * @return The connection to count through.
     * @throws SQLException If reading the JDBC connection fails.
     */
    private IDatabaseConnection connectionToCountThrough(final IDatabaseConnection connection)
            throws SQLException
    {
        final String currentSchema = currentSchemaOf(connection);
        if (currentSchema == null)
        {
            return connection;
        }

        log.debug("connectionToCountThrough: the connection names no schema, so counting the"
                + " tables of the current schema '{}'", currentSchema);
        return new CurrentSchemaConnection(connection, currentSchema);
    }

    /**
     * Returns the current schema of the JDBC connection of a connection that names no schema,
     * or null when there is none to count in: the connection names a schema, or its table names
     * already carry theirs, or it has no JDBC connection, or the driver reports no current
     * schema, fails to report it, or predates JDBC 4.1 and cannot.
     *
     * @param connection The connection the check was asked to count.
     * @return The current schema, or null if the connection is to be counted as it is.
     * @throws SQLException If reading the JDBC connection of the connection fails.
     */
    private String currentSchemaOf(final IDatabaseConnection connection) throws SQLException
    {
        if (connection.getSchema() != null)
        {
            return null;
        }

        final Connection jdbcConnection = connection.getConnection();
        if (jdbcConnection == null)
        {
            return null;
        }

        final String currentSchema = currentSchemaReportedBy(jdbcConnection);
        if (currentSchema == null || currentSchema.isEmpty())
        {
            return null;
        }

        final boolean tableNamesCarryTheirSchema =
                connection.getConfig().getFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES);
        if (tableNamesCarryTheirSchema)
        {
            return null;
        }
        return currentSchema;
    }

    /**
     * Lists the tables of a connection for one snapshot. An {@link AbstractDatabaseConnection}
     * keeps the dataset of its first listing for its lifetime, so this lists the tables of such a
     * connection anew, through a new {@link DatabaseDataSet} like the one its first listing built,
     * and sees the tables that exist when the snapshot is taken, whether or not the connection
     * names a schema. It lists any other implementation of {@link IDatabaseConnection} through
     * its own {@link IDatabaseConnection#createDataSet()}, as it cannot know how that one lists.
     *
     * @param connection The connection to count through.
     * @return The names of the tables the connection lists.
     * @throws SQLException If reading the metadata of the connection fails.
     * @throws DataSetException If listing the tables fails.
     */
    private static String[] tableNamesListedAfresh(final IDatabaseConnection connection)
            throws SQLException, DataSetException
    {
        if (connection instanceof AbstractDatabaseConnection)
        {
            final boolean caseSensitiveTableNames = connection.getConfig()
                    .getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES);
            final IDataSet dataSet = new DatabaseDataSet(connection, caseSensitiveTableNames);
            return dataSet.getTableNames();
        }

        final IDataSet dataSet = connection.createDataSet();
        return dataSet.getTableNames();
    }

    private String currentSchemaReportedBy(final Connection jdbcConnection)
    {
        try
        {
            return jdbcConnection.getSchema();
        }
        catch (final SQLException | AbstractMethodError e)
        {
            log.debug("currentSchemaReportedBy: the JDBC connection does not report its current"
                    + " schema", e);
            return null;
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
     * It does discard work the caller left uncommitted on the connection, which is why the
     * check is not meant for a connection carrying a pending transaction of its own. A
     * best-effort attempt - a snapshot that already failed, or a connection the database has
     * dropped, is the caller's to handle.
     *
     * @param connection The connection the snapshot queried.
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
