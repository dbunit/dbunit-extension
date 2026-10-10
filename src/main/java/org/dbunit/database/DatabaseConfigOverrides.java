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

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig.ConfigProperty;

/**
 * Property values, given as strings, to apply to {@link DatabaseConfig} instances for the
 * length of one test, and the means to put back what they replaced.
 *
 * <p>A connection that outlives a test - a fixed connection handed to a tester, one cached by a
 * {@link CachingConnectionProvider}, or one a test case keeps with
 * {@code closeConnectionAfterTest} off - carries its {@link DatabaseConfig} into whatever uses
 * it next. Applying a test's values with {@link DatabaseConfig#setPropertiesByString(Properties)}
 * alone leaves them there; {@link #applyTo(DatabaseConfig)} remembers the value each one
 * replaced, and {@link #restore()} puts those back.
 *
 * <p>Only the first value seen for a property on a given {@link DatabaseConfig} is remembered, so
 * applying the same values to a connection several times in one test, as the lifecycle does each
 * time it retrieves the connection, still restores what was there before the first.
 *
 * <p>Not thread-safe; a test's steps run sequentially on one thread.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class DatabaseConfigOverrides
{
    private final Properties properties;
    private final Map<DatabaseConfig, Map<String, Object>> replacedValues = new IdentityHashMap<>();

    /**
     * Creates overrides for the given values.
     *
     * @param properties The property name and value pairs to apply, with names in the long or
     *            short forms {@link DatabaseConfig#setPropertiesByString(Properties)} accepts;
     *            copied, so later changes to it have no effect.
     */
    public DatabaseConfigOverrides(final Properties properties)
    {
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    /**
     * Returns whether there are no values to apply.
     *
     * @return {@code true} if {@link #applyTo(DatabaseConfig)} would change nothing.
     */
    public boolean isEmpty()
    {
        return properties.isEmpty();
    }

    /**
     * Returns whether {@link #applyTo(DatabaseConfig)} has changed the given configuration since
     * the last {@link #restore()}.
     *
     * @param config The configuration to ask about.
     * @return {@code true} if its values are in force and {@link #restore()} would put the
     *         replaced ones back.
     */
    public boolean hasAppliedTo(final DatabaseConfig config)
    {
        return replacedValues.containsKey(config);
    }

    /**
     * Applies the values to the given configuration, remembering each one's previous value for
     * {@link #restore()}. Does nothing when there are no values. A failure part way leaves the
     * values already applied in force, and {@link #restore()} still puts them back.
     *
     * @param config The configuration to apply the values to.
     * @throws DatabaseUnitException If a value cannot be converted to its property's type.
     */
    public void applyTo(final DatabaseConfig config) throws DatabaseUnitException
    {
        if (isEmpty())
        {
            return;
        }

        final Map<String, Object> replacedByName = replacedValues.computeIfAbsent(config,
                key -> new LinkedHashMap<>());
        for (final String name : properties.stringPropertyNames())
        {
            rememberReplacedValue(config, name, replacedByName);
        }
        config.setPropertiesByString(properties);
    }

    private void rememberReplacedValue(final DatabaseConfig config, final String name,
            final Map<String, Object> replacedByName)
    {
        ConfigProperty known = DatabaseConfig.findByName(name);
        if (known == null)
        {
            known = DatabaseConfig.findByShortName(name);
        }
        if (known == null)
        {
            return;
        }

        final String fullName = known.getProperty();
        if (replacedByName.containsKey(fullName))
        {
            return;
        }

        final Object replacedValue = config.getProperty(fullName);
        replacedByName.put(fullName, replacedValue);
    }

    /**
     * Puts back, on every configuration {@link #applyTo(DatabaseConfig)} changed, the values it
     * replaced. Does nothing when nothing was applied or it was already restored.
     */
    public void restore()
    {
        for (final Map.Entry<DatabaseConfig, Map<String, Object>> entry : replacedValues
                .entrySet())
        {
            final DatabaseConfig config = entry.getKey();
            for (final Map.Entry<String, Object> replaced : entry.getValue().entrySet())
            {
                config.setProperty(replaced.getKey(), replaced.getValue());
            }
        }
        replacedValues.clear();
    }
}
