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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
public class LongDataType extends AbstractDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(LongDataType.class);

    LongDataType()
    {
        super("BIGINT", Types.BIGINT, Long.class, true);
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

        if (value instanceof Number)
        {
            return ((Number)value).longValue();
        }

        try
        {
            return typeCast(new BigDecimal(value.toString()));
        }
        catch (java.lang.NumberFormatException e)
        {
            throw new TypeCastException(value, this, e);
        }
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException
    {
    	logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        final long value = resultSet.getLong(column);
        if (resultSet.wasNull())
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

        statement.setLong(column, ((Number)typeCast(value)).longValue());
    }
}





