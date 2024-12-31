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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fede
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Sep 12, 2004 (pre 2.3)
 */
public class BinaryStreamDataType extends BytesDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(BinaryStreamDataType.class);

    public BinaryStreamDataType(final String name, final int sqlType)
    {
        super(name, sqlType);
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column,
                resultSet);
        final InputStream in = resultSet.getBinaryStream(column);
        final Object value = resultSet.wasNull() ? null : readValue(in);
        logger.debug("getSqlValue: column={}, value={}", column, value);
        return value;
    }

    private Object readValue(final InputStream in) throws TypeCastException
    {
        try
        {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[32];
            int length = in.read(buffer);
            while (length != -1)
            {
                out.write(buffer, 0, length);
                length = in.read(buffer);
            }
            return out.toByteArray();
        } catch (final IOException e)
        {
            throw new TypeCastException(e);
        }
    }

    /**
     * Sets the given value on the given statement and therefore invokes
     * {@link BytesDataType#typeCast(Object)}.
     *
     * @see org.dbunit.dataset.datatype.BytesDataType#setSqlValue(java.lang.Object,
     *      int, java.sql.PreparedStatement)
     */
    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        final byte[] bytes = (byte[]) typeCast(value);
        if (value == null || bytes == null)
        {
            logger.debug("Setting SQL column value to <null>");
            statement.setNull(column, getSqlType());
        } else
        {
            final ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            statement.setBinaryStream(column, in, bytes.length);
        }
    }
}