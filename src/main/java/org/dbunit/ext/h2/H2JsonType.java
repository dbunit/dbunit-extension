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
package org.dbunit.ext.h2;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;

/**
 * Adapter to handle conversion between the H2 {@code JSON} type and the text of a JSON document.
 *
 * <p>H2 returns a {@code JSON} value from {@code ResultSet.getObject()} as a {@code byte[]}, and
 * stores a value bound with {@code setString()} as a JSON string, not as the document it holds.
 * This type therefore reads the value with {@code getString()}, which returns the text of the
 * document, and writes it with {@code setBytes()} of the UTF-8 bytes of the text, which H2 parses
 * as the document. A text that is not valid JSON fails at the insert with H2's own error.
 *
 * <p>{@link H2DataTypeFactory} creates it for each column of the type name {@code JSON}; there is
 * normally no need to instantiate this class directly.
 *
 * <p>This class only shuttles the text between the driver and the dataset. Use
 * {@link org.dbunit.assertion.comparer.value.IsActualEqualToExpectedJsonValueComparer} to compare
 * the values of a {@code JSON} column by their parsed document structure instead of their text,
 * as H2 may rewrite the text it stores.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class H2JsonType extends AbstractDataType
{
    /**
     * Creates the data type of an H2 {@code JSON} column.
     */
    public H2JsonType()
    {
        super("JSON", Types.OTHER, String.class, false);
    }

    @Override
    public Object getSqlValue(final int column, final ResultSet resultSet)
            throws SQLException, TypeCastException
    {
        return resultSet.getString(column);
    }

    @Override
    public void setSqlValue(final Object value, final int column,
            final PreparedStatement statement) throws SQLException, TypeCastException
    {
        if (value == null)
        {
            statement.setNull(column, Types.OTHER);
            return;
        }

        final String text = (String) typeCast(value);
        final byte[] utf8Text = text.getBytes(StandardCharsets.UTF_8);
        statement.setBytes(column, utf8Text);
    }

    @Override
    public Object typeCast(final Object value) throws TypeCastException
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof byte[])
        {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return value.toString();
    }
}
