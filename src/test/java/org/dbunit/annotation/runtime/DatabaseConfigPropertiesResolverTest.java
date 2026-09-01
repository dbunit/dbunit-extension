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
import org.dbunit.database.DatabaseConfigPropertiesProvider;
import org.junit.jupiter.api.Test;

class DatabaseConfigPropertiesResolverTest
{
    private final DatabaseConfigPropertiesResolver resolver =
            new DatabaseConfigPropertiesResolver();

    @Test
    void testResolve_noConfig_returnsEmpty()
    {
        assertThat(resolver.resolve(null)).as("No @DbUnitConfig declares no properties.")
                .isEmpty();
    }

    @Test
    void testResolve_inlineProperties_collectsEveryNameValuePair()
    {
        final Properties properties = resolver.resolve(config(WithProperties.class));

        assertThat(properties).containsEntry("batchSize", "50")
                .containsEntry("caseSensitiveTableNames", "true");
    }

    @Test
    void testResolve_duplicatePropertyName_throwsNamingIt()
    {
        assertThatThrownBy(() -> resolver.resolve(config(WithDuplicatePropertyName.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("batchSize");
    }

    @Test
    void testResolve_inlineUnknownPropertyName_throwsNamingItAndTheSource()
    {
        assertThatThrownBy(() -> resolver.resolve(config(WithUnknownPropertyName.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notAProperty")
                .hasMessageContaining("@DbUnitConfig.properties()");
    }

    @Test
    void testResolve_propertiesProvider_usesTheValuesItSupplies()
    {
        final Properties properties = resolver.resolve(config(WithPropertiesProvider.class));

        assertThat(properties).containsEntry("fetchSize", "25");
    }

    @Test
    void testResolve_propertiesProviderReturnsNull_throwsNamingTheProvider()
    {
        assertThatThrownBy(() -> resolver.resolve(config(WithNullReturningProvider.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NullReturningProvider.class.getName())
                .hasMessageContaining("DbUnitConfig.propertiesProvider");
    }

    @Test
    void testResolve_propertiesProviderSuppliesUnknownName_throwsNamingItAndTheProvider()
    {
        assertThatThrownBy(() -> resolver.resolve(config(WithUnknownNameProvider.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notAProperty")
                .hasMessageContaining(UnknownNameProvider.class.getName());
    }

    @Test
    void testResolve_bothPropertiesAndProviderSet_throws()
    {
        assertThatThrownBy(() -> resolver.resolve(config(WithBothPropertiesAndProvider.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("properties()")
                .hasMessageContaining("propertiesProvider()");
    }

    private static DbUnitConfig config(final Class<?> annotated)
    {
        return annotated.getAnnotation(DbUnitConfig.class);
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

    @DbUnitConfig(properties = @DbUnitProperty(name = "notAProperty", value = "x"))
    private static class WithUnknownPropertyName
    {
    }

    @DbUnitConfig(propertiesProvider = SuppliesFetchSize.class)
    private static class WithPropertiesProvider
    {
    }

    @DbUnitConfig(propertiesProvider = NullReturningProvider.class)
    private static class WithNullReturningProvider
    {
    }

    @DbUnitConfig(propertiesProvider = UnknownNameProvider.class)
    private static class WithUnknownNameProvider
    {
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"),
            propertiesProvider = SuppliesFetchSize.class)
    private static class WithBothPropertiesAndProvider
    {
    }

    static class SuppliesFetchSize implements DatabaseConfigPropertiesProvider
    {
        @Override
        public Properties getDatabaseConfigProperties()
        {
            final Properties properties = new Properties();
            properties.setProperty("fetchSize", "25");
            return properties;
        }
    }

    static class NullReturningProvider implements DatabaseConfigPropertiesProvider
    {
        @Override
        public Properties getDatabaseConfigProperties()
        {
            return null;
        }
    }

    static class UnknownNameProvider implements DatabaseConfigPropertiesProvider
    {
        @Override
        public Properties getDatabaseConfigProperties()
        {
            final Properties properties = new Properties();
            properties.setProperty("notAProperty", "x");
            return properties;
        }
    }
}
