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

/**
 * Rejects the declarations that ask more of an injected {@code @DbUnitTestCase} than it gives.
 * An injected test case is used as its owner built it: dbUnit gives it the test's files and
 * calls {@code configureTest()}, {@code preTest()} and {@code postTest()}, and for a
 * {@link DefaultPrepAndExpectedTestCase} applies {@code @DbUnitRowCountCheck} through its
 * override methods, and nothing else. Everything that would configure it, or needs a tester or a
 * connection of it that a test case which is not a {@link DefaultPrepAndExpectedTestCase} cannot
 * tell dbUnit about, is rejected with one message when declared instead of silently never
 * taking effect. The loader the test's files need is checked separately, just before the test
 * case is used, see {@link #requireDataFileLoaderForTheFiles(PrepAndExpectedTestCase)}.
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
     * @throws IllegalStateException If the test declares anything that needs more than
     *             {@code testCase} gives, naming all of it.
     */
    void requireSupportedBy(final PrepAndExpectedTestCase testCase)
    {
        final boolean defaultTestCase = testCase instanceof DefaultPrepAndExpectedTestCase;
        final List<String> unsupported = declarationsThatConfigureTheTestCase();
        if (!defaultTestCase)
        {
            addIf(configuration.isRowCountCheckDeclared(), unsupported, "@DbUnitRowCountCheck");
            addIf(!configuration.isExpected(), unsupported,
                    "a test without @DbUnitExpected, which would have no tester to set up");
        }
        if (unsupported.isEmpty())
        {
            return;
        }
        final String gives;
        if (defaultTestCase)
        {
            gives = "uses it as it was built: dbUnit gives it the test's files, applies"
                    + " @DbUnitRowCountCheck, and calls configureTest(), preTest() and"
                    + " postTest()";
        } else
        {
            gives = "is not a DefaultPrepAndExpectedTestCase, so dbUnit only gives it the"
                    + " test's files and calls configureTest(), preTest() and postTest()";
        }
        throw new IllegalStateException("The @DbUnitTestCase field's test case "
                + testCase.getClass().getName() + " " + gives + ": it cannot apply "
                + String.join(", ", unsupported)
                + ". Configure the test case where it is built, or drop what the test declares.");
    }

    /**
     * Fails when the test names dataset files and an injected
     * {@link DefaultPrepAndExpectedTestCase} has no loader to read them with: dbUnit gives it
     * none, as it uses the instance as it was built. Run just before the test case is used
     * rather than when the executor is created, as a {@code @BeforeEach} method of the test
     * class may still be setting the loader. Any other {@link PrepAndExpectedTestCase} tells
     * dbUnit of no loader, so nothing is checked for it. The tester is not checked here: the
     * {@link TestInstanceTesterResolver} requires one of the instance when it resolves it.
     *
     * @param testCase The injected {@code @DbUnitTestCase} instance.
     * @throws IllegalStateException If the test names dataset files and {@code testCase} has no
     *             loader.
     */
    void requireDataFileLoaderForTheFiles(final PrepAndExpectedTestCase testCase)
    {
        if (!(testCase instanceof DefaultPrepAndExpectedTestCase))
        {
            return;
        }

        final boolean namesFiles = configuration.getPrepDataFiles().length > 0
                || configuration.getExpectedDataFiles().length > 0;
        final DefaultPrepAndExpectedTestCase defaultTestCase =
                (DefaultPrepAndExpectedTestCase) testCase;
        if (namesFiles && defaultTestCase.getDataFileLoader() == null)
        {
            throw new IllegalStateException("The @DbUnitTestCase field's test case "
                    + testCase.getClass().getName() + " has no DataFileLoader to read the test's"
                    + " dataset files with. dbUnit uses it as it was built: give it one where it"
                    + " is built, or in a @BeforeEach method.");
        }
    }

    private List<String> declarationsThatConfigureTheTestCase()
    {
        final List<String> declared = new ArrayList<>();
        addIf(configuration.isSetupDeclared(), declared, "@DbUnitSetUpOperation");
        addIf(configuration.isTearDownDeclared(), declared, "@DbUnitTearDownOperation");
        addIf(configuration.isDataFileLoaderDeclared(), declared,
                "@DbUnitConfig(dataFileLoader)");
        addIf(configuration.getFailureHandler() != null, declared, "@DbUnitConfig(failureHandler)");
        addIf(!configuration.getDatabaseConfigProperties().isEmpty(), declared,
                "@DbUnitConfig(properties)");
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
