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

import org.dbunit.database.AbstractDatabaseConnection;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

/**
 * A view of a connection that names no schema, which names the current schema of its JDBC
 * connection instead. It has the same JDBC connection and the same {@link DatabaseConfig}, so
 * every setting of the caller still applies, but the tables it lists, and the names it counts
 * them by, are those of that one schema. {@link RowCountCheck} counts through it for a connection
 * that names none: such a connection lists the tables of every schema it can see, the system
 * schemas of a database such as H2 among them, and counts them by names that carry no schema.
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
     * @param schema The schema to name, the current one of the JDBC connection of
     *            {@code connection}.
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
}
