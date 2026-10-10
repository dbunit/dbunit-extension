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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;

import org.dbunit.DatabaseUnitException;
import org.junit.jupiter.api.Test;

class DatabaseConfigOverridesTest
{
    private static final Object DEFAULT_BATCH_SIZE =
            new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);
    private static final Object DEFAULT_FETCH_SIZE =
            new DatabaseConfig().getProperty(DatabaseConfig.PROPERTY_FETCH_SIZE);

    private final DatabaseConfig config = new DatabaseConfig();

    @Test
    void testApplyTo_valuesGiven_setsThemOnTheConfig() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50", "fetchSize", "7"));

        overrides.applyTo(config);

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The batch size must be the overridden value.").isEqualTo(50);
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_FETCH_SIZE))
                .as("The fetch size must be the overridden value.").isEqualTo(7);
    }

    @Test
    void testApplyTo_noValues_leavesTheConfigAlone() throws Exception
    {
        final DatabaseConfigOverrides overrides = new DatabaseConfigOverrides(new Properties());

        overrides.applyTo(config);

        assertThat(overrides.isEmpty()).as("Overrides without values must be empty.").isTrue();
        assertThat(overrides.hasAppliedTo(config))
                .as("Nothing was applied, so there is nothing to restore.").isFalse();
    }

    @Test
    void testRestore_valuesApplied_putsBackEveryReplacedValue() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50", "fetchSize", "7"));
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The batch size must be back to what it was.").isEqualTo(DEFAULT_BATCH_SIZE);
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_FETCH_SIZE))
                .as("The fetch size must be back to what it was.").isEqualTo(DEFAULT_FETCH_SIZE);
    }

    @Test
    void testRestore_valueGivenByLongName_putsItBack() throws Exception
    {
        final DatabaseConfigOverrides overrides = new DatabaseConfigOverrides(
                propertiesOf(DatabaseConfig.PROPERTY_BATCH_SIZE, "50"));
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A value named in its long form must be restored too.")
                .isEqualTo(DEFAULT_BATCH_SIZE);
    }

    @Test
    void testRestore_featureApplied_putsBackTheFeature() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchedStatements", "true"));
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .as("A feature flag must be restored like any other value.").isFalse();
    }

    @Test
    void testRestore_replacedValueWasNull_putsBackNull() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("escapePattern", "[?]"));
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN))
                .as("A nullable property that had no value must have none again.").isNull();
    }

    @Test
    void testRestore_appliedToTheConfigSeveralTimes_putsBackTheValueFromBeforeTheFirst()
            throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        overrides.applyTo(config);
        overrides.applyTo(config);
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The lifecycle applies the values each time it retrieves the connection;"
                        + " restoring must not settle on a value an earlier application set.")
                .isEqualTo(DEFAULT_BATCH_SIZE);
    }

    @Test
    void testRestore_valueWasSetBeforeApplying_putsBackThatValue() throws Exception
    {
        config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, 10);
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        overrides.applyTo(config);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("What a listener configured before the override is what must come back,"
                        + " not dbUnit's default.")
                .isEqualTo(10);
    }

    @Test
    void testRestore_appliedToTwoConfigs_putsBackBoth() throws Exception
    {
        final DatabaseConfig other = new DatabaseConfig();
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        overrides.applyTo(config);
        overrides.applyTo(other);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The first config must be restored.").isEqualTo(DEFAULT_BATCH_SIZE);
        assertThat(other.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The second config must be restored.").isEqualTo(DEFAULT_BATCH_SIZE);
    }

    @Test
    void testRestore_calledTwice_secondCallLeavesLaterChangesAlone() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        overrides.applyTo(config);
        overrides.restore();
        config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, 10);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("A restored override has nothing left to put back.").isEqualTo(10);
    }

    @Test
    void testRestore_nothingApplied_changesNothing()
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, 10);

        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("Overrides never applied must not touch the config.").isEqualTo(10);
    }

    @Test
    void testHasAppliedTo_beforeApplyingAfterApplyingAndAfterRestoring_tracksTheConfig()
            throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50"));
        final boolean beforeApplying = overrides.hasAppliedTo(config);

        overrides.applyTo(config);
        final boolean afterApplying = overrides.hasAppliedTo(config);
        final boolean otherConfig = overrides.hasAppliedTo(new DatabaseConfig());

        overrides.restore();
        final boolean afterRestoring = overrides.hasAppliedTo(config);

        assertThat(new boolean[] {beforeApplying, afterApplying, otherConfig, afterRestoring})
                .as("Applied only to the config it was applied to, and only until restored.")
                .containsExactly(false, true, false, false);
    }

    @Test
    void testApplyTo_unknownPropertyName_isIgnoredAsSetPropertiesByStringDoes()
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("noSuchProperty", "x"));

        assertThatCode(() ->
        {
            overrides.applyTo(config);
            overrides.restore();
        }).as("An unknown name is skipped, not an error.").doesNotThrowAnyException();
    }

    @Test
    void testRestore_applyingFailedPartWayThrough_stillPutsBackWhatWasApplied()
    {
        final DatabaseConfigOverrides overrides = new DatabaseConfigOverrides(
                propertiesOf("batchSize", "50", "datatypeFactory", "not.a.real.ClassName"));

        assertThatThrownBy(() -> overrides.applyTo(config))
                .as("An unusable value must fail the application.")
                .isInstanceOf(DatabaseUnitException.class);
        overrides.restore();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("Whatever was applied before the failure must still be put back.")
                .isEqualTo(DEFAULT_BATCH_SIZE);
    }

    @Test
    void testConstructor_callerChangesPropertiesAfterward_overridesKeepTheOriginalValues()
            throws Exception
    {
        final Properties properties = propertiesOf("batchSize", "50");
        final DatabaseConfigOverrides overrides = new DatabaseConfigOverrides(properties);

        properties.setProperty("batchSize", "999");
        overrides.applyTo(config);

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("The overrides must hold a copy of the values they were given.")
                .isEqualTo(50);
    }

    private static Properties propertiesOf(final String... namesAndValues)
    {
        final Properties properties = new Properties();
        for (int i = 0; i < namesAndValues.length; i += 2)
        {
            properties.setProperty(namesAndValues[i], namesAndValues[i + 1]);
        }
        return properties;
    }
}
