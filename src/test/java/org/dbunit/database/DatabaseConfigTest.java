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
    void testSetPropertyToNullWhereNotAllowed() throws Exception
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
    void testSetPropertyToNullWhereAllowed() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setProperty(DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER, null);
        assertThat(
                config.getProperty(DatabaseConfig.PROPERTY_PRIMARY_KEY_FILTER))
                        .isNull();
    }

    @Test
    void testSetFeatureViaSetPropertyMethod() throws Exception
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
    void testSetFeatureViaSetFeatureMethod() throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
        assertThat(
                config.getProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                        .isEqualTo(Boolean.TRUE);
        assertThat(config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                .isTrue();
    }

}
