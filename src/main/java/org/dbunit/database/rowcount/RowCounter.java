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
import java.util.List;
import java.util.Map;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

/**
 * Counts the rows of a set of database tables. The strategy behind
 * {@link RowCountCheck}, configured via
 * {@link DatabaseConfig#PROPERTY_ROW_COUNTER}; counting is the expensive part
 * of the row count check and the part most likely to be improved, so it is
 * the one seam the check opens.
 * <p>
 * An implementation's contract:
 * <ul>
 * <li>Return an entry for <b>every</b> requested table name, keyed exactly as
 * supplied.
 * <li>Return entries for <b>no other</b> tables.
 * <li>Counts must be <b>exact</b>. Estimates, such as vendor statistics views
 * that lag asynchronously, are not acceptable however cheap they are, since
 * they would produce false failures.
 * </ul>
 * Table enumeration and exclusion filtering are handled by {@link RowCountCheck}
 * before this interface is ever consulted, so an implementation's whole job is
 * counting the list it is handed.
 *
 * @author dbunit
 * @since 3.6.0
 */
public interface RowCounter
{
    /**
     * Counts the rows of every given table.
     *
     * @param connection the connection to count rows through.
     * @param tableNames the names of the tables to count; every name must appear
     *            as a key in the returned map, and no other keys may appear.
     * @return the row count of each requested table, keyed exactly as supplied.
     * @throws SQLException if counting a table's rows fails.
     */
    Map<String, Integer> countRows(IDatabaseConnection connection, List<String> tableNames)
            throws SQLException;
}
