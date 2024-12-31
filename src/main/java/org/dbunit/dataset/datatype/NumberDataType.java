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
 * @since Feb 17, 2002
 */
public class NumberDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(NumberDataType.class);

    private static final Number TRUE = new BigDecimal((double) 1);
    private static final Number FALSE = new BigDecimal((double) 0);

    NumberDataType(final String name, final int sqlType)
    {
        super(name, sqlType, BigDecimal.class, true);
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

        if (value instanceof BigDecimal)
        {
            return value;
        }

        if (value instanceof Boolean)
        {
            return ((Boolean) value) ? TRUE : FALSE;
        }

        try
        {
            return new BigDecimal(value.toString());
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

        final BigDecimal value = resultSet.getBigDecimal(column);
        if (value == null || resultSet.wasNull())
        {
            return null;
        }
        return value;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        statement.setBigDecimal(column, (BigDecimal) typeCast(value));
    }
}
