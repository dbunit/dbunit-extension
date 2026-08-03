/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

package org.dbunit.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * This class adapts a JDBC <code>DataSource</code> to a
 * {@link IDatabaseConnection}.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 8, 2002
 */
public class DatabaseDataSourceConnection extends AbstractDatabaseConnection
        implements IDatabaseConnection
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDataSourceConnection.class);

    private final String _schema;
    private final DataSource _dataSource;
    private final String _user;
    private final String _password;
    private Connection _connection;

    /**
     * Creates a connection using the data source bound at the given JNDI name, in the given schema.
     *
     * @param context the JNDI context to look up the data source in.
     * @param jndiName the JNDI name the data source is bound at.
     * @param schema the database schema.
     * @throws NamingException if the JNDI lookup fails.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(InitialContext context, String jndiName,
            String schema) throws NamingException, SQLException
    {
        this((DataSource)context.lookup(jndiName), schema, null, null);
    }

    /**
     * Creates a connection using the data source bound at the given JNDI name, in the given schema,
     * authenticating with the given credentials.
     *
     * @param context the JNDI context to look up the data source in.
     * @param jndiName the JNDI name the data source is bound at.
     * @param schema the database schema.
     * @param user the database user.
     * @param password the database password.
     * @throws NamingException if the JNDI lookup fails.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(InitialContext context, String jndiName,
            String schema, String user, String password)
            throws NamingException, SQLException
    {
        this((DataSource)context.lookup(jndiName), schema, user, password);
    }

    /**
     * Creates a connection using the data source bound at the given JNDI name.
     *
     * @param context the JNDI context to look up the data source in.
     * @param jndiName the JNDI name the data source is bound at.
     * @throws NamingException if the JNDI lookup fails.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(InitialContext context, String jndiName)
            throws NamingException, SQLException
    {
        this(context, jndiName, null);
    }

    /**
     * Creates a connection using the data source bound at the given JNDI name, authenticating
     * with the given credentials.
     *
     * @param context the JNDI context to look up the data source in.
     * @param jndiName the JNDI name the data source is bound at.
     * @param user the database user.
     * @param password the database password.
     * @throws NamingException if the JNDI lookup fails.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(InitialContext context, String jndiName,
            String user, String password) throws NamingException, SQLException
    {
        this(context, jndiName, null, user, password);
    }

    /**
     * Creates a connection using the given data source.
     *
     * @param dataSource the data source to adapt.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(DataSource dataSource)
            throws SQLException
    {
        this(dataSource, null, null, null);
    }

    /**
     * Creates a connection using the given data source, authenticating with the given credentials.
     *
     * @param dataSource the data source to adapt.
     * @param user the database user.
     * @param password the database password.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(DataSource dataSource, String user,
            String password) throws SQLException
    {
        this(dataSource, null, user, password);
    }

    /**
     * Creates a connection using the given data source, in the given schema.
     *
     * @param dataSource the data source to adapt.
     * @param schema the database schema.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(DataSource dataSource, String schema)
            throws SQLException
    {
        this(dataSource, schema, null, null);
    }

    /**
     * Creates a connection using the given data source, in the given schema, authenticating
     * with the given credentials.
     *
     * @param dataSource the data source to adapt.
     * @param schema the database schema.
     * @param user the database user.
     * @param password the database password.
     * @throws SQLException if a database access error occurs.
     */
    public DatabaseDataSourceConnection(DataSource dataSource, String schema,
            String user, String password) throws SQLException
    {
        _dataSource = dataSource;
        _schema = schema;
        _user = user;
        _password = password;
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDatabaseConnection interface

    public Connection getConnection() throws SQLException
    {
        logger.debug("getConnection() - start");

        if (_connection == null)
        {
            try
            {
                if (_user != null) {
                    _connection = _dataSource.getConnection(_user, _password);
                } else {
                    _connection = _dataSource.getConnection();
                }
            }
            catch (SQLException e)
            {
                logger.error("getConnection(): ", e);
                throw e;
            }
        }
        return _connection;
    }

    public String getSchema()
    {
        return _schema;
    }

    public void close() throws SQLException
    {
        logger.debug("close() - start");

        if (_connection != null)
        {
            _connection.close();
            _connection = null;
        }
    }
}
