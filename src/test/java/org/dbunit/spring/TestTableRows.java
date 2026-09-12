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
package org.dbunit.spring;

import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.database.IDatabaseConnection;

/**
 * Counts and clears the rows of the tables the {@code DbUnitTestExecutionListener} integration
 * tests assert on.
 */
final class TestTableRows
{
    private TestTableRows()
    {
    }

    /**
     * Counts the rows of a table.
     *
     * @param connection The connection to count with.
     * @param tableName The table to count.
     * @return The number of rows in the table.
     * @throws Exception If counting fails.
     */
    static int count(final IDatabaseConnection connection, final String tableName)
            throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + tableName))
        {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /**
     * Deletes every row of the tables, ignoring any failure: this is best-effort cleanup only,
     * and a failure here must not fail the test that already ran.
     *
     * @param environment The environment whose connection to delete with.
     * @param tableNames The tables to clear.
     */
    static void deleteAllQuietly(final DatabaseEnvironment environment,
            final String... tableNames)
    {
        for (final String tableName : tableNames)
        {
            try (Statement statement =
                    environment.getConnection().getConnection().createStatement())
            {
                statement.execute("DELETE FROM " + tableName);
            } catch (final Exception e)
            {
                // best-effort cleanup only
            }
        }
    }
}
