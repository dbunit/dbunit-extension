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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of table row counts, keyed by table name, taken at one point in
 * a {@link RowCountCheck}. Holds no connection and issues no SQL of its own; it is built
 * from the map a {@link RowCounter} already returned.
 *
 * @author dbunit
 * @since 3.6.0
 */
public final class RowCountSnapshot
{
    private final Map<String, Integer> rowCounts;

    /**
     * Creates a snapshot from a {@link RowCounter}'s result.
     *
     * @param rowCounts The row count of each table, keyed by table name; copied, not retained.
     */
    public RowCountSnapshot(final Map<String, Integer> rowCounts)
    {
        this.rowCounts = Collections.unmodifiableMap(new LinkedHashMap<>(rowCounts));
    }

    /**
     * Returns the row count of each table in this snapshot.
     *
     * @return An unmodifiable map of row count keyed by table name.
     */
    public Map<String, Integer> getRowCounts()
    {
        return rowCounts;
    }

    /**
     * Compares this snapshot, used as the baseline, against a snapshot captured later.
     * Does not assume the two snapshots share the identical key set: a table absent from
     * one side - e.g. dropped or created between the two captures, or the two connections
     * behind them not enumerating identically - counts as {@code 0} on that side rather
     * than throwing, so the difference is reported like any other rather than failing with
     * a {@code NullPointerException}.
     *
     * @param current The snapshot to compare this baseline against.
     * @return One {@link RowCountDifference} per table whose count changed, in this
     *         snapshot's table order followed by any table {@code current} has that this
     *         snapshot does not, in {@code current}'s order; empty when every count is
     *         unchanged.
     */
    public List<RowCountDifference> difference(final RowCountSnapshot current)
    {
        final Set<String> tableNames = new LinkedHashSet<>(rowCounts.keySet());
        tableNames.addAll(current.rowCounts.keySet());

        final List<RowCountDifference> differences = new ArrayList<>();
        for (final String tableName : tableNames)
        {
            final int baselineCount = rowCounts.getOrDefault(tableName, 0);
            final int currentCount = current.rowCounts.getOrDefault(tableName, 0);
            if (currentCount != baselineCount)
            {
                differences.add(
                        new RowCountDifference(tableName, baselineCount, currentCount));
            }
        }
        return differences;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof RowCountSnapshot))
        {
            return false;
        }
        final RowCountSnapshot other = (RowCountSnapshot) o;
        return Objects.equals(rowCounts, other.rowCounts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(rowCounts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName() + rowCounts;
    }
}
