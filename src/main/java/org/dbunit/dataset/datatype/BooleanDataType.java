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

import org.dbunit.dataset.ITable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 */
public class BooleanDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(BooleanDataType.class);

    BooleanDataType()
    {
        this("BOOLEAN", Types.BOOLEAN);
    }

    /**
     * @since 2.3
     */
    BooleanDataType(final String name, final int sqlType)
    {
        super(name, sqlType, Boolean.class, false);
    }

    ////////////////////////////////////////////////////////////////////////////
    // DataType class

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == null || value == ITable.NO_VALUE)
        {
            return null;
        }

        if (value instanceof Boolean)
        {
            return value;
        }

        if (value instanceof Number)
        {
            final Number number = (Number) value;
            if (number.intValue() == 0)
            {
                return Boolean.FALSE;
            } else
            {
                return Boolean.TRUE;
            }
        }

        if (value instanceof String)
        {
            final String string = (String) value;

            if ("true".equalsIgnoreCase(string)
                    || "false".equalsIgnoreCase(string))
            {
                return Boolean.valueOf(string);
            } else
            {
                return typeCast(DataType.INTEGER.typeCast(string));
            }
        }

        throw new TypeCastException(value, this);
    }

    @Override
    protected int compareNonNulls(final Object value1, final Object value2)
            throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start", value1,
                value2);

        final Boolean value1bool = (Boolean) value1;
        final Boolean value2bool = (Boolean) value2;

        if (value1bool.equals(value2bool))
        {
            return 0;
        }

        if (!value1bool)
        {
            return -1;
        }

        return 1;
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);
        final boolean rawValue = resultSet.getBoolean(column);
        final Boolean value = resultSet.wasNull() ? null : rawValue;
        logger.debug("getSqlValue: column={}, value={}", column, value);
        return value;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        final Boolean castValue = (Boolean) typeCast(value);
        if (castValue == null)
        {
            statement.setNull(column, Types.BOOLEAN);
        } else
        {
            statement.setBoolean(column, castValue);
        }
    }
}
