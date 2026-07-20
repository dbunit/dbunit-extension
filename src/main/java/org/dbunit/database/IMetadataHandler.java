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

/**
 * Handler to specify the behavior for a lookup of column metadata using database metadata.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.4
 */
public interface IMetadataHandler 
{

    /**
     * Returns the result set for an invocation of {@link DatabaseMetaData#getColumns(String, String, String, String)}.
     * @param databaseMetaData The database metadata to be used for retrieving the columns
     * @param schemaName The schema name
     * @param tableName The table name
     * @return The result set containing all columns
     * @throws SQLException
     * @since 2.4.4
     */
    ResultSet getColumns(DatabaseMetaData databaseMetaData, String schemaName, String tableName)
    throws SQLException;

    /**
     * Checks if the given <code>resultSet</code> matches the given schema and table name.
     * The comparison is <b>case sensitive</b>.
     * @param resultSet A result set produced via {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param schema
     * @param table
     * @param caseSensitive Whether or not the comparison should be case sensitive
     * @return <code>true</code> if the column metadata of the given <code>resultSet</code> matches
     * the given schema and table parameters.
     * @throws SQLException
     * @see #matches(ResultSet, String, String, String, String, boolean)
     * @since 2.4.4
     */
    public boolean matches(ResultSet resultSet, String schema, String table, boolean caseSensitive) 
    throws SQLException;

    /**
     * Checks if the given <code>resultSet</code> matches the given schema and table name.
     * The comparison is <b>case sensitive</b>.
     * @param resultSet A result set produced via {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param catalog The name of the catalog to check. If <code>null</code> it is ignored in the comparison
     * @param schema The name of the schema to check. If <code>null</code> it is ignored in the comparison
     * @param table The name of the table to check. If <code>null</code> it is ignored in the comparison
     * @param column The name of the column to check. If <code>null</code> it is ignored in the comparison
     * @param caseSensitive Whether or not the comparison should be case sensitive
     * @return <code>true</code> if the column metadata of the given <code>resultSet</code> matches
     * the given schema and table parameters.
     * @throws SQLException
     * @since 2.4.4
     */
    boolean matches(ResultSet resultSet, String catalog, String schema,
            String table, String column, boolean caseSensitive) throws SQLException;

    /**
     * Returns the schema name to which the table of the current result set index belongs.
     * @param resultSet The result set pointing to a valid record in the database that was returned
     * by {@link DatabaseMetaData#getTables(String, String, String, String[])}.
     * @return The name of the schema from the given result set
     * @since 2.4.4
     */
    String getSchema(ResultSet resultSet)  throws SQLException;

    /**
     * Checks if the given table exists.
     * @param databaseMetaData The database meta data
     * @param schemaName The schema in which the table should be searched. If <code>null</code>
     * the schema is not used to narrow the table name.
     * @param tableName The table name to be searched
     * @return Returns <code>true</code> if the given table exists in the given schema.
     * Else returns <code>false</code>.
     * @throws SQLException
     * @since 2.4.5
     */
    boolean tableExists(DatabaseMetaData databaseMetaData, String schemaName, String tableName)
    throws SQLException;

    /**
     * Returns the tables in the given schema that matches one of the given tableTypes.
     * @param databaseMetaData The database meta data
     * @param schemaName schema for which the tables should be retrieved; <code>null</code> returns all schemas
     * @param tableTypes a list of table types to include; <code>null</code> returns all types
     * @return The ResultSet which is retrieved using {@link DatabaseMetaData#getTables(String, String, String, String[])}
     * @throws SQLException
     * @since 2.4.5
     */
    ResultSet getTables(DatabaseMetaData databaseMetaData, String schemaName, String[] tableTypes)
    throws SQLException;

    /**
     * @param databaseMetaData The database meta data
     * @param schemaName schema for which the tables should be retrieved; <code>null</code> returns all schemas
     * @param tableName table for which the primary keys are retrieved
     * @return The ResultSet which is retrieved using {@link DatabaseMetaData#getPrimaryKeys(String, String, String)}
     * @throws SQLException
     * @since 2.4.5
     */
    public ResultSet getPrimaryKeys(DatabaseMetaData databaseMetaData, String schemaName, String tableName)
    throws SQLException;

    /**
     * Tests whether a candidate column's metadata values match the search criteria, using the
     * same semantics as {@link #matches(ResultSet, String, String, String, String, boolean)} but
     * operating on already-extracted values instead of a live {@link ResultSet} row. This lets a
     * caller that batch-fetches and caches {@code DatabaseMetaData#getColumns} rows (see
     * {@code ResultSetTableMetaData}) replay this handler's matching rules against a cached row
     * without re-querying or holding a {@link ResultSet} open.
     * <p>
     * The default implementation mirrors {@link DefaultMetadataHandler}'s matching rules. A
     * handler whose {@link #matches(ResultSet, String, String, String, String, boolean)} override
     * differs must override this method the same way, and override
     * {@link #supportsColumnCache()} to return {@code true}.
     * @param searchCatalog The catalog to search for. If <code>null</code> or empty it is ignored in the comparison.
     * @param actualCatalog The candidate row's catalog.
     * @param searchSchema The schema to search for. If <code>null</code> or empty it is ignored in the comparison.
     * @param actualSchema The candidate row's schema.
     * @param searchTable The table to search for. If <code>null</code> or empty it is ignored in the comparison.
     * @param actualTable The candidate row's table.
     * @param searchColumn The column to search for. If <code>null</code> or empty it is ignored in the comparison.
     * @param actualColumn The candidate row's column.
     * @param caseSensitive Whether or not the comparison should be case sensitive.
     * @return <code>true</code> if the candidate's values match the search criteria.
     * @since 3.2.1
     */
    default boolean matchesColumn(String searchCatalog, String actualCatalog,
            String searchSchema, String actualSchema, String searchTable, String actualTable,
            String searchColumn, String actualColumn, boolean caseSensitive)
    {
        return SQLHelper.areEqualIgnoreNull(searchCatalog, actualCatalog, caseSensitive)
                && SQLHelper.areEqualIgnoreNull(searchSchema, actualSchema, caseSensitive)
                && SQLHelper.areEqualIgnoreNull(searchTable, actualTable, caseSensitive)
                && SQLHelper.areEqualIgnoreNull(searchColumn, actualColumn, caseSensitive);
    }

    /**
     * Whether {@code ResultSetTableMetaData}'s per-table column-metadata cache may safely use
     * {@link #matchesColumn(String, String, String, String, String, String, String, String, boolean)}
     * in place of the row-by-row {@link #matches(ResultSet, String, String, String, String, boolean)}
     * scan for this handler.
     * <p>
     * Returns {@code false} by default, so a custom {@link IMetadataHandler} whose
     * {@code matches(...)} override is not also replicated in {@code matchesColumn(...)} keeps
     * the legacy per-column behavior. Override to return {@code true} only alongside a
     * {@code matchesColumn(...)} override that fully replicates this handler's matching semantics.
     * @return <code>true</code> if this handler's cache fast path is safe to use.
     * @since 3.2.1
     */
    default boolean supportsColumnCache()
    {
        return false;
    }

}
