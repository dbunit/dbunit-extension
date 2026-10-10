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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.DataFileLoader;
import org.junit.jupiter.api.Test;

class VerifyTableDefinitionResolverTest
{
    private final VerifyTableDefinitionResolver resolver = new VerifyTableDefinitionResolver();

    @Test
    void testResolveDeclared_verifyTablesNamesOnly_buildsOneDefaultDefinitionPerName()
    {
        final DbUnitExpected expected =
                WithVerifyTables.class.getAnnotation(DbUnitExpected.class);

        final VerifyTableDefinition[] definitions =
                resolver.resolveDeclared(null, expected, new String[] {"expected.xml"}).get();

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testResolveDeclared_duplicateVerifyTablesName_throws()
    {
        final DbUnitExpected expected =
                WithDuplicateVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolver.resolveDeclared(null, expected,
                new String[] {"expected.xml"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testResolveDeclared_noVerifySpecWithExpectedDataset_declaresNothingSoTablesAreDerivedLater()
    {
        final DbUnitExpected expected = Bare.class.getAnnotation(DbUnitExpected.class);

        assertThat(resolver.resolveDeclared(null, expected, new String[] {"expected.xml"}))
                .as("With no verify spec, nothing is declared and no loader is touched yet:"
                        + " the tables are derived later, with the loader that will compare"
                        + " them.")
                .isEmpty();
    }

    @Test
    void testDeriveFromExpectedDatasets_datasetWithTables_buildsOneDefinitionPerTable()
            throws Exception
    {
        final DataFileLoader loader = mock(DataFileLoader.class);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT", "LEDGER"});
        when(loader.load("expected.xml")).thenReturn(dataSet);

        final VerifyTableDefinition[] definitions =
                resolver.deriveFromExpectedDatasets(loader, new String[] {"expected.xml"});

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testDeriveFromExpectedDatasets_tableEnumerationFails_throwsNamingThePath()
            throws Exception
    {
        final DataFileLoader loader = mock(DataFileLoader.class);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenThrow(new DataSetException("corrupt"));
        when(loader.load("expected.xml")).thenReturn(dataSet);

        assertThatThrownBy(() -> resolver.deriveFromExpectedDatasets(loader,
                new String[] {"expected.xml"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected.xml")
                .hasCauseInstanceOf(DataSetException.class);
    }

    @Test
    void testResolveDeclared_noExpectedDatasetAndNoVerifySpec_throwsBecauseNothingWouldBeVerified()
    {
        final DbUnitExpected expected = NoDatasetNoSpec.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolver.resolveDeclared(null, expected, new String[0]))
                .as("A @DbUnitExpected naming no dataset and no tables has nothing to verify"
                        + " against, which must be rejected rather than silently pass.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names no expected dataset");
    }

    @Test
    void testResolveDeclared_noExpectedDatasetButVerifyTablesNamed_throwsBecauseTheTablesHaveNothingToBeComparedTo()
    {
        final DbUnitExpected expected =
                VerifyTablesWithoutDataset.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolver.resolveDeclared(null, expected, new String[0]))
                .as("Naming tables to verify does not make a missing expected dataset valid:"
                        + " the tables are compared to that dataset, and verifying one that is"
                        + " not in it always fails afterward with an obscure error.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names no expected dataset");
    }

    @Test
    void testResolveDeclared_noExpectedDatasetButInlineVerify_throwsBecauseTheTablesHaveNothingToBeComparedTo()
    {
        final DbUnitExpected expected =
                InlineVerifyWithoutDataset.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolver.resolveDeclared(null, expected, new String[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names no expected dataset");
    }

    @Test
    void testResolveDeclared_comparerClassFromAnotherClassLoader_doesNotKeepThatLoaderAlive()
            throws Exception
    {
        IsolatedClassLoader loader = new IsolatedClassLoader(DeclaresIsolatedComparer.class,
                IsolatedComparer.class);
        final WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
        Class<?> declaring = loader.loadClass(DeclaresIsolatedComparer.class.getName());
        DbUnitExpected expected = declaring.getAnnotation(DbUnitExpected.class);

        resolver.resolveDeclared(null, expected, new String[] {"expected.xml"});
        loader = null;
        declaring = null;
        expected = null;

        assertThat(CollectionProbe.isCollected(loaderRef))
                .as("Resolving a comparer class must not pin the class loader that defined it,"
                        + " as a JVM-lifetime cache keyed by the class would.")
                .isTrue();
    }

    @DbUnitExpected(verify = @DbUnitVerifyTable(value = "ACCOUNT",
            defaultComparer = IsolatedComparer.class))
    static class DeclaresIsolatedComparer
    {
    }

    /** Public, with a no-arg constructor, so the resolver can instantiate it reflectively. */
    public static class IsolatedComparer extends NeverFailsValueComparer
    {
    }

    @DbUnitExpected
    private static class NoDatasetNoSpec
    {
    }

    @DbUnitExpected(verifyTables = {"ACCOUNT", "LEDGER"})
    private static class VerifyTablesWithoutDataset
    {
    }

    @DbUnitExpected(verify = @DbUnitVerifyTable("ACCOUNT"))
    private static class InlineVerifyWithoutDataset
    {
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "LEDGER"})
    private static class WithVerifyTables
    {
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "ACCOUNT"})
    private static class WithDuplicateVerifyTables
    {
    }

    @DbUnitExpected("expected.xml")
    private static class Bare
    {
    }
}
