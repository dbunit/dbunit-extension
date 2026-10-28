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

import java.util.HashMap;
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
 * {@link DbUnitVerifyTable}s or its {@code verifyTables()} names, with the rules of the
 * {@link DbUnitConfig#verifyDefinitions()} class-level catalogs - into the
 * {@link VerifyTableDefinition}[] {@link AnnotatedTestConfiguration} carries.
 *
 * <p>Precedence, first match wins: {@code verify()}, then {@code verifyTables()}, then one
 * definition per table found in the expected datasets. The tables verified are the ones named
 * or the ones the expected datasets hold; a catalog only supplies each table's rules, and a
 * table it does not define gets a default definition. Declaring {@code verify()} together with
 * {@code verifyTables()}, or the same table twice, is rejected. So is a {@code @DbUnitExpected}
 * that names no expected dataset, whatever else it declares, since the tables it verifies are
 * compared to that dataset.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class VerifyTableDefinitionResolver
{
    private static final String COMPARER_CONSTRUCTOR_HINT = "It is likely a configured instance"
            + " built with constructor arguments, which an annotation cannot express. Either"
            + " declare it as a VerifyTableDefinition constant in a catalog class, or name a"
            + " subclass with a public no-arg constructor that passes the arguments to super(...).";

    private static final ClassValue<ValueComparer> COMPARERS = new ClassValue<ValueComparer>()
    {
        /**
         * {@inheritDoc}
         * <p>
         * Creates the comparer the first time a caller asks for the class, and the
         * {@link ClassValue} keeps it from then on.
         */
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
     * @param classLevelCatalogs The catalog classes {@code @DbUnitConfig(verifyDefinitions)}
     *            declares, which supply the rules of the tables {@code verifyTables()} names;
     *            empty when none.
     * @param expected The resolved {@code @DbUnitExpected} (never {@code null} - this runs only
     *            when it is present).
     * @param expectedDataFiles The resolved expected dataset paths.
     * @return The table definitions to verify, or empty when the annotations declare none and
     *         they are to be derived from the expected datasets.
     * @throws IllegalStateException If no expected dataset is named, or the spec is
     *             contradictory.
     */
    Optional<VerifyTableDefinition[]> resolveDeclared(final Class<?>[] classLevelCatalogs,
            final DbUnitExpected expected, final String[] expectedDataFiles)
    {
        if (expectedDataFiles.length == 0)
        {
            throw new IllegalStateException("@DbUnitExpected names no expected dataset, so"
                    + " there is nothing to verify the database against: verify(),"
                    + " and verifyTables() only choose which tables of that dataset are"
                    + " compared. Name the dataset with value(), or drop"
                    + " @DbUnitExpected for a test that verifies nothing.");
        }
        final DbUnitVerifyTable[] verify = expected.verify();
        final String[] verifyTables = expected.verifyTables();

        if (verify.length > 0 && verifyTables.length > 0)
        {
            throw new IllegalStateException("@DbUnitExpected sets both verify() and"
                    + " verifyTables(); set only one. verify() declares the rules of its"
                    + " tables, verifyTables() takes them from a catalog.");
        }
        requireNoDuplicateTableNames(verifyTables);
        if (verify.length > 0)
        {
            final VerifyTableDefinition[] definitions =
                    new VerifyTableDefinition[verify.length];
            final Set<String> tableNames = new LinkedHashSet<>(verify.length);
            for (int i = 0; i < verify.length; i++)
            {
                if (!tableNames.add(TableNameKey.of(verify[i].value())))
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
            return Optional.of(VerifyTableDefinitionCatalog.forClasses(classLevelCatalogs)
                    .select(verifyTables));
        }
        return Optional.empty();
    }

    /**
     * Returns the definitions of the class-level catalogs, the rules of the tables derived from
     * the expected datasets - see
     * {@link #deriveFromExpectedDatasets(DataFileLoader, String[], VerifyTableDefinition[])}.
     *
     * @param classLevelCatalogs The catalog classes {@code @DbUnitConfig(verifyDefinitions)}
     *            declares; empty when none.
     * @return Every definition the catalogs hold; empty when there are none.
     */
    VerifyTableDefinition[] catalogDefinitions(final Class<?>[] classLevelCatalogs)
    {
        return VerifyTableDefinitionCatalog.forClasses(classLevelCatalogs).select();
    }

    private void requireNoDuplicateTableNames(final String[] verifyTables)
    {
        final Set<String> tableNames = new LinkedHashSet<>(verifyTables.length);
        for (final String tableName : verifyTables)
        {
            if (!tableNames.add(TableNameKey.of(tableName)))
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
     * Derives one definition per table found in {@code expectedDataFiles}, read with
     * {@code dataFileLoader}: the catalog's for a table it defines, a default one for the
     * others. Parses the files here, just to enumerate table names, for a
     * {@code PrepAndExpectedTestCase} that cannot derive them from the expected dataset it
     * loads itself, which only a {@code DefaultPrepAndExpectedTestCase} does - see
     * {@code PrepAndExpectedTestData#verifyingExpectedTables(String[], String[],
     * VerifyTableDefinition[])}; its {@code configureTest()} parses the same files again for the
     * real verification.
     *
     * <p>Takes the loader as an argument because it must be the one that test case compares the
     * files with, which can differ from the {@code @DbUnitConfig} one when the test case is
     * injected with a loader of its own.
     *
     * @param dataFileLoader The loader to read the expected datasets with.
     * @param expectedDataFiles The resolved expected dataset paths.
     * @param catalogDefinitions The definitions of the class-level catalogs, the rules of the
     *            tables they define; the other tables get a default definition.
     * @return One definition per table, in first-seen order.
     */
    VerifyTableDefinition[] deriveFromExpectedDatasets(final DataFileLoader dataFileLoader,
            final String[] expectedDataFiles, final VerifyTableDefinition[] catalogDefinitions)
    {
        final Map<String, VerifyTableDefinition> catalogByTableNameKey = new HashMap<>();
        for (final VerifyTableDefinition definition : catalogDefinitions)
        {
            catalogByTableNameKey.put(TableNameKey.of(definition.getTableName()), definition);
        }
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
            final VerifyTableDefinition inCatalog =
                    catalogByTableNameKey.get(TableNameKey.of(tableName));
            if (inCatalog == null)
            {
                definitions[i++] = new VerifyTableDefinition(tableName, (String[]) null);
            } else
            {
                definitions[i++] = inCatalog;
            }
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
        return new ReflectiveInstantiation().instantiate(comparerClass,
                "DbUnitColumnComparer.comparer", COMPARER_CONSTRUCTOR_HINT);
    }
}
