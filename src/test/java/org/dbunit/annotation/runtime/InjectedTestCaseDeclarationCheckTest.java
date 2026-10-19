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
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link InjectedTestCaseDeclarationCheck}: one test per declaration a test case that is not
 * a {@link DefaultPrepAndExpectedTestCase} cannot be given.
 */
@ClearRowCountCheckSystemProperties
class InjectedTestCaseDeclarationCheckTest
{
    private final PrepAndExpectedTestCase otherTestCase = mock(PrepAndExpectedTestCase.class);

    @Test
    void testRequireSupportedBy_otherTestCaseAndOnlyFilesAndCatalogDeclared_accepts()
    {
        assertThatCode(() -> requireSupportedByOther(FilesAndCatalog.class))
                .as("Files, a catalog and a base directory need nothing but the lifecycle calls.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_defaultTestCaseAndEverythingDeclared_accepts()
    {
        final DefaultPrepAndExpectedTestCase defaultTestCase =
                mock(DefaultPrepAndExpectedTestCase.class);

        assertThatCode(() -> new InjectedTestCaseDeclarationCheck(configFrom(Everything.class))
                .requireSupportedBy(defaultTestCase))
                .as("A DefaultPrepAndExpectedTestCase is never rejected by this check.")
                .doesNotThrowAnyException();
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndSetUpOperation_rejects()
    {
        assertRejected(WithSetUpOperation.class, "@DbUnitSetUpOperation");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndTearDownOperation_rejects()
    {
        assertRejected(WithTearDownOperation.class, "@DbUnitTearDownOperation");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndRowCountCheck_rejects()
    {
        assertRejected(WithRowCountCheck.class, "@DbUnitRowCountCheck");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndProperties_rejects()
    {
        assertRejected(WithProperties.class, "@DbUnitConfig(properties");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndDataFileLoader_rejects()
    {
        assertRejected(WithDataFileLoader.class, "@DbUnitConfig(dataFileLoader)");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndFailureHandler_rejects()
    {
        assertRejected(WithFailureHandler.class, "@DbUnitConfig(failureHandler)");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndCloseConnectionAfterTestFalse_rejects()
    {
        assertRejected(WithCloseConnectionAfterTestFalse.class,
                "@DbUnitConfig(closeConnectionAfterTest)");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndDatabaseTesterFactory_rejects()
    {
        assertRejected(WithDatabaseTesterFactory.class, "@DbUnitConfig(databaseTesterFactory)");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndPrepAndExpectedTestCaseClass_rejects()
    {
        assertRejected(WithPrepAndExpectedTestCaseClass.class,
                "@DbUnitConfig(prepAndExpectedTestCase)");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndNoExpected_rejects()
    {
        assertRejected(WithPrepOnly.class, "a test without @DbUnitExpected");
    }

    @Test
    void testRequireSupportedBy_otherTestCaseAndSeveralDeclarations_rejectsOnceNamingThemAll()
    {
        assertThatThrownBy(() -> requireSupportedByOther(Everything.class))
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

    private void assertRejected(final Class<?> fixture, final String declaration)
    {
        assertThatThrownBy(() -> requireSupportedByOther(fixture))
                .as("A test case that is not a DefaultPrepAndExpectedTestCase cannot be given "
                        + declaration + ".")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(declaration);
    }

    private void requireSupportedByOther(final Class<?> fixture)
    {
        new InjectedTestCaseDeclarationCheck(configFrom(fixture)).requireSupportedBy(otherTestCase);
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
    @DbUnitConfig(closeConnectionAfterTest = false)
    private static class WithCloseConnectionAfterTestFalse
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
