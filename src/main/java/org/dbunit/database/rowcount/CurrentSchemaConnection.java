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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dbunit.database.AbstractDatabaseConnection;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

/**
 * A view of a connection that names no schema, which names the schema its JDBC connection
 * works in instead, as the metadata handler of the connection reports it. It has the same JDBC
 * connection and the same {@link DatabaseConfig}, so every setting of the caller still applies,
 * but the tables it lists, and the names it counts them by, are those of that one schema.
 * {@link RowCountCheck} counts through it for a connection that names none: such a connection
 * lists the tables of every schema it can see, the system schemas of a database such as H2 among
 * them, and counts them by names that carry no schema.
 *
 * <p>The schema is the name the database stores, so counting writes it between the identifier
 * quote string of the JDBC connection, unless the {@code escapePattern} property is set, which
 * then quotes it as it quotes every name, or the driver reports no quote string. The table names
 * stay as they are listed.
 *
 * <p>It is a view and not a connection of its own, so closing it closes nothing.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class CurrentSchemaConnection extends AbstractDatabaseConnection
{
    private final IDatabaseConnection connection;
    private final String schema;

    /**
     * Creates a view of a connection that names the given schema.
     *
     * @param connection The connection to view, which names no schema.
     * @param schema The schema to name, the one the JDBC connection of {@code connection}
     *            works in.
     */
    CurrentSchemaConnection(final IDatabaseConnection connection, final String schema)
    {
        this.connection = connection;
        this.schema = schema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the JDBC connection of the viewed connection.
     */
    @Override
    public Connection getConnection() throws SQLException
    {
        return connection.getConnection();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the schema this view names, which the viewed connection does not.
     */
    @Override
    public String getSchema()
    {
        return schema;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the {@link DatabaseConfig} of the viewed connection, not one of this view's own.
     */
    @Override
    public DatabaseConfig getConfig()
    {
        return connection.getConfig();
    }

    /**
     * Closes nothing: the connection this is a view of is its caller's to close.
     */
    @Override
    public void close() throws SQLException
    {
        // nothing to close
    }

    /**
     * {@inheritDoc}
     * <p>
     * Writes the schema this view names between the identifier quote string of the JDBC
     * connection, as the driver reports the schema as the database stores it. With an escape
     * pattern set, or a driver that reports no quote string, it counts as
     * {@link AbstractDatabaseConnection} does.
     */
    @Override
    public int getRowCount(final String tableName, final String whereClause) throws SQLException
    {
        final Object escapePattern =
                getConfig().getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN);
        final String quoteString = identifierQuoteString();
        if (escapePattern != null || quoteString == null)
        {
            return super.getRowCount(tableName, whereClause);
        }

        final String doubledQuote = quoteString + quoteString;
        final String escapedSchema = schema.replace(quoteString, doubledQuote);
        final StringBuilder sql = new StringBuilder("select count(*) from ");
        sql.append(quoteString).append(escapedSchema).append(quoteString);
        sql.append('.').append(tableName);
        if (whereClause != null)
        {
            sql.append(' ').append(whereClause);
        }

        try (Statement statement = getConnection().createStatement();
                ResultSet resultSet = statement.executeQuery(sql.toString()))
        {
            if (resultSet.next())
            {
                return resultSet.getInt(1);
            }
            throw new SQLException("Select count did not return any results for table '"
                    + tableName + "'. Statement: " + sql);
        }
    }

    private String identifierQuoteString()
    {
        try
        {
            final DatabaseMetaData metaData = getConnection().getMetaData();
            final String quoteString = metaData.getIdentifierQuoteString();
            if (quoteString == null)
            {
                return null;
            }
            final String trimmed = quoteString.trim();
            if (trimmed.isEmpty())
            {
                return null;
            }
            return trimmed;
        } catch (final SQLException e)
        {
            return null;
        }
    }
}
