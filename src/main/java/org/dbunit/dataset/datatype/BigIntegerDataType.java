/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts and reads {@link BigInteger} values into/from a database.
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.6
 */
public class BigIntegerDataType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(BigIntegerDataType.class);

    public BigIntegerDataType()
    {
        super("BIGINT", Types.BIGINT, BigInteger.class, true);
    }

    // //////////////////////////////////////////////////////////////////////////
    // DataType class

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        logger.debug("typeCast(value={}) - start", value);

        if (value == null || value == ITable.NO_VALUE)
        {
            return null;
        }

        if (value instanceof BigInteger)
        {
            return value;
        } else if (value instanceof BigDecimal)
        {
            return ((BigDecimal) value).toBigInteger();
        } else if (value instanceof Number)
        {
            final long l = ((Number) value).longValue();
            return new BigInteger(String.valueOf(l));
        }

        try
        {
            final BigDecimal bd = new BigDecimal(value.toString());
            return bd.toBigInteger();
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
        final BigDecimal rawValue = resultSet.getBigDecimal(column);
        final BigInteger value =
                resultSet.wasNull() ? null : rawValue.toBigInteger();
        logger.debug("getSqlValue: column={}, value={}", column, value);
        return value;
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        logger.debug("setSqlValue(value={}, column={}, statement={}) - start",
                value, column, statement);

        final BigInteger val = (BigInteger) typeCast(value);
        final BigDecimal valueBigDecimal =
                (val == null) ? null : new BigDecimal(val);
        statement.setBigDecimal(column, valueBigDecimal);
    }
}
