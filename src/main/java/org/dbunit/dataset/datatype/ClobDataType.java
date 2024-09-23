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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Clob;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Jan 12, 2004
 */
public class ClobDataType extends StringDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ClobDataType.class);

    public ClobDataType()
    {
        super("CLOB", Types.CLOB);
    }

    public Object getSqlValue(int column, ResultSet resultSet) throws SQLException, TypeCastException
    {
    	logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        Clob value = resultSet.getClob(column);
        if (value == null || resultSet.wasNull())
        {
            return null;
        }
        return typeCast(value);
    }

    public void setSqlValue(Object value, int column, PreparedStatement statement) throws SQLException, TypeCastException
    {
    	logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
        		value, column, statement);

        statement.setObject(column, typeCast(value), getSqlType());
    }
}
