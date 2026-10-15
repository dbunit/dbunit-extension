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
package org.dbunit.util;

/**
 * Splits a list of values given as one comma separated string, the way a list is configured in
 * a string-valued place such as a build tool property or a system property. One parser for
 * every such place, so the same string means the same list wherever it is written.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class CommaSeparatedValues
{
    private CommaSeparatedValues()
    {
    }

    /**
     * Splits the string at each comma and trims the whitespace around each value. As with
     * {@link String#split(String)}, trailing empty values are dropped, and an empty string gives
     * one empty value.
     *
     * @param commaSeparatedValues The comma separated string.
     * @return The trimmed values, in order.
     */
    public static String[] split(final String commaSeparatedValues)
    {
        final String[] values = commaSeparatedValues.split(",");
        for (int i = 0; i < values.length; i++)
        {
            values[i] = values[i].trim();
        }
        return values;
    }
}
