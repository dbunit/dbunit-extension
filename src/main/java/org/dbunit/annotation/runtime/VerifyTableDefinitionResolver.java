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
import java.util.Optional;
import java.util.Set;

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
 * {@code verify()} and either catalog form, or the same table twice, is rejected. So is a
 * {@code @DbUnitExpected} that names no expected dataset, whatever else it declares, since the
 * tables it verifies are compared to that dataset.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class VerifyTableDefinitionResolver
{
    private static final ClassValue<ValueComparer> COMPARERS = new ClassValue<ValueComparer>()
    {
        @Override
        @SuppressWarnings("unchecked")
        protected ValueComparer computeValue(final Class<?> comparerClass)
        {
            return newComparerInstance((Class<? extends ValueComparer>) comparerClass);
        }
    };

    /**
     * Resolves the verification spec the annotations spell out, leaving the one form that needs
     * a data file loader - the default of one definition per table in the expected datasets -
     * to {@link #deriveFromExpectedDatasets(DataFileLoader, String[])}, so the loader can be the
     * one the test case that will compare those datasets actually uses.
     *
     * @param config The resolved {@code @DbUnitConfig}, or {@code null} if absent.
     * @param expected The resolved {@code @DbUnitExpected} (never {@code null} - this runs only
     *            when it is present).
     * @param expectedDataFiles The resolved expected dataset paths.
     * @return The table definitions to verify, or empty when the annotations declare none and
     *         they are to be derived from the expected datasets.
     * @throws IllegalStateException If no expected dataset is named, or the spec is
     *             contradictory.
     */
    Optional<VerifyTableDefinition[]> resolveDeclared(final DbUnitConfig config,
            final DbUnitExpected expected, final String[] expectedDataFiles)
    {
        if (expectedDataFiles.length == 0)
        {
            throw new IllegalStateException("@DbUnitExpected names no expected dataset, so"
                    + " there is nothing to verify the database against: verify(),"
                    + " verifyTables() and verifyDefinitions() only choose which tables of that"
                    + " dataset are compared. Name the dataset with value() or provider(), or"
                    + " drop @DbUnitExpected for a test that verifies nothing.");
        }
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
            return Optional.of(
                    VerifyTableDefinitionCatalog.forClasses(catalogClasses).select(verifyTables));
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
            return Optional.of(definitions);
        }
        if (verifyTables.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verifyTables.length];
            for (int i = 0; i < verifyTables.length; i++)
            {
                definitions[i] = new VerifyTableDefinition(verifyTables[i], (String[]) null);
            }
            return Optional.of(definitions);
        }
        return Optional.empty();
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
     * Derives one default definition per table found in {@code expectedDataFiles}, read with
     * {@code dataFileLoader}. Parses the files a second time here, just to enumerate table
     * names - {@code DefaultPrepAndExpectedTestCase#configureTest()} parses the same files
     * again later for the real verification. Left as-is: avoiding the second parse needs a real
     * change to the boundary between resolving a configuration and the
     * {@code PrepAndExpectedTestCase} that consumes it, not a small tweak - not worth it for
     * what is normally a small test fixture file.
     *
     * <p>Takes the loader as an argument because it must be the one that test case compares the
     * files with, which can differ from the {@code @DbUnitConfig} one when the test case is
     * injected with a loader of its own.
     *
     * @param dataFileLoader The loader to read the expected datasets with.
     * @param expectedDataFiles The resolved expected dataset paths.
     * @return One default definition per table, in first-seen order.
     */
    VerifyTableDefinition[] deriveFromExpectedDatasets(final DataFileLoader dataFileLoader,
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
     * Caches a comparer instance for as long as its class lives - the same once-and-reused
     * caching {@link VerifyTableDefinitionCatalog#forClasses} already applies to a catalog
     * combination, for the same reason: this resolves fresh on every test method, and a suite
     * naming the same comparer class from many methods would otherwise reflectively construct a
     * new, functionally-identical instance every time. The instance is held by the class
     * itself, through a {@link ClassValue}, so it goes away with the class and its class loader
     * rather than keeping them alive. See {@link DbUnitColumnComparer#comparer()} for the
     * resulting purity requirement.
     */
    private static ValueComparer instantiateComparer(
            final Class<? extends ValueComparer> comparerClass)
    {
        return COMPARERS.get(comparerClass);
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
