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

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitColumnComparer;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * Resolves {@code @DbUnitExpected}'s verification spec - its inline {@code verify()}
 * {@link DbUnitVerifyTable}s, its {@code verifyTables()} names, its {@code verifyDefinitions()}
 * catalog classes, or {@link DbUnitConfig#verifyDefinitions()} class-level catalogs - into the
 * {@link VerifyTableDefinition}[] {@link AnnotatedTestConfiguration} carries.
 *
 * <p>Precedence, first match wins: {@code @DbUnitExpected.verifyDefinitions()}, then
 * {@code verify()}, then {@code @DbUnitConfig.verifyDefinitions()}, then bare
 * {@code verifyTables()}, then one definition per table found in the expected datasets.
 * {@code verifyTables()} narrows a catalog rather than conflicting with it; declaring both
 * {@code verify()} and either catalog form, or the same table twice, is rejected.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class VerifyTableDefinitionResolver
{
    private static final Map<Class<? extends ValueComparer>, ValueComparer> COMPARER_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Resolves the verification spec.
     *
     * @param config The resolved {@code @DbUnitConfig}, or {@code null} if absent.
     * @param expected The resolved {@code @DbUnitExpected} (never {@code null} - this runs only
     *            when it is present).
     * @param dataFileLoader The loader used to enumerate expected-dataset table names for the
     *            default case.
     * @param expectedDataFiles The resolved expected dataset paths.
     * @return The table definitions to verify.
     */
    VerifyTableDefinition[] resolve(final DbUnitConfig config, final DbUnitExpected expected,
            final DataFileLoader dataFileLoader, final String[] expectedDataFiles)
    {
        final Class<?>[] classLevelCatalogs =
                config == null ? new Class<?>[0] : config.verifyDefinitions();
        final DbUnitVerifyTable[] verify = expected.verify();
        final String[] verifyTables = expected.verifyTables();

        ProvidedAttribute.rejectBothSet(
                verify.length > 0 && expected.verifyDefinitions().length > 0, "@DbUnitExpected",
                "verify()", "verifyDefinitions()", "See VerifyTableDefinitionsProvider.");
        ProvidedAttribute.rejectBothSet(verify.length > 0 && verifyTables.length > 0,
                "@DbUnitExpected", "verify()", "verifyTables()",
                "verifyTables() only narrows a catalog.");
        requireNoDuplicateTableNames(verifyTables);
        final Class<?>[] catalogClasses;
        if (expected.verifyDefinitions().length > 0)
        {
            catalogClasses = expected.verifyDefinitions();
        } else if (verify.length > 0)
        {
            catalogClasses = new Class<?>[0];
        } else
        {
            catalogClasses = classLevelCatalogs;
        }
        if (catalogClasses.length > 0)
        {
            return VerifyTableDefinitionCatalog.forClasses(catalogClasses).select(verifyTables);
        }
        if (verify.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verify.length];
            final Set<String> tableNames = new LinkedHashSet<>(verify.length);
            for (int i = 0; i < verify.length; i++)
            {
                if (!tableNames.add(verify[i].value()))
                {
                    throw new IllegalStateException("@DbUnitExpected declares more than one"
                            + " @DbUnitVerifyTable entry for table '" + verify[i].value()
                            + "'; declare at most one per table.");
                }
                definitions[i] = toVerifyTableDefinition(verify[i]);
            }
            return definitions;
        }
        if (verifyTables.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verifyTables.length];
            for (int i = 0; i < verifyTables.length; i++)
            {
                definitions[i] = new VerifyTableDefinition(verifyTables[i], (String[]) null);
            }
            return definitions;
        }
        return defaultPerExpectedTable(dataFileLoader, expectedDataFiles);
    }

    private void requireNoDuplicateTableNames(final String[] verifyTables)
    {
        final Set<String> tableNames = new LinkedHashSet<>(verifyTables.length);
        for (final String tableName : verifyTables)
        {
            if (!tableNames.add(tableName))
            {
                throw new IllegalStateException("@DbUnitExpected declares more than one"
                        + " verifyTables() entry for table '" + tableName
                        + "'; declare at most one per table.");
            }
        }
    }

    private VerifyTableDefinition toVerifyTableDefinition(final DbUnitVerifyTable v)
    {
        final String[] include = v.include().length == 0 ? null : v.include();
        final ValueComparer defaultComparer = v.defaultComparer() == ValueComparer.class ? null
                : instantiateComparer(v.defaultComparer());
        final Map<String, ValueComparer> columnComparers =
                new LinkedHashMap<>(v.columnComparers().length);
        for (final DbUnitColumnComparer c : v.columnComparers())
        {
            if (columnComparers.containsKey(c.column()))
            {
                throw new IllegalStateException("@DbUnitVerifyTable(\"" + v.value()
                        + "\") declares more than one @DbUnitColumnComparer for column '"
                        + c.column() + "'; declare at most one per column.");
            }
            columnComparers.put(c.column(), instantiateComparer(c.comparer()));
        }
        return new VerifyTableDefinition(v.value(), v.exclude(), include, defaultComparer,
                columnComparers, v.sortOnFilteredColumnsOnly());
    }

    /**
     * Parses {@code expectedDataFiles} a second time here, just to enumerate table names -
     * {@code DefaultPrepAndExpectedTestCase#configureTest()} parses the same files again later
     * for the real verification. Left as-is: resolving a configuration is JUnit-free and runs
     * before any {@code PrepAndExpectedTestCase} exists to hand a parsed result to (or receive
     * one from), so avoiding the second parse needs a real change to that boundary, not a small
     * tweak - not worth it for what is normally a small test fixture file.
     */
    private VerifyTableDefinition[] defaultPerExpectedTable(final DataFileLoader dataFileLoader,
            final String[] expectedDataFiles)
    {
        final Set<String> tableNames = new LinkedHashSet<>();
        for (final String path : expectedDataFiles)
        {
            final IDataSet dataSet = dataFileLoader.load(path);
            try
            {
                for (final String tableName : dataSet.getTableNames())
                {
                    tableNames.add(tableName);
                }
            } catch (final DataSetException e)
            {
                throw new IllegalStateException(
                        "Failed to read table names from expected dataset '" + path + "'.", e);
            }
        }
        final VerifyTableDefinition[] definitions =
                new VerifyTableDefinition[tableNames.size()];
        int i = 0;
        for (final String tableName : tableNames)
        {
            definitions[i++] = new VerifyTableDefinition(tableName, (String[]) null);
        }
        return definitions;
    }

    /**
     * Caches a comparer instance, for the JVM's entire lifetime, keyed by class - the same
     * once-and-reused-forever caching {@link VerifyTableDefinitionCatalog#forClasses} already
     * applies to a catalog combination, for the same reason: this resolves fresh on every test
     * method, and a suite naming the same comparer class from many methods would otherwise
     * reflectively construct a new, functionally-identical instance every time. See
     * {@link DbUnitColumnComparer#comparer()} for the resulting purity requirement.
     */
    private static ValueComparer instantiateComparer(
            final Class<? extends ValueComparer> comparerClass)
    {
        return COMPARER_CACHE.computeIfAbsent(comparerClass,
                VerifyTableDefinitionResolver::newComparerInstance);
    }

    private static ValueComparer newComparerInstance(
            final Class<? extends ValueComparer> comparerClass)
    {
        try
        {
            return ReflectiveInstantiation.newInstance(comparerClass);
        } catch (final InvocationTargetException e)
        {
            throw new IllegalStateException("ValueComparer " + comparerClass.getName()
                    + " threw from its no-arg constructor.", e.getCause());
        } catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException("ValueComparer " + comparerClass.getName()
                    + " has no accessible no-arg constructor - it is likely a configured"
                    + " instance built with constructor arguments, which an annotation cannot"
                    + " express. Declare it as a VerifyTableDefinition constant in a catalog"
                    + " class instead; see VerifyTableDefinitionsProvider.", e);
        }
    }
}
