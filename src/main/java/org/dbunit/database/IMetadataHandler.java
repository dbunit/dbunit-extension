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


}
