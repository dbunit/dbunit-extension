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
package org.dbunit.annotation.runtime;

import java.util.Locale;

/**
 * The form in which two table names an annotation spells out are compared: ignoring case, the
 * way dbUnit finds a table in a dataset unless
 * {@link org.dbunit.database.DatabaseConfig#FEATURE_CASE_SENSITIVE_TABLE_NAMES} is on, as
 * {@link org.dbunit.dataset.OrderedTableNameMap} compares them. Annotations are resolved before
 * any connection, so before that feature can be read; a schema with tables whose names differ
 * only in case cannot declare more than one of them in one verification spec.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class TableNameKey
{
    private TableNameKey()
    {
    }

    /**
     * Returns the form of {@code tableName} to compare and index table names by.
     *
     * @param tableName The table name, as the annotation or catalog spells it.
     * @return The name to compare it by, equal for names differing only in case.
     */
    static String of(final String tableName)
    {
        return tableName.toUpperCase(Locale.ENGLISH);
    }
}
