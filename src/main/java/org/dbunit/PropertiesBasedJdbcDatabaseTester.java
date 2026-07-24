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

import org.dbunit.database.CachingConnectionProvider;

/**
 * DatabaseTester that configures a DriverManager from environment properties.<br>
 * This class defines a set of keys for system properties that need to be
 * present in the environment before using it. Example:
 * <xmp>
 * System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS,
 *             "com.mycompany.myDriver" );
 * System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL,
 *             "jdbc:mydb://host/dbname" );
 * System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME,
 *             "myuser" );
 * System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD,
 *             "mypasswd" );
 * System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_SCHEMA,
 *             "myschema" );
 * </xmp>
 *
 * @author Andres Almiray(aalmiray@users.sourceforge.net)
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public class PropertiesBasedJdbcDatabaseTester extends JdbcDatabaseTester
{

    /** A key for property that defines the connection url */
    public static final String DBUNIT_CONNECTION_URL = "dbunit.connectionUrl";
    /** A key for property that defines the driver classname */
    public static final String DBUNIT_DRIVER_CLASS = "dbunit.driverClass";
    /** A key for property that defines the user's password */
    public static final String DBUNIT_PASSWORD = "dbunit.password";
    /** A key for property that defines the username */
    public static final String DBUNIT_USERNAME = "dbunit.username";
    /** A key for property that defines the database schema */
    public static final String DBUNIT_SCHEMA = "dbunit.schema";

    /**
     * Creates a new {@link JdbcDatabaseTester} using specific {@link System#getProperty(String)}
     * values as initialization parameters
     * @throws Exception
     */
    public PropertiesBasedJdbcDatabaseTester() throws Exception
    {
        super(  System.getProperty(DBUNIT_DRIVER_CLASS),
                System.getProperty(DBUNIT_CONNECTION_URL),
                System.getProperty(DBUNIT_USERNAME),
                System.getProperty(DBUNIT_PASSWORD),
                System.getProperty(DBUNIT_SCHEMA)
            );
    }

    /**
     * Creates a new {@link JdbcDatabaseTester} using specific {@link System#getProperty(String)}
     * values as initialization parameters, reusing one {@link org.dbunit.database.IDatabaseConnection}
     * across calls via the given {@link CachingConnectionProvider} instead of creating a new one on
     * every call.<br>
     * Share the same <code>connectionProvider</code> instance across the testers created for each
     * test to get reuse across test methods; pair it with {@link IOperationListener#NO_OP_OPERATION_LISTENER}
     * (or an equivalent non-closing listener) so the cached connection is not closed after every
     * {@link #onSetup()}/{@link #onTearDown()} call.
     *
     * @param connectionProvider Caches and validates the connection across
     *            calls. Can be <code>null</code>, in which case a new
     *            connection is created on every call as before.
     * @throws Exception If the configured driver class was not found
     * @since 3.4.0
     */
    public PropertiesBasedJdbcDatabaseTester(final CachingConnectionProvider connectionProvider)
            throws Exception
    {
        super(  System.getProperty(DBUNIT_DRIVER_CLASS),
                System.getProperty(DBUNIT_CONNECTION_URL),
                System.getProperty(DBUNIT_USERNAME),
                System.getProperty(DBUNIT_PASSWORD),
                System.getProperty(DBUNIT_SCHEMA),
                connectionProvider
            );
    }

}
