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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Jan 12, 2004
 */
public class BlobDataType extends BytesDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(BlobDataType.class);

    public BlobDataType()
    {
        super("BLOB", Types.BLOB);
    }

    public BlobDataType(final String name, final int sqlType)
    {
        super(name, sqlType);
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);
        final Blob rawValue = resultSet.getBlob(column);
        final Object value = resultSet.wasNull() ? null : typeCast(rawValue);
        logger.debug("getSqlValue: column={}, value is null?={}", column,
                value == null);
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
                    value, String.valueOf(column), statement);
        }

        statement.setObject(column, typeCast(value), super.getSqlType());
    }
}
