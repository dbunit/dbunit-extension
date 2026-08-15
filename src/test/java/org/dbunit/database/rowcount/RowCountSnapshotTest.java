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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RowCountSnapshotTest
{
    @Test
    void testDifference_identicalSnapshots_returnsEmptyList()
    {
        final RowCountSnapshot baseline = snapshotOf("ACCOUNT", 5, "COUNTRY_CODE", 12);
        final RowCountSnapshot current = snapshotOf("ACCOUNT", 5, "COUNTRY_CODE", 12);

        assertThat(baseline.difference(current))
                .as("Identical snapshots must report no differences.").isEmpty();
    }

    @Test
    void testDifference_tableGainedRows_returnsPositiveDelta()
    {
        final RowCountSnapshot baseline = snapshotOf("ACCOUNT_AUDIT", 0);
        final RowCountSnapshot current = snapshotOf("ACCOUNT_AUDIT", 3);

        final List<RowCountDifference> differences = baseline.difference(current);

        assertThat(differences).as("Exactly one table changed.").hasSize(1);
        assertThat(differences.get(0))
                .as("A table with more current rows than baseline must report a positive delta"
                        + " naming both counts.")
                .isEqualTo(new RowCountDifference("ACCOUNT_AUDIT", 0, 3));
    }

    @Test
    void testDifference_tableLostRows_returnsNegativeDelta()
    {
        final RowCountSnapshot baseline = snapshotOf("COUNTRY_CODE", 12);
        final RowCountSnapshot current = snapshotOf("COUNTRY_CODE", 0);

        final List<RowCountDifference> differences = baseline.difference(current);

        assertThat(differences).as("Exactly one table changed.").hasSize(1);
        assertThat(differences.get(0))
                .as("A table with fewer current rows than baseline must report a negative delta"
                        + " naming both counts.")
                .isEqualTo(new RowCountDifference("COUNTRY_CODE", 12, 0));
    }

    @Test
    void testDifference_multipleTablesChanged_returnsOneDifferencePerTable()
    {
        final RowCountSnapshot baseline =
                snapshotOf("ACCOUNT", 5, "ACCOUNT_AUDIT", 0, "COUNTRY_CODE", 12);
        final RowCountSnapshot current =
                snapshotOf("ACCOUNT", 5, "ACCOUNT_AUDIT", 3, "COUNTRY_CODE", 0);

        final List<RowCountDifference> differences = baseline.difference(current);

        assertThat(differences)
                .as("Only the two tables whose counts actually changed must be reported;"
                        + " ACCOUNT is unchanged and must be absent.")
                .containsExactlyInAnyOrder(new RowCountDifference("ACCOUNT_AUDIT", 0, 3),
                        new RowCountDifference("COUNTRY_CODE", 12, 0));
    }

    @Test
    void testDifference_tableMissingFromCurrent_treatsItAsZeroInsteadOfThrowing()
    {
        final RowCountSnapshot baseline = snapshotOf("ACCOUNT", 5, "COUNTRY_CODE", 12);
        final RowCountSnapshot current = snapshotOf("ACCOUNT", 5);

        final List<RowCountDifference> differences = baseline.difference(current);

        assertThat(differences)
                .as("A table dropped between the two captures must be reported with a"
                        + " current count of 0, not throw a NullPointerException.")
                .containsExactly(new RowCountDifference("COUNTRY_CODE", 12, 0));
    }

    @Test
    void testDifference_tableAddedInCurrent_treatsBaselineAsZero()
    {
        final RowCountSnapshot baseline = snapshotOf("ACCOUNT", 5);
        final RowCountSnapshot current = snapshotOf("ACCOUNT", 5, "COUNTRY_CODE", 12);

        final List<RowCountDifference> differences = baseline.difference(current);

        assertThat(differences)
                .as("A table created between the two captures must be reported with a"
                        + " baseline count of 0, naming it even though this snapshot never"
                        + " saw it.")
                .containsExactly(new RowCountDifference("COUNTRY_CODE", 0, 12));
    }

    @Test
    void testGetRowCounts_afterConstruction_returnsSuppliedCounts()
    {
        final RowCountSnapshot snapshot = snapshotOf("ACCOUNT", 5);

        assertThat(snapshot.getRowCounts())
                .as("getRowCounts() must return the counts the snapshot was built from.")
                .hasSize(1).containsEntry("ACCOUNT", 5);
    }

    private static RowCountSnapshot snapshotOf(final Object... tableNameAndCountPairs)
    {
        final Map<String, Integer> rowCounts = new LinkedHashMap<>();
        for (int i = 0; i < tableNameAndCountPairs.length; i += 2)
        {
            rowCounts.put((String) tableNameAndCountPairs[i],
                    (Integer) tableNameAndCountPairs[i + 1]);
        }
        return new RowCountSnapshot(rowCounts);
    }
}
