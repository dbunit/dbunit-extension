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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package org.dbunit.ext.h2;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dbunit.database.DefaultMetadataHandler;
import org.dbunit.database.InMemoryMetadataResultSet;

/**
 * Special metadata handler for H2.
 * <p>
 * H2 2.x rewrote {@code INFORMATION_SCHEMA} to be SQL-standard-compliant, and most of its internal
 * tables now report a JDBC {@code TABLE_TYPE} of {@code "BASE TABLE"} instead of the
 * {@code "SYSTEM TABLE"} type H2 1.x used. Since
 * {@link org.dbunit.database.DatabaseConfig#PROPERTY_TABLE_TYPE} defaults to {@code {"TABLE"}},
 * those tables now pass dbunit's default system-table filter and leak into table listings that are
 * not scoped to a single schema (i.e. {@code schemaName} is {@code null}). This handler excludes
 * the {@code INFORMATION_SCHEMA} schema from {@link #getTables} results to restore the pre-2.x
 * behavior.
 *
 * @since 3.4.1
 */
public class H2MetadataHandler extends DefaultMetadataHandler
{
    private static final String INFORMATION_SCHEMA = "INFORMATION_SCHEMA";

    /**
     * {@inheritDoc}
     * Excludes tables belonging to the {@code INFORMATION_SCHEMA} schema.
     */
    @Override
    public ResultSet getTables(final DatabaseMetaData metaData, final String schemaName,
            final String[] tableType) throws SQLException
    {
        final ResultSet resultSet = super.getTables(metaData, schemaName, tableType);
        return InMemoryMetadataResultSet.filter(resultSet,
                row -> !INFORMATION_SCHEMA.equalsIgnoreCase(getSchema(row)));
    }
}
