/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2017, DbUnit.org
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
package org.dbunit;

import org.dbunit.testutil.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Test Helper class for Executing DDL.
 *
 * @author Andrew Landsverk
 * @version $Revision$
 * @since DbUnit 2.6.0
 */
public final class DdlExecutor
{
    private static final Logger LOG =
            LoggerFactory.getLogger(DdlExecutor.class);

    private DdlExecutor()
    {
        // no instances
    }

    /**
     * Execute DDL from the file (by name) against the given {@link Connection},
     * dispatches to executeDdlFile and passes false for ignoreErrors.
     * 
     * @param ddlFileName
     *            The name of the DDL file to execute.
     * @param connection
     *            The {@link Connection} to execute the DDL against.
     * @param multiLineSupport
     *            If this DataSource supports passing in all the lines at once
     *            or if it needs to separate on ';'.
     * @throws Exception
     */
    public static void execute(final String ddlFileName,
            final Connection connection, final boolean multiLineSupport)
            throws Exception
    {
        execute(ddlFileName, connection, multiLineSupport, false);
    }

    /**
     * Execute DDL from the file (by name) against the given {@link Connection},
     * dispatches to executeDdlFile.
     * 
     * @param ddlFileName
     *            The name of the DDL file to execute.
     * @param connection
     *            The {@link Connection} to execute the DDL against.
     * @param multiLineSupport
     *            If this DataSource supports passing in all the lines at once
     *            or if it needs to separate on ';'.
     * @param ignoreErrors
     *            Set this to true if you want syntax errors to be ignored.
     * @throws Exception
     */
    public static void execute(final String ddlFileName,
            final Connection connection, final boolean multiLineSupport,
            final boolean ignoreErrors) throws Exception
    {
        final File ddlFile = TestUtils.getFile(ddlFileName);
        executeDdlFile(ddlFile, connection, multiLineSupport, ignoreErrors);
    }

    /**
     * Executes DDL from the {@link File} against the given {@link Connection}.
     * Retrieves the multiLineSupport parameter from the profile.
     * 
     * @param ddlFile
     *            The {@link File} object of the DDL file to execute.
     * @param connection
     *            The {@link Connection} to execute the DDL against.
     * @throws Exception
     */
    public static void executeDdlFile(final File ddlFile,
            final Connection connection) throws Exception
    {
        final boolean multiLineSupport = DatabaseEnvironment.getInstance()
                .getProfile().getProfileMultilineSupport();

        LOG.debug("Executing DDL from file={}, multiLineSupport={}", ddlFile,
                multiLineSupport);

        executeDdlFile(ddlFile, connection, multiLineSupport);
    }

    /**
     * Executes DDL from the {@link File} against the given {@link Connection}.
     * Retrieves the multiLineSupport parameter from the profile and passes
     * false for ignoreErrors.
     * 
     * @param ddlFile
     *            The {@link File} object of the DDL file to execute.
     * @param connection
     *            The {@link Connection} to execute the DDL against.
     * @param multiLineSupport
     *            If this DataSource supports passing in all the lines at once
     *            or if it needs to separate on ';'.
     * @throws Exception
     */
    public static void executeDdlFile(final File ddlFile,
            final Connection connection, final boolean multiLineSupport)
            throws Exception
    {
        executeDdlFile(ddlFile, connection, multiLineSupport, false);
    }

    /**
     * Execute DDL from the {@link File} against the given {@link Connection}.
     * 
     * @param ddlFile
     *            The {@link File} object of the DDL file to execute.
     * @param connection
     *            The {@link Connection} to execute the DDL against.
     * @param multiLineSupport
     *            If this DataSource supports passing in all the lines at once
     *            or if it needs to separate on ';'.
     * @param ignoreErrors
     *            Set this to true if you want syntax errors to be ignored.
     * @throws Exception
     */
    public static void executeDdlFile(final File ddlFile,
            final Connection connection, final boolean multiLineSupport,
            final boolean ignoreErrors) throws Exception
    {
        final String sql = readSqlFromFile(ddlFile);

        if (!multiLineSupport)
        {
            StringTokenizer tokenizer = new StringTokenizer(sql, ";");
            while (tokenizer.hasMoreTokens())
            {
                String token = tokenizer.nextToken();
                token = token.trim();
                if (token.length() > 0)
                {
                    executeSql(connection, token, ignoreErrors);
                }
            }
        } else
        {
            executeSql(connection, sql, ignoreErrors);
        }
    }

    /**
     * Execute an un-prepared SQL statement against the given
     * {@link Connection}, passes false to ignoreErrors.
     *
     * @param connection
     *            The {@link Connection} to execute against
     * @param sql
     *            The SQL {@link String} to execute
     * @throws SQLException
     */
    public static void executeSql(final Connection connection, final String sql)
            throws SQLException
    {
        executeSql(connection, sql, false);
    }

