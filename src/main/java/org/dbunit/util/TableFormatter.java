/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
package org.dbunit.util;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;

/**
 * Simple formatter to print out {@link ITable} objects in a beautiful way, for
 * example on a console.
 *
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.1
 */
public class TableFormatter
{

    public TableFormatter()
    {

    }

    /**
     * Pads the given String with the given <code>padChar</code> up to the
     * given
     * <code>length</code>.
     *
     * @param s
     * @param length
     * @param padChar
     * @return The padded string
     */
    public static final String padLeft(String s, int length, char padChar)
    {
        String result = s;

        char[] padCharArray = getPadCharArray(s, length, padChar);
        if (padCharArray != null)
            result = pad(s, padCharArray, true);

        return result;
    }

    /**
     * Pads the given String with the given <code>padChar</code> up to the given
     * <code>length</code>.
     *
     * @param s
     * @param length
     * @param padChar
     * @return The padded string
     */
    public static final String padRight(String s, int length, char padChar)
    {
        String result = s;

        char[] padCharArray = getPadCharArray(s, length, padChar);
        if (padCharArray != null)
            result = pad(s, padCharArray, false);

        return result;
    }

    private static final char[] getPadCharArray(String s, int length,
            char padChar)
    {
        if (length > 0 && length > s.length())
        {
            int padCount = length - s.length();
            char[] padArray = new char[padCount];
            for (int i = 0; i < padArray.length; i++)
            {
                padArray[i] = padChar;
            }
            return padArray;
        } else
        {
            return null;
        }
    }

    private static final String pad(String s, char[] padArray, boolean padLeft)
    {
        final StringBuilder sb = new StringBuilder(s);
        if (padLeft)
        {
            sb.insert(0, padArray);
        } else
        {
            sb.append(padArray);
        }
        return sb.toString();
    }

    /**
     * Formats a table with all data in a beautiful way. Can be useful to print
     * out the table data on a console.
     *
     * @param table
     *         The table to be formatted in a beautiful way
     * @return The table data as a formatted String
     * @throws DataSetException
     */
    public String format(ITable table) throws DataSetException
    {
        final StringBuilder sb = new StringBuilder();
        ITableMetaData tableMetaData = table.getTableMetaData();
        // Title line
        sb.append("******");
        sb.append(" table: ").append(tableMetaData.getTableName()).append(" ");
        sb.append("**");
        sb.append(" row count: ").append(table.getRowCount()).append(" ");
        sb.append("******");
        sb.append("\n");

        // Column headers
        int width = 20;
        Column[] cols = tableMetaData.getColumns();
        for (int i = 0; i < cols.length; i++)
        {
            sb.append(padRight(cols[i].getColumnName(), width, ' '));
            sb.append("|");
        }
        sb.append("\n");

        // Separator
        for (int i = 0; i < cols.length; i++)
        {
            sb.append(padRight("", width, '='));
            sb.append("|");
        }
        sb.append("\n");

        // Values
        for (int i = 0; i < table.getRowCount(); i++)
        {
            for (int j = 0; j < cols.length; j++)
            {
                Object value = table.getValue(i, cols[j].getColumnName());
                String stringValue = String.valueOf(value);
                sb.append(padRight(stringValue, 20, ' '));
                sb.append("|");
            }
            // New row
            sb.append("\n");
        }

        return sb.toString();
    }

}
