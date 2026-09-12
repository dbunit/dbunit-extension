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
package org.dbunit.junit.jupiter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;

/**
 * Resolves, for one test, the {@link IDatabaseTester} and (when present) the
 * {@link PrepAndExpectedTestCase} instance {@link DbUnitExtension} drives - from a
 * {@link DbUnitTestCase @DbUnitTestCase} field, a {@link DbUnitTester @DbUnitTester} field,
 * {@link AnnotatedTestConfiguration#getDatabaseTesterFactory()}, or the 3.5.0 unmarked-field
 * auto-scan, first match wins, walking every test instance in scope innermost first for
 * {@code @Nested} support. The rules themselves are {@link TestInstanceTesterResolver}'s,
 * shared with the Spring binding; this class only supplies the test instances JUnit knows about.
 * See {@link DbUnitExtension}'s class Javadoc for the user-facing precedence contract.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class TesterResolver
{
    /**
     * Resolves the tester and test case for the current test.
     *
     * @param context The extension context for the test method.
     * @param configuration The resolved configuration (for
     *            {@code databaseTesterFactory()}).
     * @return The resolved tester, and the test case when a {@code @DbUnitTestCase} field
     *         supplied one.
     * @throws Exception If a field is null or the wrong type, a factory fails, or no tester can
     *             be found.
     */
    TestInstanceTesterResolver.Resolution resolve(final ExtensionContext context,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        final Class<?> testClass = context.getRequiredTestClass();
        final List<Object> instances = innermostFirst(context.getRequiredTestInstances());
        return TestInstanceTesterResolver.resolve(instances,
                () -> findTester(instances, testClass, configuration));
    }

    private List<Object> innermostFirst(final TestInstances instances)
    {
        final List<Object> all = new ArrayList<>(instances.getAllInstances());
        Collections.reverse(all);
        return all;
    }

    private IDatabaseTester findTester(final List<Object> instances, final Class<?> testClass,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        final IDatabaseTester factoryTester =
                TestInstanceTesterResolver.fromFactory(configuration);
        if (factoryTester != null)
        {
            return factoryTester;
        }
        final IDatabaseTester fieldTester = TestInstanceTesterResolver.fromUnmarkedField(instances);
        if (fieldTester != null)
        {
            return fieldTester;
        }
        throw new IllegalStateException("No IDatabaseTester field found in "
                + testClass.getName() + " or its superclasses/enclosing classes. Declare a"
                + " non-static field whose type implements IDatabaseTester, mark it with"
                + " @DbUnitTester, or configure @DbUnitConfig(databaseTesterFactory = ...).");
    }
}
