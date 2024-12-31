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
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.dbunit.dataset.ITable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data type that maps a SQL {@link Types#TIME} object to a java object.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Feb 19, 2002)
 */
public class TimeDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(TimeDataType.class);

    TimeDataType()
    {
        super("TIME", Types.TIME, Time.class, false);
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

        if (value instanceof java.sql.Time)
        {
            return value;
        }

        if (value instanceof java.util.Date)
        {
            final java.util.Date date = (java.util.Date) value;
            return new java.sql.Time(date.getTime());
        }

        if (value instanceof Long)
        {
            final Long date = (Long) value;
            return new java.sql.Time(date);
        }

        if (value instanceof String)
        {
            final String stringValue = (String) value;

            if (isExtendedSyntax(stringValue))
            {
                // Relative date.
                try
                {
                    final LocalDateTime datetime =
                            RELATIVE_DATE_TIME_PARSER.parse(stringValue);
                    return java.sql.Time.valueOf(datetime.toLocalTime());
                } catch (IllegalArgumentException | DateTimeParseException e)
                {
                    throw new TypeCastException(value, this, e);
                }
            }

            try
            {
                return java.sql.Time.valueOf(stringValue);
            } catch (final IllegalArgumentException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        throw new TypeCastException(value, this);
    }

    @Override
    public boolean isDateTime()
    {
        logger.debug("isDateTime() - start");

        return true;
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);

        return resultSet.wasNull() ? null : resultSet.getTime(column);
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        statement.setTime(column, (java.sql.Time) typeCast(value));
    }
}
