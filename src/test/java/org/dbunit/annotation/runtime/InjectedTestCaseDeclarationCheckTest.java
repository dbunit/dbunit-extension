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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.assertion.DiffCollectingFailureHandler;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.DataFileLoader;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link InjectedTestCaseDeclarationCheck}: one test per declaration an injected test case
 * cannot be given, for a {@link DefaultPrepAndExpectedTestCase} and for any other
 * {@link PrepAndExpectedTestCase}.
 */
@ClearRowCountCheckSystemProperties
class InjectedTestCaseDeclarationCheckTest
{
    private final PrepAndExpectedTestCase otherTestCase = mock(PrepAndExpectedTestCase.class);
    private final DefaultPrepAndExpectedTestCase defaultTestCase =
            mock(DefaultPrepAndExpectedTestCase.class);

    @Test
    void testRequireSupportedBy_otherTestCaseAndOnlyFilesAndCatalogDeclared_accepts()
    {
        assertThatCode(() -> check(FilesAndCatalog.class, otherTestCase))
                .as("Files, a catalog and a base directory need nothing but the lifecycle calls.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_defaultTestCaseAndOnlyFilesAndCatalogDeclared_accepts()
    {
        assertThatCode(() -> check(FilesAndCatalog.class, defaultTestCase))
                .as("The test's files and catalog are what an injected test case is given.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_defaultTestCaseAndRowCountCheck_accepts()
    {
        assertThatCode(() -> check(WithRowCountCheck.class, defaultTestCase))
                .as("A DefaultPrepAndExpectedTestCase takes the row count check through its"
                        + " override methods.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndRowCountCheck_rejects()
    {
        assertRejected(WithRowCountCheck.class, otherTestCase, "@DbUnitRowCountCheck");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndNoExpected_rejects()
    {
        assertRejected(WithPrepOnly.class, otherTestCase, "a test without @DbUnitExpected");
    }

    @Test
    void testRequireDataFileLoaderForTheFiles_defaultTestCaseWithLoader_accepts()
    {
        when(defaultTestCase.getDataFileLoader()).thenReturn(mock(DataFileLoader.class));

        assertThatCode(() -> checkLoader(FilesAndCatalog.class, defaultTestCase))
                .as("A test case built with a loader can read the files the test names.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireDataFileLoaderForTheFiles_defaultTestCaseWithoutLoaderAndFilesDeclared_rejects()
    {
        assertThatThrownBy(() -> checkLoader(FilesAndCatalog.class, defaultTestCase))
                .as("dbUnit does not give an injected test case a loader, so one with none cannot"
                        + " read the files the test names.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(defaultTestCase.getClass().getName())
                .hasMessageContaining("has no DataFileLoader to read the test's dataset files")
                .hasMessageContaining("where it is built, or in a @BeforeEach method");
    }

    @Test
    void testRequireDataFileLoaderForTheFiles_defaultTestCaseWithoutLoaderAndNoFilesDeclared_accepts()
    {
        assertThatCode(() -> checkLoader(NoFiles.class, defaultTestCase))
                .as("A loader is needed only to read files, and the test names none.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireDataFileLoaderForTheFiles_otherTestCase_acceptsAsItTellsDbUnitOfNoLoader()
    {
        assertThatCode(() -> checkLoader(FilesAndCatalog.class, otherTestCase))
                .as("Only a DefaultPrepAndExpectedTestCase tells dbUnit of its loader, so there is"
                        + " nothing to check of any other.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_setUpOperation_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithSetUpOperation.class, "@DbUnitSetUpOperation");
    }

    @Test
    void testRequireSupportedBy_tearDownOperation_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithTearDownOperation.class, "@DbUnitTearDownOperation");
    }

    @Test
    void testRequireSupportedBy_properties_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithProperties.class, "@DbUnitConfig(properties");
    }

    @Test
    void testRequireSupportedBy_dataFileLoader_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithDataFileLoader.class, "@DbUnitConfig(dataFileLoader)");
    }

    @Test
    void testRequireSupportedBy_failureHandler_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithFailureHandler.class, "@DbUnitConfig(failureHandler)");
    }

    @Test
    void testRequireSupportedBy_databaseTesterFactory_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithDatabaseTesterFactory.class,
                "@DbUnitConfig(databaseTesterFactory)");
    }

    @Test
    void testRequireSupportedBy_prepAndExpectedTestCaseClass_rejectsForBothKindsOfTestCase()
    {
        assertRejectedForBoth(WithPrepAndExpectedTestCaseClass.class,
                "@DbUnitConfig(prepAndExpectedTestCase)");
    }

    @Test
    void testRequireSupportedBy_severalDeclarations_rejectsOnceNamingThemAll()
    {
        assertThatThrownBy(() -> check(Everything.class, otherTestCase))
                .as("One exception names every declaration the test case cannot be given, and"
                        + " the test case itself.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(otherTestCase.getClass().getName())
                .hasMessageContaining("@DbUnitSetUpOperation")
                .hasMessageContaining("@DbUnitTearDownOperation")
                .hasMessageContaining("@DbUnitRowCountCheck")
                .hasMessageContaining("@DbUnitConfig(dataFileLoader)")
                .hasMessageContaining("@DbUnitConfig(failureHandler)")
                .hasMessageContaining("Configure the test case where it is built");
    }

    @Test
    void testRequireSupportedBy_defaultTestCaseAndSeveralDeclarations_namesThemAllButTheRowCountCheck()
    {
        assertThatThrownBy(() -> check(Everything.class, defaultTestCase))
                .as("The row count check is the one declaration a DefaultPrepAndExpectedTestCase"
                        + " can be given, so it is not named.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitSetUpOperation")
                .hasMessageContaining("@DbUnitConfig(failureHandler)")
                .hasMessageNotContaining("@DbUnitRowCountCheck(");
    }

    private void assertRejectedForBoth(final Class<?> fixture, final String declaration)
    {
        assertRejected(fixture, otherTestCase, declaration);
        assertRejected(fixture, defaultTestCase, declaration);
    }

    private void assertRejected(final Class<?> fixture, final PrepAndExpectedTestCase testCase,
            final String declaration)
    {
        assertThatThrownBy(() -> check(fixture, testCase))
                .as(testCase.getClass().getSimpleName() + " cannot be given " + declaration + ".")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(declaration);
    }

    private static void check(final Class<?> fixture, final PrepAndExpectedTestCase testCase)
    {
        new InjectedTestCaseDeclarationCheck(configFrom(fixture)).requireSupportedBy(testCase);
    }

    private static void checkLoader(final Class<?> fixture, final PrepAndExpectedTestCase testCase)
    {
        new InjectedTestCaseDeclarationCheck(configFrom(fixture))
                .requireDataFileLoaderForTheFiles(testCase);
    }

    private static AnnotatedTestConfiguration configFrom(final Class<?> fixture)
    {
        return AnnotatedTestConfiguration.from(fixture, fixture.getAnnotation(DbUnitConfig.class),
                fixture.getAnnotation(DbUnitPrep.class),
                fixture.getAnnotation(DbUnitSetUpOperation.class),
                fixture.getAnnotation(DbUnitExpected.class),
                fixture.getAnnotation(DbUnitTearDownOperation.class),
                fixture.getAnnotation(DbUnitRowCountCheck.class));
    }

    @DbUnitPrep("prep.xml")
    @DbUnitExpected(value = "expected.xml", verifyTables = "ACCOUNT")
    @DbUnitConfig(dataSetBaseDir = "/some/dir")
    private static class FilesAndCatalog
    {
    }

    private static class NoFiles
    {
    }

    @DbUnitPrep("prep.xml")
    private static class WithPrepOnly
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
    private static class WithSetUpOperation
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    private static class WithTearDownOperation
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitRowCountCheck
    private static class WithRowCountCheck
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = "50"))
    private static class WithProperties
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class)
    private static class WithDataFileLoader
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitConfig(failureHandler = DiffCollectingFailureHandler.class)
    private static class WithFailureHandler
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitConfig(databaseTesterFactory = NoTesterFactory.class)
    private static class WithDatabaseTesterFactory
    {
    }

    @DbUnitExpected("expected.xml")
    @DbUnitConfig(prepAndExpectedTestCase = SubclassedTestCase.class)
    private static class WithPrepAndExpectedTestCaseClass
    {
    }

    @DbUnitPrep("prep.xml")
    @DbUnitExpected("expected.xml")
    @DbUnitSetUpOperation(DbUnitOperation.REFRESH)
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    @DbUnitRowCountCheck
    @DbUnitConfig(dataFileLoader = FlatXmlDataFileLoader.class,
            failureHandler = DiffCollectingFailureHandler.class)
    private static class Everything
    {
    }

    public static class NoTesterFactory implements DatabaseTesterFactory
    {
        @Override
        public org.dbunit.IDatabaseTester getDatabaseTester()
        {
            return null;
        }
    }

    public static class SubclassedTestCase extends DefaultPrepAndExpectedTestCase
    {
    }
}
