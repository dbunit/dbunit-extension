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
package org.dbunit.ext.mariadb;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.ext.mysql.MySqlDataTypeFactory;

/**
 * Specialized factory that recognizes MariaDB data types.
 * <p>
 * MariaDB is wire-compatible with MySQL, so this extends
 * {@link MySqlDataTypeFactory} and adds recognition for the MariaDB-native
 * types that have no MySQL equivalent: {@code UUID} (10.7+) and
 * {@code INET4}/{@code INET6} (10.10+). MariaDB's JDBC driver reports all
 * three as SQL type {@link Types#OTHER}, which {@link MySqlDataTypeFactory}
 * (and its {@code DefaultDataTypeFactory} parent) does not otherwise
 * recognize.
 * <p>
 * MariaDB's {@code JSON} type is a {@code LONGTEXT} alias enforced by a
 * {@code CHECK} constraint, not a distinct SQL type — it already reports as
 * plain {@code LONGTEXT} and needs no special handling here.
 *
 * @author Jeff Jensen
 * @since 3.4.1
 */
public class MariaDbDataTypeFactory extends MySqlDataTypeFactory
{
    /** SQL type name MariaDB reports for a native {@code UUID} column. */
    public static final String SQL_TYPE_NAME_UUID = "UUID";
    /** SQL type name MariaDB reports for a native {@code INET4} column. */
    public static final String SQL_TYPE_NAME_INET4 = "INET4";
    /** SQL type name MariaDB reports for a native {@code INET6} column. */
    public static final String SQL_TYPE_NAME_INET6 = "INET6";

    /**
     * Database product names supported.
     */
    private static final Collection DATABASE_PRODUCTS = Arrays.asList(new String[] {"mariadb"});

    /**
     * @see org.dbunit.dataset.datatype.IDbProductRelatable#getValidDbProducts()
     */
    @Override
    public Collection getValidDbProducts()
    {
        return DATABASE_PRODUCTS;
    }

    @Override
    public DataType createDataType(int sqlType, String sqlTypeName) throws DataTypeException
    {
        if (sqlType == Types.OTHER)
        {
            if (SQL_TYPE_NAME_UUID.equalsIgnoreCase(sqlTypeName)
                    || SQL_TYPE_NAME_INET4.equalsIgnoreCase(sqlTypeName)
                    || SQL_TYPE_NAME_INET6.equalsIgnoreCase(sqlTypeName))
            {
                return DataType.VARCHAR;
            }
        }

        return super.createDataType(sqlType, sqlTypeName);
    }
}
