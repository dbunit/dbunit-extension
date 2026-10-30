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
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
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
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchSize", "50", "datatypeFactory",
                        UninstantiableDataTypeFactory.class.getName()));

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

    @Test
    void testConstructor_booleanPropertyWithYes_throwsNamingThePropertyAndValue()
    {
        final Properties properties = propertiesOf("batchedStatements", "yes");

        assertThatThrownBy(() -> new DatabaseConfigOverrides(properties))
                .as("Boolean.valueOf() would read yes as false and turn the feature off without"
                        + " a word, so a value that is neither true nor false is rejected.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'batchedStatements'")
                .hasMessageContaining(DatabaseConfig.FEATURE_BATCHED_STATEMENTS)
                .hasMessageContaining("'yes'").hasMessageContaining("true or false");
    }

    @Test
    void testConstructor_booleanPropertyInUpperCase_isAccepted() throws Exception
    {
        final DatabaseConfigOverrides overrides =
                new DatabaseConfigOverrides(propertiesOf("batchedStatements", "TRUE"));

        overrides.applyTo(config);

        assertThat(config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .as("True is accepted in any case, as Boolean.valueOf() reads it.").isTrue();
    }

    @Test
    void testConstructor_integerPropertyNotANumber_throwsNamingTheProperty()
    {
        final Properties properties = propertiesOf("batchSize", "abc");

        assertThatThrownBy(() -> new DatabaseConfigOverrides(properties))
                .as("An integer property whose value is no integer is rejected, naming the"
                        + " property, instead of failing later with a bare NumberFormatException.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'batchSize'")
                .hasMessageContaining(DatabaseConfig.PROPERTY_BATCH_SIZE)
                .hasMessageContaining("'abc'").hasMessageContaining("an integer")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void testConstructor_classPropertyThatDoesNotLoad_throwsNamingTheProperty()
    {
        final Properties properties = propertiesOf("datatypeFactory", "not.a.real.ClassName");

        assertThatThrownBy(() -> new DatabaseConfigOverrides(properties))
                .as("A class name that cannot be loaded is rejected when the overrides are made,"
                        + " naming the property, not when they are applied.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'datatypeFactory'")
                .hasMessageContaining(DatabaseConfig.PROPERTY_DATATYPE_FACTORY)
                .hasMessageContaining("'not.a.real.ClassName'")
                .hasMessageContaining("can be loaded and implements "
                        + "org.dbunit.dataset.datatype.IDataTypeFactory")
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void testConstructor_classPropertyOfAnotherType_throwsNamingTheProperty()
    {
        final Properties properties = propertiesOf("datatypeFactory", "java.lang.String");

        assertThatThrownBy(() -> new DatabaseConfigOverrides(properties))
                .as("A class that is no IDataTypeFactory is rejected, instead of failing with an"
                        + " IllegalArgumentException that names no property when it is set.")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'datatypeFactory'")
                .hasMessageContaining("'java.lang.String'")
                .hasMessageContaining("implements org.dbunit.dataset.datatype.IDataTypeFactory");
    }

    @Test
    void testConstructor_classPropertyThatCannotBeInstantiated_isAcceptedAndFailsWhenApplied()
    {
        final DatabaseConfigOverrides overrides = new DatabaseConfigOverrides(propertiesOf(
                "datatypeFactory", UninstantiableDataTypeFactory.class.getName()));

        assertThatThrownBy(() -> overrides.applyTo(config))
                .as("The constructor checks that the class loads and has the type of the"
                        + " property, and leaves instantiating it to when the value is applied.")
                .isInstanceOf(DatabaseUnitException.class);
    }

    @Test
    void testConstructor_stringProperties_acceptAnyValue()
    {
        assertThatCode(() -> new DatabaseConfigOverrides(
                propertiesOf("escapePattern", "[?]", "tableType", "TABLE, VIEW")))
                        .as("A String and a String[] property take any text.")
                        .doesNotThrowAnyException();
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

    /** A data type factory that dbUnit can load but not instantiate. */
    public abstract static class UninstantiableDataTypeFactory extends DefaultDataTypeFactory
    {
    }
}
