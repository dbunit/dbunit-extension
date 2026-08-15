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
import java.util.List;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;

/**
 * Thrown by {@link RowCountCheck#verify(RowCountSnapshot, IDatabaseConnection)} when one or
 * more tables' row counts no longer match the baseline. The message names every affected
 * table, both counts, the signed delta, and direction-specific advice, in the form a reader can
 * paste straight into a dataset or an exclude list.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class UnexpectedRowCountException extends DatabaseUnitException
{
    private static final long serialVersionUID = 1L;

    private final List<RowCountDifference> differences;

    /**
     * Creates an exception reporting the given differences.
     *
     * @param differences The tables whose row counts no longer match the baseline; must not be
     *            empty.
     */
    public UnexpectedRowCountException(final List<RowCountDifference> differences)
    {
        super(buildMessage(differences));
        this.differences = Collections.unmodifiableList(new ArrayList<>(differences));
    }

    /**
     * Returns the tables whose row counts no longer match the baseline.
     *
     * @return The differences, one per affected table.
     */
    public List<RowCountDifference> getDifferences()
    {
        return differences;
    }

    private static String buildMessage(final List<RowCountDifference> differences)
    {
        final int count = differences.size();
        final String tableWord = count == 1 ? " table differs" : " tables differ";

        final StringBuilder message = new StringBuilder();
        message.append("Row count check failed: ").append(count).append(tableWord)
                .append(" from the pre-test baseline.");
        for (final RowCountDifference difference : differences)
        {
            message.append(System.lineSeparator()).append("  ").append(difference);
        }
        return message.toString();
    }
}
