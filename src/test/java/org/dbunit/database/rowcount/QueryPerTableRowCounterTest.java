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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

class QueryPerTableRowCounterTest
{
    private final QueryPerTableRowCounter rowCounter = new QueryPerTableRowCounter();

    @Test
    void testCountRows_severalTables_returnsOneEntryPerRequestedTable() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        when(connection.getRowCount("ACCOUNT_AUDIT")).thenReturn(0);
        final List<String> tableNames = Arrays.asList("ACCOUNT", "ACCOUNT_AUDIT");

        final Map<String, Integer> result = rowCounter.countRows(connection, tableNames);

        assertThat(result)
                .as("Every requested table must have its own counted entry.")
                .hasSize(2).containsEntry("ACCOUNT", 5).containsEntry("ACCOUNT_AUDIT", 0);
    }

    @Test
    void testCountRows_emptyTableNameList_returnsEmptyMap() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);

        final Map<String, Integer> result =
                rowCounter.countRows(connection, Collections.emptyList());

        assertThat(result).as("No requested tables must produce no counted entries.").isEmpty();
    }

    @Test
    void testCountRows_tableNameWithMixedCase_keysResultWithTheNameAsSupplied() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getRowCount("Account_Audit")).thenReturn(1);

        final Map<String, Integer> result =
                rowCounter.countRows(connection, Collections.singletonList("Account_Audit"));

        assertThat(result)
                .as("The result must be keyed exactly as the table name was supplied, not"
                        + " normalized to another case.")
                .containsOnlyKeys("Account_Audit");
    }

    @Test
    void testCountRows_getRowCountThrows_propagatesTheSameSQLExceptionUnchanged() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final SQLException failure = new SQLException("boom");
        when(connection.getRowCount("ACCOUNT")).thenThrow(failure);

        assertThatThrownBy(() -> rowCounter.countRows(connection,
                Collections.singletonList("ACCOUNT")))
                        .as("A counting failure for one table must propagate as-is, not be"
                                + " swallowed or wrapped.")
                        .isSameAs(failure);
    }
}
