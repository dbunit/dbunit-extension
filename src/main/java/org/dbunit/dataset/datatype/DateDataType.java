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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.ITable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class DateDataType extends AbstractDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DateDataType.class);

    DateDataType()
    {
        super("DATE", Types.DATE, java.sql.Date.class, false);
    }

    ////////////////////////////////////////////////////////////////////////////
    // DataType class

    public Object typeCast(Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == null || value == ITable.NO_VALUE)
        {
            return null;
        }

        if (value instanceof java.sql.Date)
        {
            return value;
        }

        if (value instanceof java.util.Date)
        {
            java.util.Date date = (java.util.Date)value;
            return new java.sql.Date(date.getTime());
        }

        if (value instanceof Long)
        {
            Long date = (Long)value;
            return new java.sql.Date(date.longValue());
        }

        if (value instanceof String)
        {
            final String stringValue = (String)value;

            if (isExtendedSyntax(stringValue))
            {
                // Relative date.
                try
                {
                    LocalDateTime datetime =
                            RELATIVE_DATE_TIME_PARSER.parse(stringValue);
                    return java.sql.Date.valueOf(datetime.toLocalDate());
                } catch (IllegalArgumentException | DateTimeParseException e)
                {
                    throw new TypeCastException(value, this, e);
                }
            }

            // Probably a Timestamp, try it just in case!
            if (stringValue.length() > 10)
            {
                try
                {
                    long time = java.sql.Timestamp.valueOf(stringValue).getTime();
                    return new java.sql.Date(time);
//                    return java.sql.Date.valueOf(new java.sql.Date(time).toString());
                }
                catch (IllegalArgumentException e)
                {
                    // Was not a Timestamp, let java.sql.Date handle this value
                }
            }

            try
            {
                return java.sql.Date.valueOf(stringValue);
            }
            catch (IllegalArgumentException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        throw new TypeCastException(value, this);
    }

    public boolean isDateTime()
    {
        logger.debug("isDateTime() - start");

        return true;
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException
    {
    	logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        java.sql.Date value = resultSet.getDate(column);
        if (value == null || resultSet.wasNull())
        {
            return null;
        }
        return value;
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement)
            throws SQLException, TypeCastException
    {
    	logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
        		value, column, statement);

        statement.setDate(column, (java.sql.Date)typeCast(value));
    }
}





