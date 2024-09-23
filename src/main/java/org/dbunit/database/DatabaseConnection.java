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

import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class adapts a JDBC <code>Connection</code> to a
 * {@link IDatabaseConnection}.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 21, 2002
 */
public class DatabaseConnection extends AbstractDatabaseConnection
        implements IDatabaseConnection
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    private final Connection _connection;
    private final String _schema;

    /**
     * Creates a new <code>DatabaseConnection</code>.
     *
     * @param connection the adapted JDBC connection
     * @throws DatabaseUnitException
     */
    public DatabaseConnection(Connection connection) throws DatabaseUnitException
    {
      this( connection, null );
    }

    /**
     * Creates a new <code>DatabaseConnection</code> using a specific schema.
     *
     * @param connection the adapted JDBC connection
     * @param schema the database schema. Note that the schema name is case sensitive. This
     * is necessary because schemas with the same name but different case can coexist on one
     * database. <br>
     * Here an example that creates two users/schemas for oracle where only the case is different:<br>
     * <code>
     * create user dbunittest identified by dbunittest;
     * create user "dbunittest" identified by "dbunittest";
     * </code>
     * The first one creates the "default" user where everything is interpreted by oracle in uppercase.
     * The second one is completely lowercase because of the quotes.
     * @throws DatabaseUnitException 
     */
    public DatabaseConnection(Connection connection, String schema) throws DatabaseUnitException
    {
    	this(connection, schema, false);
    }

    /**
     * Creates a new <code>DatabaseConnection</code> using a specific schema.
     *
     * @param connection the adapted JDBC connection
     * @param schema the database schema. Note that the schema name is case sensitive. This
     * is necessary because schemas with the same name but different case can coexist on one
     * database. <br>
     * Here an example that creates two users/schemas for oracle where only the case is different:<br>
     * <code>
     * create user dbunittest identified by dbunittest;
     * create user "dbunittest" identified by "dbunittest";
     * </code>
     * The first one creates the "default" user where everything is interpreted by oracle in uppercase.
     * The second one is completely lowercase because of the quotes.
     * @param validate If <code>true</code> an exception is thrown when the given schema
     * does not exist according to the DatabaseMetaData. If <code>false</code> the validation
     * will only print a warning if the schema was not found.
     * @since 2.3.0
     * @throws DatabaseUnitException If the <code>validate</code> parameter is <code>true</code> and the 
     * validation of the given connection/schema was not successful (added with 2.3.0). This can happen if the given
     * schema does not exist or if the jdbc driver does not implement the metaData.getSchemas() method properly.
     */
    public DatabaseConnection(Connection connection, String schema, boolean validate) throws DatabaseUnitException
    {
        if(connection == null)
        {
            throw new NullPointerException("The parameter 'connection' must not be null");
        }
        _connection = connection;
        
        if(schema != null)
        {
            _schema = SQLHelper.correctCase(schema, connection);
            SQLHelper.logInfoIfValueChanged(schema, _schema, "Corrected schema name:", DatabaseConnection.class);
        }
        else
        {
            _schema = null;
        }

        printConnectionInfo();
        validateSchema(validate);
    }

	////////////////////////////////////////////////////////////////////////////
    // IDatabaseConnection interface

    public Connection getConnection() throws SQLException
    {
        return _connection;
    }

    public String getSchema()
    {
        return _schema;
    }

    public void close() throws SQLException
    {
        logger.debug("close() - start");
        _connection.close();
    }
    
    
    /**
     * Prints debugging information about the current JDBC connection
     */
    private void printConnectionInfo() 
    {
    	if(logger.isDebugEnabled()) 
    	{
        	try {
    			logger.debug("Database connection info: " + SQLHelper.getDatabaseInfo(_connection.getMetaData()));
    		} 
        	catch (SQLException e) {
    			logger.warn("Exception while trying to retrieve database info from connection", e);
    		}
    	}
	}

    /**
     * Validates if the database schema exists for this connection.
     * @param validateStrict If <code>true</code> an exception is thrown when the given schema
     * does not exist according to the DatabaseMetaData. If <code>false</code> the validation
     * will only print a warning if the schema was not found.
     * @throws DatabaseUnitException
     */
    private void validateSchema(boolean validateStrict) throws DatabaseUnitException
    {
    	logger.debug("validateSchema(validateStrict={}) - start", validateStrict);
        
        if(this._schema == null)
        {
            logger.debug("Schema is null. Nothing to validate.");
            return;
        }
        
        try
        {
            boolean schemaExists = SQLHelper.schemaExists(this._connection, this._schema);
            if(!schemaExists)
            {
            	// Under certain circumstances the cause might be that the JDBC driver
				// implementation of 'DatabaseMetaData.getSchemas()' is not correct 
            	// (known issue of MySQL driver).
            	String msg = "The given schema '" + this._schema + "' does not exist.";
            	// If strict validation is wished throw an exception
            	if(validateStrict)
            		throw new DatabaseUnitException(msg);
            	else
            		logger.warn(msg);
            }
        }
        catch(SQLException e)
        {
            throw new DatabaseUnitException("Exception while checking the schema for validity", e);
        }
    }
    
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("schema=").append(_schema);
        sb.append(", connection=").append(_connection);
        sb.append(", super=").append(super.toString());
        sb.append("]");
        return sb.toString();
    }
}
