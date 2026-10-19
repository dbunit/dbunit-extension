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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.util.fileloader.FileExtensionDataFileLoader;

/**
 * Rejects the declarations that ask more of an injected {@code @DbUnitTestCase} than it gives.
 * A test case that is not a {@link DefaultPrepAndExpectedTestCase} is driven as it is: dbUnit
 * gives it the test's files and calls {@code configureTest()}, {@code preTest()} and
 * {@code postTest()}, and nothing else. Without a tester or a connection it can tell dbUnit
 * about, everything that needs one cannot be applied, and is rejected with one message when
 * declared instead of silently never taking effect.
 *
 * <p>Not intended for direct use by test code; machinery consumed by {@link ExpectedLifecycle}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class InjectedTestCaseDeclarationCheck
{
    private final AnnotatedTestConfiguration configuration;

    /**
     * Creates a check of the declarations of one test.
     *
     * @param configuration The resolved configuration of the test.
     */
    InjectedTestCaseDeclarationCheck(final AnnotatedTestConfiguration configuration)
    {
        this.configuration = configuration;
    }

    /**
     * Fails when the test declares something that {@code testCase} cannot be given.
     *
     * @param testCase The injected {@code @DbUnitTestCase} instance.
     * @throws IllegalStateException If {@code testCase} is not a
     *             {@link DefaultPrepAndExpectedTestCase} and the test declares anything that
     *             needs more than its files and its lifecycle calls, naming all of it.
     */
    void requireSupportedBy(final PrepAndExpectedTestCase testCase)
    {
        if (testCase instanceof DefaultPrepAndExpectedTestCase)
        {
            return;
        }

        final List<String> unsupported = declarationsNeedingMoreThanItsLifecycle();
        if (!configuration.isExpected())
        {
            unsupported.add("a test without @DbUnitExpected, which would have no tester to"
                    + " set up");
        }
        if (unsupported.isEmpty())
        {
            return;
        }
        throw new IllegalStateException("The @DbUnitTestCase field's test case "
                + testCase.getClass().getName() + " is not a DefaultPrepAndExpectedTestCase, so"
                + " dbUnit only gives it the test's files and calls configureTest(), preTest()"
                + " and postTest(): it cannot apply " + String.join(", ", unsupported)
                + ". Configure the test case where it is built, or drop what the test declares.");
    }

    private List<String> declarationsNeedingMoreThanItsLifecycle()
    {
        final List<String> declared = new ArrayList<>();
        addIf(configuration.isSetupDeclared(), declared, "@DbUnitSetUpOperation");
        addIf(configuration.isTearDownDeclared(), declared, "@DbUnitTearDownOperation");
        addIf(configuration.isRowCountCheckDeclared(), declared, "@DbUnitRowCountCheck");
        addIf(configuration.getDataFileLoader().getClass() != FileExtensionDataFileLoader.class,
                declared, "@DbUnitConfig(dataFileLoader)");
        addIf(configuration.getFailureHandler() != null, declared, "@DbUnitConfig(failureHandler)");
        addIf(!configuration.getDatabaseConfigProperties().isEmpty(), declared,
                "@DbUnitConfig(properties or propertiesProvider)");
        addIf(!configuration.isCloseConnectionAfterTest(), declared,
                "@DbUnitConfig(closeConnectionAfterTest)");
        addIf(configuration.getDatabaseTesterFactory() != null, declared,
                "@DbUnitConfig(databaseTesterFactory)");
        addIf(configuration.getPrepAndExpectedTestCaseClass() != DefaultPrepAndExpectedTestCase.class,
                declared, "@DbUnitConfig(prepAndExpectedTestCase)");
        return declared;
    }

    private static void addIf(final boolean declared, final List<String> names,
            final String name)
    {
        if (declared)
        {
            names.add(name);
        }
    }
}
