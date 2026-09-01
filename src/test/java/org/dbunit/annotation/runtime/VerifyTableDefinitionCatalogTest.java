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

import java.util.Collections;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.VerifyTableDefinitionsProvider;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.junit.jupiter.api.Test;

class VerifyTableDefinitionCatalogTest
{
    @Test
    void testRead_constantsOnlyClass_indexesEveryPublicStaticFinalDefinitionByTableName()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(ConstantsOnlyCatalog.class);

        assertThat(catalog.select()).as("Every public static final constant must be indexed.")
                .containsExactlyInAnyOrder(ConstantsOnlyCatalog.ACCOUNT,
                        ConstantsOnlyCatalog.CUSTOMER);
    }

    @Test
    void testForClasses_calledTwiceWithSameClasses_returnsTheSameCachedInstance()
    {
        final VerifyTableDefinitionCatalog first =
                VerifyTableDefinitionCatalog.forClasses(ConstantsOnlyCatalog.class);
        final VerifyTableDefinitionCatalog second =
                VerifyTableDefinitionCatalog.forClasses(ConstantsOnlyCatalog.class);

        assertThat(second)
                .as("The same catalog class combination must return the cached instance, not"
                        + " re-reflect it every call.")
                .isSameAs(first);
    }

    @Test
    void testForClasses_differentClassCombination_returnsADifferentInstance()
    {
        final VerifyTableDefinitionCatalog constantsOnly =
                VerifyTableDefinitionCatalog.forClasses(ConstantsOnlyCatalog.class);
        final VerifyTableDefinitionCatalog outOfOrder =
                VerifyTableDefinitionCatalog.forClasses(OutOfOrderCatalog.class);

        assertThat(outOfOrder)
                .as("A different catalog class combination must not hit another"
                        + " combination's cache entry.")
                .isNotSameAs(constantsOnly);
    }

    @Test
    void testForClasses_sameClassesDifferentOrder_treatedAsADifferentCombination()
    {
        final VerifyTableDefinitionCatalog abOrder =
                VerifyTableDefinitionCatalog.forClasses(CatalogA.class, CatalogB.class);
        final VerifyTableDefinitionCatalog baOrder =
                VerifyTableDefinitionCatalog.forClasses(CatalogB.class, CatalogA.class);

        assertThat(baOrder)
                .as("forClasses() caches by the exact ordered combination; the same classes in"
                        + " a different order must not hit the other order's cache entry.")
                .isNotSameAs(abOrder);
        assertThat(baOrder.select())
                .as("Regardless of which order got cached first, each ordering must still"
                        + " resolve its own correct, complete catalog rather than reusing"
                        + " whatever the other order's cache entry happened to contain.")
                .containsExactlyInAnyOrder(CatalogA.ACCOUNT, CatalogB.CUSTOMER);
    }

    @Test
    void testRead_constantsDeclaredOutOfAlphabeticalOrder_returnsThemSortedByFieldName()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(OutOfOrderCatalog.class);

        assertThat(catalog.select())
                .as("Class#getFields() does not guarantee declaration order, so constants"
                        + " must be sorted by field name for a deterministic catalog order"
                        + " across JVMs.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("APPLE", "MANGO", "ZEBRA");
    }

    @Test
    void testRead_nonPublicOrNonStaticFieldsOrWrongType_areIgnored()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(MixedAccessCatalog.class);

        assertThat(catalog.select())
                .as("Only the public static final VerifyTableDefinition-typed constant must be"
                        + " indexed; a public static final field of any other type must be"
                        + " ignored the same as a non-public or non-static one.")
                .containsExactly(MixedAccessCatalog.ACCOUNT);
    }

    @Test
    void testRead_catalogClassExtendsAnotherDeclaringConstants_inheritedConstantsAreNotIncluded()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(SubCatalogDeclaringNothing.class);

        assertThat(catalog.select())
                .as("Only a catalog class's own declared constants belong to its catalog; an"
                        + " inherited public static final VerifyTableDefinition constant from"
                        + " an unrelated superclass must not be silently pulled in too.")
                .isEmpty();
    }

    @Test
    void testRead_declaringClassNotPublicAndInAnotherPackage_stillReadsThePublicStaticFinalField()
            throws Exception
    {
        // package-private and in another package, so - unlike a nested fixture in this
        // package - genuinely requires setAccessible(true) to read; looked up by name
        // since a package-private type in another package cannot be named directly here.
        final Class<?> crossPackageCatalog =
                Class.forName("org.dbunit.annotation.CrossPackageVerifyTableCatalog");

        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(crossPackageCatalog);

        assertThat(catalog.select())
                .as("A public static final field must be readable even when its declaring"
                        + " class is package-private and in a different package - the field,"
                        + " not the class, is what getFields() and this catalog's contract"
                        + " require to be public.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT");
    }

    @Test
    void testRead_classImplementingProvider_usesProviderInsteadOfConstants()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(ProviderCatalog.class);

        assertThat(catalog.select()).as("A VerifyTableDefinitionsProvider must be used"
                + " instead of reading the class's constant fields.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("FROM_PROVIDER");
    }

    @Test
    void testRead_providerReturnsNull_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> new VerifyTableDefinitionCatalog(NullReturningProviderCatalog.class))
                .as("A VerifyTableDefinitionsProvider returning null must be rejected with a"
                        + " clear message, not a raw NullPointerException.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VerifyTableDefinitionsProvider.class.getSimpleName())
                .hasMessageContaining(NullReturningProviderCatalog.class.getName());
    }

    @Test
    void testRead_nullValuedConstant_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> new VerifyTableDefinitionCatalog(NullConstantCatalog.class))
                .as("A null-valued VerifyTableDefinition constant must be rejected with a clear"
                        + " message, not a raw NullPointerException.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NullConstantCatalog.class.getName());
    }

    @Test
    void testRead_definitionCarryingColumnValueComparers_preservesThemUnchanged()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(ComparerCatalog.class);

        final VerifyTableDefinition[] selected = catalog.select("WITH_COMPARER");

        assertThat(selected).as("The catalog constant must be returned unchanged.")
                .containsExactly(ComparerCatalog.WITH_COMPARER);
        assertThat(selected[0].getColumnValueComparers())
                .as("Column value comparers must be preserved.")
                .isSameAs(ComparerCatalog.WITH_COMPARER.getColumnValueComparers());
    }

    @Test
    void testRead_severalCatalogClasses_mergesDefinitionsFromAll()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(CatalogA.class, CatalogB.class);

        assertThat(catalog.select())
                .as("Definitions from every catalog class must be merged.")
                .containsExactlyInAnyOrder(CatalogA.ACCOUNT, CatalogB.CUSTOMER);
    }

    @Test
    void testRead_sameTableNameInTwoCatalogs_throwsNamingBothCatalogs()
    {
        assertThatThrownBy(
                () -> new VerifyTableDefinitionCatalog(CatalogA.class, CatalogADuplicate.class))
                        .as("The same table name in two catalogs must be rejected, naming"
                                + " both catalog classes.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(CatalogA.class.getName())
                        .hasMessageContaining(CatalogADuplicate.class.getName())
                        .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testRead_sameTableNameTwiceInOneCatalog_throwsMessageNamingThatCatalogOnce()
    {
        assertThatThrownBy(() -> new VerifyTableDefinitionCatalog(SameClassDuplicateCatalog.class))
                .as("Two fields naming the same table within one catalog class must be"
                        + " rejected with a message naming that one class once, not the"
                        + " confusing 'defined in both X and X'.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defined more than once in")
                .hasMessageContaining(SameClassDuplicateCatalog.class.getName())
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testSelect_namedTablesPresentInCatalog_returnsThoseDefinitions()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(CatalogA.class, CatalogB.class);

        assertThat(catalog.select("CUSTOMER"))
                .as("Only the named table's definition must be returned.")
                .containsExactly(CatalogB.CUSTOMER);
    }

    @Test
    void testSelect_noNamesGiven_returnsEveryDefinitionInCatalog()
    {
        final VerifyTableDefinitionCatalog catalog =
                new VerifyTableDefinitionCatalog(CatalogA.class, CatalogB.class);

        assertThat(catalog.select())
                .as("No names given must return every definition in the catalog.")
                .containsExactlyInAnyOrder(CatalogA.ACCOUNT, CatalogB.CUSTOMER);
    }

    @Test
    void testSelect_nameAbsentFromCatalog_throwsListingCatalogClassAndAvailableNames()
    {
        final VerifyTableDefinitionCatalog catalog = new VerifyTableDefinitionCatalog(
                ConstantsOnlyCatalog.class);

        assertThatThrownBy(() -> catalog.select("NOT_IN_CATALOG"))
                .as("An unknown table name must throw, naming the catalog and the"
                        + " available table names.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_IN_CATALOG")
                .hasMessageContaining(ConstantsOnlyCatalog.class.getName())
                .hasMessageContaining("ACCOUNT")
                .hasMessageContaining("CUSTOMER");
    }

    static class ConstantsOnlyCatalog
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
        public static final VerifyTableDefinition CUSTOMER =
                new VerifyTableDefinition("CUSTOMER", (String[]) null);
    }

    static class OutOfOrderCatalog
    {
        public static final VerifyTableDefinition ZEBRA =
                new VerifyTableDefinition("ZEBRA", (String[]) null);
        public static final VerifyTableDefinition APPLE =
                new VerifyTableDefinition("APPLE", (String[]) null);
        public static final VerifyTableDefinition MANGO =
                new VerifyTableDefinition("MANGO", (String[]) null);
    }

    static class MixedAccessCatalog
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
        @SuppressWarnings("unused")
        static final VerifyTableDefinition PACKAGE_PRIVATE =
                new VerifyTableDefinition("PACKAGE_PRIVATE", (String[]) null);
        @SuppressWarnings("unused")
        public final VerifyTableDefinition nonStatic =
                new VerifyTableDefinition("NON_STATIC", (String[]) null);
        @SuppressWarnings("unused")
        public static VerifyTableDefinition publicStaticNonFinal =
                new VerifyTableDefinition("PUBLIC_STATIC_NON_FINAL", (String[]) null);
        @SuppressWarnings("unused")
        public static final String WRONG_TYPE = "not a VerifyTableDefinition";
    }

    static class ProviderCatalog implements VerifyTableDefinitionsProvider
    {
        @SuppressWarnings("unused")
        public static final VerifyTableDefinition IGNORED_CONSTANT =
                new VerifyTableDefinition("IGNORED", (String[]) null);

        @Override
        public VerifyTableDefinition[] getVerifyTableDefinitions()
        {
            return new VerifyTableDefinition[] {
                    new VerifyTableDefinition("FROM_PROVIDER", (String[]) null)};
        }
    }

    static class NullConstantCatalog
    {
        public static final VerifyTableDefinition ACCOUNT = null;
    }

    static class NullReturningProviderCatalog implements VerifyTableDefinitionsProvider
    {
        @Override
        public VerifyTableDefinition[] getVerifyTableDefinitions()
        {
            return null;
        }
    }

    static class ComparerCatalog
    {
        public static final VerifyTableDefinition WITH_COMPARER = new VerifyTableDefinition(
                "WITH_COMPARER", ValueComparers.isActualEqualToExpected,
                Collections.singletonMap("COL", ValueComparers.isActualGreaterThanExpected));
    }

    static class CatalogA
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
    }

    static class CatalogADuplicate
    {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
    }

    static class CatalogB
    {
        public static final VerifyTableDefinition CUSTOMER =
                new VerifyTableDefinition("CUSTOMER", (String[]) null);
    }

    static class SameClassDuplicateCatalog
    {
        public static final VerifyTableDefinition ACCOUNT_1 =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
        public static final VerifyTableDefinition ACCOUNT_2 =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
    }

    static class BaseCatalogWithConstant
    {
        @SuppressWarnings("unused")
        public static final VerifyTableDefinition INHERITED =
                new VerifyTableDefinition("INHERITED", (String[]) null);
    }

    static class SubCatalogDeclaringNothing extends BaseCatalogWithConstant
    {
    }
}
