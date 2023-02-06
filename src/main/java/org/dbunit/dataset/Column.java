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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.datatype.DataType;

import java.sql.DatabaseMetaData;

/**
 * Represents a table column.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Feb 17, 2002)
 */
public class Column
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Column.class);

    /**
     * Indicates that the column might not allow <code>NULL</code> values.
     */
    public static final Nullable NO_NULLS = new Nullable("noNulls");
    /**
     * Indicates that the column definitely allows <code>NULL</code> values.
     */
    public static final Nullable NULLABLE = new Nullable("nullable");
    /**
     * Indicates that the nullability of columns is unknown.
     */
    public static final Nullable NULLABLE_UNKNOWN = new Nullable("nullableUnknown");

    private final String _columnName;
    private final DataType _dataType;
    private final String _sqlTypeName;
    private final Nullable _nullable;
    private final String _defaultValue;
    private final String _remarks;
    private final AutoIncrement _autoIncrement;
    private final Boolean _generatedColumn;

    /**
     * Creates a Column object. This constructor set nullable to true.
     *
     * @param columnName the column name
     * @param dataType the data type
     */
    public Column(String columnName, DataType dataType)
    {
        this(columnName, dataType, NULLABLE_UNKNOWN);
    }

    /**
     * Creates a Column object.
     */
    public Column(String columnName, DataType dataType, Nullable nullable)
    {
        this(columnName, dataType, dataType.toString(), nullable, null);
    }

    /**
     * Creates a Column object.
     */
    public Column(String columnName, DataType dataType, String sqlTypeName,
            Nullable nullable)
    {
        this(columnName, dataType, sqlTypeName, nullable, null);
    }

    /**
     * Creates a Column object.
     * @param columnName The name of the column
     * @param dataType The DbUnit {@link DataType} of the column
     * @param sqlTypeName The SQL name of the column which comes from the JDBC driver.
     * See value 'TYPE_NAME' in {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param nullable whether or not the column is nullable
     * @param defaultValue The default value on the DB for this column. Can be <code>null</code>.
     */
    public Column(String columnName, DataType dataType, String sqlTypeName,
            Nullable nullable, String defaultValue)
    {
        this(columnName, dataType, sqlTypeName, nullable, defaultValue, null, null);
    }

    /**
     * Creates a Column object.
     * @param columnName The name of the column
     * @param dataType The DbUnit {@link DataType} of the column
     * @param sqlTypeName The SQL name of the column which comes from the JDBC driver.
     * See value 'TYPE_NAME' in {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param nullable whether or not the column is nullable
     * @param defaultValue The default value on the DB for this column. Can be <code>null</code>.
     * @param remarks The remarks on the DB for this column. Can be <code>null</code>.
     * @param autoIncrement The auto increment setting for this column. Can be <code>null</code>.
     */
    public Column(String columnName, DataType dataType, String sqlTypeName,
            Nullable nullable, String defaultValue, String remarks, AutoIncrement autoIncrement)
    {
        this(columnName, dataType, sqlTypeName, nullable, defaultValue, remarks, autoIncrement, null);
    }

    /**
     * Creates a Column object.
     * @param columnName The name of the column
     * @param dataType The DbUnit {@link DataType} of the column
     * @param sqlTypeName The SQL name of the column which comes from the JDBC driver.
     * See value 'TYPE_NAME' in {@link DatabaseMetaData#getColumns(String, String, String, String)}
     * @param nullable whether or not the column is nullable
     * @param defaultValue The default value on the DB for this column. Can be <code>null</code>.
     * @param remarks The remarks on the DB for this column. Can be <code>null</code>.
     * @param autoIncrement The auto increment setting for this column. Can be <code>null</code>.
     * @param generatedColumn Whether this column is a generated column. Can be <code>null</code>.
     */
    public Column(String columnName, DataType dataType, String sqlTypeName,
            Nullable nullable, String defaultValue, String remarks, AutoIncrement autoIncrement,
            Boolean generatedColumn)
    {
        _columnName = columnName;
        _dataType = dataType;
        _sqlTypeName = sqlTypeName;
        _nullable = nullable;
        _defaultValue = defaultValue;
        _remarks = remarks;
        _autoIncrement = autoIncrement;
        _generatedColumn = generatedColumn;
    }

    public boolean hasDefaultValue()
    {
        return _defaultValue != null;
    }
    
    public boolean isNotNullable()
    {
        return _nullable== Column.NO_NULLS;
    }
    
    /**
     * Returns this column name.
     */
    public String getColumnName()
    {
        return _columnName;
    }

    /**
     * Returns this column data type.
     */
    public DataType getDataType()
    {
        return _dataType;
    }

    /**
     * Returns this column sql data type name.
     */
    public String getSqlTypeName()
    {
        return _sqlTypeName;
    }

    /**
     * Returns <code>true</code> if this column is nullable.
     */
    public Nullable getNullable()
    {
        return _nullable;
    }

    /**
     * @return The default value the database uses for this column 
     * if not specified in the insert column list
     */
    public String getDefaultValue()
    {
        return _defaultValue;
    }
    
    /**
     * @return The remarks set on the database for this column
     * @since 2.4.3
     */
    public String getRemarks()
    {
        return _remarks;
    }
    
    /**
     * @return The auto-increment property for this column
     * @since 2.4.3
     */
    public AutoIncrement getAutoIncrement()
    {
        return _autoIncrement;
    }
    
    /**
     * @return Whether the column is a generated column
     * @since 3.0.0
     */
    public Boolean getGeneratedColumn()
    {
        return _generatedColumn;
    }
    
    /**
     * Returns the appropriate Nullable constant according specified JDBC
     * DatabaseMetaData constant.
     *
     * @param nullable one of the following constants
     * {@link java.sql.DatabaseMetaData#columnNoNulls},
     * {@link java.sql.DatabaseMetaData#columnNullable},
     * {@link java.sql.DatabaseMetaData#columnNullableUnknown}
     */
    public static Nullable nullableValue(int nullable)
    {
        if(logger.isDebugEnabled())
            logger.debug("nullableValue(nullable={}) - start", String.valueOf(nullable));

        switch (nullable)
        {
            case DatabaseMetaData.columnNoNulls:
                return NO_NULLS;

            case DatabaseMetaData.columnNullable:
                return NULLABLE;

            case DatabaseMetaData.columnNullableUnknown:
                return NULLABLE_UNKNOWN;

            default:
                throw new IllegalArgumentException("Unknown constant value "
                        + nullable);
        }
    }

    /**
     * Returns the appropriate Nullable constant.
     *
     * @param nullable <code>true</code> if null is allowed
     */
    public static Nullable nullableValue(boolean nullable)
    {
        if(logger.isDebugEnabled())
            logger.debug("nullableValue(nullable={}) - start", String.valueOf(nullable));
        
        return nullable ? NULLABLE : NO_NULLS;
    }
    
    /**
     * Converts a DatabaseMetaData boolean string to a Boolean object.
     * @param value The string to convert
     * @return True if string is "YES", false if string is "NO", null otherwise
     */
    public static Boolean convertMetaDataBoolean(String value)
    {
        if ("YES".equalsIgnoreCase(value)) {
            return true;
        } else if ("NO".equalsIgnoreCase(value)) {
            return false;
        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // Object class

    public String toString()
    {
        return "(" + _columnName + ", " + _dataType + ", " + _nullable + ")";
    }

    public boolean equals(Object o)
    {
        logger.debug("equals(o={}) - start", o);

        if (this == o) return true;
        if (!(o instanceof Column)) return false;

        final Column column = (Column)o;

        if (!_columnName.equals(column._columnName)) return false;
        if (!_dataType.equals(column._dataType)) return false;
        if (!_nullable.equals(column._nullable)) return false;
        if (!_sqlTypeName.equals(column._sqlTypeName)) return false;
        
        // Default value is nullable
        if (_defaultValue==null){
            if(column._defaultValue!=null)
                return false;
        }
        else{
            if(!_defaultValue.equals(column._defaultValue))
                return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _columnName.hashCode();
        result = 29 * result + _dataType.hashCode();
        result = 29 * result + _sqlTypeName.hashCode();
        result = 29 * result + _nullable.hashCode();
        result = 29 * result + (_defaultValue==null? 0 : _defaultValue.hashCode());
        return result;
    }

    /**
     * Specifies nullable usage.
     * 
	 * @author Manuel Laflamme
	 * @author Last changed by: $Author$
	 * @version $Revision$ $Date$
	 * @since Feb 17, 2002
	 * @see Column
     */
    public static class Nullable
    {

        private final String _name;

        private Nullable(String name)
        {
            _name = name;
        }

        ////////////////////////////////////////////////////////////////////////////
        // Object class

        public String toString()
        {
            return _name;
        }
    }
    
    
    /**
     * Enumeration for valid auto-increment values provided by JDBC driver implementations.
     * 
     * @author gommma
     * @author Last changed by: $Author$
     * @version $Revision$ $Date$
     * @since 2.4.3
     * @see Column
     */
    public static class AutoIncrement
    {
        public static final AutoIncrement YES = new AutoIncrement("YES");
        public static final AutoIncrement NO = new AutoIncrement("NO");
        public static final AutoIncrement UNKNOWN = new AutoIncrement("UNKNOWN");
        
        /**
         * Logger for this class
         */
        private static final Logger LOGGER = LoggerFactory.getLogger(AutoIncrement.class);

        private final String key;
        private AutoIncrement(String key)
        {
            this.key = key;
        }
        
        public String getKey() 
        {
            return key;
        }

        /**
         * Searches the enumeration type for the given String provided by the JDBC driver.
         * <p>
         * If the parameter <code>autoIncrementValue</code>
         * <ul>
         * <li>equalsIgnoreCase &quot;YES&quot; or equals &quot;1&quot; then {@link AutoIncrement#YES} is returned</li>
         * <li></li>
         * </ul>
         * </p>
         * @param isAutoIncrement The String from the JDBC driver.
         * @return The enumeration
         */
        public static AutoIncrement autoIncrementValue(String isAutoIncrement) 
        {
            if(LOGGER.isDebugEnabled())
                logger.debug("autoIncrementValue(isAutoIncrement={}) - start", isAutoIncrement);
            
            AutoIncrement result = AutoIncrement.UNKNOWN;
            
            if(isAutoIncrement != null)
            {
                if(isAutoIncrement.equalsIgnoreCase("YES") || isAutoIncrement.equals("1"))
                {
                    result = AutoIncrement.YES;
                }
                else if(isAutoIncrement.equalsIgnoreCase("NO") || isAutoIncrement.equals("0"))
                {
                    result = AutoIncrement.NO;
                }
            }
            return result;
        }


        public String toString()
        {
            return "autoIncrement=" + key;
        }
    }

}
