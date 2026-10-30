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
 * <p>Applying a value that does not suit its property would read a boolean such as {@code yes}
 * as {@code false}, which turns the feature off without a word, and fail on an integer that is
 * none with a bare exception that names no property. So the constructor checks each value
 * against the type of the property it names and rejects one that does not suit it. It does not
 * check a name {@link DatabaseConfig} does not know, as
 * {@link DatabaseConfig#setPropertiesByString(Properties)} leaves it out.
 *
 * <p>Not thread-safe; a test's steps run sequentially on one thread.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class DatabaseConfigOverrides
{
    private final Properties properties;
    private final Map<DatabaseConfig, Map<String, Object>> replacedValues = new IdentityHashMap<>();

    /**
     * Creates overrides for the given values.
     *
     * @param properties The property name and value pairs to apply, with names in the long or
     *            short forms {@link DatabaseConfig#setPropertiesByString(Properties)} accepts;
     *            copied, so later changes to it have no effect.
     * @throws IllegalArgumentException If a value does not suit the type of its property: a
     *             boolean other than {@code true} or {@code false}, in any case, an integer that
     *             is no number, or a class name that names no class dbUnit can load or a class
     *             of another type than the property's.
     */
    public DatabaseConfigOverrides(final Properties properties)
    {
        this.properties = new Properties();
        this.properties.putAll(properties);
        for (final String name : this.properties.stringPropertyNames())
        {
            final String value = this.properties.getProperty(name);
            requireValueSuitsItsProperty(name, value);
        }
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
     * @throws DatabaseUnitException If a value names a class that dbUnit cannot load or
     *             instantiate.
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

    private static void requireValueSuitsItsProperty(final String name, final String value)
    {
        final ConfigProperty property = DatabaseConfig.findByNameOrShortName(name);
        if (property == null)
        {
            return;
        }

        final Class<?> type = property.getPropertyType();
        if (type == String.class || type == String[].class)
        {
            return;
        }
        if (type == Boolean.class)
        {
            requireBoolean(property, name, value);
            return;
        }
        if (type == Integer.class)
        {
            requireInteger(property, name, value);
            return;
        }
        requireLoadableClassOfType(property, name, value, type);
    }

    private static void requireBoolean(final ConfigProperty property, final String name,
            final String value)
    {
        final boolean isTrue = "true".equalsIgnoreCase(value);
        final boolean isFalse = "false".equalsIgnoreCase(value);
        if (isTrue || isFalse)
        {
            return;
        }
        throw invalidValue(property, name, value, "true or false", null);
    }

    private static void requireInteger(final ConfigProperty property, final String name,
            final String value)
    {
        try
        {
            Integer.valueOf(value);
        } catch (final NumberFormatException e)
        {
            throw invalidValue(property, name, value, "an integer", e);
        }
    }

    /**
     * Requires the class a value names to load, through the class loader that
     * {@link DatabaseConfig#setPropertiesByString(Properties)} loads it through, and to be of the
     * type of the property. Does not instantiate it: applying the value does that.
     */
    private static void requireLoadableClassOfType(final ConfigProperty property,
            final String name, final String value, final Class<?> type)
    {
        final String requirement = "the name of a class that can be loaded and implements "
                + type.getName();
        final Class<?> loaded;
        try
        {
            loaded = Class.forName(value, false, DatabaseConfig.class.getClassLoader());
        } catch (final ClassNotFoundException | LinkageError e)
        {
            throw invalidValue(property, name, value, requirement, e);
        }
        if (!type.isAssignableFrom(loaded))
        {
            throw invalidValue(property, name, value, requirement, null);
        }
    }

    private static IllegalArgumentException invalidValue(final ConfigProperty property,
            final String name, final String value, final String requirement, final Throwable cause)
    {
        final String longName = property.getProperty();
        final String described;
        if (name.equals(longName))
        {
            described = "'" + name + "'";
        } else
        {
            described = "'" + name + "' (" + longName + ")";
        }
        final String message = "The value '" + value + "' of the DatabaseConfig property "
                + described + " must be " + requirement + ".";
        return new IllegalArgumentException(message, cause);
    }

    private void rememberReplacedValue(final DatabaseConfig config, final String name,
            final Map<String, Object> replacedByName)
    {
        final ConfigProperty known = DatabaseConfig.findByNameOrShortName(name);
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
