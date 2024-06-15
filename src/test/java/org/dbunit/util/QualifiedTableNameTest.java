/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2004-2008, DbUnit.org
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
package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.stream.Stream;

import org.dbunit.database.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
class QualifiedTableNameTest
{

    @Test
    void testQualifiedTableNamePresent_PrecedesDefaultSchemaName()
    {
        final QualifiedTableName qualifiedTableName =
                new QualifiedTableName("MYSCHEMA.MYTABLE", "DEFAULT_SCHEMA");
        assertThat(qualifiedTableName.getSchema()).isEqualTo("MYSCHEMA");
        assertThat(qualifiedTableName.getTable()).isEqualTo("MYTABLE");
        assertThat(qualifiedTableName.getQualifiedName())
                .isEqualTo("MYSCHEMA.MYTABLE");
    }

    @Test
    void testQualifiedTableNameNotPresentUsingDefaultSchema()
    {
        final QualifiedTableName qualifiedTableName =
                new QualifiedTableName("MYTABLE", "DEFAULT_SCHEMA");
        assertThat(qualifiedTableName.getSchema()).isEqualTo("DEFAULT_SCHEMA");
        assertThat(qualifiedTableName.getTable()).isEqualTo("MYTABLE");
        assertThat(qualifiedTableName.getQualifiedName())
                .isEqualTo("DEFAULT_SCHEMA.MYTABLE");
    }

    @Test
    void testQualifiedTableNameNotPresentAndNoDefaultSchema()
    {
        final QualifiedTableName qualifiedTableName =
                new QualifiedTableName("MYTABLE", null);
        assertThat(qualifiedTableName.getSchema()).isNull();
        assertThat(qualifiedTableName.getTable()).isEqualTo("MYTABLE");
        assertThat(qualifiedTableName.getQualifiedName()).isEqualTo("MYTABLE");
    }

    @Test
    void testQualifiedTableNameNotPresentAndEmptyDefaultSchema()
    {
        final QualifiedTableName qualifiedTableName =
                new QualifiedTableName("MYTABLE", "");
        assertThat(qualifiedTableName.getSchema()).isEmpty();
        assertThat(qualifiedTableName.getTable()).isEqualTo("MYTABLE");
        assertThat(qualifiedTableName.getQualifiedName()).isEqualTo("MYTABLE");
    }

    @Test
    void testGetQualifiedTableName()
    {
        final String qualifiedName =
                new QualifiedTableName("MY_SCHEMA.MY_TABLE", null, "'?'")
                        .getQualifiedName();
        assertThat(qualifiedName).isEqualTo("'MY_SCHEMA'.'MY_TABLE'");
    }

    @Test
    void testGetQualifiedTableName_DefaultSchema()
    {
        final String qualifiedName =
                new QualifiedTableName("MY_TABLE", "DEFAULT_SCHEMA", "'?'")
                        .getQualifiedName();
        assertThat(qualifiedName).isEqualTo("'DEFAULT_SCHEMA'.'MY_TABLE'");
    }

    @ParameterizedTest
    @MethodSource("provideQualifiedTableNames")
    void testGetQualifiedTableName_DefaultSchema_FeatureEnabled(
            final String qualifiedTableName, final boolean qualifed,
            final String escapePattern)
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES,
                qualifed);
        final String qualifiedName =
                new QualifiedTableName("MY_TABLE", "DEFAULT_SCHEMA",
                        escapePattern).getQualifiedNameIfEnabled(config);
        assertThat(qualifiedName)
                .as("qualifedTablename: " + qualifiedTableName + " qualifed: "
                        + qualifed + " escapePattern: " + escapePattern)
                .isEqualTo(qualifiedTableName);
    }

    private static Stream<Arguments> provideQualifiedTableNames()
    {
        return Stream.of(Arguments.of("DEFAULT_SCHEMA.MY_TABLE", true, null),
                Arguments.of("MY_TABLE", false, null),
                Arguments.of("'DEFAULT_SCHEMA'.'MY_TABLE'", true, "'?'"),
                Arguments.of("'MY_TABLE'", false, "'?'"),
                Arguments.of("'DEFAULT_SCHEMA'.'MY_TABLE'", true, "'"));
    }

    @Test
    void testConstructorWithNullTable()
    {
        try
        {
            new QualifiedTableName(null, "SCHEMA");
            fail("Should not be able to create object with null table");
        } catch (final NullPointerException expected)
        {
            assertThat(expected.getMessage())
                    .isEqualTo("The parameter 'tableName' must not be null");
        }
    }

}
