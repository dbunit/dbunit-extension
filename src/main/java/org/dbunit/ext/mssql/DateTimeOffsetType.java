/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2019, DbUnit.org
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
package org.dbunit.ext.mssql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;

/**
 * @author Richard DiCroce
 * @since 2.7.0
 */
public class DateTimeOffsetType extends AbstractDataType
{
    public static final int TYPE = -155;

    /** @see https://docs.microsoft.com/en-us/sql/t-sql/data-types/datetimeoffset-transact-sql?view=sql-server-2017 */
    private static final DateTimeFormatter SQL_SERVER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.n] xxx");

    public DateTimeOffsetType()
    {
        super("datetimeoffset", TYPE, OffsetDateTime.class, false);
    }

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        if (value == null || value instanceof OffsetDateTime)
        {
            return value;
        }

        // if a java.time type, attempt a direct conversion
        // if that fails, there's not enough info to do the conversion, so don't
        // bother trying string parse
        if (value instanceof TemporalAccessor)
        {
            try
            {
                return OffsetDateTime.from((TemporalAccessor) value);
            } catch (final DateTimeException e)
            {
                throw new TypeCastException(e);
            }
        }

        final String valueAsString = value.toString();

        // attempt to parse using ISO 8601 format
        DateTimeParseException isoParseException;
        try
        {
            return OffsetDateTime.parse(valueAsString);
        } catch (final DateTimeParseException e)
        {
            isoParseException = e;
        }

        // attempt to parse using SQL Server's ISO-like format
        try
        {
            return OffsetDateTime.parse(valueAsString, SQL_SERVER_FORMAT);
        } catch (final DateTimeParseException e)
        {
            final TypeCastException toThrow = new TypeCastException(
                    "Could not parse value using ISO 8601 or SQL Server's format",
                    e);
            toThrow.addSuppressed(isoParseException);
            throw toThrow;
        }
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        return resultSet.getObject(column, OffsetDateTime.class);
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        statement.setObject(column, typeCast(value));
    }

    @Override
    public boolean isDateTime()
    {
        return true;
    }
}
