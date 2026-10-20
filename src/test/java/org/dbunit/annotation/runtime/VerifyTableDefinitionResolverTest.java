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

import java.lang.ref.WeakReference;
import java.util.Optional;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.assertion.comparer.value.NeverFailsValueComparer;
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
                resolveDeclared(expected, new String[] {"expected.xml"}).get();

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testResolveDeclared_duplicateVerifyTablesName_throws()
    {
        final DbUnitExpected expected =
                WithDuplicateVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolveDeclared(expected,
                new String[] {"expected.xml"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than one")
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testResolveDeclared_verifyTablesDifferingOnlyInCase_throws()
    {
        final DbUnitExpected expected =
                WithDifferentCaseDuplicateVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolveDeclared(expected,
                new String[] {"expected.xml"}))
                        .as("'ACCOUNT' and 'account' name the same table, as dbUnit compares"
                                + " table names ignoring case unless told otherwise.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("more than one")
                        .hasMessageContaining("account");
    }

    @Test
    void testResolveDeclared_inlineVerifyEntriesDifferingOnlyInCase_throws()
    {
        final DbUnitExpected expected =
                WithDifferentCaseInlineVerify.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolveDeclared(expected,
                new String[] {"expected.xml"}))
                        .as("Two @DbUnitVerifyTable entries naming one table in different case"
                                + " verify it twice.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("more than one")
                        .hasMessageContaining("account");
    }

    @Test
    void testResolveDeclared_noVerifySpecWithExpectedDataset_declaresNothingSoTablesAreDerivedLater()
    {
        final DbUnitExpected expected = Bare.class.getAnnotation(DbUnitExpected.class);

        assertThat(resolveDeclared(expected, new String[] {"expected.xml"}))
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
                resolver.deriveFromExpectedDatasets(loader, new String[] {"expected.xml"},
                        new VerifyTableDefinition[0]);

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
    }

    @Test
    void testDeriveFromExpectedDatasets_catalogDefinesSomeTables_usesItsDefinitionsAndDefaultsForTheRest()
            throws Exception
    {
        final DataFileLoader loader = loaderOfTables("ACCOUNT", "LEDGER");
        final VerifyTableDefinition ledgerRules = new VerifyTableDefinition("LEDGER",
                new String[] {"UPDATED_AT"});

        final VerifyTableDefinition[] definitions = resolver.deriveFromExpectedDatasets(loader,
                new String[] {"expected.xml"}, new VerifyTableDefinition[] {ledgerRules});

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
        assertThat(definitions[1])
                .as("A table the catalog defines is verified with the catalog's own definition.")
                .isSameAs(ledgerRules);
        assertThat(definitions[0].getColumnExclusionFilters())
                .as("A table it does not define is verified with the default rules.").isNull();
    }

    @Test
    void testDeriveFromExpectedDatasets_catalogDefinesATableTheDatasetLacks_doesNotVerifyIt()
            throws Exception
    {
        final DataFileLoader loader = loaderOfTables("ACCOUNT");
        final VerifyTableDefinition audit = new VerifyTableDefinition("AUDIT", (String[]) null);

        final VerifyTableDefinition[] definitions = resolver.deriveFromExpectedDatasets(loader,
                new String[] {"expected.xml"}, new VerifyTableDefinition[] {audit});

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .as("The expected dataset decides which tables are verified, the catalog only"
                        + " supplies rules.")
                .containsExactly("ACCOUNT");
    }

    @Test
    void testDeriveFromExpectedDatasets_catalogSpellsTheTableNameInAnotherCase_usesItsDefinition()
            throws Exception
    {
        final DataFileLoader loader = loaderOfTables("account");
        final VerifyTableDefinition upperCase =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);

        final VerifyTableDefinition[] definitions = resolver.deriveFromExpectedDatasets(loader,
                new String[] {"expected.xml"}, new VerifyTableDefinition[] {upperCase});

        assertThat(definitions).as("Tables are matched ignoring case, as dbUnit finds a table in"
                + " a dataset.").containsExactly(upperCase);
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
                new String[] {"expected.xml"}, new VerifyTableDefinition[0]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected.xml")
                .hasCauseInstanceOf(DataSetException.class);
    }

    @Test
    void testResolveDeclared_verifyTablesNamedInDifferentCaseThanTheCatalog_selectsTheCatalogsDefinition()
    {
        final DbUnitExpected expected =
                WithLowerCaseTable.class.getAnnotation(DbUnitExpected.class);

        final VerifyTableDefinition[] definitions = resolver.resolveDeclared(
                new Class<?>[] {UpperCaseAccountCatalog.class}, expected,
                new String[] {"expected.xml"}).get();

        assertThat(definitions)
                .as("verifyTables selects from the catalog ignoring case, the way dbUnit finds a"
                        + " table in a dataset.")
                .containsExactly(UpperCaseAccountCatalog.ACCOUNT);
    }

    @Test
    void testResolveDeclared_verifyTablesNamesATableTheCatalogLacks_getsADefaultDefinition()
    {
        final DbUnitExpected expected =
                WithVerifyTables.class.getAnnotation(DbUnitExpected.class);

        final VerifyTableDefinition[] definitions = resolver.resolveDeclared(
                new Class<?>[] {UpperCaseAccountCatalog.class}, expected,
                new String[] {"expected.xml"}).get();

        assertThat(definitions).extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "LEDGER");
        assertThat(definitions[0]).as("The table the catalog defines keeps its definition.")
                .isSameAs(UpperCaseAccountCatalog.ACCOUNT);
    }

    @Test
    void testResolveDeclared_inlineVerifyAndCatalog_inlineVerifyWins()
    {
        final DbUnitExpected expected =
                InlineAccount.class.getAnnotation(DbUnitExpected.class);

        final VerifyTableDefinition[] definitions = resolver.resolveDeclared(
                new Class<?>[] {UpperCaseAccountCatalog.class}, expected,
                new String[] {"expected.xml"}).get();

        assertThat(definitions).hasSize(1);
        assertThat(definitions[0]).as("The inline rules are the test's own, not the catalog's.")
                .isNotSameAs(UpperCaseAccountCatalog.ACCOUNT);
    }

    @Test
    void testResolveDeclared_verifyAndVerifyTablesBothSet_throws()
    {
        final DbUnitExpected expected =
                InlineAndVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolveDeclared(expected, new String[] {"expected.xml"}))
                .as("verify() declares the rules of its tables, so verifyTables() beside it is"
                        + " ambiguous.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@DbUnitExpected sets both verify() and verifyTables(); set only"
                        + " one. verify() declares the rules of its tables, verifyTables() takes"
                        + " them from a catalog.");
    }

    @Test
    void testCatalogDefinitions_catalogClasses_returnsEveryDefinitionTheyHold()
    {
        assertThat(resolver.catalogDefinitions(new Class<?>[] {UpperCaseAccountCatalog.class}))
                .containsExactly(UpperCaseAccountCatalog.ACCOUNT);
    }

    @Test
    void testCatalogDefinitions_noCatalogClass_returnsNone()
    {
        assertThat(resolver.catalogDefinitions(new Class<?>[0])).isEmpty();
    }

    @Test
    void testResolveDeclared_noExpectedDatasetAndNoVerifySpec_throwsBecauseNothingWouldBeVerified()
    {
        final DbUnitExpected expected = NoDatasetNoSpec.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> resolveDeclared(expected, new String[0]))
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

        assertThatThrownBy(() -> resolveDeclared(expected, new String[0]))
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

        assertThatThrownBy(() -> resolveDeclared(expected, new String[0]))
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

        resolveDeclared(expected, new String[] {"expected.xml"});
        loader = null;
        declaring = null;
        expected = null;

        assertThat(CollectionProbe.isCollected(loaderRef))
                .as("Resolving a comparer class must not pin the class loader that defined it,"
                        + " as a JVM-lifetime cache keyed by the class would.")
                .isTrue();
    }

    private static DataFileLoader loaderOfTables(final String... tableNames) throws Exception
    {
        final DataFileLoader loader = mock(DataFileLoader.class);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(tableNames);
        when(loader.load("expected.xml")).thenReturn(dataSet);
        return loader;
    }

    /** Resolves what the annotations spell out, with no class-level catalog declared. */
    private Optional<VerifyTableDefinition[]> resolveDeclared(final DbUnitExpected expected,
            final String[] expectedDataFiles)
    {
        return resolver.resolveDeclared(new Class<?>[0], expected, expectedDataFiles);
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

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "account"})
    private static class WithDifferentCaseDuplicateVerifyTables
    {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = {@DbUnitVerifyTable("ACCOUNT"), @DbUnitVerifyTable("account")})
    private static class WithDifferentCaseInlineVerify
    {
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = "account")
    private static class WithLowerCaseTable
    {
    }

    @DbUnitExpected(value = "expected.xml", verify = @DbUnitVerifyTable("ACCOUNT"))
    private static class InlineAccount
    {
    }

    @DbUnitExpected(value = "expected.xml", verify = @DbUnitVerifyTable("ACCOUNT"),
            verifyTables = "ACCOUNT")
    private static class InlineAndVerifyTables
    {
    }

    /** Public, so the resolver can read its constants whatever package the test runs in. */
    public static class UpperCaseAccountCatalog
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
    }

    @DbUnitExpected("expected.xml")
    private static class Bare
    {
    }
}
