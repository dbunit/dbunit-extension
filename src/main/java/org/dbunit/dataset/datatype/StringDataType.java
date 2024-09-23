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

import java.sql.Blob;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.dataset.ITable;
import org.dbunit.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0
 */
public class StringDataType extends AbstractDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(StringDataType.class);

    public StringDataType(String name, int sqlType)
    {
        super(name, sqlType, String.class, false);
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

        if (value instanceof String)
        {
            return value;
        }

        if (value instanceof java.sql.Date ||
                value instanceof java.sql.Time ||
                value instanceof java.sql.Timestamp ||
                value instanceof java.time.temporal.TemporalAccessor)
        {
            return value.toString();
        }

        if (value instanceof Boolean)
        {
            return value.toString();
        }

        if (value instanceof Number)
        {
            try
            {
                return value.toString();
            }
            catch (java.lang.NumberFormatException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        if (value instanceof byte[])
        {
            return Base64.encodeBytes((byte[])value);
        }

        if (value instanceof Blob)
        {
            try
            {
                Blob blob = (Blob)value;
                byte[] blobValue = blob.getBytes(1, (int)blob.length());
                return typeCast(blobValue);
            }
            catch (SQLException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        if (value instanceof Clob)
        {
            try
            {
                Clob clobValue = (Clob)value;
                int length = (int)clobValue.length();
                if (length > 0)
                {
                    return clobValue.getSubString(1, length);
                }
                return "";
            }
            catch (SQLException e)
            {
                throw new TypeCastException(value, this, e);
            }
        }

        logger.warn("Unknown/unsupported object type '{}' - " +
                "will invoke toString() as last fallback which " +
                "might produce undesired results",
                value.getClass().getName());
        return value.toString();
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException
    {
    	if (logger.isDebugEnabled())
    		logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        String value = resultSet.getString(column);
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

        statement.setString(column, asString(value));
    }
}









