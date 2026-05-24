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
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.dbunit.dataset.AbstractTableMetaData;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.dataset.filter.IColumnFilter;
import org.dbunit.util.QualifiedTableName;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container for the metadata for one database table. The metadata is initialized
 * using a {@link IDatabaseConnection}.
 * 
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Mar 8, 2002
 * @see ITableMetaData
 */
public class DatabaseTableMetaData extends AbstractTableMetaData
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTableMetaData.class);

    /**
     * Table name, potentially qualified
     */
    private final QualifiedTableName _qualifiedTableNameSupport;
    private final String _originalTableName;
    private final IDatabaseConnection _connection;
    private Column[] _columns;
    private Column[] _primaryKeys;
    private boolean _caseSensitiveMetaData;
	//added by hzhan032
    private IColumnFilter lastKeyFilter;

    
    DatabaseTableMetaData(String tableName, IDatabaseConnection connection) throws DataSetException
    {
    	this(tableName, connection, true);
    }
    
    /**
     * Creates a new database table metadata
     * @param tableName The name of the table - can be fully qualified
     * @param connection The database connection
     * @param validate Whether or not to validate the given input data. It is not recommended to
     * set the validation to <code>false</code> because it is then possible to create an instance
     * of this object for a db table that does not exist.
     * @throws DataSetException
     */
    DatabaseTableMetaData(String tableName, IDatabaseConnection connection, boolean validate) throws DataSetException
    {
        this(tableName, connection, validate, false);
    }
    
    /**
     * Creates a new database table metadata
     * @param tableName The name of the table - can be fully qualified
     * @param connection The database connection
     * @param validate Whether or not to validate the given input data. It is not recommended to
     * set the validation to <code>false</code> because it is then possible to create an instance
     * of this object for a db table that does not exist.
     * @param caseSensitiveMetaData Whether or not the metadata looked up in a case sensitive way
     * @throws DataSetException
     * @since 2.4.1
     */
    DatabaseTableMetaData(final String tableName, IDatabaseConnection connection, boolean validate, boolean caseSensitiveMetaData) throws DataSetException
    {
    	if (tableName == null) {
			throw new NullPointerException("The parameter 'tableName' must not be null");
		}
    	if (connection == null) {
			throw new NullPointerException("The parameter 'connection' must not be null");
		}
    	
        _connection = connection;
        _caseSensitiveMetaData = caseSensitiveMetaData;

        try
        {
             Connection jdbcConnection = connection.getConnection();
             if(!caseSensitiveMetaData)
             {
                 _originalTableName = SQLHelper.correctCase(tableName, jdbcConnection);
                 SQLHelper.logDebugIfValueChanged(tableName, _originalTableName, "Corrected table name:", DatabaseTableMetaData.class);
             }
             else
             {
                 _originalTableName = tableName;
             }
             
             // qualified names support - table name and schema is stored here
             _qualifiedTableNameSupport = new QualifiedTableName(_originalTableName, _connection.getSchema());

             if(validate) 
             {
                 String schemaName = _qualifiedTableNameSupport.getSchema();
                 String plainTableName = _qualifiedTableNameSupport.getTable();
                 logger.debug("Validating if table '{}' exists in schema '{}' ...", plainTableName, schemaName);
                 try {
                     DatabaseConfig config = connection.getConfig();
                     IMetadataHandler metadataHandler = (IMetadataHandler) config.getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
                     DatabaseMetaData databaseMetaData = jdbcConnection.getMetaData();
                     if(!metadataHandler.tableExists(databaseMetaData, schemaName, plainTableName))
                     {
                         throw new NoSuchTableException("Did not find table '" + plainTableName + "' in schema '" + schemaName + "'");
                     }
                 }
                 catch (SQLException e)
                 {
                     throw new DataSetException("Exception while validation existence of table '" + plainTableName + "'", e);
                 }
             }
             else
             {
                 logger.debug("Validation switched off. Will not check if table exists.");
             }
        }
        catch (SQLException e)
        {
            throw new DataSetException("Exception while retrieving JDBC connection from dbunit connection '" + connection + "'", e);
        }
        
    }

    /**
     * @param tableName
     * @param resultSet
     * @param dataTypeFactory
     * @return The table metadata created for the given parameters
     * @throws DataSetException
     * @throws SQLException
     * @deprecated since 2.3.0. use {@link ResultSetTableMetaData#ResultSetTableMetaData(String, ResultSet, IDataTypeFactory, boolean)}
     */
    public static ITableMetaData createMetaData(String tableName,
            ResultSet resultSet, IDataTypeFactory dataTypeFactory)
            throws DataSetException, SQLException
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("createMetaData(tableName={}, resultSet={}, dataTypeFactory={}) - start",
    				new Object[]{ tableName, resultSet, dataTypeFactory });
    	}

    	return new ResultSetTableMetaData(tableName, resultSet, dataTypeFactory, false);
    }


    
    /**
     * @param tableName
     * @param resultSet
     * @param connection
     * @return The table metadata created for the given parameters
     * @throws SQLException
     * @throws DataSetException
     * @deprecated since 2.3.0. use {@link org.dbunit.database.ResultSetTableMetaData#ResultSetTableMetaData(String, ResultSet, IDatabaseConnection, boolean)}
     */
    public static ITableMetaData createMetaData(String tableName,
            ResultSet resultSet, IDatabaseConnection connection)
            throws SQLException, DataSetException
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("createMetaData(tableName={}, resultSet={}, connection={}) - start",
    				new Object[] { tableName, resultSet, connection });
    	}
    	return new ResultSetTableMetaData(tableName,resultSet,connection, false);
    }

    private String[] getPrimaryKeyNames() throws SQLException
    {
        logger.debug("getPrimaryKeyNames() - start");

    	String schemaName = _qualifiedTableNameSupport.getSchema();
    	String tableName = _qualifiedTableNameSupport.getTable();

        Connection connection = _connection.getConnection();
        DatabaseMetaData databaseMetaData = connection.getMetaData();
        
        DatabaseConfig config = _connection.getConfig();
        IMetadataHandler metadataHandler = (IMetadataHandler) config.getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
        
        ResultSet resultSet = metadataHandler.getPrimaryKeys(databaseMetaData, schemaName, tableName);

        List list = new ArrayList();
        try
        {
            while (resultSet.next())
            {
                String name = resultSet.getString(4);
                int sequence = resultSet.getInt(5);
                list.add(new PrimaryKeyData(name, sequence));
            }
        }
        finally
        {
            resultSet.close();
        }

        list.sort(Comparator.naturalOrder());
        String[] keys = new String[list.size()];
        for (int i = 0; i < keys.length; i++)
        {
            PrimaryKeyData data = (PrimaryKeyData)list.get(i);
            keys[i] = data.getName();
        }

        return keys;
    }

    private class PrimaryKeyData implements Comparable
    {
        private final String _name;
        private final int _index;

        public PrimaryKeyData(String name, int index)
        {
            _name = name;
            _index = index;
        }

        public String getName()
        {
            logger.debug("getName() - start");

            return _name;
        }

        public int getIndex()
        {
            return _index;
        }

        ////////////////////////////////////////////////////////////////////////
        // Comparable interface

        public int compareTo(Object o)
        {
            PrimaryKeyData data = (PrimaryKeyData)o;
            return getIndex() - data.getIndex();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableMetaData interface

    public String getTableName()
    {
        // Ensure that the same table name is returned as specified in the input.
        // This is necessary to support fully qualified XML dataset imports.
        //"<dataset>"
        //"<FREJA.SALES SALES_ID=\"8756\" DEALER_ID=\"4467\"/>"
        //"<CAS.ORDERS ORDER_ID=\"1000\" DEALER_CODE=\"4468\"/>"
        //"</dataset>";
        return this._originalTableName;
    }

    public Column[] getColumns() throws DataSetException
    {
        logger.debug("getColumns() - start");

        if (_columns == null)
        {
            try
            {
                // qualified names support
            	String schemaName = _qualifiedTableNameSupport.getSchema();
            	String tableName = _qualifiedTableNameSupport.getTable();
            	
                Connection jdbcConnection = _connection.getConnection();
                DatabaseMetaData databaseMetaData = jdbcConnection.getMetaData();
                
                DatabaseConfig config = _connection.getConfig();
                
                IMetadataHandler metadataHandler = (IMetadataHandler)config.getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
                ResultSet resultSet = metadataHandler.getColumns(databaseMetaData, schemaName, tableName);

                try
                {
                    IDataTypeFactory dataTypeFactory = super.getDataTypeFactory(_connection);
                    boolean datatypeWarning = config.getFeature(
                            DatabaseConfig.FEATURE_DATATYPE_WARNING);

                    List columnList = new ArrayList();
                    while (resultSet.next())
                    {
                        // Check for exact table/schema name match because
                        // databaseMetaData.getColumns() uses patterns for the lookup
                        boolean match = metadataHandler.matches(resultSet, schemaName, tableName, _caseSensitiveMetaData);
                        if(match)
                        {
                            Column column = SQLHelper.createColumn(resultSet, dataTypeFactory, datatypeWarning);
                            if(column != null)
                            {
                                columnList.add(column);
                            }
                        }
                        else
                        {
                            logger.debug("Skipping <schema.table> '" + resultSet.getString(2) + "." + 
                                    resultSet.getString(3) + "' because names do not exactly match.");
                        }
                    }

                    if (columnList.size() == 0)
                    {
                    	logger.warn("No columns found for table '"+ tableName +"' that are supported by dbunit. " +
                    			"Will return an empty column list");
                    }

                    _columns = (Column[])columnList.toArray(new Column[0]);
                }
                finally
                {
                    resultSet.close();
                }
            }
            catch (SQLException e)
            {
                throw new DataSetException(e);
            }
        }
        return _columns;
    }

    private boolean primaryKeyFilterChanged(IColumnFilter keyFilter)
    {
        return (keyFilter != lastKeyFilter);
    }

    public Column[] getPrimaryKeys() throws DataSetException
    {
        logger.debug("getPrimaryKeys() - start");
                DatabaseConfig config = _connection.getConfig();
        IColumnFilter primaryKeysFilter = (IColumnFilter) config.getProperty(
                        DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER);

        if (_primaryKeys == null || primaryKeyFilterChanged(primaryKeysFilter)) {
            try {
                lastKeyFilter = primaryKeysFilter;
                if (primaryKeysFilter != null) {
                	_primaryKeys = Columns.getColumns(getTableName(), getColumns(),
                            primaryKeysFilter);
                } else {
                	String[] pkNames = getPrimaryKeyNames();
                    _primaryKeys = Columns.getColumns(pkNames, getColumns());
                }
            }
            catch (SQLException e)
            {
                throw new DataSetException(e);
            }
        }
        return _primaryKeys;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Object class
    public String toString()
    {
        try
        {
            String tableName = getTableName();
            String columns = Arrays.asList(getColumns()).toString();
            String primaryKeys = Arrays.asList(getPrimaryKeys()).toString();
            return "table=" + tableName + ", cols=" + columns + ", pk=" + primaryKeys + "";
        }
        catch (DataSetException e)
        {
            return super.toString();
        }
    }
}
