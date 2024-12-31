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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.dataset.ITable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class IntegerDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(IntegerDataType.class);

    IntegerDataType(final String name, final int sqlType)
    {
        super(name, sqlType, Integer.class, true);
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

        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }

        // Treat "false" as 0, "true" as 1
        if (value instanceof String)
        {
            final String string = (String) value;

            if ("false".equalsIgnoreCase(string))
            {
                return 0;
            }

            if ("true".equalsIgnoreCase(string))
            {
                return 1;
            }
        }

        // Bugfix in release 2.4.6
        final String stringValue = value.toString().trim();
        if (stringValue.length() <= 0)
        {
            return null;
        }

        try
        {
            return typeCast(new BigDecimal(stringValue));
        } catch (final java.lang.NumberFormatException e)
        {
            throw new TypeCastException(value, this, e);
        }
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);
        final int rawValue = resultSet.getInt(column);
        final Integer value = resultSet.wasNull() ? null : rawValue;
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

        statement.setInt(column, (Integer) typeCast(value));
    }
}
