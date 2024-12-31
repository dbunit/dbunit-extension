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

package org.dbunit.dataset.datatype;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.dbunit.util.RelativeDateTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data type that maps {@link java.sql.Types} objects to their java
 * counterparts. It also provides immutable constants for the most common data
 * types.
 * 
 * @see <a href=
 *      "http://java.sun.com/j2se/1.3/docs/guide/jdbc/getstart/mapping.html#table1">sun
 *      JDBC object mapping</a>
 * 
 * @author Manuel Laflamme
 * @version $Revision$
 */
public abstract class DataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(DataType.class);

    public static final DataType UNKNOWN = new UnknownDataType();

    public static final DataType CHAR = new StringDataType("CHAR", Types.CHAR);
    public static final DataType VARCHAR =
            new StringDataType("VARCHAR", Types.VARCHAR);
    public static final DataType LONGVARCHAR =
            new StringDataType("LONGVARCHAR", Types.LONGVARCHAR);
    public static final DataType CLOB = new ClobDataType();

    public static final DataType NUMERIC =
            new NumberDataType("NUMERIC", Types.NUMERIC);
    public static final DataType DECIMAL =
            new NumberDataType("DECIMAL", Types.DECIMAL);

    public static final DataType BOOLEAN = new BooleanDataType();
    public static final DataType BIT = new BitDataType();

    public static final DataType TINYINT =
            new IntegerDataType("TINYINT", Types.TINYINT);
    public static final DataType SMALLINT =
            new IntegerDataType("SMALLINT", Types.SMALLINT);
    public static final DataType INTEGER =
            new IntegerDataType("INTEGER", Types.INTEGER);

    // public static final DataType BIGINT = new LongDataType();
    public static final DataType BIGINT = new BigIntegerDataType();
    /**
     * Auxiliary for the BIGINT type using a long. Is currently only needed for
     * method {@link DataType#forObject(Object)}.
     */
    public static final DataType BIGINT_AUX_LONG = new LongDataType();

    public static final DataType REAL = new FloatDataType();

    public static final DataType FLOAT =
            new DoubleDataType("FLOAT", Types.FLOAT);
    public static final DataType DOUBLE =
            new DoubleDataType("DOUBLE", Types.DOUBLE);

    // To calculate consistent relative date and time.
    public static final RelativeDateTimeParser RELATIVE_DATE_TIME_PARSER =
            new RelativeDateTimeParser();
    public static final DataType DATE = new DateDataType();
    public static final DataType TIME = new TimeDataType();
    public static final DataType TIMESTAMP = new TimestampDataType();

    public static final DataType BINARY =
            new UuidAwareBytesDataType("BINARY", Types.BINARY);
    public static final DataType VARBINARY =
            new UuidAwareBytesDataType("VARBINARY", Types.VARBINARY);
    public static final DataType LONGVARBINARY =
            new UuidAwareBytesDataType("LONGVARBINARY", Types.LONGVARBINARY);
    public static final DataType BLOB = new BlobDataType();

    // New JDBC 4.0 types:
    // todo: ROWID = -8, NCLOB = 2011, SQLXML = 2009.

    public static final DataType NCHAR = new StringDataType("NCHAR", -15);

    public static final DataType NVARCHAR = new StringDataType("NVARCHAR", -9);

    public static final DataType LONGNVARCHAR =
            new StringDataType("LONGNVARCHAR", -16);

    private static final DataType[] TYPES = {VARCHAR, CHAR, LONGVARCHAR, NCHAR,
            NVARCHAR, LONGNVARCHAR, CLOB, NUMERIC, DECIMAL, BOOLEAN, BIT,
            INTEGER, TINYINT, SMALLINT, BIGINT, REAL, DOUBLE, FLOAT, DATE, TIME,
            TIMESTAMP, VARBINARY, BINARY, LONGVARBINARY, BLOB,
            // auxiliary types at the very end
            BIGINT_AUX_LONG};

    /**
     * Returns the specified value typecasted to this <code>DataType</code>
     */
    public abstract Object typeCast(Object value) throws TypeCastException;

    /**
     * Returns a negative integer, zero, or a positive integer as the first
     * argument is less than, equal to, or greater than the second.
     * <p>
     * The two values are typecast to this DataType before being compared.
     *
     * @throws TypeCastException
     *             if the arguments' types prevent them from being compared by
     *             this Comparator.
     */
    public abstract int compare(Object o1, Object o2) throws TypeCastException;

    /**
     * Returns the corresponding {@link java.sql.Types}.
     */
    public abstract int getSqlType();

    /**
     * Returns the runtime class of the typecast result.
     */
    public abstract Class getTypeClass();

    /**
     * Returns the SQL type name for user types (null for basic SQL types)
     */
    public String getSqlTypeName()
    {
        return null;
    }

    /**
     * Returns <code>true</code> if this <code>DataType</code> represents a
     * number.
     */
    public abstract boolean isNumber();

    /**
     * Returns <code>true</code> if this <code>DataType</code> represents a date
     * and/or time.
     */
    public abstract boolean isDateTime();

    /**
     * Returns the specified column value from the specified resultset object.
     */
    public abstract Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException;

    /**
     * Set the specified value to the specified prepared statement object.
     */
    public abstract void setSqlValue(Object value, int column,
            PreparedStatement statement) throws SQLException, TypeCastException;

    /**
     * Typecast the specified value to string.
     */
    public static String asString(Object value) throws TypeCastException
    {
        logger.debug("asString(value={}) - start", value);

        return (String) DataType.VARCHAR.typeCast(value);
    }

    /**
     * Returns the <code>DataType</code> corresponding to the specified Sql
     * type. See {@link java.sql.Types}.
     *
     */
    public static DataType forSqlType(int sqlType) throws DataTypeException
    {
        logger.debug("forSqlType(sqlType={}) - start", sqlType);

        for (int i = 0; i < TYPES.length; i++)
        {
            if (sqlType == TYPES[i].getSqlType())
            {
                return TYPES[i];
            }
        }

        return UNKNOWN;
    }

    /**
     * Returns the <code>DataType</code> corresponding to the specified Sql type
     * name.
     *
     * @deprecated Should not be used anymore
     */
    public static DataType forSqlTypeName(String sqlTypeName)
            throws DataTypeException
    {
        logger.debug("forSqlTypeName(sqlTypeName={}) - start", sqlTypeName);

        for (int i = 0; i < TYPES.length; i++)
        {
            if (sqlTypeName.equals(TYPES[i].toString()))
            {
                return TYPES[i];
            }
        }

        return UNKNOWN;
    }

    /**
     * Returns the <code>DataType</code> corresponding to the specified value
     * runtime class. This method returns <code>DataType.UNKNOWN</code> if the
     * value is <code>null</code> or runtime class not recognized.
     */
    public static DataType forObject(Object value)
    {
        logger.debug("forObject(value={}) - start", value);

        if (value == null)
        {
            return UNKNOWN;
        }

        for (int i = 0; i < TYPES.length; i++)
        {
            Class typeClass = TYPES[i].getTypeClass();
            if (typeClass.isInstance(value))
            {
                return TYPES[i];
            }
        }

        return UNKNOWN;
    }

    /**
     * Performs a quick check to test if the specified string uses extended
     * syntax.
     * 
     * @param input
     *            a string to check.
     * @return {@code true} if the input uses extended syntax; {@code false}
     *         otherwise.
     */
    protected static boolean isExtendedSyntax(String input)
    {
        return !input.isEmpty() && input.charAt(0) == '[';
    }
}
