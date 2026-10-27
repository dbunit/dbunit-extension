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

import java.sql.Connection;
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
 * <p>
 * MySQL has no schema distinct from its database, which MySQL Connector/J and MariaDB Connector/J
 * report as the catalog, so this handler passes a schema to the driver as the catalog. An empty
 * schema means the database the connection works in: MySQL Connector/J lists nothing for an
 * empty catalog, where MariaDB Connector/J lists the tables of the current database.
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
                catalogOf(databaseMetaData, schemaName), null, tableName, "%");
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

    /**
     * Returns the database the connection works in, its JDBC catalog. MySQL Connector/J and
     * MariaDB Connector/J report that database as the catalog and no current schema by default,
     * and this handler passes a schema to the driver as the catalog, so the database is the
     * current schema in this handler's terms.
     *
     * @param connection The JDBC connection to read the current database of.
     * @return The current database, or {@code null} if the connection works in none.
     * @throws SQLException If reading the catalog fails.
     * @since 3.6.0
     */
    @Override
    public String getCurrentSchema(Connection connection) throws SQLException
    {
        return connection.getCatalog();
    }

    /**
     * Returns the catalog to pass to the driver for a schema: the schema itself, or for an empty
     * schema the database the connection works in, as MySQL Connector/J lists nothing for an
     * empty catalog.
     *
     * @param metaData The metadata of the connection.
     * @param schemaName The schema, or {@code null} to search every catalog.
     * @return The catalog to pass to the driver.
     * @throws SQLException If reading the catalog of the connection fails.
     */
    private static String catalogOf(final DatabaseMetaData metaData, final String schemaName)
            throws SQLException
    {
        if (schemaName != null && schemaName.isEmpty())
        {
            final Connection connection = metaData.getConnection();
            return connection.getCatalog();
        }
        return schemaName;
    }

    public boolean tableExists(DatabaseMetaData metaData, String schema, String tableName) 
    throws SQLException 
    {
        ResultSet tableRs = metaData.getTables(catalogOf(metaData, schema), null, tableName, null);
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

        return metaData.getTables(catalogOf(metaData, schemaName), null, "%", tableType);
    }

    public ResultSet getPrimaryKeys(DatabaseMetaData metaData, String schemaName, String tableName)
    throws SQLException
    {
        if(logger.isTraceEnabled())
            logger.trace("getPrimaryKeys(metaData={}, schemaName={}, tableName={}) - start",
                    new Object[] {metaData, schemaName, tableName} );

        ResultSet resultSet = metaData.getPrimaryKeys(
                catalogOf(metaData, schemaName), null, tableName);
        return resultSet;
    }

    /**
     * {@inheritDoc}
     * Mirrors {@link #matches(ResultSet, String, String, String, String, boolean)}'s
     * catalog/schema swap: MySQL's {@code getColumns} rows report only a catalog, not a schema,
     * so when the search side expects a schema but the row has none, the row's catalog is
     * compared against the searched schema instead.
     */
    public boolean matchesColumn(String searchCatalog, String actualCatalog,
            String searchSchema, String actualSchema, String searchTable, String actualTable,
            String searchColumn, String actualColumn, boolean caseSensitive)
    {
        if(searchSchema != null && actualSchema == null && searchCatalog == null && actualCatalog != null){
            logger.debug("Switching catalog/schema because the are mutually null");
            actualSchema = actualCatalog;
            actualCatalog = null;
        }

        return areEqualIgnoreNull(searchCatalog, actualCatalog, caseSensitive) &&
            areEqualIgnoreNull(searchSchema, actualSchema, caseSensitive) &&
            areEqualIgnoreNull(searchTable, actualTable, caseSensitive) &&
            areEqualIgnoreNull(searchColumn, actualColumn, caseSensitive);
    }

    /**
     * {@inheritDoc}
     * @return <code>true</code>, since {@link #matchesColumn} replicates this class's
     * {@link #matches(ResultSet, String, String, String, String, boolean)} semantics.
     */
    public boolean supportsColumnCache()
    {
        return true;
    }

}
