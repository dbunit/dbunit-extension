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

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 */
public class BooleanDataType extends AbstractDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(BooleanDataType.class);

    BooleanDataType()
    {
        this("BOOLEAN", Types.BOOLEAN);
    }

    /**
     * @param name
     * @param sqlType
     * @since 2.3
     */
    BooleanDataType(String name, int sqlType)
    {
        super(name, sqlType, Boolean.class, false);
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

        if (value instanceof Boolean)
        {
            return value;
        }

        if (value instanceof Number)
        {
            Number number = (Number)value;
            if (number.intValue() == 0)
                return Boolean.FALSE;
            else
                return Boolean.TRUE;
        }

        if (value instanceof String)
        {
            String string = (String)value;

            if (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("false"))
            {
                return Boolean.valueOf(string);
            }
            else
            {
                return typeCast(DataType.INTEGER.typeCast(string));
            }
        }

        throw new TypeCastException(value, this);
    }


    protected int compareNonNulls(Object value1, Object value2) throws TypeCastException
    {
        logger.debug("compareNonNulls(value1={}, value2={}) - start", value1, value2);
        
        Boolean value1bool = (Boolean)value1;
        Boolean value2bool = (Boolean)value2;
        
        if (value1bool.equals(value2bool))
        {
            return 0;
        }

        if (value1bool.equals(Boolean.FALSE))
        {
            return -1;
        }

        return 1;
    }

    public Object getSqlValue(int column, ResultSet resultSet)
            throws SQLException, TypeCastException
    {
    	logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        boolean value = resultSet.getBoolean(column);
        if (resultSet.wasNull())
        {
            return null;
        }
        return value ? Boolean.TRUE : Boolean.FALSE;
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement)
            throws SQLException, TypeCastException
    {
    	logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
        		value, column, statement);

    	Boolean castValue = (Boolean)typeCast(value);
    	if(castValue==null)
    	{
    	    statement.setNull(column, Types.BOOLEAN);
    	}
    	else
    	{
    	    statement.setBoolean(column, castValue.booleanValue());
    	}
    }

}





