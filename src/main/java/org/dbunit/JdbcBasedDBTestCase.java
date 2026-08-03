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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestCase that uses a JdbcDatabaseTester.
 *
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public abstract class JdbcBasedDBTestCase extends DBTestCase
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(JdbcBasedDBTestCase.class);

    /**
     * Default constructor.
     */
    public JdbcBasedDBTestCase()
    {
        super();
    }

    /**
     * Constructs a test case with the given name.
     *
     * @param name the test case name.
     */
    public JdbcBasedDBTestCase( String name )
    {
        super( name );
    }

    /**
     * Creates a new IDatabaseTester.<br>
     * Default implementation returns a {@link JdbcDatabaseTester} configured
     * with the values returned from {@link #getDriverClass},
     * {@link #getConnectionUrl}, {@link #getUsername} and {@link #getPassword()}.
     * @throws ClassNotFoundException when the driverClass was not found
     */
    protected IDatabaseTester newDatabaseTester() throws ClassNotFoundException
    {
        logger.debug("newDatabaseTester() - start");

        JdbcDatabaseTester databaseTester = new JdbcDatabaseTester( 
                getDriverClass(),
                getConnectionUrl(),
                getUsername(),
                getPassword() );
        return databaseTester;
    }

    /**
     * Returns the test connection url.
     *
     * @return the test connection url.
     */
    protected abstract String getConnectionUrl();

    /**
     * Returns the JDBC driver classname.
     *
     * @return the JDBC driver classname.
     */
    protected abstract String getDriverClass();

    /**
     * Returns the password for the connection.<br>
     * Subclasses may override this method to provide a custom password.<br>
     * Default implementations returns null.
     *
     * @return the password for the connection.
     */
    protected String getPassword()
    {
        return null;
    }

    /**
     * Returns the username for the connection.<br>
     * Subclasses may override this method to provide a custom username.<br>
     * Default implementations returns null.
     *
     * @return the username for the connection.
     */
    protected String getUsername()
    {
        return null;
    }
}
