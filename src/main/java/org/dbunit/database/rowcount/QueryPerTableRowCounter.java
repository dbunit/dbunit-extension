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
package org.dbunit.database.rowcount;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

/**
 * {@link RowCounter} that issues one <code>SELECT COUNT(*)</code> per table, by
 * looping {@link IDatabaseConnection#getRowCount(String)}. Already handles schema
 * qualification and {@link DatabaseConfig#PROPERTY_ESCAPE_PATTERN} through
 * {@link org.dbunit.util.QualifiedTableName}, and is exercised across every
 * supported database by that method's own tests.
 * <p>
 * This is the {@link RowCountCheck} v1 implementation, and the value
 * {@link DatabaseConfig#PROPERTY_ROW_COUNTER} initialises to.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class QueryPerTableRowCounter implements RowCounter
{
    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Integer> countRows(final IDatabaseConnection connection,
            final List<String> tableNames) throws SQLException
    {
        final Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (final String tableName : tableNames)
        {
            rowCounts.put(tableName, connection.getRowCount(tableName));
        }
        return rowCounts;
    }
}
