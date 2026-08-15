/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;

import org.dbunit.database.rowcount.QueryPerTableRowCounter;
import org.dbunit.database.rowcount.RowCounter;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.DataTypeException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Test;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
class DatabaseConfigTest
{
    @Test
    void testSetProperty_InvalidType_Array() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        final String simpleString = "TABLE";
        final String expectedMsg =
                "Cannot cast object of type 'class java.lang.String' to allowed type 'class [Ljava.lang.String;'.";

        final IllegalArgumentException expected = assertThrows(
                IllegalArgumentException.class,
                () -> config.setProperty(DatabaseConfig.PROPERTY_TABLE_TYPE,
                        simpleString),
                "The property 'table type' should be a string array");

        assertThat(expected).hasMessage(expectedMsg);
    }

    @Test
    void testSetProperty_CorrectType_Array() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        final String[] stringArray = new String[] {"TABLE"};
        config.setProperty(DatabaseConfig.PROPERTY_TABLE_TYPE, stringArray);
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_TABLE_TYPE))
                .isEqualTo(stringArray);
    }

    @Test
    void testSetProperty_Interface() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        final IDataTypeFactory myFactory = new IDataTypeFactory()
        {

            @Override
            public DataType createDataType(final int sqlType,
                    final String sqlTypeName, final String tableName,
                    final String columnName) throws DataTypeException
            {
                return null;
            }

            @Override
            public DataType createDataType(final int sqlType,
                    final String sqlTypeName) throws DataTypeException
            {
                return null;
            }
        };
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, myFactory);
        assertThat(config.getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY))
                .isEqualTo(myFactory);
    }

    @Test
    void testSetPropertyToNullWhereNotAllowed_withNotNullableProperty_throwsIllegalArgumentException() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        final String expectedMsg =
                "The property 'http://www.dbunit.org/properties/batchSize' is not nullable.";
        final IllegalArgumentException expected =
                assertThrows(IllegalArgumentException.class, () -> {
                    config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE,
                            null);
                    assertThat(config
                            .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                                    .isNull();
                }, "Should not be able to set a not-nullable property to null");

        assertThat(expected).hasMessage(expectedMsg);
    }

    @Test
    void testSetPropertyToNullWhereAllowed_withNullableProperty_setsPropertyToNull() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setProperty(DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER, null);
        assertThat(
                config.getProperty(DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER))
                        .isNull();
    }

    @Test
    void testSetFeatureViaSetPropertyMethod_withStringTrue_setsFeatureToTrue() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, "true");
        assertThat(
                config.getProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                        .isEqualTo(Boolean.TRUE);
        assertThat(config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .isTrue();
    }

    @Test
    void testSetFeatureViaSetFeatureMethod_withBooleanTrue_setsFeatureToTrue() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        assertThat(
                config.getProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                        .isEqualTo(Boolean.TRUE);
        assertThat(config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .isTrue();
    }

    @Test
    void testCopyPropertiesInto_withConfiguredPropertyAndFeature_copiesValuesToTargetConfig()
            throws Exception
    {
        final DatabaseConfig source = new DatabaseConfig();
        source.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE, 500);
        source.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        final DatabaseConfig target = new DatabaseConfig();

        source.copyPropertiesInto(target);

        assertThat(target.getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                .as("copyPropertiesInto() must copy a configured property value to the target config.")
                .isEqualTo(500);
        assertThat(target.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .as("copyPropertiesInto() must copy a configured feature value to the target config.")
                .isTrue();
    }

    @Test
    void testGetFeature_skipCycleCheckDefault_isFalse() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();

        assertThat(config.getFeature(DatabaseConfig.FEATURE_SKIP_CYCLE_CHECK))
                .as("FEATURE_SKIP_CYCLE_CHECK must default to false, preserving the "
                        + "pre-existing fail-on-cycle behavior for callers who never touch it.")
                .isFalse();
    }

    @Test
    void testCopyPropertiesInto_withNullablePropertyAtDefault_overwritesTargetWithNull()
            throws Exception
    {
        final DatabaseConfig source = new DatabaseConfig();
        final DatabaseConfig target = new DatabaseConfig();
        target.setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "[?]");

        source.copyPropertiesInto(target);

        assertThat(target.getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN))
                .as("copyPropertiesInto() must overwrite the target's property even when the "
                        + "source left it at its nullable default.")
                .isNull();
    }

    @Test
    void testGetFeature_rowCountCheckDefault_isFalse() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();

        assertThat(config.getFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK))
                .as("FEATURE_ROW_COUNT_CHECK must default to false; the check is opt-in.")
                .isFalse();
    }

    @Test
    void testGetProperty_rowCountCheckExcludeTablesDefault_isEmptyArray() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();

        assertThat(
                (String[]) config
                        .getProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES))
                                .as("PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES must default to an"
                                        + " empty pattern list.")
                                .isEmpty();
    }

    @Test
    void testGetProperty_rowCounterDefault_isQueryPerTableRowCounterInstance() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_ROW_COUNTER))
                .as("PROPERTY_ROW_COUNTER must default to a QueryPerTableRowCounter, the v1"
                        + " RowCounter implementation.")
                .isInstanceOf(QueryPerTableRowCounter.class);
    }

    @Test
    void testFindByName_rowCountCheckFeature_returnsBooleanConfigProperty() throws Exception
    {
        final DatabaseConfig.ConfigProperty property =
                DatabaseConfig.findByName(DatabaseConfig.FEATURE_ROW_COUNT_CHECK);

        assertThat(property)
                .as("FEATURE_ROW_COUNT_CHECK must be registered so its type can be validated,"
                        + " instead of only logging \"Unknown property\".")
                .isNotNull();
        assertThat(property.getPropertyType()).as("The feature is boolean-valued.")
                .isEqualTo(Boolean.class);
    }

    @Test
    void testFindByShortName_rowCountCheckExcludeTables_returnsStringArrayConfigProperty()
            throws Exception
    {
        final DatabaseConfig.ConfigProperty property =
                DatabaseConfig.findByShortName("rowCountCheckExcludeTables");

        assertThat(property)
                .as("The short name must resolve to PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES.")
                .isNotNull();
        assertThat(property.getPropertyType())
                .as("The exclude patterns are a String array, matching the shape of"
                        + " PROPERTY_TABLE_TYPE and the dbunit.rowCountCheckExcludeTables"
                        + " system property.")
                .isEqualTo(String[].class);
    }

    @Test
    void testFindByName_rowCounter_returnsRowCounterConfigProperty() throws Exception
    {
        final DatabaseConfig.ConfigProperty property =
                DatabaseConfig.findByName(DatabaseConfig.PROPERTY_ROW_COUNTER);

        assertThat(property)
                .as("PROPERTY_ROW_COUNTER must be registered so its type can be validated.")
                .isNotNull();
        assertThat(property.getPropertyType()).as("The property holds a RowCounter instance.")
                .isEqualTo(RowCounter.class);
    }

    @Test
    void testSetPropertiesByString_rowCounterClassName_instantiatesThatCounter() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        final Properties stringProperties = new Properties();
        stringProperties.setProperty("rowCounter", QueryPerTableRowCounter.class.getName());

        config.setPropertiesByString(stringProperties);

        assertThat(config.getProperty(DatabaseConfig.PROPERTY_ROW_COUNTER))
                .as("A class name configured via a String property (e.g. from Ant or Maven)"
                        + " must be reflectively instantiated, the same as any other"
                        + " object-valued property.")
                .isInstanceOf(QueryPerTableRowCounter.class);
    }

}
