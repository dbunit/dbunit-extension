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

import java.lang.reflect.Method;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitColumnComparer;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.assertion.comparer.value.IsActualGreaterThanExpectedValueComparer;
import org.dbunit.assertion.comparer.value.IsActualWithinToleranceOfExpectedTimestampValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.database.DatabaseConfigPropertiesProvider;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataSetPathsProvider;
import org.junit.jupiter.api.Test;

import java.util.Properties;

class AnnotatedTestConfigurationTest {
    // ---- @DbUnitPrep / @DbUnitSetup ----

    @Test
    void testFrom_classLevelSetupAndMethodLevelPrep_keepsClassLevelOperation() throws Exception {
        final DbUnitSetup setup = ClassLevelSetup.class.getAnnotation(DbUnitSetup.class);
        final DbUnitPrep prep = method(ClassLevelSetup.class, "method")
                .getAnnotation(DbUnitPrep.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(ClassLevelSetup.class, null, prep, setup, null, null, null);

        assertThat(config.getSetUpOperation())
                .as("A class-level operation must survive a method-level @DbUnitPrep.")
                .isEqualTo(DatabaseOperation.REFRESH);
        assertThat(config.getPrepDataFiles())
                .as("The method-level prep file must be resolved.")
                .containsExactly("/org/dbunit/annotation/runtime/method.xml");
    }

    @Test
    void testFrom_setupWithoutPrep_producesNoDataSet() {
        final DbUnitSetup setup = SetupOnly.class.getAnnotation(DbUnitSetup.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(SetupOnly.class, null, null, setup, null, null, null);

        assertThat(config.getPrepDataFiles())
                .as("@DbUnitSetup with no @DbUnitPrep must produce no dataset.").isEmpty();
    }

    @Test
    void testFrom_inlineValueAndProviderBothSet_throwsIllegalStateException() {
        final DbUnitPrep prep = BothValueAndProvider.class.getAnnotation(DbUnitPrep.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(BothValueAndProvider.class, null, prep, null, null, null, null))
                        .as("Setting both value() and provider() must be rejected.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("DbUnitPrep");
    }

    @Test
    void testFrom_providerConstructorThrows_reportsConstructorFailureNotMissingConstructor() {
        final DbUnitPrep prep =
                WithThrowingConstructorProvider.class.getAnnotation(DbUnitPrep.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithThrowingConstructorProvider.class, null, prep, null, null, null,
                        null))
                        .as("A provider whose no-arg constructor throws must report that"
                                + " failure, not be misreported as having no accessible no-arg"
                                + " constructor.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageNotContaining("no accessible no-arg constructor")
                        .hasCauseInstanceOf(RuntimeException.class)
                        .hasRootCauseMessage("boom");
    }

    @Test
    void testFrom_providerWithoutNoArgConstructor_throwsIllegalStateException() {
        final DbUnitPrep prep = WithBadProvider.class.getAnnotation(DbUnitPrep.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithBadProvider.class, null, prep, null, null, null, null))
                        .as("A provider class with no accessible no-arg constructor must be"
                                + " rejected.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(NoNoArgProvider.class.getName());
    }

    @Test
    void testFrom_dataSetPathsProviderReturnsNull_throwsIllegalStateException() {
        final DbUnitPrep prep = WithNullReturningProvider.class.getAnnotation(DbUnitPrep.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithNullReturningProvider.class, null, prep, null, null, null, null))
                        .as("A DataSetPathsProvider returning null must be rejected with a"
                                + " clear message, not a raw NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(DataSetPathsProvider.class.getSimpleName())
                        .hasMessageContaining(NullReturningProvider.class.getName())
                        .hasMessageContaining("DbUnitPrep.provider");
    }

    @Test
    void testFrom_dataSetBaseDirConfigured_prefixesPrepPaths() {
        final DbUnitConfig config = WithBaseDir.class.getAnnotation(DbUnitConfig.class);
        final DbUnitPrep prep = WithBaseDir.class.getAnnotation(DbUnitPrep.class);

        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration
                .from(WithBaseDir.class, config, prep, null, null, null, null);

        assertThat(resolved.getPrepDataFiles())
                .as("dataSetBaseDir must prefix a relative prep path.")
                .containsExactly("/dbunit/accounts/prep.xml");
    }

    // ---- @DbUnitTearDown ----

    @Test
    void testFrom_tearDownDeclared_reportsDeclaredAndResolvesOperation() {
        final DbUnitTearDown tearDown =
                WithTearDown.class.getAnnotation(DbUnitTearDown.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(WithTearDown.class, null, null, null, null, tearDown, null);

        assertThat(config.isTearDownDeclared())
                .as("@DbUnitTearDown presence must be reported.").isTrue();
        assertThat(config.getTearDownOperation())
                .as("The declared teardown operation must resolve to DELETE_ALL.")
                .isEqualTo(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testFrom_tearDownAbsent_reportsNotDeclared() {
        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(AnnotatedTestConfigurationTest.class, null, null, null, null, null, null);

        assertThat(config.isTearDownDeclared())
                .as("No @DbUnitTearDown must be reported as not declared, so a binding"
                        + " leaves the tester's own teardown operation untouched.")
                .isFalse();
    }

    // ---- @DbUnitRowCountCheck ----

    @Test
    void testFrom_rowCountCheckAnnotationAbsent_leavesCheckDisabled() {
        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(AnnotatedTestConfigurationTest.class, null, null, null, null, null, null);

        assertThat(config.isRowCountCheckDeclared())
                .as("No @DbUnitRowCountCheck must be reported as not declared, so a binding"
                        + " leaves the row count check's own resolution untouched.")
                .isFalse();
    }

    @Test
    void testFrom_bareRowCountCheckAnnotation_enablesCheck() {
        final DbUnitRowCountCheck rowCountCheck =
                WithBareRowCountCheck.class.getAnnotation(DbUnitRowCountCheck.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration.from(
                WithBareRowCountCheck.class, null, null, null, null, null, rowCountCheck);

        assertThat(config.isRowCountCheckDeclared())
                .as("@DbUnitRowCountCheck presence must be reported.").isTrue();
        assertThat(config.isRowCountCheckEnabled())
                .as("A bare @DbUnitRowCountCheck must default to enabled.").isTrue();
        assertThat(config.getRowCountCheckExclude())
                .as("A bare @DbUnitRowCountCheck must exclude no tables.").isEmpty();
    }

    @Test
    void testFrom_rowCountCheckEnabledFalseOnMethod_disablesCheckForThatMethod() {
        final DbUnitRowCountCheck rowCountCheck =
                WithRowCountCheckDisabled.class.getAnnotation(DbUnitRowCountCheck.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration.from(
                WithRowCountCheckDisabled.class, null, null, null, null, null, rowCountCheck);

        assertThat(config.isRowCountCheckDeclared())
                .as("@DbUnitRowCountCheck presence must be reported.").isTrue();
        assertThat(config.isRowCountCheckEnabled())
                .as("enabled = false must resolve to disabled.").isFalse();
    }

    @Test
    void testFrom_methodAndClassLevelExcludes_usesMethodLevelExclusivelyWithoutMerging()
            throws Exception {
        // a binding resolves exactly one @DbUnitRowCountCheck instance - method wins over
        // class outright - so the method-level instance's exclude() is all that ever reaches
        // from(); this asserts that value is used as-is, not combined with anything else.
        final DbUnitRowCountCheck methodLevel =
                method(WithClassAndMethodRowCountCheck.class, "method")
                        .getAnnotation(DbUnitRowCountCheck.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration.from(
                WithClassAndMethodRowCountCheck.class, null, null, null, null, null,
                methodLevel);

        assertThat(config.getRowCountCheckExclude())
                .as("The method-level exclude list must be used exclusively, not merged with"
                        + " the class-level one.")
                .containsExactly("METHOD_TABLE");
    }

    // ---- @DbUnitExpected / verify table resolution ----

    @Test
    void testFrom_verifyTableWithEmptyInclude_mapsToNullIncludeColumns() {
        final DbUnitExpected expected =
                WithEmptyInclude.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(WithEmptyInclude.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions()[0].getColumnInclusionFilters())
                .as("An empty include() must map to null - include every column - since an"
                        + " annotation cannot express null directly.")
                .isNull();
    }

    @Test
    void testFrom_columnComparers_buildsComparerMapKeyedByColumn() {
        final DbUnitExpected expected =
                WithColumnComparer.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(WithColumnComparer.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions()[0].getColumnValueComparers())
                .as("Column comparers must be keyed by column name.")
                .hasSize(1)
                .hasEntrySatisfying("BALANCE", comparer -> assertThat(comparer)
                        .isInstanceOf(IsActualGreaterThanExpectedValueComparer.class));
    }

    @Test
    void testFrom_verifyTableSortOnFilteredColumnsOnlyTrue_mapsToTrue() {
        final DbUnitExpected expected =
                WithSortOnFilteredColumnsOnly.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration.from(
                WithSortOnFilteredColumnsOnly.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions()[0].isSortOnFilteredColumnsOnly())
                .as("sortOnFilteredColumnsOnly() = true must map through to"
                        + " VerifyTableDefinition.isSortOnFilteredColumnsOnly().")
                .isTrue();
    }

    @Test
    void testFrom_verifyTableSortOnFilteredColumnsOnlyNotSet_defaultsToFalse() {
        final DbUnitExpected expected =
                WithColumnComparer.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(WithColumnComparer.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions()[0].isSortOnFilteredColumnsOnly())
                .as("sortOnFilteredColumnsOnly() left unset must default to false.")
                .isFalse();
    }

    @Test
    void testFrom_duplicateColumnComparerForSameColumn_throwsIllegalStateException() {
        final DbUnitExpected expected =
                WithDuplicateColumnComparer.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithDuplicateColumnComparer.class, null, null, null, expected, null, null))
                        .as("Two @DbUnitColumnComparer entries for the same column must be"
                                + " rejected rather than silently letting the later one win.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("BALANCE");
    }

    @Test
    void testFrom_comparerConstructorThrows_reportsConstructorFailureNotMissingConstructor() {
        final DbUnitExpected expected =
                WithThrowingComparer.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithThrowingComparer.class, null, null, null, expected, null, null))
                        .as("A ValueComparer whose no-arg constructor throws must report that"
                                + " failure, not be misreported as having no accessible no-arg"
                                + " constructor.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageNotContaining("no accessible no-arg constructor")
                        .hasCauseInstanceOf(RuntimeException.class)
                        .hasRootCauseMessage("boom");
    }

    @Test
    void testFrom_comparerClassWithoutNoArgConstructor_throwsIllegalStateException() {
        final DbUnitExpected expected =
                WithBadComparer.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithBadComparer.class, null, null, null, expected, null, null))
                        .as("A ValueComparer with no accessible no-arg constructor must be"
                                + " rejected, pointing at a catalog as the fix.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(
                                IsActualWithinToleranceOfExpectedTimestampValueComparer.class
                                        .getName())
                        .hasMessageContaining("catalog");
    }

    @Test
    void testFrom_verifyTablesNamesOnly_buildsDefaultDefinitionPerTable() {
        final DbUnitExpected expected =
                WithVerifyTablesOnly.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(WithVerifyTablesOnly.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions())
                .as("verifyTables alone must build one default definition per named table.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT", "CUSTOMER");
    }

    @Test
    void testFrom_noVerifyDeclared_buildsDefaultDefinitionPerExpectedTable() {
        final DbUnitExpected expected =
                NoVerifyDeclared.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(NoVerifyDeclared.class, null, null, null, expected, null, null);

        assertThat(config.getVerifyTableDefinitions())
                .as("Declaring nothing must build one default definition per table actually"
                        + " present in the loaded expected dataset.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactlyInAnyOrder("ACCOUNT", "CUSTOMER");
    }

    @Test
    void testFrom_verifyAndVerifyDefinitionsBothSet_throwsIllegalStateException() {
        final DbUnitExpected expected =
                WithVerifyAndVerifyDefinitions.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithVerifyAndVerifyDefinitions.class, null, null, null, expected, null,
                        null))
                        .as("verify() together with verifyDefinitions() must be rejected as"
                                + " ambiguous.")
                        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFrom_verifyAndVerifyTablesBothSet_throwsIllegalStateException() {
        final DbUnitExpected expected =
                WithVerifyAndVerifyTables.class.getAnnotation(DbUnitExpected.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithVerifyAndVerifyTables.class, null, null, null, expected, null, null))
                        .as("verify() together with verifyTables() must be rejected instead of"
                                + " silently dropping verifyTables(), since verifyTables() only"
                                + " narrows a catalog and verify() declares none.")
                        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFrom_verifyDefinitionsNarrowedByVerifyTables_selectsNamedTablesFromCatalog() {
        final DbUnitExpected expected =
                WithCatalogNarrowedByVerifyTables.class.getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration.from(
                WithCatalogNarrowedByVerifyTables.class, null, null, null, expected, null,
                null);

        assertThat(config.getVerifyTableDefinitions())
                .as("verifyDefinitions narrowed by verifyTables must select just those"
                        + " tables from the catalog.")
                .containsExactly(Catalog.CUSTOMER);
    }

    @Test
    void testFrom_classLevelVerifyDefinitions_usedWhenMethodLevelEmpty() throws Exception {
        final DbUnitConfig config =
                ClassLevelCatalog.class.getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected = method(ClassLevelCatalog.class, "method")
                .getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration
                .from(ClassLevelCatalog.class, config, null, null, expected, null, null);

        assertThat(resolved.getVerifyTableDefinitions())
                .as("A class-level catalog must be used when the method declares none of"
                        + " its own.")
                .containsExactlyInAnyOrder(Catalog.ACCOUNT, Catalog.CUSTOMER);
    }

    @Test
    void testFrom_classLevelCatalogAndMethodLevelVerify_methodLevelVerifyWinsWithoutThrowing()
            throws Exception {
        final DbUnitConfig config = ClassLevelCatalogWithMethodLevelVerify.class
                .getAnnotation(DbUnitConfig.class);
        final DbUnitExpected expected =
                method(ClassLevelCatalogWithMethodLevelVerify.class, "method")
                        .getAnnotation(DbUnitExpected.class);

        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration.from(
                ClassLevelCatalogWithMethodLevelVerify.class, config, null, null, expected,
                null, null);

        assertThat(resolved.getVerifyTableDefinitions())
                .as("A method-level verify() must win over a class-level catalog rather than"
                        + " throwing the ambiguous-conflict exception meant for"
                        + " @DbUnitExpected's own verify()+verifyDefinitions() combination.")
                .extracting(VerifyTableDefinition::getTableName)
                .containsExactly("ACCOUNT");
    }

    // ---- @DbUnitConfig ----

    @Test
    void testFrom_properties_buildsPropertiesWithEveryNameValuePair() {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);

        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);

        final Properties properties = resolved.getDatabaseConfigProperties();
        assertThat(properties)
                .as("Every @DbUnitProperty entry must be collected.")
                .containsEntry("caseSensitiveTableNames", "true")
                .containsEntry("batchSize", "50");
    }

    @Test
    void testFrom_duplicatePropertyName_throwsIllegalStateException() {
        final DbUnitConfig config =
                WithDuplicatePropertyName.class.getAnnotation(DbUnitConfig.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithDuplicatePropertyName.class, config, null, null, null, null, null))
                        .as("Two @DbUnitProperty entries with the same name must be rejected"
                                + " rather than silently letting the later one win, matching"
                                + " every other duplicate-configuration check in this class.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("batchSize");
    }

    @Test
    void testGetDatabaseConfigProperties_mutatingReturnedInstance_doesNotAffectLaterCalls() {
        final DbUnitConfig config = WithProperties.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration
                .from(WithProperties.class, config, null, null, null, null, null);

        resolved.getDatabaseConfigProperties().setProperty("mutatedAfterReturn", "true");

        assertThat(resolved.getDatabaseConfigProperties())
                .as("getDatabaseConfigProperties() must return a defensive copy, like every"
                        + " other getter on this immutable configuration class - mutating a"
                        + " previously returned instance must not leak into this class's"
                        + " internal state or a later caller's copy.")
                .doesNotContainKey("mutatedAfterReturn");
    }

    @Test
    void testFrom_propertiesProviderSet_usesProviderProperties() {
        final DbUnitConfig config =
                WithPropertiesProvider.class.getAnnotation(DbUnitConfig.class);

        final AnnotatedTestConfiguration resolved = AnnotatedTestConfiguration
                .from(WithPropertiesProvider.class, config, null, null, null, null, null);

        assertThat(resolved.getDatabaseConfigProperties())
                .as("A properties provider must supply the DatabaseConfig properties.")
                .containsEntry("fetchSize", "25");
    }

    @Test
    void testFrom_propertiesProviderReturnsNull_throwsIllegalStateException() {
        final DbUnitConfig config =
                WithNullReturningPropertiesProvider.class.getAnnotation(DbUnitConfig.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithNullReturningPropertiesProvider.class, config, null, null, null, null,
                        null))
                        .as("A DatabaseConfigPropertiesProvider returning null must be rejected"
                                + " with a clear message, not a raw NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(DatabaseConfigPropertiesProvider.class
                                .getSimpleName())
                        .hasMessageContaining(NullReturningPropertiesProvider.class.getName())
                        .hasMessageContaining("DbUnitConfig.propertiesProvider");
    }

    @Test
    void testFrom_propertiesAndPropertiesProviderBothSet_throwsIllegalStateException() {
        final DbUnitConfig config =
                WithPropertiesAndProvider.class.getAnnotation(DbUnitConfig.class);

        assertThatThrownBy(() -> AnnotatedTestConfiguration
                .from(WithPropertiesAndProvider.class, config, null, null, null, null, null))
                        .as("Setting both properties() and propertiesProvider() must be"
                                + " rejected.")
                        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testFrom_failureHandlerConfigured_instantiatesAndReturnsIt() {
        final DbUnitConfig config = WithFailureHandler.class.getAnnotation(DbUnitConfig.class);

        final AnnotatedTestConfiguration result =
                AnnotatedTestConfiguration.from(WithFailureHandler.class, config, null, null,
                        null, null, null);

        assertThat(result.getFailureHandler())
                .as("@DbUnitConfig.failureHandler() must be reflectively instantiated and"
                        + " returned by getFailureHandler().")
                .isInstanceOf(DiffCollectingFailureHandler.class);
    }

    @Test
    void testFrom_noConfig_usesSensibleDefaults() {
        final AnnotatedTestConfiguration config = AnnotatedTestConfiguration
                .from(AnnotatedTestConfigurationTest.class, null, null, null, null, null, null);

        assertThat(config.getDatabaseTesterFactory())
                .as("No @DbUnitConfig must leave the tester factory unset.").isNull();
        assertThat(config.getFailureHandler())
                .as("No @DbUnitConfig must leave the failure handler unset.").isNull();
        assertThat(config.isCloseConnectionAfterTest())
                .as("closeConnectionAfterTest must default to true.").isTrue();
        assertThat(config.getPrepAndExpectedTestCaseClass())
                .as("The default PrepAndExpectedTestCase implementation must be"
                        + " DefaultPrepAndExpectedTestCase.")
                .isEqualTo(DefaultPrepAndExpectedTestCase.class);
    }

    // ---- test fixtures ----

    private static Method method(final Class<?> testClass, final String name) throws Exception {
        return testClass.getDeclaredMethod(name);
    }

    @DbUnitSetup(operation = DbUnitOperation.REFRESH)
    static class ClassLevelSetup {
        @DbUnitPrep("method.xml")
        void method() {
        }
    }

    @DbUnitSetup(operation = DbUnitOperation.REFRESH)
    static class SetupOnly {
    }

    @DbUnitPrep(value = "a.xml", provider = SomeProvider.class)
    static class BothValueAndProvider {
    }

    static class SomeProvider implements DataSetPathsProvider {
        @Override
        public String[] getDataSetPaths() {
            return new String[] {"b.xml"};
        }
    }

    @DbUnitPrep(provider = NoNoArgProvider.class)
    static class WithBadProvider {
    }

    static class NoNoArgProvider implements DataSetPathsProvider {
        NoNoArgProvider(final String unused) {
        }

        @Override
        public String[] getDataSetPaths() {
            return new String[0];
        }
    }

    @DbUnitPrep(provider = NullReturningProvider.class)
    static class WithNullReturningProvider {
    }

    static class NullReturningProvider implements DataSetPathsProvider {
        @Override
        public String[] getDataSetPaths() {
            return null;
        }
    }

    @DbUnitPrep(provider = ThrowingConstructorProvider.class)
    static class WithThrowingConstructorProvider {
    }

    static class ThrowingConstructorProvider implements DataSetPathsProvider {
        ThrowingConstructorProvider() {
            throw new RuntimeException("boom");
        }

        @Override
        public String[] getDataSetPaths() {
            return new String[0];
        }
    }

    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    static class WithFailureHandler {
    }

    @DbUnitConfig(dataSetBaseDir = "/dbunit/accounts/")
    @DbUnitPrep("prep.xml")
    static class WithBaseDir {
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class WithTearDown {
    }

    @DbUnitRowCountCheck
    static class WithBareRowCountCheck {
    }

    @DbUnitRowCountCheck(enabled = false)
    static class WithRowCountCheckDisabled {
    }

    @DbUnitRowCountCheck(exclude = "CLASS_TABLE")
    static class WithClassAndMethodRowCountCheck {
        @DbUnitRowCountCheck(exclude = "METHOD_TABLE")
        void method() {
        }
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT", include = {}))
    static class WithEmptyInclude {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT",
                    columnComparers = @DbUnitColumnComparer(column = "BALANCE",
                            comparer = IsActualGreaterThanExpectedValueComparer.class)))
    static class WithColumnComparer {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT", sortOnFilteredColumnsOnly = true))
    static class WithSortOnFilteredColumnsOnly {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT",
                    columnComparers = {
                            @DbUnitColumnComparer(column = "BALANCE",
                                    comparer = IsActualGreaterThanExpectedValueComparer.class),
                            @DbUnitColumnComparer(column = "BALANCE",
                                    comparer = IsActualGreaterThanExpectedValueComparer.class)}))
    static class WithDuplicateColumnComparer {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT",
                    defaultComparer = IsActualWithinToleranceOfExpectedTimestampValueComparer.class))
    static class WithBadComparer {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable(value = "ACCOUNT",
                    defaultComparer = ThrowingComparer.class))
    static class WithThrowingComparer {
    }

    static class ThrowingComparer implements ValueComparer {
        ThrowingComparer() {
            throw new RuntimeException("boom");
        }

        @Override
        public String compare(final ITable expectedTable, final ITable actualTable,
                final int rowNum, final String columnName, final DataType dataType,
                final Object expectedValue, final Object actualValue) {
            return null;
        }
    }

    @DbUnitExpected(value = "expected.xml", verifyTables = {"ACCOUNT", "CUSTOMER"})
    static class WithVerifyTablesOnly {
    }

    @DbUnitExpected("expected.xml")
    static class NoVerifyDeclared {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable("ACCOUNT"), verifyDefinitions = Catalog.class)
    static class WithVerifyAndVerifyDefinitions {
    }

    @DbUnitExpected(value = "expected.xml",
            verify = @DbUnitVerifyTable("ACCOUNT"), verifyTables = "ACCOUNT")
    static class WithVerifyAndVerifyTables {
    }

    @DbUnitExpected(value = "expected.xml", verifyDefinitions = Catalog.class,
            verifyTables = "CUSTOMER")
    static class WithCatalogNarrowedByVerifyTables {
    }

    @DbUnitConfig(verifyDefinitions = Catalog.class)
    static class ClassLevelCatalog {
        @DbUnitExpected("expected.xml")
        void method() {
        }
    }

    static class Catalog {
        public static final VerifyTableDefinition ACCOUNT =
                new VerifyTableDefinition("ACCOUNT", (String[]) null);
        public static final VerifyTableDefinition CUSTOMER =
                new VerifyTableDefinition("CUSTOMER", (String[]) null);
    }

    @DbUnitConfig(verifyDefinitions = Catalog.class)
    static class ClassLevelCatalogWithMethodLevelVerify {
        @DbUnitExpected(value = "expected.xml", verify = @DbUnitVerifyTable("ACCOUNT"))
        void method() {
        }
    }

    @DbUnitConfig(properties = {
            @DbUnitProperty(name = "caseSensitiveTableNames", value = "true"),
            @DbUnitProperty(name = "batchSize", value = "50")})
    static class WithProperties {
    }

    @DbUnitConfig(properties = {
            @DbUnitProperty(name = "batchSize", value = "50"),
            @DbUnitProperty(name = "batchSize", value = "999")})
    static class WithDuplicatePropertyName {
    }

    @DbUnitConfig(propertiesProvider = SomePropertiesProvider.class)
    static class WithPropertiesProvider {
    }

    static class SomePropertiesProvider implements DatabaseConfigPropertiesProvider {
        @Override
        public Properties getProperties() {
            final Properties properties = new Properties();
            properties.setProperty("fetchSize", "25");
            return properties;
        }
    }

    @DbUnitConfig(propertiesProvider = NullReturningPropertiesProvider.class)
    static class WithNullReturningPropertiesProvider {
    }

    static class NullReturningPropertiesProvider implements DatabaseConfigPropertiesProvider {
        @Override
        public Properties getProperties() {
            return null;
        }
    }

    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"),
            propertiesProvider = SomePropertiesProvider.class)
    static class WithPropertiesAndProvider {
    }
}
