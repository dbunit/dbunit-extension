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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.filter.ExcludeTableFilter;
import org.dbunit.util.CommaSeparatedValues;

/**
 * Resolves whether the row count check is enabled, its excluded table patterns, and the
 * {@link RowCounter} to use, from a {@link DatabaseConfig} or from the values a test declares,
 * and the {@code dbunit.*} system property overrides.
 * <p>
 * Precedence for the enabled flag, highest first:
 * <ol>
 * <li>The {@link #DBUNIT_ROW_COUNT_CHECK} system property when it is {@code false}, so the check
 * can be force-disabled locally without editing code.</li>
 * <li>The value a test declares, such as {@code @DbUnitRowCountCheck(enabled = false)}, passed to
 * {@link #RowCountCheckConfiguration(boolean, String[], RowCounter)}. A test that legitimately
 * leaves rows behind stays exempt when a CI run force-enables the check.</li>
 * <li>The {@link #DBUNIT_ROW_COUNT_CHECK} system property when it is {@code true} or present with
 * no value, so the check can be force-enabled across a CI run for every test that declares
 * nothing.</li>
 * <li>The {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} feature.</li>
 * <li>{@code false}.</li>
 * </ol>
 * <p>
 * The exclude patterns are the declared or {@link DatabaseConfig} ones plus those of the
 * {@link #DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES} system property, which adds to them and never
 * replaces them, so a suite-wide list does not silently re-enable the check for the tables a test
 * excluded.
 * <p>
 * The {@link RowCounter} has no system property override: swapping the counting implementation
 * is a code-level decision made once for a suite, not something flipped per run.
 * <p>
 * The {@link #RowCountCheckConfiguration(DatabaseConfig)} constructor reads the three values a
 * {@link DatabaseConfig} carries. A caller that already holds the values a test declares - an
 * annotation override, say - passes them to
 * {@link #RowCountCheckConfiguration(boolean, String[], RowCounter)} directly rather than
 * staging them through a throwaway {@link DatabaseConfig}.
 *
 * @author dbunit
 * @since 3.6.0
 */
public class RowCountCheckConfiguration
{
    /**
     * System property overriding the enabled flag when present. {@code false} force-disables the
     * check, whatever the {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} feature or a test
     * declares. {@code true}, or a bare {@code -Ddbunit.rowCountCheck}, which Java reads as an
     * empty value, force-enables it over the feature, but not for a test that declares it
     * disabled. Any other value follows {@link Boolean#parseBoolean(String)}.
     */
    public static final String DBUNIT_ROW_COUNT_CHECK = "dbunit.rowCountCheck";

    /**
     * System property adding comma separated table name patterns, when present, to the ones
     * {@link DatabaseConfig#PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES} or a test declares.
     */
    public static final String DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES =
            "dbunit.rowCountCheckExcludeTables";

    private final boolean enabled;
    private final ExcludeTableFilter excludeTableFilter;
    private final RowCounter rowCounter;

    /**
     * Resolves the configuration from the three values a {@link DatabaseConfig} carries. The
     * {@link #DBUNIT_ROW_COUNT_CHECK} system property, when present, wins over the
     * {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK} feature in either direction.
     *
     * @param databaseConfig the database config to read {@link DatabaseConfig#FEATURE_ROW_COUNT_CHECK},
     *            {@link DatabaseConfig#PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES}, and
     *            {@link DatabaseConfig#PROPERTY_ROW_COUNTER} from.
     */
    public RowCountCheckConfiguration(final DatabaseConfig databaseConfig)
    {
        final boolean feature = databaseConfig.getFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK);
        this.enabled = resolveEnabledOverFeature(feature);
        final String[] configuredPatterns = (String[]) databaseConfig
                .getProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES);
        final String[] patterns = withSystemPropertyPatterns(configuredPatterns);
        this.excludeTableFilter = new ExcludeTableFilter(patterns);
        final RowCounter configuredRowCounter =
                (RowCounter) databaseConfig.getProperty(DatabaseConfig.PROPERTY_ROW_COUNTER);
        this.rowCounter = rowCounterOrDefault(configuredRowCounter);
    }

    /**
     * Resolves the configuration from the values a test declares, such as those of
     * {@code @DbUnitRowCountCheck}, and the current system properties. A declared {@code enabled}
     * of {@code false} stays disabled when {@code -Ddbunit.rowCountCheck} force-enables the check,
     * and a declared {@code true} is disabled by {@code -Ddbunit.rowCountCheck=false}. The
     * {@code dbunit.rowCountCheckExcludeTables} system property adds its patterns to
     * {@code excludeTablePatterns}.
     *
     * @param enabled whether the test declares the check enabled.
     * @param excludeTablePatterns the excluded table patterns the test declares; {@code null}
     *            resolves to no exclusions of its own.
     * @param rowCounter the {@link RowCounter} to use; {@code null} resolves to a new
     *            {@link QueryPerTableRowCounter}, so a value not carried by
     *            {@link DatabaseConfig}'s own default initialization is still tolerated.
     */
    public RowCountCheckConfiguration(final boolean enabled,
            final String[] excludeTablePatterns, final RowCounter rowCounter)
    {
        this.enabled = resolveDeclaredEnabled(enabled);
        final String[] patterns = withSystemPropertyPatterns(excludeTablePatterns);
        this.excludeTableFilter = new ExcludeTableFilter(patterns);
        this.rowCounter = rowCounterOrDefault(rowCounter);
    }

    private static boolean resolveEnabledOverFeature(final boolean featureValue)
    {
        final String systemProperty = System.getProperty(DBUNIT_ROW_COUNT_CHECK);
        if (systemProperty == null)
        {
            return featureValue;
        }
        return isSystemPropertyTrue(systemProperty);
    }

    private static boolean resolveDeclaredEnabled(final boolean declaredValue)
    {
        final String systemProperty = System.getProperty(DBUNIT_ROW_COUNT_CHECK);
        if (systemProperty == null)
        {
            return declaredValue;
        }
        return declaredValue && isSystemPropertyTrue(systemProperty);
    }

    private static boolean isSystemPropertyTrue(final String systemProperty)
    {
        if (systemProperty.trim().isEmpty())
        {
            return true;
        }
        return Boolean.parseBoolean(systemProperty);
    }

    private static String[] withSystemPropertyPatterns(final String[] ownPatterns)
    {
        final String[] patterns = ownPatterns == null ? new String[0] : ownPatterns;
        final String systemProperty =
                System.getProperty(DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES);
        if (systemProperty == null)
        {
            return patterns;
        }
        final String[] added = CommaSeparatedValues.split(systemProperty);
        final String[] combined = Arrays.copyOf(patterns, patterns.length + added.length);
        System.arraycopy(added, 0, combined, patterns.length, added.length);
        return combined;
    }

    private static RowCounter rowCounterOrDefault(final RowCounter configuredRowCounter)
    {
        if (configuredRowCounter == null)
        {
            return new QueryPerTableRowCounter();
        }
        return configuredRowCounter;
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