    /**
     * Execute an un-prepared SQL statement against the given
     * {@link Connection}.
     *
     * @param connection
     *            The {@link Connection} to execute against
     * @param sql
     *            The SQL {@link String} to execute
     * @param ignoreErrors
     *            Set this to true if you want syntax errors to be ignored.
     * @throws SQLException
     */
    public static void executeSql(final Connection connection, final String sql,
            final boolean ignoreErrors) throws SQLException
    {
        final Statement statement = connection.createStatement();
        try
        {
            LOG.debug("Executing SQL={}", sql);
            statement.execute(sql);
        } catch (SQLSyntaxErrorException exception)
        {
            if (!ignoreErrors)
            {
                throw exception;
            }
            LOG.debug("Ignoring error executing DDL={}",
                    exception.getMessage());
        } finally
        {
            statement.close();
        }
    }

    /**
     * Drops the specified tables from the given {@link Connection}. First drops
     * FK constraints that reference any of the specified tables to handle both
     * acyclic and cyclic FK graphs, then drops the tables themselves. Tables or
     * constraints that do not exist are silently ignored.
     *
     * @param connection
     *            The {@link Connection} to execute the DROP statements against.
     * @param tableNames
     *            The names of the tables to drop.
     * @throws Exception
     */
    public static void dropTables(final Connection connection,
            final String... tableNames) throws Exception
    {
        dropFkConstraintsAmong(connection, tableNames);
        for (final String table : tableNames)
        {
            try (final Statement stmt = connection.createStatement())
            {
                stmt.execute("DROP TABLE " + table);
            }
            catch (final SQLException e)
            {
                LOG.debug("Could not drop table {}: {}", table,
                        e.getMessage());
            }
        }
    }

    /**
     * Drops all FK constraints among the specified tables by querying
     * {@link DatabaseMetaData} for exported keys and issuing ALTER TABLE DROP
     * CONSTRAINT statements. Constraints that fail to drop are silently ignored.
     *
     * @param connection
     *            The {@link Connection} to use.
     * @param tableNames
     *            The names of the tables whose inter-table FK constraints should
     *            be dropped.
     * @throws Exception
     */
    private static void dropFkConstraintsAmong(final Connection connection,
            final String... tableNames) throws Exception
    {
        // Case-insensitive: PostgreSQL stores lowercase but callers may pass uppercase
        final Set<String> tableSet =
                new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        tableSet.addAll(Arrays.asList(tableNames));
        final Map<String, String> constraintsToDrop = new LinkedHashMap<>();
        final DatabaseMetaData metaData = connection.getMetaData();
        for (final String tableName : tableNames)
        {
            final String storedName = normalizeIdentifier(metaData, tableName);
            try (final ResultSet rs =
                    metaData.getExportedKeys(null, null, storedName))
            {
                while (rs.next())
                {
                    final String fkTableName = rs.getString("FKTABLE_NAME");
                    final String fkName = rs.getString("FK_NAME");
                    if (tableSet.contains(fkTableName) && fkName != null)
                    {
                        constraintsToDrop.put(fkTableName + "." + fkName,
                                "ALTER TABLE " + fkTableName
                                        + " DROP CONSTRAINT " + fkName);
                    }
                }
            }
            catch (final SQLException e)
            {
                LOG.debug(
                        "Could not query exported keys for table {}: {}",
                        tableName, e.getMessage());
            }
        }
        for (final String sql : constraintsToDrop.values())
        {
            try (final Statement stmt = connection.createStatement())
            {
                stmt.execute(sql);
            }
            catch (final SQLException e)
            {
                LOG.debug("Could not drop constraint: {}: {}", sql,
                        e.getMessage());
            }
        }
    }

    private static String normalizeIdentifier(final DatabaseMetaData metaData,
            final String identifier) throws SQLException
    {
        if (metaData.storesLowerCaseIdentifiers())
        {
            return identifier.toLowerCase();
        }
        if (metaData.storesUpperCaseIdentifiers())
        {
            return identifier.toUpperCase();
        }
        return identifier;
    }

    private static String readSqlFromFile(final File ddlFile) throws IOException
    {
        final BufferedReader sqlReader =
                new BufferedReader(new FileReader(ddlFile));
        final StringBuilder sqlBuffer = new StringBuilder();
        while (sqlReader.ready())
        {
            String line = sqlReader.readLine();
            if (!line.startsWith("-"))
            {
                sqlBuffer.append(line);
            }
        }

        sqlReader.close();

        final String sql = sqlBuffer.toString();
        return sql;
    }

}
