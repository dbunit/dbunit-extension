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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
public class VerifyTableDefinitionCatalog {
    private final Map<String, VerifyTableDefinition> definitionsByTableName =
            new LinkedHashMap<>();
    private final Map<String, Class<?>> catalogClassByTableName = new LinkedHashMap<>();
    private final List<String> catalogClassNames = new ArrayList<>();

    /**
     * Reads and merges the given catalog classes.
     *
     * @param catalogClasses The catalog classes to read; each either implements
     *            {@link VerifyTableDefinitionsProvider} or exposes {@code public static
     *            final VerifyTableDefinition} fields.
     */
    public VerifyTableDefinitionCatalog(final Class<?>... catalogClasses) {
        for (final Class<?> catalogClass : catalogClasses) {
            catalogClassNames.add(catalogClass.getName());
            for (final VerifyTableDefinition definition : readDefinitions(catalogClass)) {
                addDefinition(catalogClass, definition);
            }
        }
    }

    private void addDefinition(final Class<?> catalogClass,
            final VerifyTableDefinition definition) {
        if (definition == null) {
            throw new IllegalStateException("Catalog class " + catalogClass.getName()
                    + " supplied a null VerifyTableDefinition.");
        }
        final String tableName = definition.getTableName();
        final Class<?> existingCatalogClass = catalogClassByTableName.get(tableName);
        if (existingCatalogClass != null) {
            throw new IllegalStateException("Table '" + tableName
                    + "' is defined in both " + existingCatalogClass.getName() + " and "
                    + catalogClass.getName()
                    + ". Each table name must be defined in exactly one catalog class.");
        }
        definitionsByTableName.put(tableName, definition);
        catalogClassByTableName.put(tableName, catalogClass);
    }

    private VerifyTableDefinition[] readDefinitions(final Class<?> catalogClass) {
        if (VerifyTableDefinitionsProvider.class.isAssignableFrom(catalogClass)) {
            final VerifyTableDefinitionsProvider provider =
                    (VerifyTableDefinitionsProvider) instantiate(catalogClass);
            final VerifyTableDefinition[] definitions = provider.getVerifyTableDefinitions();
            if (definitions == null) {
                throw new IllegalStateException("VerifyTableDefinitionsProvider "
                        + catalogClass.getName() + ", named by DbUnitConfig.verifyDefinitions()"
                        + " or DbUnitExpected.verifyDefinitions(), returned null from"
                        + " getVerifyTableDefinitions().");
            }
            return definitions;
        }
        return readConstants(catalogClass);
    }

    private VerifyTableDefinition[] readConstants(final Class<?> catalogClass) {
        final List<Field> fields = new ArrayList<>();
        for (final Field field : catalogClass.getFields()) {
            final int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && VerifyTableDefinition.class.equals(field.getType())) {
                fields.add(field);
            }
        }
        // Class#getFields() does not guarantee declaration order; sort by name so catalog
        // order is deterministic across JVMs rather than incidentally stable on most of them.
        fields.sort(Comparator.comparing(Field::getName));
        final List<VerifyTableDefinition> definitions = new ArrayList<>(fields.size());
        for (final Field field : fields) {
            definitions.add(readConstant(catalogClass, field));
        }
        return definitions.toArray(new VerifyTableDefinition[0]);
    }

    private VerifyTableDefinition readConstant(final Class<?> catalogClass, final Field field) {
        try {
            field.setAccessible(true);
            return (VerifyTableDefinition) field.get(null);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Field '" + field.getName() + "' on catalog class "
                    + catalogClass.getName() + " is not accessible.", e);
        }
    }

    private Object instantiate(final Class<?> catalogClass) {
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
    public VerifyTableDefinition[] select(final String... tableNames) {
        if (tableNames.length == 0) {
            return definitionsByTableName.values()
                    .toArray(new VerifyTableDefinition[0]);
        }
        final VerifyTableDefinition[] selected =
                new VerifyTableDefinition[tableNames.length];
        for (int i = 0; i < tableNames.length; i++) {
            selected[i] = select(tableNames[i]);
        }
        return selected;
    }

    private VerifyTableDefinition select(final String tableName) {
        final VerifyTableDefinition definition = definitionsByTableName.get(tableName);
        if (definition == null) {
            throw new IllegalStateException("Table '" + tableName
                    + "' not found in catalog class(es) " + catalogClassNames
                    + ". Available table names: " + definitionsByTableName.keySet() + ".");
        }
        return definition;
    }
}
