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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;

import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.database.DatabaseConfig;
import org.junit.jupiter.api.Test;

class DatabaseConfigPropertiesResolverTest
{
    private final DatabaseConfigPropertiesResolver resolver =
            new DatabaseConfigPropertiesResolver();

    @Test
    void testResolve_nothingDeclared_returnsEmpty()
    {
        assertThat(resolver.resolve(new DbUnitProperty[0])).as("No entry declares no properties.")
                .isEmpty();
    }

    @Test
    void testResolve_inlineProperties_collectsEveryNameValuePair()
    {
        final Properties properties = resolver.resolve(declaredIn(WithProperties.class));

        assertThat(properties)
                .as("Every name is stored in the long form DatabaseConfig names the property by,"
                        + " whichever form the annotation spelled it in.")
                .containsEntry(DatabaseConfig.PROPERTY_BATCH_SIZE, "50")
                .containsEntry(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES, "true")
                .hasSize(2);
    }

    @Test
    void testResolve_duplicatePropertyName_throwsNamingIt()
    {
        assertThatThrownBy(() -> resolver.resolve(declaredIn(WithDuplicatePropertyName.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("batchSize");
    }

    @Test
    void testResolve_longAndShortFormOfOnePropertyDeclared_throwsNamingIt()
    {
        assertThatThrownBy(() -> resolver.resolve(declaredIn(WithLongAndShortFormOfOneName.class)))
                .as("'batchSize' and its long form name one property, so which value wins would"
                        + " depend on the order a Properties happens to be iterated in.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("batchSize");
    }

    @Test
    void testResolve_inlineUnknownPropertyName_throwsNamingItAndTheSource()
    {
        assertThatThrownBy(() -> resolver.resolve(declaredIn(WithUnknownPropertyName.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notAProperty")
                .hasMessageContaining("@DbUnitConfig.properties()");
    }

    @Test
    void testResolve_inlineNameIsOnlyTheEndOfAShortName_throwsNamingIt()
    {
        assertThatThrownBy(() -> resolver.resolve(declaredIn(WithPartialPropertyName.class)))
                .as("'Size' is only the end of the short name batchSize, so it must be"
                        + " rejected as unknown rather than applied to batchSize.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'Size'")
                .hasMessageContaining("is not a known dbUnit DatabaseConfig property name");
    }

    private static DbUnitProperty[] declaredIn(final Class<?> annotated)
    {
        return annotated.getAnnotation(DbUnitConfig.class).properties();
    }

    @DbUnitConfig(properties = {@DbUnitProperty(name = "batchSize", value = "50"),
            @DbUnitProperty(name = "caseSensitiveTableNames", value = "true")})
    private static class WithProperties
    {
    }

    @DbUnitConfig(properties = {@DbUnitProperty(name = "batchSize", value = "50"),
            @DbUnitProperty(name = "batchSize", value = "100")})
    private static class WithDuplicatePropertyName
    {
    }

    @DbUnitConfig(properties = {@DbUnitProperty(name = "batchSize", value = "10"),
            @DbUnitProperty(name = "http://www.dbunit.org/properties/batchSize", value = "50")})
    private static class WithLongAndShortFormOfOneName
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "Size", value = "10"))
    private static class WithPartialPropertyName
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "notAProperty", value = "x"))
    private static class WithUnknownPropertyName
    {
    }

}
