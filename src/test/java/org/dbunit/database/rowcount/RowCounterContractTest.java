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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

/**
 * Contract every {@link RowCounter} implementation must satisfy, so a user-written counter can
 * be checked against the same rules the shipped implementations are. Extend this class and
 * implement {@link #createRowCounter()} and {@link #createConnectionReturningRowCount(int)} to
 * verify a new implementation.
 */
abstract class RowCounterContractTest
{
    /**
     * Creates the {@link RowCounter} under test.
     */
    protected abstract RowCounter createRowCounter();

    /**
     * Creates a connection that reports {@code rowCount} for every table name the counter under
     * test may query.
     */
    protected abstract IDatabaseConnection createConnectionReturningRowCount(int rowCount)
            throws Exception;

    @Test
    void testCountRows_requestedTables_returnsAnEntryForEveryOne() throws Exception
    {
        final RowCounter rowCounter = createRowCounter();
        final IDatabaseConnection connection = createConnectionReturningRowCount(0);
        final List<String> tableNames = Arrays.asList("ACCOUNT", "ACCOUNT_AUDIT", "COUNTRY_CODE");

        final Map<String, Integer> result = rowCounter.countRows(connection, tableNames);

        assertThat(result.keySet())
                .as("Every requested table name must appear as a key in the result.")
                .containsExactlyInAnyOrderElementsOf(tableNames);
    }

    @Test
    void testCountRows_requestedTables_returnsNoUnrequestedEntries() throws Exception
    {
        final RowCounter rowCounter = createRowCounter();
        final IDatabaseConnection connection = createConnectionReturningRowCount(0);
        final List<String> tableNames = Arrays.asList("ACCOUNT", "ACCOUNT_AUDIT");

        final Map<String, Integer> result = rowCounter.countRows(connection, tableNames);

        assertThat(result)
                .as("The result must contain no entries beyond the requested tables.")
                .hasSameSizeAs(tableNames);
    }

    @Test
    void testCountRows_mixedCaseTableNames_keysMatchTheSuppliedNamesExactly() throws Exception
    {
        final RowCounter rowCounter = createRowCounter();
        final IDatabaseConnection connection = createConnectionReturningRowCount(0);
        final List<String> tableNames = Arrays.asList("Account", "ACCOUNT_AUDIT");

        final Map<String, Integer> result = rowCounter.countRows(connection, tableNames);

        assertThat(result.keySet())
                .as("Result keys must match the supplied table names exactly, including case -"
                        + " not normalized to another form.")
                .containsExactlyInAnyOrderElementsOf(tableNames);
    }

    @Test
    void testCountRows_connectionReportsNonZeroCount_returnsThatExactCount() throws Exception
    {
        final RowCounter rowCounter = createRowCounter();
        final IDatabaseConnection connection = createConnectionReturningRowCount(7);
        final List<String> tableNames = Arrays.asList("ACCOUNT", "ACCOUNT_AUDIT");

        final Map<String, Integer> result = rowCounter.countRows(connection, tableNames);

        assertThat(result.values())
                .as("Counts must be exact, not estimated or defaulted regardless of what the"
                        + " connection actually reports.")
                .containsOnly(7);
    }
}
