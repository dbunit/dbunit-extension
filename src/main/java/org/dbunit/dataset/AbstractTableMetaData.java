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
package org.dbunit.dataset;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.dataset.datatype.IDbProductRelatable;
import org.dbunit.dataset.filter.IColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Mar 8, 2002)
 */
public abstract class AbstractTableMetaData implements ITableMetaData
{

	private Map _columnsToIndexes;
	
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractTableMetaData.class);

    /**
     * Default constructor
     */
    public AbstractTableMetaData()
    {
    }
    
    /**
     * @param columns
     * @param keyNames
     * @return The primary key columns
     * @deprecated since 2.3.0 - use {@link Columns#getColumns(String[], Column[])}
     */
    protected static Column[] getPrimaryKeys(Column[] columns, String[] keyNames)
    {
        logger.debug("getPrimaryKeys(columns={}, keyNames={}) - start", columns, keyNames);
        return Columns.getColumns(keyNames, columns);
    }

    /**
     * @param tableName
     * @param columns
     * @param columnFilter
     * @return The filtered primary key columns
     * @deprecated since 2.3.0 - use {@link Columns#getColumns(String[], Column[])}
     */
    protected static Column[] getPrimaryKeys(String tableName, Column[] columns,
            IColumnFilter columnFilter)
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("getPrimaryKeys(tableName={}, columns={}, columnFilter={}) - start",
    				tableName, columns, columnFilter);
    	}
    	return Columns.getColumns(tableName, columns, columnFilter);
    }

	/**
	 * Provides the index of the column with the given name within this table.
	 * Uses method {@link ITableMetaData#getColumns()} to retrieve all available columns.
	 * @throws DataSetException 
	 * @see org.dbunit.dataset.ITableMetaData#getColumnIndex(java.lang.String)
	 */
	public int getColumnIndex(String columnName) throws DataSetException 
	{
        logger.debug("getColumnIndex(columnName={}) - start", columnName);

        if(this._columnsToIndexes == null) 
		{
			// lazily create the map
			this._columnsToIndexes = createColumnIndexesMap(this.getColumns());
		}
		
        String columnNameUpperCase = columnName.toUpperCase();
		Integer colIndex = (Integer) this._columnsToIndexes.get(columnNameUpperCase);
		if(colIndex != null)
		{
			return colIndex.intValue();
		}
		else 
		{
			throw new NoSuchColumnException(this.getTableName(), columnNameUpperCase,
					" (Non-uppercase input column: " + columnName + ") in ColumnNameToIndexes cache map. " +
					"Note that the map's column names are NOT case sensitive.");
		}
	}

	/**
	 * @param columns The columns to be put into the hash table
	 * @return A map having the key value pair [columnName, columnIndexInInputArray]
	 */
	private Map createColumnIndexesMap(Column[] columns) 
	{
		Map colsToIndexes = new HashMap(columns.length);
		for (int i = 0; i < columns.length; i++) 
		{
			colsToIndexes.put(columns[i].getColumnName().toUpperCase(), i);
		}
		return colsToIndexes;
	}

	/**
	 * Validates and returns the datatype factory of the given connection
	 * @param connection The connection providing the {@link IDataTypeFactory}
	 * @return The datatype factory of the given connection
	 * @throws SQLException
	 */
	public IDataTypeFactory getDataTypeFactory(IDatabaseConnection connection) 
	throws SQLException 
	{
		DatabaseConfig config = connection.getConfig();
		Object factoryObj = config.getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);
		if(!IDataTypeFactory.class.isAssignableFrom(factoryObj.getClass())) {
		    String msg = "Invalid datatype factory configured. Class '" + 
                        factoryObj.getClass() + "' does not implement '" + IDataTypeFactory.class + "'.";
		    if(factoryObj instanceof String){
		        msg += " Ensure not to specify the fully qualified class name as String but the concrete " +
		        		"instance of the datatype factory (for example 'new OracleDataTypeFactory()').";
		    }
		    // TODO Would a "DatabaseUnitConfigurationException make more sense?
		    throw new DatabaseUnitRuntimeException(msg);
		}
        IDataTypeFactory dataTypeFactory = (IDataTypeFactory)factoryObj;
        
    	// Validate, e.g. oracle metaData + oracleDataTypeFactory ==> OK
        Connection jdbcConnection = connection.getConnection();
        DatabaseMetaData metaData = jdbcConnection.getMetaData();
    	String validationMessage = validateDataTypeFactory(dataTypeFactory, metaData);
    	if(validationMessage!=null){
            // Inform the user that we think he could get trouble with the current configuration
            logger.warn("Potential problem found: " + validationMessage);
    	}

    	return dataTypeFactory;
	}

	/**
	 * Verifies that the data type factory supports the database product on the connection.
	 * If the data type factory is not valid for the connection, a warning is logged.
	 * @param dataTypeFactory The data type factory to validate.
	 * @param metaData The {@link DatabaseMetaData} needed to get the DB product name of the connection RDBMS.
	 * @return A validation message if there is a potential problem or <code>null</code> if everything is fine.
	 * @throws java.sql.SQLException A database problem.
	 */
	String validateDataTypeFactory(IDataTypeFactory dataTypeFactory, DatabaseMetaData metaData)
	throws SQLException
	{
	    if (!(dataTypeFactory instanceof IDbProductRelatable))
	    {
	        return null;
	    }
	    IDbProductRelatable productRelatable = (IDbProductRelatable) dataTypeFactory;
	    String databaseProductName = metaData.getDatabaseProductName();

	    Collection validDbProductCollection = productRelatable.getValidDbProducts();
	    if (validDbProductCollection != null)
	    {
	        String lowerCaseDbProductName = databaseProductName.toLowerCase();
	        for (Iterator iterator = validDbProductCollection.iterator(); iterator.hasNext();) {
	            String validDbProduct = ((String) iterator.next()).toLowerCase();
	            if(lowerCaseDbProductName.indexOf(validDbProduct) > -1) {
	                logger.debug("The current database '{}' fits to the configured data type factory '{}'. Validation successful.",
	                        databaseProductName, dataTypeFactory);
	                return null;
	            }
	        }
	    }

	    // If we get here, the validation failed
	    String validationMessage = "The configured data type factory '" + dataTypeFactory.getClass() +
    	    "' might cause problems with the current database '" + databaseProductName +
    	    "' (e.g. some datatypes may not be supported properly). " +
    	    "In rare cases you might see this message because the list of supported database " +
    	    "products is incomplete (list=" + validDbProductCollection + "). " +
    	    "If so please request a java-class update via the forums." +
    	    "If you are using your own IDataTypeFactory extending " +
    	    "DefaultDataTypeFactory, ensure that you override getValidDbProducts() " +
    	    "to specify the supported database products.";
	    return validationMessage;
	}
}
