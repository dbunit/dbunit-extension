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
import static org.mockito.Mockito.mock;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * CAUTION: mutates the {@code dbunit.rowCountCheck} and
 * {@code dbunit.rowCountCheckExcludeTables} system properties; {@link ClearRowCountCheckSystemProperties}
 * clears them before each case and restores whatever they were afterward, to stay independent
 * of test execution order and any ambient value already set for the whole JVM.
 */
@ClearRowCountCheckSystemProperties
class RowCountCheckConfigurationTest
{
    @Test
    void testIsEnabled_noSystemPropertyAndNoFeature_returnsFalse()
    {
        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(new DatabaseConfig());

        assertThat(configuration.isEnabled())
                .as("With neither the system property nor the feature set, the check must"
                        + " default to disabled.")
                .isFalse();
    }

    @Test
    void testIsEnabled_featureTrueAndNoSystemProperty_returnsTrue()
    {
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(databaseConfig);

        assertThat(configuration.isEnabled())
                .as("With no system property override, the DatabaseConfig feature must be used.")
                .isTrue();
    }

    @Test
    void testIsEnabled_systemPropertyFalseAndFeatureTrue_returnsFalse()
    {
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, "false");

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(databaseConfig);

        assertThat(configuration.isEnabled())
                .as("The system property must win over the DatabaseConfig feature, so it can"
                        + " force-disable locally even when the feature is on.")
                .isFalse();
    }

    @Test
    void testIsEnabled_systemPropertyTrueAndFeatureFalse_returnsTrue()
    {
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK, "true");

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(databaseConfig);

        assertThat(configuration.isEnabled())
                .as("The system property must win over the DatabaseConfig feature, so it can"
                        + " force-enable in CI even when the feature is off.")
                .isTrue();
    }

    @Test
    void testExcludeTables_systemPropertyAndConfiguredPatterns_replacesConfiguredPatterns()
            throws DataSetException
    {
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                new String[] {"CONFIGURED_TABLE"});
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                "SYSTEM_PROPERTY_TABLE");

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(databaseConfig);

        assertThat(configuration.getExcludeTableFilter().isValidName("CONFIGURED_TABLE"))
                .as("The system property must replace, not append to, the configured patterns,"
                        + " so a pattern configured only on DatabaseConfig no longer excludes.")
                .isTrue();
        assertThat(configuration.getExcludeTableFilter().isValidName("SYSTEM_PROPERTY_TABLE"))
                .as("The system property's own pattern must be applied.").isFalse();
    }

    @Test
    void testExcludeTables_commaSeparatedSystemProperty_splitsAndTrimsEveryPattern()
            throws DataSetException
    {
        System.setProperty(RowCountCheckConfiguration.DBUNIT_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                "AUDIT_* , HIBERNATE_SEQUENCES");

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(new DatabaseConfig());

        assertThat(configuration.getExcludeTableFilter().isValidName("AUDIT_LOG"))
                .as("Whitespace around a comma-separated pattern must be trimmed before"
                        + " matching, and wildcards must still work.")
                .isFalse();
        assertThat(configuration.getExcludeTableFilter().isValidName("HIBERNATE_SEQUENCES"))
                .as("Every comma-separated pattern must be applied, not only the first.")
                .isFalse();
        assertThat(configuration.getExcludeTableFilter().isValidName("ACCOUNT"))
                .as("A table matching no configured pattern must not be excluded.").isTrue();
    }

    @Test
    void testGetRowCounter_defaultConfiguration_returnsQueryPerTableRowCounter()
    {
        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(new DatabaseConfig());

        assertThat(configuration.getRowCounter())
                .as("With no configured PROPERTY_ROW_COUNTER, DatabaseConfig's own default"
                        + " (QueryPerTableRowCounter) must be used.")
                .isInstanceOf(QueryPerTableRowCounter.class);
    }

    @Test
    void testGetRowCounter_customRowCounterConfigured_returnsIt()
    {
        final RowCounter customRowCounter = mock(RowCounter.class);
        final DatabaseConfig databaseConfig = new DatabaseConfig();
        databaseConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNTER, customRowCounter);

        final RowCountCheckConfiguration configuration =
                new RowCountCheckConfiguration(databaseConfig);

        assertThat(configuration.getRowCounter())
                .as("A RowCounter configured on DatabaseConfig must be used as-is; there is no"
                        + " system property override for it.")
                .isSameAs(customRowCounter);
    }
}
