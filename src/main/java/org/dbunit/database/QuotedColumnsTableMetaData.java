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
package org.dbunit.database;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;

/**
 * Table metadata that asks for the select statement of a table to write each
 * column name between a quote string, such as the one
 * {@link java.sql.DatabaseMetaData#getIdentifierQuoteString()} returns. It is
 * for column names that come exactly as the database stores them, for example
 * from the driver's result set metadata, so that the database reads each name
 * as it is stored: a reserved word such as <code>VALUE</code> or a name in
 * mixed case such as <code>mixedCase</code> stays what it is and is not folded
 * to another case.
 *
 * <p>
 * It applies to the column names of the select list and of the order by, not
 * to the table name, and only when no
 * {@link DatabaseConfig#PROPERTY_ESCAPE_PATTERN escape pattern} is set; the
 * escape pattern, when set, escapes the names as it always has. Everything
 * else is that of the wrapped metadata.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class QuotedColumnsTableMetaData implements ITableMetaData
{
    private final ITableMetaData metaData;
    private final String quoteString;

    /**
     * Creates metadata that wraps the given metadata and asks for its column
     * names to be quoted.
     *
     * @param metaData
     *            The metadata to wrap.
     * @param quoteString
     *            The string that opens and closes a quoted column name, such as
     *            a double quote or a backtick. It must not be blank.
     * @throws NullPointerException
     *             If the metadata or the quote string is null.
     * @throws IllegalArgumentException
     *             If the quote string is blank.
     */
    public QuotedColumnsTableMetaData(final ITableMetaData metaData,
            final String quoteString)
    {
        if (metaData == null)
        {
            throw new NullPointerException(
                    "The parameter 'metaData' must not be null");
        }
        if (quoteString == null)
        {
            throw new NullPointerException(
                    "The parameter 'quoteString' must not be null");
        }
        if (quoteString.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "The parameter 'quoteString' must not be blank");
        }
        this.metaData = metaData;
        this.quoteString = quoteString;
    }

    /**
     * Returns the column name written between the quote string, with each quote
     * string inside the name doubled, as the SQL standard escapes it.
     *
     * @param columnName
     *            The column name as the database stores it.
     * @return The quoted column name.
     */
    public String quote(final String columnName)
    {
        final String doubledQuoteString = quoteString + quoteString;
        final String escapedName =
                columnName.replace(quoteString, doubledQuoteString);
        return quoteString + escapedName + quoteString;
    }

    @Override
    public String getTableName()
    {
        return metaData.getTableName();
    }

    @Override
    public Column[] getColumns() throws DataSetException
    {
        return metaData.getColumns();
    }

    @Override
    public Column[] getPrimaryKeys() throws DataSetException
    {
        return metaData.getPrimaryKeys();
    }

    @Override
    public int getColumnIndex(final String columnName) throws DataSetException
    {
        return metaData.getColumnIndex(columnName);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "[quoteString=" + quoteString
                + ", metaData=" + metaData + "]";
    }
}
