/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.dbunit.dataset.AbstractTableMetaData;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ResultSet} based {@link org.dbunit.dataset.ITableMetaData} implementation.
 * <p>
 * The lookup for the information needed to create the {@link Column} objects is retrieved
 * in two phases:
 * <ol>
 * <li>Try to find the information from the given {@link ResultSet} via a {@link DatabaseMetaData}
 * object. Therefore the {@link ResultSetMetaData} is used to get the catalog/schema/table/column
 * names which in turn are used to get column information via
 * {@link DatabaseMetaData#getColumns(String, String, String, String)}. The reason for this is
 * that the {@link DatabaseMetaData} is more precise and contains more information about columns
 * than the {@link ResultSetMetaData} does. Another reason is that some JDBC drivers (currently known
 * from MYSQL driver) provide an inconsistent implementation of those two MetaData objects
 * and the {@link DatabaseMetaData} is hence considered to be the master by dbunit.
 * </li>
 * <li>
 * Since some JDBC drivers (one of them being Oracle) cannot (or just do not) provide the 
 * catalog/schema/table/column values on a {@link ResultSetMetaData} instance the second 
 * step will create the dbunit {@link Column} using the {@link ResultSetMetaData} methods 
 * directly (for example {@link ResultSetMetaData#getColumnType(int)}. (This is also the way
 * dbunit worked until the 2.4 release)
 * </li>
 * </ol> 
 * </p>
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class ResultSetTableMetaData extends AbstractTableMetaData 
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTableMetaData.class);

    /**
     * The actual table metadata
     */
    private DefaultTableMetaData wrappedTableMetaData;
	private boolean _caseSensitiveMetaData;

	/**
	 * @param tableName The name of the database table
	 * @param resultSet The JDBC result set that is used to retrieve the columns
	 * @param connection The connection which is needed to retrieve some configuration values
	 * @param caseSensitiveMetaData Whether or not the metadata is case sensitive
	 * @throws DataSetException
	 * @throws SQLException
	 */
	public ResultSetTableMetaData(String tableName,
            ResultSet resultSet, IDatabaseConnection connection, boolean caseSensitiveMetaData) 
	throws DataSetException, SQLException 
	{
		super();
        _caseSensitiveMetaData = caseSensitiveMetaData;
		this.wrappedTableMetaData = createMetaData(tableName, resultSet, connection);
		
	}

	/**
	 * @param tableName The name of the database table
	 * @param resultSet The JDBC result set that is used to retrieve the columns
	 * @param dataTypeFactory
     * @param caseSensitiveMetaData Whether or not the metadata is case sensitive
	 * @throws DataSetException
	 * @throws SQLException
     * @deprecated since 2.4.4. use {@link ResultSetTableMetaData#ResultSetTableMetaData(String, ResultSet, IDatabaseConnection, boolean)}
	 */
	public ResultSetTableMetaData(String tableName,
            ResultSet resultSet, IDataTypeFactory dataTypeFactory, boolean caseSensitiveMetaData) 
	throws DataSetException, SQLException 
	{
		super();
		_caseSensitiveMetaData = caseSensitiveMetaData;
		this.wrappedTableMetaData = createMetaData(tableName, resultSet, dataTypeFactory, new DefaultMetadataHandler());
	}

	
    private DefaultTableMetaData createMetaData(String tableName,
            ResultSet resultSet, IDatabaseConnection connection)
            throws SQLException, DataSetException
    {
    	if (logger.isTraceEnabled())
    		logger.trace("createMetaData(tableName={}, resultSet={}, connection={}) - start",
    				new Object[] { tableName, resultSet, connection });

    	DatabaseConfig dbConfig = connection.getConfig();
    	IMetadataHandler columnFactory = (IMetadataHandler)dbConfig.getProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER);
        IDataTypeFactory typeFactory = super.getDataTypeFactory(connection);
        return createMetaData(tableName, resultSet, typeFactory, columnFactory);
    }

    private DefaultTableMetaData createMetaData(String tableName,
            ResultSet resultSet, IDataTypeFactory dataTypeFactory, IMetadataHandler columnFactory)
            throws DataSetException, SQLException
    {
    	if (logger.isTraceEnabled())
    		logger.trace("createMetaData(tableName={}, resultSet={}, dataTypeFactory={}, columnFactory={}) - start",
    				new Object[]{ tableName, resultSet, dataTypeFactory, columnFactory });

    	Connection connection = resultSet.getStatement().getConnection();
    	DatabaseMetaData databaseMetaData = connection.getMetaData();
    	
        ResultSetMetaData metaData = resultSet.getMetaData();
        Column[] columns = new Column[metaData.getColumnCount()];
        for (int i = 0; i < columns.length; i++)
        {
            int rsIndex = i+1;
            
            // 1. try to create the column from the DatabaseMetaData object. The DatabaseMetaData
            // provides more information and is more precise so that it should always be used in
            // preference to the ResultSetMetaData object.
            columns[i] = createColumnFromDbMetaData(metaData, rsIndex, databaseMetaData, dataTypeFactory, columnFactory);
            
            // 2. If we could not create the Column from a DatabaseMetaData object, try to create it
            // from the ResultSetMetaData object directly
            if(columns[i] == null)
            {
                columns[i] = createColumnFromRsMetaData(metaData, rsIndex, tableName, dataTypeFactory);
            }
        }

        return new DefaultTableMetaData(tableName, columns);
    }

    private Column createColumnFromRsMetaData(ResultSetMetaData rsMetaData,
            int rsIndex, String tableName, IDataTypeFactory dataTypeFactory) 
    throws SQLException, DataTypeException 
    {
        if(logger.isTraceEnabled()){
            logger.trace("createColumnFromRsMetaData(rsMetaData={}, rsIndex={}," + 
                    " tableName={}, dataTypeFactory={}) - start",
                new Object[]{rsMetaData, String.valueOf(rsIndex), 
                    tableName, dataTypeFactory});
        }

        int columnType = rsMetaData.getColumnType(rsIndex);
        String columnTypeName = rsMetaData.getColumnTypeName(rsIndex);
        String columnName = rsMetaData.getColumnLabel(rsIndex);
        int isNullable = rsMetaData.isNullable(rsIndex);

        DataType dataType = dataTypeFactory.createDataType(
                    columnType, columnTypeName, tableName, columnName);

        Column column = new Column(
                columnName,
                dataType,
                columnTypeName,
                Column.nullableValue(isNullable));
        return column;
    }

    /**
     * Try to create the Column using information from the given {@link ResultSetMetaData}
     * to search the column via the given {@link DatabaseMetaData}. If the
     * {@link ResultSetMetaData} does not provide the required information 
     * (one of catalog/schema/table is "")
     * the search for the Column via {@link DatabaseMetaData} is not executed and <code>null</code>
     * is returned immediately.
     * @param rsMetaData The {@link ResultSetMetaData} from which to retrieve the {@link DatabaseMetaData}
     * @param rsIndex The current index in the {@link ResultSetMetaData}
     * @param databaseMetaData The {@link DatabaseMetaData} which is used to lookup detailed
     * information about the column if possible
     * @param dataTypeFactory dbunit {@link IDataTypeFactory} needed to create the Column
     * @param metadataHandler the handler to be used for {@link DatabaseMetaData} handling
     * @return The column or <code>null</code> if it can be not created using a 
     * {@link DatabaseMetaData} object because of missing information in the 
     * {@link ResultSetMetaData} object
     * @throws SQLException
     * @throws DataTypeException 
     */
    private Column createColumnFromDbMetaData(ResultSetMetaData rsMetaData, int rsIndex, 
            DatabaseMetaData databaseMetaData, IDataTypeFactory dataTypeFactory,
            IMetadataHandler metadataHandler) 
    throws SQLException, DataTypeException 
    {
        if(logger.isTraceEnabled()){
            logger.trace("createColumnFromMetaData(rsMetaData={}, rsIndex={}," + 
                    " databaseMetaData={}, dataTypeFactory={}, columnFactory={}) - start",
                new Object[]{rsMetaData, String.valueOf(rsIndex), 
                            databaseMetaData, dataTypeFactory, metadataHandler});
        }
        
        // use DatabaseMetaData to retrieve the actual column definition
        String catalogName = rsMetaData.getCatalogName(rsIndex);
        String schemaName = rsMetaData.getSchemaName(rsIndex);
        String tableName = rsMetaData.getTableName(rsIndex);
        String columnName = rsMetaData.getColumnLabel(rsIndex);
        
        // Due to a bug in the DB2 JDBC driver we have to trim the names
        catalogName = trim(catalogName);
        schemaName = trim(schemaName);
        tableName = trim(tableName);
        columnName = trim(columnName);
        
        // Check if at least one of catalog/schema/table attributes is
        // not applicable (i.e. "" is returned). If so do not try
        // to get the column metadata from the DatabaseMetaData object.
        // This is the case for all oracle JDBC drivers
        if(catalogName != null && catalogName.equals("")) {
            // Catalog name is not required
            catalogName = null;
        }
        if(schemaName != null && schemaName.equals("")) {
            logger.debug("The 'schemaName' from the ResultSetMetaData is empty-string and not applicable hence. " +
            "Will not try to lookup column properties via DatabaseMetaData.getColumns.");
            return null;
        }
        if(tableName != null && tableName.equals("")) {
            logger.debug("The 'tableName' from the ResultSetMetaData is empty-string and not applicable hence. " +
            "Will not try to lookup column properties via DatabaseMetaData.getColumns.");
            return null;
        }
        
        if(logger.isDebugEnabled())
            logger.debug("All attributes from the ResultSetMetaData are valid, " +
                    "trying to lookup values in DatabaseMetaData. catalog={}, schema={}, table={}, column={}",
                    new Object[]{catalogName, schemaName, tableName, columnName} );
        
        // All of the retrieved attributes are valid, 
        // so lookup the column via DatabaseMetaData
        ResultSet columnsResultSet = metadataHandler.getColumns(databaseMetaData, schemaName, tableName);

        try
        {
            // Scroll resultset forward - must have one result which exactly matches the required parameters
            scrollTo(columnsResultSet, metadataHandler, catalogName, schemaName, tableName, columnName);

            Column column = SQLHelper.createColumn(columnsResultSet, dataTypeFactory, true);
            return column;
        }
        catch(IllegalStateException e)
        {
            logger.warn("Cannot find column from ResultSetMetaData info via DatabaseMetaData. Returning null." +
                    " Even if this is expected to never happen it probably happened due to a JDBC driver bug." +
                    " To get around this you may want to configure a user defined " + IMetadataHandler.class, e);
            return null;
        }
        finally
        {
            SQLHelper.close(columnsResultSet);
        }
    }


    /**
     * Trims the given string in a null-safe way
     * @param value
     * @return
     * @since 2.4.6
     */
    private String trim(String value) 
    {
        return (value==null ? null : value.trim());
    }

    private void scrollTo(ResultSet columnsResultSet, IMetadataHandler metadataHandler,
            String catalog, String schema, String table, String column) 
    throws SQLException 
    {
        while(columnsResultSet.next())
        {
            boolean match = metadataHandler.matches(columnsResultSet, catalog, schema, table, column, _caseSensitiveMetaData);
            if(match)
            {
                // All right. Return immediately because the resultSet is positioned on the correct row
                return;
            }
        }

        // If we get here the column could not be found
        String msg = 
                "Did not find column '" + column + 
                "' for <schema.table> '" + schema + "." + table + 
                "' in catalog '" + catalog + "' because names do not exactly match.";

        throw new IllegalStateException(msg);
    }

	public Column[] getColumns() throws DataSetException {
		return this.wrappedTableMetaData.getColumns();
	}

	public Column[] getPrimaryKeys() throws DataSetException {
		return this.wrappedTableMetaData.getPrimaryKeys();
	}

	public String getTableName() {
		return this.wrappedTableMetaData.getTableName();
	}

	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(getClass().getName()).append("[");
		sb.append("wrappedTableMetaData=").append(this.wrappedTableMetaData);
		sb.append("]");
		return sb.toString();
	}
}
