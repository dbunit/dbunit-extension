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

import java.util.Arrays;

/**
 * The values a test declares for its {@link RowCountCheck}, such as those of
 * {@code @DbUnitRowCountCheck}: whether the check is enabled and which table patterns it
 * excludes. Given to a {@link RowCountBaselineTracker}, it replaces those two settings of the
 * connection's own {@link org.dbunit.database.DatabaseConfig}. The {@link RowCounter} still
 * comes from the {@code DatabaseConfig}, and the {@code dbunit.*} system properties apply as
 * {@link RowCountCheckConfiguration} documents.
 *
 * <p>The exclude patterns are copied on the way in and on the way out, so an instance never
 * changes after it is created.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class RowCountCheckOverride
{
    private final boolean enabled;
    private final String[] exclude;

    /**
     * Creates an override.
     *
     * @param enabled Whether the check is enabled.
     * @param exclude The excluded table patterns; {@code null} is treated as empty, which
     *            excludes none. Copied, not retained.
     */
    public RowCountCheckOverride(final boolean enabled, final String[] exclude)
    {
        this.enabled = enabled;
        this.exclude = exclude == null ? new String[0] : exclude.clone();
    }

    /**
     * Returns whether the check is enabled.
     *
     * @return {@code true} when the check is enabled.
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns the excluded table patterns.
     *
     * @return A copy of the patterns; empty when none are excluded.
     */
    public String[] getExclude()
    {
        return exclude.clone();
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
        if (!(o instanceof RowCountCheckOverride))
        {
            return false;
        }
        final RowCountCheckOverride other = (RowCountCheckOverride) o;
        return enabled == other.enabled && Arrays.equals(exclude, other.exclude);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return 31 * Boolean.hashCode(enabled) + Arrays.hashCode(exclude);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return "RowCountCheckOverride[enabled=" + enabled + ", exclude="
                + Arrays.toString(exclude) + "]";
    }
}
