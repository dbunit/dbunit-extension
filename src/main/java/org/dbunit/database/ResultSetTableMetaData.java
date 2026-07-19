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
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        // Fast-path cache of one getColumns() result per distinct (schema, table) origin, so a
        // multi-column table is not re-queried once per column. Only safe for the exact
        // DefaultMetadataHandler class (not instanceof: e.g. Db2MetadataHandler extends it but
        // overrides matches() with DB2-specific catalog handling that this cache bypasses) --
        // custom/ext handlers keep the legacy per-column path below.
        Map columnsByOrigin = columnFactory.getClass() == DefaultMetadataHandler.class
                ? new HashMap()
                : null;
        for (int i = 0; i < columns.length; i++)
        {
            int rsIndex = i+1;

            // 1. try to create the column from the DatabaseMetaData object. The DatabaseMetaData
            // provides more information and is more precise so that it should always be used in
            // preference to the ResultSetMetaData object.
            columns[i] = createColumnFromDbMetaData(metaData, rsIndex, databaseMetaData, dataTypeFactory,
                    columnFactory, columnsByOrigin);

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
     * @param columnsByOrigin Fast-path cache of one getColumns() result per (schema, table)
     * origin, or <code>null</code> to always use the legacy per-column lookup (see
     * {@link #createMetaData(String, ResultSet, IDataTypeFactory, IMetadataHandler)}).
     * @return The column or <code>null</code> if it can be not created using a
     * {@link DatabaseMetaData} object because of missing information in the
     * {@link ResultSetMetaData} object
     * @throws SQLException
     * @throws DataTypeException
     */
    private Column createColumnFromDbMetaData(ResultSetMetaData rsMetaData, int rsIndex,
            DatabaseMetaData databaseMetaData, IDataTypeFactory dataTypeFactory,
            IMetadataHandler metadataHandler, Map columnsByOrigin)
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

        if (columnsByOrigin != null)
        {
            return createColumnFromCache(columnsByOrigin, databaseMetaData, metadataHandler,
                    catalogName, schemaName, tableName, columnName, dataTypeFactory);
        }

        // Legacy path (custom/ext IMetadataHandler): fetch and linearly scan per column, since
        // a custom handler's getColumns()/matches() overrides cannot be safely replayed against
        // cached rows.
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
     * Looks up a column's metadata from the per-(schema, table)-origin cache, lazily fetching
     * and caching a whole table's columns (via a single {@code getColumns} call) on first need.
     * Candidates are bucketed by {@link Locale#ENGLISH}-uppercased column name, then filtered by
     * exact-case column name (only when {@link #_caseSensitiveMetaData} is set) and by catalog
     * and table, using the same {@link SQLHelper#areEqualIgnoreNull(String, String, boolean)}
     * semantics as the legacy {@code matches(...)} path -- a null/empty search catalog matches
     * any row. The table check matters because {@code metadataHandler.getColumns} passes
     * {@code tableName} to JDBC's {@code DatabaseMetaData#getColumns} as a LIKE pattern, not an
     * exact match: a table name containing {@code _} or {@code %} (e.g. {@code USER_ACCOUNT})
     * can make the driver also return columns from an unrelated, differently-named table whose
     * name happens to match that pattern (e.g. {@code USERXACCOUNT}). The first remaining
     * candidate, in {@code getColumns} row order, wins, consistent with a forward-scanning
     * {@code scrollTo} match stopping at the first hit. A miss returns <code>null</code>, exactly
     * like the not-found branch of the legacy per-column path.
     */
    private Column createColumnFromCache(Map columnsByOrigin, DatabaseMetaData databaseMetaData,
            IMetadataHandler metadataHandler, String catalogName, String schemaName, String tableName,
            String columnName, IDataTypeFactory dataTypeFactory)
    throws SQLException, DataTypeException
    {
        String originKey = schemaName + '\0' + tableName;
        Map columnsByName = (Map) columnsByOrigin.get(originKey);
        if (columnsByName == null)
        {
            columnsByName = fetchColumnsByName(databaseMetaData, metadataHandler, schemaName, tableName);
            columnsByOrigin.put(originKey, columnsByName);
        }

        List candidates = (List) columnsByName.get(columnName.toUpperCase(Locale.ENGLISH));
        if (candidates == null)
        {
            return null;
        }
        for (Iterator it = candidates.iterator(); it.hasNext();)
        {
            ColumnMetaData data = (ColumnMetaData) it.next();
            if (_caseSensitiveMetaData && !data.columnName.equals(columnName))
            {
                continue;
            }
            if (!SQLHelper.areEqualIgnoreNull(catalogName, data.catalogName, _caseSensitiveMetaData))
            {
                continue;
            }
            if (!SQLHelper.areEqualIgnoreNull(tableName, data.tableName, _caseSensitiveMetaData))
            {
                continue;
            }
            return data.toColumn(dataTypeFactory);
        }
        return null;
    }

    /**
     * Fetches all columns for one (schema, table) origin in a single {@code getColumns} call and
     * snapshots the fields {@link SQLHelper#createColumn} consumes, bucketed by
     * {@link Locale#ENGLISH}-uppercased {@code COLUMN_NAME}. Rows are never dropped here (unlike
     * the previous first-one-wins cache): when more than one row shares a column name -- e.g. the
     * same schema/table existing in more than one catalog, since {@code getColumns} is invoked
     * with a <code>null</code> catalog -- every candidate is kept so {@link #createColumnFromCache}
     * can pick the one whose catalog (and, if case sensitive, exact-case name) actually matches.
     */
    private Map fetchColumnsByName(DatabaseMetaData databaseMetaData, IMetadataHandler metadataHandler,
            String schemaName, String tableName)
    throws SQLException
    {
        Map columnsByName = new HashMap();
        ResultSet columnsResultSet = metadataHandler.getColumns(databaseMetaData, schemaName, tableName);
        try
        {
            while (columnsResultSet.next())
            {
                ColumnMetaData data = ColumnMetaData.readFrom(columnsResultSet);
                String key = data.columnName.toUpperCase(Locale.ENGLISH);
                List candidates = (List) columnsByName.get(key);
                if (candidates == null)
                {
                    candidates = new ArrayList();
                    columnsByName.put(key, candidates);
                }
                candidates.add(data);
            }
        }
        finally
        {
            SQLHelper.close(columnsResultSet);
        }
        return columnsByName;
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

    /**
     * Snapshot of the {@code DatabaseMetaData#getColumns} row fields that
     * {@link SQLHelper#createColumn(ResultSet, IDataTypeFactory, boolean)} consumes, so a
     * table's columns can be fetched once and converted to {@link Column} objects afterwards
     * without re-querying per column.
     */
    private static final class ColumnMetaData
    {
        private final String catalogName;
        private final String tableName;
        private final String columnName;
        private final int sqlType;
        private final String sqlTypeName;
        private final int nullable;
        private final String remarks;
        private final String columnDefaultValue;
        private final String isAutoIncrement;
        private final String isGenerated;

        private ColumnMetaData(String catalogName, String tableName, String columnName, int sqlType,
                String sqlTypeName, int nullable, String remarks, String columnDefaultValue,
                String isAutoIncrement, String isGenerated)
        {
            this.catalogName = catalogName;
            this.tableName = tableName;
            this.columnName = columnName;
            this.sqlType = sqlType;
            this.sqlTypeName = sqlTypeName;
            this.nullable = nullable;
            this.remarks = remarks;
            this.columnDefaultValue = columnDefaultValue;
            this.isAutoIncrement = isAutoIncrement;
            this.isGenerated = isGenerated;
        }

        /**
         * Reads the same {@code getColumns} result set columns, by the same indexes, as
         * {@link SQLHelper#createColumn(ResultSet, IDataTypeFactory, boolean)}, plus
         * {@code TABLE_CAT} so cache candidates can be matched by catalog too.
         */
        private static ColumnMetaData readFrom(ResultSet resultSet) throws SQLException
        {
            String catalogName = resultSet.getString(1);
            String tableName = resultSet.getString(3);
            String columnName = resultSet.getString(4);
            int sqlType = resultSet.getInt(5);
            // If Types.DISTINCT like SQL DOMAIN, then get Source Date Type of SQL-DOMAIN
            if(sqlType == Types.DISTINCT)
            {
                sqlType = resultSet.getInt("SOURCE_DATA_TYPE");
            }
            String sqlTypeName = resultSet.getString(6);
            int nullable = resultSet.getInt(11);
            String remarks = resultSet.getString(12);
            String columnDefaultValue = resultSet.getString(13);
            String isAutoIncrement = resultSet.getString(23);
            // some JDBC drivers do not have this column even though they claim to be compliant with JDBC 4.1 or later
            String isGenerated = resultSet.getMetaData().getColumnCount() >= 24 ? resultSet.getString(24) : null;
            return new ColumnMetaData(catalogName, tableName, columnName, sqlType, sqlTypeName, nullable,
                    remarks, columnDefaultValue, isAutoIncrement, isGenerated);
        }

        /**
         * Mirrors {@link SQLHelper#createColumn(ResultSet, IDataTypeFactory, boolean)}'s
         * construction (always with {@code datatypeWarning=true}, as {@code ResultSetTableMetaData}
         * calls it), operating on these cached field values instead of a live result set row.
         */
        private Column toColumn(IDataTypeFactory dataTypeFactory) throws DataTypeException
        {
            DataType dataType = dataTypeFactory.createDataType(sqlType, sqlTypeName, tableName, columnName);
            if (dataType == DataType.UNKNOWN)
            {
                logger.warn(tableName + "." + columnName +
                        " data type (" + sqlType + ", '" + sqlTypeName +
                        "') not recognized and will be ignored. See FAQ for more information.");
                return null;
            }
            return new Column(columnName, dataType, sqlTypeName, Column.nullableValue(nullable),
                    columnDefaultValue, remarks, Column.AutoIncrement.autoIncrementValue(isAutoIncrement),
                    Column.convertMetaDataBoolean(isGenerated));
        }
    }
}
