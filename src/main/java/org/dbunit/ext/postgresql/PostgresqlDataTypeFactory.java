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
package org.dbunit.ext.postgresql;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

/**
 * Specialized factory that recognizes Postgresql data types.
 * <p>
 * Derived from work by manuel.laflamme
 * </p>
 *
 * @author Jarvis Cochrane (jarvis@cochrane.com.au)
 * @author manuel.laflamme
 * @author Martin Gollogly (zemertz@gmail.com)
 * @since 2.4.5 (Apr 27, 2009)
 */
public class PostgresqlDataTypeFactory extends DefaultDataTypeFactory
{
    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(PostgresqlDataTypeFactory.class);
    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS =
            Arrays.asList(new String[] {"PostgreSQL"});

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    @Override
    public Collection getValidDbProducts()
    {
        return DATABASE_PRODUCTS;
    }

    public static Collection getDatabaseProducts()
    {
        return DATABASE_PRODUCTS;
    }

    @Override
    public DataType createDataType(final int sqlType, final String sqlTypeName)
            throws DataTypeException
    {
        logger.debug("createDataType(sqlType={}, sqlTypeName={})",
                String.valueOf(sqlType), sqlTypeName);

        if (sqlType == Types.OTHER)
        {
            // Treat Postgresql UUID types as VARCHARS
            if ("uuid".equals(sqlTypeName))
            {
                return new UuidType();
            } else if ("interval".equals(sqlTypeName))
            {
                return new IntervalType();
            } else if ("inet".equals(sqlTypeName))
            {
                return new InetType();
            } else if ("geometry".equals(sqlTypeName))
            {
                return new GeometryType();
            } else if ("citext".equals(sqlTypeName))
            {
                return new CitextType();
            } else
            {
                // Finally check whether the user defined a custom datatype
                if (isEnumType(sqlTypeName))
                {
                    logger.debug(
                            "Custom enum type used for sqlTypeName {} (sqlType '{}')",
                            sqlTypeName, sqlType);
                    return new GenericEnumType(sqlTypeName);
                }
            }
        } else if (sqlType == Types.BIGINT && "oid".equals(sqlTypeName)) {
            return new PostgreSQLOidDataType();
        }

        return super.createDataType(sqlType, sqlTypeName);
    }

    /**
     * Returns a data type for the given sql type name if the user wishes one.
     * <b>Designed to be overridden by custom implementations extending this
     * class.</b> Override this method if you have a custom enum type in the
     * database and want to map it via dbunit.
     *
     * @param sqlTypeName
     *            The sql type name for which users can specify a custom data
     *            type.
     * @return <code>null</code> if the given type name is not a custom type
     *         which is the default implementation.
     * @since 2.4.6
     */
    public boolean isEnumType(final String sqlTypeName)
    {
        return false;
    }
}
