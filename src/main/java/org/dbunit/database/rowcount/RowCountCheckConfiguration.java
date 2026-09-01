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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.filter.ExcludeTableFilter;

/**
 * Resolves whether the row count check is enabled, its excluded table patterns, and the
 * {@link RowCounter} to use, from a {@link DatabaseConfig} and the {@code dbunit.*} system
 * property overrides.
 * <p>
 * Precedence, for both the enabled flag and the exclude patterns: the system property, when
 * present, wins in either direction, so it can force-enable in CI and force-disable locally.
 * Absent, resolution falls through to the {@link DatabaseConfig} value; absent there too, the
 * enabled flag defaults to {@code false}. The exclude system property, when present, replaces
 * rather than appends to the configured patterns.
 * <p>
 * The {@link RowCounter} has no system property override: swapping the counting implementation
 * is a code-level decision made once for a suite, not something flipped per run.
 * <p>
 * {@code RowCountChecker#overlayEnabledOverride(DatabaseConfig)} builds a {@link DatabaseConfig}
 * carrying exactly the three {@link DatabaseConfig} properties this constructor reads, in place
 * of a connection's own - nothing enforces the two stay in sync, so a property added here needs
 * a matching addition there too.
 *
 * @author dbunit
 * @since 3.6.0
 */
public final class RowCountCheckConfiguration
{
    /**
     * System property overriding {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK}, in either
     * direction, when present.
     */
    public static final String DBUNIT_ROW_COUNT_CHECK = "dbunit.rowCountCheck";

    /**
     * System property overriding {@link DatabaseConfig#PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES}
     * when present, replacing rather than appending to the configured patterns.
     */
    public static final String DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES =
            "dbunit.rowCountCheckExcludeTables";

    private final boolean enabled;
    private final ExcludeTableFilter excludeTableFilter;
    private final RowCounter rowCounter;

    /**
     * Resolves the configuration from the given database config and the current system
     * properties.
     *
     * @param databaseConfig the database config to resolve {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK},
     *            {@link DatabaseConfig#PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES}, and
     *            {@link DatabaseConfig#PROPERTY_ROW_COUNTER} from when no system property
     *            overrides them. A missing exclude-patterns value resolves to no exclusions,
     *            and a missing {@link RowCounter} to a new {@link QueryPerTableRowCounter},
     *            so a config that has not been through {@link DatabaseConfig}'s own default
     *            initialization is still tolerated.
     */
    public RowCountCheckConfiguration(final DatabaseConfig databaseConfig)
    {
        enabled = resolveEnabled(databaseConfig);
        final String[] excludeTablePatterns = resolveExcludeTablePatterns(databaseConfig);
        excludeTableFilter = new ExcludeTableFilter(excludeTablePatterns);
        rowCounter = resolveRowCounter(databaseConfig);
    }

    private static boolean resolveEnabled(final DatabaseConfig databaseConfig)
    {
        final String systemProperty = System.getProperty(DBUNIT_ROW_COUNT_CHECK);
        if (systemProperty != null)
        {
            return Boolean.parseBoolean(systemProperty);
        }
        return databaseConfig.getFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK);
    }

    private static String[] resolveExcludeTablePatterns(final DatabaseConfig databaseConfig)
    {
        final String systemProperty =
                System.getProperty(DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES);
        if (systemProperty != null)
        {
            return splitAndTrim(systemProperty);
        }

        final String[] configuredPatterns = (String[]) databaseConfig
                .getProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES);
        return configuredPatterns == null ? new String[0] : configuredPatterns;
    }

    private static RowCounter resolveRowCounter(final DatabaseConfig databaseConfig)
    {
        final RowCounter configuredRowCounter = (RowCounter) databaseConfig
                .getProperty(DatabaseConfig.PROPERTY_ROW_COUNTER);
        return configuredRowCounter == null ? new QueryPerTableRowCounter()
                : configuredRowCounter;
    }

    private static String[] splitAndTrim(final String commaSeparatedPatterns)
    {
        final String[] patterns = commaSeparatedPatterns.split(",");
        for (int i = 0; i < patterns.length; i++)
        {
            patterns[i] = patterns[i].trim();
        }
        return patterns;
    }

    /**
     * Returns whether the row count check is enabled.
     *
     * @return {@code true} when enabled.
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns the filter built from the resolved exclude table patterns.
     *
     * @return the exclude table filter.
     */
    public ExcludeTableFilter getExcludeTableFilter()
    {
        return excludeTableFilter;
    }

    /**
     * Returns the {@link RowCounter} to use.
     *
     * @return the configured row counter.
     */
    public RowCounter getRowCounter()
    {
        return rowCounter;
    }
}
