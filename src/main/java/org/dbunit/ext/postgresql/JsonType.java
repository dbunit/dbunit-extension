/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.ext.postgresql;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter to handle conversion between PostgreSQL native {@code json}/
 * {@code jsonb} types and Strings.
 *
 * <p>PostgreSQL requires a bound parameter's declared type to match its
 * target {@code json}/{@code jsonb} column exactly - it has no implicit cast
 * between the two - so one instance handles only the single sql type name it
 * was constructed with. {@link PostgresqlDataTypeFactory} constructs the
 * matching instance for each column automatically; there is normally no need
 * to instantiate this class directly.
 *
 * <p>This class only shuttles the column's raw text between the driver and
 * the dataset; it does not compare JSON values semantically. Use
 * {@link org.dbunit.assertion.comparer.value.IsActualEqualToExpectedJsonValueComparer}
 * to compare json/jsonb column values by their parsed document structure
 * instead of raw text.
 *
 * @author Jeff Jensen
 * @since 3.4.1
 */
public class JsonType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(JsonType.class);

    private final String sqlTypeName;

    /**
     * Creates a data type adapter for the given PostgreSQL JSON sql type
     * name.
     *
     * @param sqlTypeName
     *            The sql type name, {@code "json"} or {@code "jsonb"},
     *            needed to invoke the {@code setType()} method on the
     *            PGobject class.
     */
    public JsonType(final String sqlTypeName)
    {
        super(Objects.requireNonNull(sqlTypeName,
                "The parameter 'sqlTypeName' must not be null"), Types.OTHER,
                String.class, false);

        this.sqlTypeName = sqlTypeName;
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        return resultSet.getString(column);
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        if (value == null)
        {
            statement.setNull(column, Types.OTHER);
            return;
        }

        statement.setObject(column,
                getJson(value, statement.getConnection()));
    }

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        return value == null ? null : value.toString();
    }

    /**
     * Returns the sql type name this instance handles.
     *
     * @return {@code "json"} or {@code "jsonb"}, whichever sql type name this
     *         instance was constructed with.
     */
    public String getSqlTypeName()
    {
        return sqlTypeName;
    }

    private Object getJson(final Object value, final Connection connection)
            throws TypeCastException
    {
        logger.debug("getJson(value={}, connection={}) - start", value,
                connection);

        Object tempJson = null;

        try
        {
            final Class<?> aPGObjectClass = super.loadClass(
                    "org.postgresql.util.PGobject", connection);
            final Constructor<?> ct = aPGObjectClass.getConstructor();
            tempJson = ct.newInstance();

            final Method setTypeMethod =
                    aPGObjectClass.getMethod("setType", String.class);
            setTypeMethod.invoke(tempJson, sqlTypeName);

            final Method setValueMethod =
                    aPGObjectClass.getMethod("setValue", String.class);
            setValueMethod.invoke(tempJson, value.toString());

        } catch (final ReflectiveOperationException e)
        {
            throw new TypeCastException(value, this, e);
        }

        return tempJson;
    }
}
