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
package org.dbunit;

import java.sql.Connection;
import java.sql.DriverManager;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseTester that uses JDBC's Driver Manager to create connections.<br>
 *
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since 2.2
 */
public class JdbcDatabaseTester extends AbstractDatabaseTester
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(JdbcDatabaseTester.class);

    private final CachingConnectionProvider connectionProvider;
    private String connectionUrl;
    private String driverClass;
    private String password;
    private String username;

    /**
     * Creates a new JdbcDatabaseTester with the specified properties.<br>
     * Username and Password are set to null.
     *
     * @param driverClass the classname of the JDBC driver to use
     * @param connectionUrl the connection url
     * @throws ClassNotFoundException If the given <code>driverClass</code> was not found
     */
    public JdbcDatabaseTester( String driverClass, String connectionUrl ) 
    throws ClassNotFoundException
    {
        this( driverClass, connectionUrl, null, null );
    }

    /**
     * Creates a new JdbcDatabaseTester with the specified properties.
     *
     * @param driverClass the classname of the JDBC driver to use
     * @param connectionUrl the connection url
     * @param username a username that can has access to the database
     * @param password the user's password
     * @throws ClassNotFoundException If the given <code>driverClass</code> was not found
     */
    public JdbcDatabaseTester( String driverClass, String connectionUrl, String username,
            String password ) 
    throws ClassNotFoundException
    {
        this(driverClass, connectionUrl, username, password, null);
    }

    /**
     * Creates a new JdbcDatabaseTester with the specified properties.
     *
     * @param driverClass the classname of the JDBC driver to use
     * @param connectionUrl the connection url
     * @param username a username that can has access to the database - can be <code>null</code>
     * @param password the user's password - can be <code>null</code>
     * @param schema the database schema to be tested - can be <code>null</code>
     * @throws ClassNotFoundException If the given <code>driverClass</code> was not found
     * @since 2.4.3
     */
    public JdbcDatabaseTester( String driverClass, String connectionUrl, String username,
            String password, String schema )
    throws ClassNotFoundException
    {
        this(driverClass, connectionUrl, username, password, schema, null);
    }

    /**
     * Creates a new JdbcDatabaseTester with the specified properties, reusing one
     * {@link IDatabaseConnection} across calls via the given {@link CachingConnectionProvider}
     * instead of creating a new one on every call.<br>
     * Share the same <code>connectionProvider</code> instance across the testers created for each
     * test to get reuse across test methods; pair it with {@link IOperationListener#NO_OP_OPERATION_LISTENER}
     * (or an equivalent non-closing listener) so the cached connection is not closed after every
     * {@link #onSetup()}/{@link #onTearDown()} call.
     *
     * @param driverClass the classname of the JDBC driver to use
     * @param connectionUrl the connection url
     * @param username a username that can has access to the database - can be <code>null</code>
     * @param password the user's password - can be <code>null</code>
     * @param schema the database schema to be tested - can be <code>null</code>
     * @param connectionProvider caches and validates the connection across calls - can be
     *            <code>null</code>, in which case a new connection is created on every call as before
     * @throws ClassNotFoundException If the given <code>driverClass</code> was not found
     * @since 3.4.0
     */
    public JdbcDatabaseTester( String driverClass, String connectionUrl, String username,
            String password, String schema, CachingConnectionProvider connectionProvider )
    throws ClassNotFoundException
    {
        super(schema);
        this.driverClass = driverClass;
        this.connectionUrl = connectionUrl;
        this.username = username;
        this.password = password;
        this.connectionProvider = connectionProvider;

        assertNotNullNorEmpty( "driverClass", driverClass );
        Class.forName( driverClass );
    }

    public IDatabaseConnection getConnection() throws Exception
    {
        logger.debug("getConnection() - start");

        if (connectionProvider != null)
        {
            return connectionProvider.getConnection(this::createConnection);
        }
        return createConnection();
    }

    private IDatabaseConnection createConnection() throws Exception
    {
        assertNotNullNorEmpty( "connectionUrl", connectionUrl );
        Connection conn = null;
        if( username == null && password == null ){
            conn = DriverManager.getConnection( connectionUrl );
        }else{
            conn = DriverManager.getConnection( connectionUrl, username, password );
        }
        return new DatabaseConnection( conn, getSchema() );
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("connectionUrl=").append(this.connectionUrl);
        sb.append(", driverClass=").append(this.driverClass);
        sb.append(", username=").append(this.username);
        sb.append(", password=**********");
        sb.append(", schema=").append(super.getSchema());
        sb.append(", connectionProvider=").append(this.connectionProvider);
        sb.append("]");
        return sb.toString();
    }

}
