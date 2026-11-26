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

import java.util.Properties;

import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConfig.ConfigProperty;

/**
 * Resolves the {@link DatabaseConfig} properties a test declares - the
 * {@code @DbUnitConfig(properties)} {@link DbUnitProperty} entries - into the {@link Properties}
 * {@link AnnotatedTestConfiguration} carries.
 *
 * <p>Rejects a property declared more than once - under one name or under its long and its
 * short form, which name one property - and any name {@link DatabaseConfig} does not recognize -
 * which {@link DatabaseConfig#setPropertiesByString(Properties)} would otherwise drop silently,
 * leaving the test running with the misspelled property never applied. Every name is resolved
 * to the long one {@link DatabaseConfig} knows the property by, which is the form the
 * {@link Properties} returned carries.
 *
 * <p>It leaves the values to {@link org.dbunit.database.DatabaseConfigOverrides}, which checks
 * each against the type of its property when the test case sets the properties.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class DatabaseConfigPropertiesResolver
{
    /**
     * Resolves the {@link DatabaseConfig} properties.
     *
     * @param properties The declared entries; empty when none were declared.
     * @return The resolved properties, by long name; empty when none were declared.
     * @throws IllegalStateException If a property is declared twice, or a name is not a known
     *             {@link DatabaseConfig} property.
     */
    Properties resolve(final DbUnitProperty[] properties)
    {
        return fromInlineProperties(properties);
    }

    private Properties fromInlineProperties(final DbUnitProperty[] properties)
    {
        final Properties result = new Properties();
        for (final DbUnitProperty property : properties)
        {
            final String fullName = requireKnownDatabaseConfigProperty(property.name(),
                    "@DbUnitConfig.properties()");
            if (result.containsKey(fullName))
            {
                throw new IllegalStateException("@DbUnitConfig declares more than one"
                        + " @DbUnitProperty for the DatabaseConfig property '" + fullName
                        + "', the later one named '" + property.name() + "'; declare at most"
                        + " one per property.");
            }
            result.setProperty(fullName, property.value());
        }
        return result;
    }

    /**
     * Rejects a {@link DatabaseConfig} property name from {@code @DbUnitProperty} that
     * {@link DatabaseConfig} does not recognize in either its long
     * {@code http://www.dbunit.org/...} or short form. A typo would otherwise
     * be dropped silently by {@link DatabaseConfig#setPropertiesByString(Properties)}, which
     * only logs and moves on, leaving the test running with the misspelled property never
     * applied.
     *
     * @param name The configured property name.
     * @param source A description of what configured it, for the exception message.
     * @return The long name of the property {@code name} names.
     */
    private static String requireKnownDatabaseConfigProperty(final String name,
            final String source)
    {
        final ConfigProperty known = DatabaseConfig.findByNameOrShortName(name);
        if (known == null)
        {
            throw new IllegalStateException("'" + name + "' (configured via " + source
                    + ") is not a known dbUnit DatabaseConfig property name. Use its long"
                    + " 'http://www.dbunit.org/...' form or its short form - e.g. 'batchSize',"
                    + " 'caseSensitiveTableNames'; see DatabaseConfig.");
        }
        return known.getProperty();
    }
}
