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

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;

/**
 * DatabaseTester that pulls a DataSource from a JNDI location.
 *
 * @author Andres Almiray (aalmiray@users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.2.0
 */
public class JndiDatabaseTester extends AbstractDatabaseTester
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(JndiDatabaseTester.class);

    private DataSource dataSource;
    private Properties environment;
    private boolean initialized = false;
    private String lookupName;

    /**
     * Creates a JndiDatabaseTester with default JNDI properties.
     *
     * @param lookupName the name of the resource in the JNDI context
     */
    public JndiDatabaseTester(String lookupName)
    {
        this(null, lookupName);
    }

    /**
     * Creates a JndiDatabaseTester with specific JNDI properties.
     *
     * @param environment A Properties object with JNDI properties. Can be null
     * @param lookupName the name of the resource in the JNDI context
     */
    public JndiDatabaseTester(Properties environment, String lookupName)
    {
        this(environment, lookupName, null);
    }

    /**
     * Creates a JndiDatabaseTester with specific JNDI properties.
     * 
     * @param environment A Properties object with JNDI properties. Can be <code>null</code>
     * @param lookupName the name of the resource in the JNDI context
     * @param schema The schema name to be used for new dbunit connections. Can be <code>null</code>
     * @since 2.4.5
     */
    public JndiDatabaseTester(Properties environment, String lookupName, String schema) 
    {
        super(schema);

        if (lookupName == null) {
            throw new NullPointerException(
                    "The parameter 'lookupName' must not be null");
        }
        this.lookupName = lookupName;
        this.environment = environment;
    }

    public IDatabaseConnection getConnection() throws Exception
    {
        logger.trace("getConnection() - start");

        if( !initialized ){
            initialize();
        }

        return new DatabaseConnection( dataSource.getConnection(), getSchema() );
    }

    /**
     * Verifies the configured properties and locates the Datasource through
     * JNDI.<br>
     * This method is called by {@link getConnection} if the tester has not been
     * initialized yet.
     */
    private void initialize() throws NamingException
    {
        logger.trace("initialize() - start");

        Context context = new InitialContext( environment );
        assertNotNullNorEmpty( "lookupName", lookupName );
        Object obj = context.lookup( lookupName );
        assertTrue( "JNDI object with [" + lookupName + "] not found", obj!=null );
        assertTrue( "Object [" + obj + "] at JNDI location [" + lookupName
                + "] is not of type [" + DataSource.class.getName() + "]", obj instanceof DataSource );
        dataSource = (DataSource) obj;
        assertTrue( "DataSource is not set", dataSource!=null );
        initialized = true;
    }

    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("lookupName=").append(this.lookupName);
        sb.append(", environment=").append(this.environment);
        sb.append(", initialized=").append(this.initialized);
        sb.append(", dataSource=").append(this.dataSource);
        sb.append(", schema=").append(super.getSchema());
        sb.append("]");
        return sb.toString();
    }

}
