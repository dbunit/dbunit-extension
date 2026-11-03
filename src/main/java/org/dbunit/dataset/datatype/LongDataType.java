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
import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DataType} mapping the SQL BIGINT type to {@link Long}.
 *
 * <p>A {@link Boolean}, or the text {@code true} or {@code false} in any case, becomes 1
 * or 0, as it does for the other integer types. A JSON or YAML dataset hands an unquoted
 * {@code true} or {@code false} over as a {@link Boolean}, and an Excel dataset hands a
 * boolean cell over as one, where a flat XML dataset hands over the text.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class LongDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(LongDataType.class);

    LongDataType()
    {
        super("BIGINT", Types.BIGINT, Long.class, true);
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
            return ((Number) value).longValue();
        }

        if (value instanceof Boolean)
        {
            final boolean flag = ((Boolean) value).booleanValue();
            return flag ? 1L : 0L;
        }

        // Treat "false" as 0, "true" as 1
        if (value instanceof String)
        {
            final String string = (String) value;

            if ("false".equalsIgnoreCase(string))
            {
                return 0L;
            }

            if ("true".equalsIgnoreCase(string))
            {
                return 1L;
            }
        }

        try
        {
            return typeCast(new BigDecimal(value.toString()));
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

        final long rawValue = resultSet.getLong(column);
        final Long value = resultSet.wasNull() ? null : rawValue;
        logger.debug("getSqlValue: column={}, value={}", column, value);
        return value;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(
                    "setSqlValue(value={}, column={}, statement={}) - start",
                    value, column, statement);
        }

        statement.setLong(column, ((Number) typeCast(value)).longValue());
    }
}
