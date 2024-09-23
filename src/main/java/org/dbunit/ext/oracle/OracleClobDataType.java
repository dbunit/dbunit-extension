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
package org.dbunit.ext.oracle;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.dataset.datatype.ClobDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Jan 12, 2004
 */
public class OracleClobDataType extends ClobDataType
{

    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(OracleClobDataType.class);

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        logger.debug("getSqlValue(column={}, resultSet={}) - start", column, resultSet);

        return typeCast(resultSet.getClob(column));
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        statement.setObject(column, getClob(value, statement.getConnection()));
    }

    protected Object getClob(final Object value, final Connection connection)
            throws TypeCastException
    {
        logger.debug("getClob(value={}, connection={}) - start", value,
                connection);

        Writer tempClobWriter = null;
        try
        {
            final java.sql.Clob tempClob = connection.createClob();
            tempClobWriter = tempClob.setCharacterStream(1);

            // Write the data into the temporary CLOB
            tempClobWriter.write((String) typeCast(value));

            // Flush and close the stream
            tempClobWriter.flush();
            return tempClob;
        } catch (IOException | SQLException e)
        {
            throw new TypeCastException(value, this, e);
        } finally
        {
            closeQuietly(tempClobWriter);
        }
    }

    private void closeQuietly(final Closeable c)
    {
        if (c != null)
        {
            try
            {
                c.close();
            } catch (final IOException ignored)
            {
                // ignore
            }
        }
    }
}
