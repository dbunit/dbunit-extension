/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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

package org.dbunit.util;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.database.IMetadataHandler;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for SQL-related stuff.
 * <br>
 * TODO: testcases, also think about refactoring so that methods are not static anymore (for better extensibility)
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Nov 5, 2005
 * 
 */
public class SQLHelper {

    /**
     * The database product name reported by Sybase JDBC drivers.
     */
    public static final String DB_PRODUCT_SYBASE = "Sybase";

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SQLHelper.class);

    // class is "static"
    private SQLHelper() {}

    /**
     * Gets the primary column for a table.
     * @param conn connection with the database
     * @param table table name
     * @return name of primary column for a table (assuming it's just 1 column).
     * @throws SQLException raised while getting the meta data
     */
    public static String getPrimaryKeyColumn( Connection conn, String table ) throws SQLException {
        logger.debug("getPrimaryKeyColumn(conn={}, table={}) - start", conn, table);

        DatabaseMetaData metadata = conn.getMetaData();
        ResultSet rs = metadata.getPrimaryKeys( null, null, table );
        rs.next();
        String pkColumn = rs.getString(4);
        return pkColumn;
    }

    /**
     * Close a result set and a prepared statement, checking for null references.
     * @param rs result set to be closed
     * @param stmt prepared statement to be closed
     * @throws SQLException exception raised in either close() method
     */
    public static void close(ResultSet rs, Statement stmt) throws SQLException {
        logger.debug("close(rs={}, stmt={}) - start", rs, stmt);

        try {
            SQLHelper.close(rs);
        } finally {
            SQLHelper.close( stmt );
        }
    }

    /**
     * Close a SQL statement, checking for null references.
     * @param stmt statement to be closed
     * @throws SQLException exception raised while closing the statement
     */
    public static void close(Statement stmt) throws SQLException {
        logger.debug("close(stmt={}) - start", stmt);

        if ( stmt != null ) {
            stmt.close();
        }
    }

    /**
     * Closes the given result set in a null-safe way
     * @param resultSet
     * @throws SQLException
     */
    public static void close(ResultSet resultSet) throws SQLException {
        logger.debug("close(resultSet={}) - start", resultSet);

        if(resultSet != null) {
            resultSet.close();
        }
    }

    /**
     * Returns <code>true</code> if the given schema exists for the given connection.
     * @param connection The connection to a database
     * @param schema The schema to be searched
     * @return Returns <code>true</code> if the given schema exists for the given connection.
     * @throws SQLException
     * @since 2.3.0
     */
    public static boolean schemaExists(Connection connection, String schema)
            throws SQLException
            {
        logger.trace("schemaExists(connection={}, schema={}) - start", connection, schema);

        if(schema == null)
        {
            throw new NullPointerException("The parameter 'schema' must not be null");
        }

        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet rs = metaData.getSchemas(); //null, schemaPattern);
        try
        {
            while(rs.next())
            {
                String foundSchema = rs.getString("TABLE_SCHEM");
                if(foundSchema.equals(schema))
                {
                    return true;
                }
            }

            // Especially for MySQL check the catalog
            if(catalogExists(connection, schema))
            {
                logger.debug("Found catalog with name {}. Returning true because DB is probably on MySQL", schema);
                return true;
            }

            return false;
        }
        finally
        {
            rs.close();
        }
            }

    /**
     * Checks via {@link DatabaseMetaData#getCatalogs()} whether or not the given catalog exists.
     * @param connection
     * @param catalog
     * @return
     * @throws SQLException
     * @since 2.4.4
     */
    private static boolean catalogExists(Connection connection, String catalog) throws SQLException
    {
        logger.trace("catalogExists(connection={}, catalog={}) - start", connection, catalog);

        if(catalog == null)
        {
            throw new NullPointerException("The parameter 'catalog' must not be null");
        }

        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet rs = metaData.getCatalogs();
        try
        {
            while(rs.next())
            {
                String foundCatalog = rs.getString("TABLE_CAT");
                if(foundCatalog.equals(catalog))
                {
                    return true;
                }
            }
            return false;
        }
        finally
        {
            rs.close();
        }

    }

    /**
     * Checks if the given table exists.
     * @param metaData The database meta data
     * @param schema The schema in which the table should be searched. If <code>null</code>
     * the schema is not used to narrow the table name.
     * @param tableName The table name to be searched
     * @return Returns <code>true</code> if the given table exists in the given schema.
     * Else returns <code>false</code>.
     * @throws SQLException
     * @since 2.3.0
     * @deprecated since 2.4.5 - use {@link IMetadataHandler#tableExists(DatabaseMetaData, String, String)}
     */
    public static boolean tableExists(DatabaseMetaData metaData, String schema,
            String tableName)
                    throws SQLException
                    {
        ResultSet tableRs = metaData.getTables(null, schema, tableName, null);
        try
        {
            return tableRs.next();
        }
        finally
        {
            SQLHelper.close(tableRs);
        }
                    }

    /**
     * Utility method for debugging to print all tables of the given metadata on the given stream
     * @param metaData
     * @param outputStream
     * @throws SQLException
     */
    public static void printAllTables(DatabaseMetaData metaData, PrintStream outputStream) throws SQLException
    {
        ResultSet rs = metaData.getTables(null, null, null, null);
        try
        {
            while (rs.next())
            {
                String catalog = rs.getString("TABLE_CAT");
                String schema = rs.getString("TABLE_SCHEM");
                String table = rs.getString("TABLE_NAME");
                StringBuffer tableInfo = new StringBuffer();
     			if(catalog!=null) tableInfo.append(catalog).append(".");
     			if(schema!=null) tableInfo.append(schema).append(".");
                tableInfo.append(table);
                // Print the info
                outputStream.println(tableInfo);
            }
            outputStream.flush();
        }
        finally
        {
            SQLHelper.close(rs);
        }

    }

    /**
     * Returns the database and JDBC driver information as pretty formatted string
     * @param metaData The JDBC database metadata needed to retrieve database information
     * @return The database information as formatted string
     */
    public static String getDatabaseInfo(DatabaseMetaData metaData)
    {
        StringBuffer sb = new StringBuffer();
        sb.append("\n");

        String dbInfo = null;

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return metaData.getDatabaseProductName();
            }
        }.executeWrappedCall(metaData);
        sb.append("\tdatabase product name=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return metaData.getDatabaseProductVersion();
            }
        }.executeWrappedCall(metaData);
        sb.append("\tdatabase version=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return String.valueOf(metaData.getDatabaseMajorVersion());
            }
        }.executeWrappedCall(metaData);
        sb.append("\tdatabase major version=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return String.valueOf(metaData.getDatabaseMinorVersion());
            }
        }.executeWrappedCall(metaData);
        sb.append("\tdatabase minor version=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return metaData.getDriverName();
            }
        }.executeWrappedCall(metaData);
        sb.append("\tjdbc driver name=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return metaData.getDriverVersion();
            }
        }.executeWrappedCall(metaData);
        sb.append("\tjdbc driver version=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return String.valueOf(metaData.getDriverMajorVersion());
            }
        }.executeWrappedCall(metaData);
        sb.append("\tjdbc driver major version=").append(dbInfo).append("\n");

        dbInfo = new ExceptionWrapper(){
            public String wrappedCall(DatabaseMetaData metaData) throws Exception {
                return String.valueOf(metaData.getDriverMinorVersion());
            }
        }.executeWrappedCall(metaData);
        sb.append("\tjdbc driver minor version=").append(dbInfo).append("\n");

        return sb.toString();
    }

    /**
     * Prints the database and JDBC driver information to the given output stream
     * @param metaData The JDBC database metadata needed to retrieve database information
     * @param outputStream The stream to which the information is printed
     * @throws SQLException
     */
    public static void printDatabaseInfo(DatabaseMetaData metaData, PrintStream outputStream) throws SQLException
    {
        String dbInfo = getDatabaseInfo(metaData);
        try {
            outputStream.println(dbInfo);
        }
        finally {
            outputStream.flush();
        }
    }

    /**
     * Detects whether or not the given metadata describes the connection to a Sybase database
     * or not.
     * @param metaData The metadata to be checked whether it is a Sybase connection
     * @return <code>true</code> if and only if the given metadata belongs to a Sybase database.
     * @throws SQLException
     */
    public static boolean isSybaseDb(DatabaseMetaData metaData) throws SQLException
    {
        String dbProductName = metaData.getDatabaseProductName();
        boolean isSybase = (dbProductName != null && dbProductName.equals(DB_PRODUCT_SYBASE));
        return isSybase;
    }


    /**
     * Utility method to create a {@link Column} object from a SQL {@link ResultSet} object.
     * 
     * @param resultSet A result set produced via {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param dataTypeFactory The factory used to lookup the {@link DataType} for this column
     * @param datatypeWarning Whether or not a warning should be printed if the column could not
     * be created because of an unknown datatype.
     * @return The {@link Column} or <code>null</code> if the column could not be initialized because of an
     * unknown datatype.
     * @throws SQLException
     * @throws DataTypeException
     * @since 2.4.0
     */
    public static final Column createColumn(ResultSet resultSet,
            IDataTypeFactory dataTypeFactory, boolean datatypeWarning)
                    throws SQLException, DataTypeException
                    {
        String tableName = resultSet.getString(3);
        String columnName = resultSet.getString(4);
        int sqlType = resultSet.getInt(5);
        //If Types.DISTINCT like SQL DOMAIN, then get Source Date Type of SQL-DOMAIN
        if(sqlType == java.sql.Types.DISTINCT)
        {
            sqlType = resultSet.getInt("SOURCE_DATA_TYPE");
        }

        String sqlTypeName = resultSet.getString(6);
        //        int columnSize = resultSet.getInt(7);
        int nullable = resultSet.getInt(11);
        String remarks = resultSet.getString(12);
        String columnDefaultValue = resultSet.getString(13);
        String isAutoIncrement = resultSet.getString(23);
        // some JDBC drivers do not have this column even though they claim to be compliant with JDBC 4.1 or later
        String isGenerated = resultSet.getMetaData().getColumnCount() >= 24 ? resultSet.getString(24) : null;

        // Convert SQL type to DataType
        DataType dataType =
                dataTypeFactory.createDataType(sqlType, sqlTypeName, tableName, columnName);
        if (dataType != DataType.UNKNOWN)
        {
            Column column = new Column(columnName, dataType,
                    sqlTypeName, Column.nullableValue(nullable), columnDefaultValue, remarks,
                    Column.AutoIncrement.autoIncrementValue(isAutoIncrement),
                    Column.convertMetaDataBoolean(isGenerated));
            return column;
        }
        else
        {
            if (datatypeWarning)
                logger.warn(
                        tableName + "." + columnName +
                        " data type (" + sqlType + ", '" + sqlTypeName +
                        "') not recognized and will be ignored. See FAQ for more information.");

            // datatype unknown - column not created
            return null;
        }
                    }

    /**
     * Checks if the given <code>resultSet</code> matches the given schema and table name.
     * The comparison is <b>case sensitive</b>.
     * @param resultSet A result set produced via {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param schema The name of the schema to check. If <code>null</code> it is ignored in the comparison
     * @param table The name of the table to check. If <code>null</code> it is ignored in the comparison
     * @param caseSensitive Whether or not the comparison should be case sensitive or not
     * @return <code>true</code> if the column metadata of the given <code>resultSet</code> matches
     * the given schema and table parameters.
     * @throws SQLException
     * @since 2.4.0
     * @deprecated since 2.4.4 - use {@link IMetadataHandler#matches(ResultSet, String, String, String, String, boolean)}
     */
    public static boolean matches(ResultSet resultSet,
            String schema, String table, boolean caseSensitive)
                    throws SQLException
                    {
        return matches(resultSet, null, schema, table, null, caseSensitive);
                    }


    /**
     * Checks if the given <code>resultSet</code> matches the given schema and table name.
     * The comparison is <b>case sensitive</b>.
     * @param resultSet A result set produced via {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param catalog The name of the catalog to check. If <code>null</code> it is ignored in the comparison
     * @param schema The name of the schema to check. If <code>null</code> it is ignored in the comparison
     * @param table The name of the table to check. If <code>null</code> it is ignored in the comparison
     * @param column The name of the column to check. If <code>null</code> it is ignored in the comparison
     * @param caseSensitive Whether or not the comparison should be case sensitive or not
     * @return <code>true</code> if the column metadata of the given <code>resultSet</code> matches
     * the given schema and table parameters.
     * @throws SQLException
     * @since 2.4.0
     * @deprecated since 2.4.4 - use {@link IMetadataHandler#matches(ResultSet, String, String, String, String, boolean)}
     */
    public static boolean matches(ResultSet resultSet,
            String catalog, String schema,
            String table, String column, boolean caseSensitive)
                    throws SQLException
                    {
        String catalogName = resultSet.getString(1);
        String schemaName = resultSet.getString(2);
        String tableName = resultSet.getString(3);
        String columnName = resultSet.getString(4);

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

    /**
     * Compares the given values and returns true if they are equal.
     * If the first value is <code>null</code> or empty String it always
     * returns <code>true</code> which is the way of ignoring <code>null</code>s
     * for this specific case.
     * @param value1 The first value to compare. Is ignored if null or empty String
     * @param value2 The second value to be compared
     * @return <code>true</code> if both values are equal or if the first value
     * is <code>null</code> or empty string.
     * @since 2.4.4
     */
    public static final boolean areEqualIgnoreNull(String value1, String value2, boolean caseSensitive)
    {
        if(value1==null || value1.equals(""))
        {
            return true;
        }
        else
        {
            if(caseSensitive && value1.equals(value2))
            {
                return true;
            }
            else if(!caseSensitive && value1.equalsIgnoreCase(value2))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    /**
     * Corrects the case of the given String according to the way in which the database stores metadata.
     * @param databaseIdentifier A database identifier such as a table name or a schema name for
     * which the case should be corrected.
     * @param connection The connection used to lookup the database metadata. This is needed to determine
     * the way in which the database stores its metadata.
     * @return The database identifier in the correct case for the RDBMS
     * @since 2.4.4
     */
    public static final String correctCase(final String databaseIdentifier, Connection connection)
    {
        logger.trace("correctCase(tableName={}, connection={}) - start", databaseIdentifier, connection);

        try
        {
            return correctCase(databaseIdentifier, connection.getMetaData());
        }
        catch (SQLException e)
        {
            throw new DatabaseUnitRuntimeException("Exception while trying to access database metadata", e);
        }
    }

    /**
     * Corrects the case of the given String according to the way in which the database stores metadata.
     * @param databaseIdentifier A database identifier such as a table name or a schema name for
     * which the case should be corrected.
     * @param databaseMetaData The database metadata needed to determine the way in which the database stores
     * its metadata.
     * @return The database identifier in the correct case for the RDBMS
     * @since 2.4.4
     */
    public static final String correctCase(final String databaseIdentifier, DatabaseMetaData databaseMetaData)
    {
        logger.trace("correctCase(tableName={}, databaseMetaData={}) - start", databaseIdentifier, databaseMetaData);

        if (databaseIdentifier == null) {
            throw new NullPointerException(
                    "The parameter 'databaseIdentifier' must not be null");
        }
        if (databaseMetaData == null) {
            throw new NullPointerException(
                    "The parameter 'databaseMetaData' must not be null");
        }

        try {
            String resultTableName = databaseIdentifier;
            String dbIdentifierQuoteString = databaseMetaData.getIdentifierQuoteString();
            if(!isEscaped(databaseIdentifier, dbIdentifierQuoteString)){
                if(databaseMetaData.storesLowerCaseIdentifiers())
                {
                    resultTableName = databaseIdentifier.toLowerCase(Locale.ENGLISH);
                }
                else if(databaseMetaData.storesUpperCaseIdentifiers())
                {
                    resultTableName = databaseIdentifier.toUpperCase(Locale.ENGLISH);
                }
                else
                {
                    logger.debug("Database does not store upperCase or lowerCase identifiers. " +
                            "Will not correct case of the table names.");
                }
            }
            else
            {
                if(logger.isDebugEnabled())
                    logger.debug("The tableName '{}' is escaped. Will not correct case.", databaseIdentifier);
                }
            return resultTableName;
        }
        catch (SQLException e)
        {
            throw new DatabaseUnitRuntimeException("Exception while trying to access database metadata", e);
        }
    }

    /**
     * Checks whether two given values are unequal and if so print a log message (level DEBUG)
     * @param oldValue The old value of a property
     * @param newValue The new value of a property
     * @param message The message to be logged
     * @param source The class which invokes this method - used for enriching the log message
     * @since 2.4.4
     */
    public static final void logInfoIfValueChanged(String oldValue, String newValue, String message, Class source)
    {
        if(logger.isInfoEnabled())
        {
            if(oldValue != null && !oldValue.equals(newValue))
                logger.debug("{}. {} oldValue={} newValue={}", new Object[] {source, message, oldValue, newValue});
            }
        }

    /**
     * Checks whether two given values are unequal and if so print a log message (level DEBUG)
     * @param oldValue The old value of a property
     * @param newValue The new value of a property
     * @param message The message to be logged
     * @param source The class which invokes this method - used for enriching the log message
     * @since 2.4.8
     */
    public static final void logDebugIfValueChanged(String oldValue, String newValue, String message, Class source)
    {
        if (logger.isDebugEnabled())
        {
            if (oldValue != null && !oldValue.equals(newValue))
                logger.debug("{}. {} oldValue={} newValue={}", new Object[] {source, message, oldValue, newValue});
            }
        }

    /**
     * @param tableName
     * @param dbIdentifierQuoteString
     * @return
     * @since 2.4.4
     */
    private static final boolean isEscaped(String tableName, String dbIdentifierQuoteString)
    {
        logger.trace("isEscaped(tableName={}, dbIdentifierQuoteString={}) - start", tableName, dbIdentifierQuoteString);

        if (dbIdentifierQuoteString == null) {
            throw new NullPointerException(
                    "The parameter 'dbIdentifierQuoteString' must not be null");
        }
        boolean isEscaped = tableName!=null && (tableName.startsWith(dbIdentifierQuoteString));
        if(logger.isDebugEnabled())
            logger.debug("isEscaped returns '{}' for tableName={} (dbIdentifierQuoteString={})",
                    new Object[]{Boolean.valueOf(isEscaped), tableName, dbIdentifierQuoteString} );
        return isEscaped;
    }


    /**
     * Performs a method invocation and catches all exceptions that occur during the invocation.
     * Utility which works similar to a closure, just a bit less elegant.
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.6
     */
    static abstract class ExceptionWrapper{

        public static final String NOT_AVAILABLE_TEXT = "<not available>";

        /**
         * Default constructor
         */
        public ExceptionWrapper()
        {
        }

        /**
         * Executes the call and catches all exception that might occur.
         * @param metaData
         * @return The result of the call
         */
        public final String executeWrappedCall(DatabaseMetaData metaData) {
            try{
                String result = wrappedCall(metaData);
                return result;
            }
            catch(Exception e){
                logger.trace("Problem retrieving DB information via DatabaseMetaData", e);
                return NOT_AVAILABLE_TEXT;
            }
        }
        /**
         * Calls the method that might throw an exception to be handled
         * @param metaData
         * @return The result of the call as human readable string
         * @throws Exception Any exception that might occur during the method invocation
         */
        public abstract String wrappedCall(DatabaseMetaData metaData) throws Exception;
    }

}
