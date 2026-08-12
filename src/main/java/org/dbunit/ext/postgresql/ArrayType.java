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

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter to handle conversion between PostgreSQL array columns
 * ({@link Types#ARRAY}, e.g. {@code integer[]}, {@code text[]}) and their
 * PostgreSQL literal text representation, such as {@code {1,2,3}} or
 * {@code {"a","b","c"}}.
 *
 * <p>
 * PostgreSQL reports every array column's sql type name as its element
 * type's own pg_catalog name prefixed with an underscore (for example
 * {@code _int4} for an {@code integer[]} column); {@link
 * PostgresqlDataTypeFactory} constructs the matching instance for each
 * column automatically, passing that name through unchanged. There is
 * normally no need to instantiate this class directly.
 *
 * <p>
 * This class round-trips a column's literal text between the driver and the
 * dataset; it does not typecast individual elements to their Java
 * equivalents, and comparisons against an expected dataset value are plain
 * string comparisons. On write, the literal text is split into its
 * top-level elements - honoring double-quoted elements (backslash-escaped
 * internally), backslash-escaped characters outside quotes, and the
 * unquoted {@code NULL} keyword - and bound via {@link
 * Connection#createArrayOf(String, Object[])}, letting PostgreSQL itself
 * parse and validate each element against the column's actual base type.
 * Only single-dimension arrays are supported for writing; a literal
 * containing a nested array (a multi-dimensional array) is rejected with a
 * {@link TypeCastException}. Reading one back for comparison or export works
 * fine either way, since the literal text is never parsed on read.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
public class ArrayType extends AbstractDataType
{
    private static final Logger logger =
            LoggerFactory.getLogger(ArrayType.class);

    private final String sqlTypeName;
    private final String elementSqlTypeName;

    /**
     * Creates a data type adapter for the given PostgreSQL array sql type
     * name.
     *
     * @param sqlTypeName
     *            The sql type name reported for the array column, e.g.
     *            {@code "_int4"} for an {@code integer[]} column.
     */
    public ArrayType(final String sqlTypeName)
    {
        super(Objects.requireNonNull(sqlTypeName,
                "The parameter 'sqlTypeName' must not be null"), Types.ARRAY,
                String.class, false);

        this.sqlTypeName = sqlTypeName;
        this.elementSqlTypeName = sqlTypeName.startsWith("_")
                ? sqlTypeName.substring(1)
                : sqlTypeName;
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        final Array value = resultSet.getArray(column);
        return value == null ? null : value.toString();
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement)
            throws SQLException, TypeCastException
    {
        if (value == null)
        {
            statement.setNull(column, Types.ARRAY);
            return;
        }

        if (value instanceof Array)
        {
            statement.setArray(column, (Array) value);
            return;
        }

        final String[] elements = parseElements(value.toString());
        final Connection connection = statement.getConnection();
        statement.setArray(column,
                connection.createArrayOf(elementSqlTypeName, elements));
    }

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        return value == null ? null : value.toString();
    }

    /**
     * Returns the sql type name this instance handles.
     *
     * @return The array sql type name this instance was constructed with,
     *         e.g. {@code "_int4"}.
     */
    public String getSqlTypeName()
    {
        return sqlTypeName;
    }

    /**
     * Splits a PostgreSQL array literal, such as {@code {1,2,3}} or
     * {@code {"a","b",NULL}}, into its top-level elements.
     *
     * @param literal
     *            The array literal text to split.
     * @return The literal's elements, in order; a {@code null} entry marks
     *         the unquoted {@code NULL} keyword.
     * @throws TypeCastException
     *             if the literal is not enclosed in {@code { }} or contains
     *             a nested array.
     */
    private String[] parseElements(final String literal)
            throws TypeCastException
    {
        logger.debug("parseElements(literal={}) - start", literal);

        final String trimmed = literal.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{'
                || trimmed.charAt(trimmed.length() - 1) != '}')
        {
            throw new TypeCastException(literal, this);
        }

        final String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isEmpty())
        {
            return new String[0];
        }

        final List<String> elements = new ArrayList<>();
        final StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++)
        {
            final char c = body.charAt(i);
            if (inQuotes)
            {
                if (c == '\\' && i + 1 < body.length())
                {
                    token.append(body.charAt(++i));
                } else if (c == '"')
                {
                    inQuotes = false;
                } else
                {
                    token.append(c);
                }
            } else if (c == '"')
            {
                inQuotes = true;
                quoted = true;
            } else if (c == '\\' && i + 1 < body.length())
            {
                token.append(body.charAt(++i));
                quoted = true;
            } else if (c == '{')
            {
                throw new TypeCastException(literal, this);
            } else if (c == ',')
            {
                elements.add(toElement(token, quoted));
                token.setLength(0);
                quoted = false;
            } else
            {
                token.append(c);
            }
        }
        elements.add(toElement(token, quoted));

        return elements.toArray(new String[0]);
    }

    private static String toElement(final StringBuilder token,
            final boolean quoted)
    {
        if (quoted)
        {
            return token.toString();
        }

        final String trimmed = token.toString().trim();
        return "NULL".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
