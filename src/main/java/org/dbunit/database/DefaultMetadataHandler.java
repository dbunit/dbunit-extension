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
package org.dbunit.database;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.util.SQLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link IMetadataHandler} which works for the most databases.
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.4
 */
public class DefaultMetadataHandler implements IMetadataHandler {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultMetadataHandler.class);

    public ResultSet getColumns(DatabaseMetaData databaseMetaData, String schemaName, String tableName) 
    throws SQLException 
    {
        if(logger.isTraceEnabled())
            logger.trace("getColumns(databaseMetaData={}, schemaName={}, tableName={}) - start", 
                    new Object[] {databaseMetaData, schemaName, tableName} );
        
        ResultSet resultSet = databaseMetaData.getColumns(
                null, schemaName, tableName, "%");
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
        if(logger.isTraceEnabled())
            logger.trace("matches(columnsResultSet={}, catalog={}, schema={}," +
            		" table={}, column={}, caseSensitive={}) - start", 
                    new Object[] {columnsResultSet, catalog, schema, 
                            table, column, Boolean.valueOf(caseSensitive)});
        
        String catalogName = columnsResultSet.getString(1);
        String schemaName = columnsResultSet.getString(2);
        String tableName = columnsResultSet.getString(3);
        String columnName = columnsResultSet.getString(4);

        if(logger.isDebugEnabled()){
            logger.debug("Comparing the following values using caseSensitive={} (searched<=>actual): " +
                    "catalog: {}<=>{} schema: {}<=>{} table: {}<=>{} column: {}<=>{}", 
                    new Object[] {
                        Boolean.valueOf(caseSensitive),
                        catalog, catalogName,
                        schema, schemaName,
                        table, tableName,
                        column, columnName
                    });
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
        if(logger.isTraceEnabled())
            logger.trace("getColumns(resultSet={}) - start", resultSet);

        String schemaName = resultSet.getString(2);
        return schemaName;
    }
    
    public boolean tableExists(DatabaseMetaData metaData, String schemaName, String tableName)
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("tableExists(metaData={}, schemaName={}, tableName={}) - start",
                    new Object[] {metaData, schemaName, tableName} );

        String escapedTableName = escapePattern(metaData, tableName);
        ResultSet tableRs = metaData.getTables(null, schemaName, escapedTableName, null);
        try
        {
            return tableRs.next();
        }
        finally
        {
            SQLHelper.close(tableRs);
        }
    }

    private String escapePattern(DatabaseMetaData metaData, String identifier) throws SQLException
    {
        String escape = metaData.getSearchStringEscape();
        if (escape == null || escape.isEmpty())
        {
            return identifier;
        }
        return identifier
                .replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }

    public ResultSet getTables(DatabaseMetaData metaData, String schemaName, String[] tableType) 
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("getTables(metaData={}, schemaName={}, tableType={}) - start", 
                    new Object[] {metaData, schemaName, tableType} );

        return metaData.getTables(null, schemaName, "%", tableType);
    }

    public ResultSet getPrimaryKeys(DatabaseMetaData metaData, String schemaName, String tableName)
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("getPrimaryKeys(metaData={}, schemaName={}, tableName={}) - start",
                    new Object[] {metaData, schemaName, tableName} );

        ResultSet resultSet = metaData.getPrimaryKeys(
                null, schemaName, tableName);
        return resultSet;
    }

    /**
     * {@inheritDoc}
     * The inherited default {@code matchesColumn(...)} already mirrors this class's
     * {@link #matches(ResultSet, String, String, String, String, boolean)}, so the cache fast
     * path is safe to use.
     */
    public boolean supportsColumnCache()
    {
        return true;
    }

}
