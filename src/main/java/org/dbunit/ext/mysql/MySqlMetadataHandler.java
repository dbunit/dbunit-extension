/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
package org.dbunit.ext.mysql;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.database.IMetadataHandler;
import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Special metadata handler for MySQL.<br/>
 * Was introduced to fix "[ 2545095 ] Mysql FEATURE_QUALIFIED_TABLE_NAMES column SQLHelper.matches".
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.4
 */
public class MySqlMetadataHandler implements IMetadataHandler {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(MySqlMetadataHandler.class);

    public ResultSet getColumns(DatabaseMetaData databaseMetaData, String schemaName, String tableName) 
    throws SQLException {
        // Note that MySQL uses the catalogName instead of the schemaName, so
        // pass in the given schema name as catalog name (first argument).
        ResultSet resultSet = databaseMetaData.getColumns(
                schemaName, null, tableName, "%");
        return resultSet;
    }
    
    public boolean matches(ResultSet resultSet,
            String schema, String table, boolean caseSensitive) 
    throws SQLException 
    {
        return matches(resultSet, null, schema, table, null, caseSensitive);
    }

    public boolean matches(ResultSet columnsResultSet, String catalog,
            String schema, String table, String column,
            boolean caseSensitive) throws SQLException 
    {
        String catalogName = columnsResultSet.getString(1);
        String schemaName = columnsResultSet.getString(2);
        String tableName = columnsResultSet.getString(3);
        String columnName = columnsResultSet.getString(4);

        // MYSQL provides only a catalog but no schema
        if(schema != null && schemaName == null && catalog==null && catalogName != null){
            logger.debug("Switching catalog/schema because the are mutually null");
            schemaName = catalogName;
            catalogName = null;
        }
        
        boolean areEqual = 
            areEqualIgnoreNull(catalog, catalogName, caseSensitive) &&
            areEqualIgnoreNull(schema, schemaName, caseSensitive) &&
            areEqualIgnoreNull(table, tableName, caseSensitive) &&
            areEqualIgnoreNull(column, columnName, caseSensitive);
        return areEqual;
    }

    private boolean areEqualIgnoreNull(String value1, String value2,
            boolean caseSensitive) {
        return SQLHelper.areEqualIgnoreNull(value1, value2, caseSensitive);
    }

    public String getSchema(ResultSet resultSet) throws SQLException {
        String catalogName = resultSet.getString(1);
        String schemaName = resultSet.getString(2);
        
        // Fix schema/catalog for mysql. Normally the schema is not set but only the catalog is set
        if(schemaName == null && catalogName != null) {
            logger.debug("Using catalogName '" + catalogName + "' as schema since the schema is null but the catalog is set (probably in a MySQL environment).");
            schemaName = catalogName;
        }
        return schemaName;
    }

    public boolean tableExists(DatabaseMetaData metaData, String schema, String tableName) 
    throws SQLException 
    {
        ResultSet tableRs = metaData.getTables(schema, null, tableName, null);
        try 
        {
            return tableRs.next();
        }
        finally
        {
            SQLHelper.close(tableRs);
        }
    }

    public ResultSet getTables(DatabaseMetaData metaData, String schemaName, String[] tableType) 
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("tableExists(metaData={}, schemaName={}, tableType={}) - start", 
                    new Object[] {metaData, schemaName, tableType} );

        return metaData.getTables(schemaName, null, "%", tableType);
    }

    public ResultSet getPrimaryKeys(DatabaseMetaData metaData, String schemaName, String tableName) 
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("getPrimaryKeys(metaData={}, schemaName={}, tableName={}) - start", 
                    new Object[] {metaData, schemaName, tableName} );

        ResultSet resultSet = metaData.getPrimaryKeys(
                schemaName, null, tableName);
        return resultSet;
    }

}
