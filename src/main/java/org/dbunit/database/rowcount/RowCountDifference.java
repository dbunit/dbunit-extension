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

import java.util.Objects;

/**
 * Immutable record of one table's row count having changed between a
 * {@link RowCountCheck} baseline and a later comparison.
 *
 * @author dbunit
 * @since 3.6.0
 */
public final class RowCountDifference
{
    private static final String ADVICE_ROWS_LEFT_BEHIND =
            "rows left behind; add the table to the expected dataset, or exclude it";
    private static final String ADVICE_ROWS_REMOVED =
            "rows removed that should remain; drop the table from the prep/expected dataset, or exclude it";

    private final String tableName;
    private final int baselineCount;
    private final int currentCount;

    /**
     * Creates a difference for the given table.
     *
     * @param tableName The name of the table whose count changed, in the form
     *            {@link org.dbunit.dataset.IDataSet#getTableNames()} returned it.
     * @param baselineCount The row count captured before the test.
     * @param currentCount The row count found at comparison time.
     */
    public RowCountDifference(final String tableName, final int baselineCount,
            final int currentCount)
    {
        this.tableName = tableName;
        this.baselineCount = baselineCount;
        this.currentCount = currentCount;
    }

    /**
     * Returns the name of the table whose count changed.
     *
     * @return The table name, in the form {@link org.dbunit.dataset.IDataSet#getTableNames()}
     *         returned it.
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * Returns the row count captured before the test.
     *
     * @return The baseline row count.
     */
    public int getBaselineCount()
    {
        return baselineCount;
    }

    /**
     * Returns the row count found at comparison time.
     *
     * @return The current row count.
     */
    public int getCurrentCount()
    {
        return currentCount;
    }

    /**
     * Returns how much the row count changed.
     *
     * @return {@link #getCurrentCount()} minus {@link #getBaselineCount()}; positive when rows
     *         were left behind, negative when rows that should remain were removed.
     */
    public int getDelta()
    {
        return currentCount - baselineCount;
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
        if (!(o instanceof RowCountDifference))
        {
            return false;
        }
        final RowCountDifference other = (RowCountDifference) o;
        return baselineCount == other.baselineCount && currentCount == other.currentCount
                && Objects.equals(tableName, other.tableName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(tableName, baselineCount, currentCount);
    }

    /**
     * Returns a one-line, human-readable description of this difference, naming the table, both
     * counts, the signed delta, and direction-specific advice; the table name prints in the form
     * {@link org.dbunit.dataset.IDataSet#getTableNames()} returned it, so it can be pasted
     * straight into a dataset or an exclude list.
     *
     * @return The one-line description.
     */
    @Override
    public String toString()
    {
        final int delta = getDelta();
        final String signedDelta = delta > 0 ? "+" + delta : String.valueOf(delta);
        final String advice = delta > 0 ? ADVICE_ROWS_LEFT_BEHIND : ADVICE_ROWS_REMOVED;
        return tableName + "  " + baselineCount + " -> " + currentCount + "  (" + signedDelta
                + ")  " + advice;
    }
}
