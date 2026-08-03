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
package org.dbunit.ext.mysql;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialized factory that recognizes MySql data types.
 * 
 * @author manuel.laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5 (Sep 3, 2003)
 */
public class MySqlDataTypeFactory extends DefaultDataTypeFactory
{
    /** Suffix MySQL appends to unsigned numeric type names, e.g. <code>"INT UNSIGNED"</code>. */
    public static final String UNSIGNED_SUFFIX = " UNSIGNED";
    /** SQL type name reported by MySQL for an unsigned <code>TINYINT</code> column. */
    public static final String SQL_TYPE_NAME_TINYINT_UNSIGNED = "TINYINT" + UNSIGNED_SUFFIX;

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(MySqlDataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"mysql"});

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    public Collection getValidDbProducts()
    {
        return DATABASE_PRODUCTS;
    }

    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException
    {
        if(logger.isDebugEnabled())
            logger.debug("createDataType(sqlType={}, sqlTypeName={}) - start", String.valueOf(sqlType), sqlTypeName);

        if (sqlType == Types.OTHER)
        {
            // CLOB
            if ("longtext".equalsIgnoreCase(sqlTypeName))
            {
                return DataType.CLOB;
            }
            // MySQL 5.0 Boolean
            else if("bit".equalsIgnoreCase(sqlTypeName))
            {
                return DataType.BOOLEAN;
            }
            else if("point".equalsIgnoreCase(sqlTypeName))
            {
                return DataType.BINARY;
            }
        }
        // Treat BIT as TINYINT
        else if("bit".equalsIgnoreCase(sqlTypeName))
        {
            return DataType.TINYINT;
        }

        // Special handling for "TINYINT UNSIGNED"
        if(SQL_TYPE_NAME_TINYINT_UNSIGNED.equalsIgnoreCase(sqlTypeName)){
            return DataType.TINYINT; // It is a bit of a waste here - we could better use a "Short" instead of an "Integer" type
        }

        // If we have an unsigned datatype check for some specialties
        // See http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-type-conversions.html
        if(sqlTypeName.endsWith(UNSIGNED_SUFFIX)) {
            if(sqlType == Types.INTEGER) {
                return DataType.BIGINT;
            }
        }

        return super.createDataType(sqlType, sqlTypeName);
    }
}
