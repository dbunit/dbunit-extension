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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.VerifyTableDefinitionsProvider;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;

/**
 * Reads one or more catalog classes named by {@link DbUnitConfig#verifyDefinitions()} or
 * {@link DbUnitExpected#verifyDefinitions()} and indexes their {@link VerifyTableDefinition}s
 * by table name, for {@link DbUnitExpected#verifyTables()} to select from.
 *
 * <p>Each catalog class is read one of two ways: if it implements
 * {@link VerifyTableDefinitionsProvider}, it is reflectively instantiated with its no-arg
 * constructor and asked for its definitions; otherwise its {@code public static final
 * VerifyTableDefinition} fields are read directly. The same table name appearing in two
 * catalog classes is rejected rather than silently resolved by declaration order.
 *
 * <p>Not intended for direct use by test code; this is machinery consumed by
 * {@link AnnotatedTestConfiguration}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class VerifyTableDefinitionCatalog
{
    private static final Map<List<Class<?>>, VerifyTableDefinitionCatalog> CACHE =
            new ConcurrentHashMap<>();

    private final Map<String, VerifyTableDefinition> definitionsByTableName =
            new LinkedHashMap<>();
    private final Map<String, Class<?>> catalogClassByTableName = new LinkedHashMap<>();
    private final List<String> catalogClassNames = new ArrayList<>();

    /**
     * Returns the catalog for the given classes, reading and merging them only the first time
     * this exact combination is asked for - resolution runs once per test method, and a suite
     * naming the same catalog class(es) from many test methods would otherwise re-reflect them
     * every time, for a result that can never change within one JVM run.
     *
     * <p>The cache key is order-sensitive: {@code forClasses(A, B)} and {@code forClasses(B, A)}
     * are different combinations, each cached (and merged) separately, even though the same
     * table name colliding between {@code A} and {@code B} would reject both the same way
     * regardless of order. Deliberately simple rather than order-insensitive, since normalizing
     * the key would only save reflection work for the unusual case of a suite naming the same
     * catalog classes in more than one order.
     *
     * @param catalogClasses The catalog classes to read; see
     *            {@link #VerifyTableDefinitionCatalog(Class[])}.
     * @return The (possibly cached) catalog.
     */
    static VerifyTableDefinitionCatalog forClasses(final Class<?>... catalogClasses)
    {
        return CACHE.computeIfAbsent(Arrays.asList(catalogClasses),
                classes -> new VerifyTableDefinitionCatalog(
                        classes.toArray(new Class<?>[0])));
    }

    /**
     * Reads and merges the given catalog classes.
     *
     * @param catalogClasses The catalog classes to read; each either implements
     *            {@link VerifyTableDefinitionsProvider} or exposes {@code public static
     *            final VerifyTableDefinition} fields.
     */
    public VerifyTableDefinitionCatalog(final Class<?>... catalogClasses)
    {
        for (final Class<?> catalogClass : catalogClasses)
        {
            catalogClassNames.add(catalogClass.getName());
            for (final VerifyTableDefinition definition : readDefinitions(catalogClass))
            {
                addDefinition(catalogClass, definition);
            }
        }
    }

    private void addDefinition(final Class<?> catalogClass,
            final VerifyTableDefinition definition)
    {
        if (definition == null)
        {
            throw new IllegalStateException("Catalog class " + catalogClass.getName()
                    + " supplied a null VerifyTableDefinition.");
        }
        final String tableName = definition.getTableName();
        final Class<?> existingCatalogClass = catalogClassByTableName.get(tableName);
        if (existingCatalogClass != null)
        {
            final String message = existingCatalogClass == catalogClass
                    ? "Table '" + tableName + "' is defined more than once in "
                            + catalogClass.getName()
                            + ". Each table name must be defined in exactly one catalog class."
                    : "Table '" + tableName + "' is defined in both "
                            + existingCatalogClass.getName() + " and " + catalogClass.getName()
                            + ". Each table name must be defined in exactly one catalog class.";
            throw new IllegalStateException(message);
        }
        definitionsByTableName.put(tableName, definition);
        catalogClassByTableName.put(tableName, catalogClass);
    }

    private VerifyTableDefinition[] readDefinitions(final Class<?> catalogClass)
    {
        if (VerifyTableDefinitionsProvider.class.isAssignableFrom(catalogClass))
        {
            final VerifyTableDefinitionsProvider provider =
                    (VerifyTableDefinitionsProvider) instantiate(catalogClass);
            final VerifyTableDefinition[] definitions = provider.getVerifyTableDefinitions();
            if (definitions == null)
            {
                throw new IllegalStateException("VerifyTableDefinitionsProvider "
                        + catalogClass.getName() + ", named by DbUnitConfig.verifyDefinitions()"
                        + " or DbUnitExpected.verifyDefinitions(), returned null from"
                        + " getVerifyTableDefinitions().");
            }
            return definitions;
        }
        return readConstants(catalogClass);
    }

    private VerifyTableDefinition[] readConstants(final Class<?> catalogClass)
    {
        final List<Field> fields = new ArrayList<>();
        // getDeclaredFields(), not getFields(): a catalog class extending some unrelated
        // superclass must not silently pull in that superclass's own public static final
        // VerifyTableDefinition constants, if it happens to declare any, as part of this
        // catalog - only fields this class declares itself are the catalog's contents.
        for (final Field field : catalogClass.getDeclaredFields())
        {
            final int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && VerifyTableDefinition.class.isAssignableFrom(field.getType()))
            {
                fields.add(field);
            }
        }
        // Class#getDeclaredFields() does not guarantee declaration order either; sort by name
        // so catalog order is deterministic across JVMs rather than incidentally stable on most
        // of them.
        fields.sort(Comparator.comparing(Field::getName));
        final List<VerifyTableDefinition> definitions = new ArrayList<>(fields.size());
        for (final Field field : fields)
        {
            definitions.add(readConstant(catalogClass, field));
        }
        return definitions.toArray(new VerifyTableDefinition[0]);
    }

    private VerifyTableDefinition readConstant(final Class<?> catalogClass, final Field field)
    {
        try
        {
            field.setAccessible(true);
            return (VerifyTableDefinition) field.get(null);
        } catch (final IllegalAccessException e)
        {
            throw new IllegalStateException("Field '" + field.getName() + "' on catalog class "
                    + catalogClass.getName() + " is not accessible.", e);
        }
    }

    private Object instantiate(final Class<?> catalogClass)
    {
        return ReflectiveInstantiation.instantiate(catalogClass,
                "VerifyTableDefinitionsProvider catalog");
    }

    /**
     * Selects the named tables' definitions, or every definition in the catalog when no names
     * are given.
     *
     * @param tableNames The table names to select; empty selects every definition in the
     *            catalog.
     * @return The selected definitions, in the order named (or catalog order, when every
     *         definition is selected).
     * @throws IllegalStateException When a named table is not present in the catalog.
     */
    public VerifyTableDefinition[] select(final String... tableNames)
    {
        if (tableNames.length == 0)
        {
            return definitionsByTableName.values()
                    .toArray(new VerifyTableDefinition[0]);
        }
        final VerifyTableDefinition[] selected =
                new VerifyTableDefinition[tableNames.length];
        for (int i = 0; i < tableNames.length; i++)
        {
            selected[i] = select(tableNames[i]);
        }
        return selected;
    }

    private VerifyTableDefinition select(final String tableName)
    {
        final VerifyTableDefinition definition = definitionsByTableName.get(tableName);
        if (definition == null)
        {
            throw new IllegalStateException("Table '" + tableName
                    + "' not found in catalog class(es) " + catalogClassNames
                    + ". Available table names: " + definitionsByTableName.keySet() + ".");
        }
        return definition;
    }
}
