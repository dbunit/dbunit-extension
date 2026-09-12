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
package org.dbunit.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.InjectedTestCaseTesterBinding;
import org.dbunit.annotation.runtime.ProvidedAttribute;
import org.dbunit.annotation.runtime.ReflectiveInstantiation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.context.TestContext;

/**
 * Resolves, for one test, the {@link IDatabaseTester} and (when present) the
 * {@link PrepAndExpectedTestCase} instance {@link DbUnitTestExecutionListener} drives.
 *
 * <p>First match wins:
 * <ol>
 *   <li>A field annotated {@link DbUnitTestCase}, whose type implements
 *   {@link PrepAndExpectedTestCase} - that instance is driven directly.</li>
 *   <li>A field annotated {@link DbUnitTester}, whose type implements {@link IDatabaseTester}.</li>
 *   <li>{@link org.dbunit.annotation.DbUnitConfig#databaseTesterFactory()}, reflectively
 *   instantiated and asked to create a tester.</li>
 *   <li>Exactly one {@link IDatabaseTester} bean in the test's {@code ApplicationContext} -
 *   Spring-specific; the JUnit 5 binding's resolver has no equivalent, since a JUnit 5 test has
 *   no bean container to ask. Falls through - rather than failing on an ambiguous match the way
 *   a marked field does - when the test has no {@code ApplicationContext}, or none or more than
 *   one such bean exists there, since an unused bean of this type is an unremarkable case for a
 *   Spring context.</li>
 *   <li>The 3.5.0-style auto-scan: exactly one non-static field assignable to
 *   {@link IDatabaseTester}.</li>
 * </ol>
 *
 * <p>Unlike {@code org.dbunit.junit.jupiter.TesterResolver}, there is exactly one test instance
 * to search - Spring's {@link TestContext} has no {@code @Nested}-style enclosing-instance chain
 * to walk - so field lookup here is a plain superclass-hierarchy walk over that one instance's
 * class, using {@code java.lang.reflect} directly rather than a JUnit Platform or Spring
 * annotation-scanning utility: a field annotation is never {@code @Inherited}, so neither would
 * shortcut the walk anyway.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class SpringTesterResolver
{
    private static final Logger log = LoggerFactory.getLogger(SpringTesterResolver.class);

    /**
     * Resolves the tester and test case for the current test.
     *
     * @param testContext The test context.
     * @param configuration The resolved configuration (for {@code databaseTesterFactory()}).
     * @return The resolved tester, and the test case when a {@code @DbUnitTestCase} field
     *         supplied one.
     * @throws Exception If a field is null or the wrong type, a factory fails, or no tester can
     *             be found.
     */
    Resolution resolve(final TestContext testContext,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        final Object testInstance = testContext.getTestInstance();
        final Class<?> testClass = testContext.getTestClass();

        final Field testCaseField = findMarkedField(testClass, DbUnitTestCase.class);
        final Field testerField = findMarkedField(testClass, DbUnitTester.class);
        if (testCaseField != null && testerField != null)
        {
            throw new IllegalStateException(
                    "Both @DbUnitTestCase and @DbUnitTester fields are declared in "
                            + testClass.getName() + "; declare at most one.");
        }

        if (testCaseField != null)
        {
            return resolveFromTestCaseField(testCaseField, testInstance, testContext,
                    configuration);
        }

        if (testerField != null)
        {
            return new Resolution(readTesterField(testerField, testInstance), null);
        }

        return new Resolution(findTester(testInstance, testContext, configuration), null);
    }

    private Resolution resolveFromTestCaseField(final Field field, final Object testInstance,
            final TestContext testContext, final AnnotatedTestConfiguration configuration)
            throws Exception
    {
        final Object fieldValue = readField(field, testInstance);
        final String fieldDescription =
                "Field '" + field.getName() + "' in " + testInstance.getClass().getName();
        if (fieldValue == null)
        {
            throw new IllegalStateException(
                    "PrepAndExpectedTestCase " + fieldDescription + " is null.");
        }
        if (!(fieldValue instanceof PrepAndExpectedTestCase))
        {
            throw new IllegalStateException(fieldDescription
                    + " is annotated @DbUnitTestCase, but its value's type ("
                    + fieldValue.getClass().getName()
                    + ") does not implement PrepAndExpectedTestCase.");
        }

        final PrepAndExpectedTestCase testCase = (PrepAndExpectedTestCase) fieldValue;
        final IDatabaseTester tester = InjectedTestCaseTesterBinding.resolveTester(testCase,
                fieldDescription, () -> findTester(testInstance, testContext, configuration));
        return new Resolution(tester, testCase);
    }

    private IDatabaseTester readTesterField(final Field field, final Object testInstance)
            throws IllegalAccessException
    {
        final Object fieldValue = readField(field, testInstance);
        if (fieldValue == null)
        {
            throw new IllegalStateException("IDatabaseTester field '" + field.getName() + "' in "
                    + testInstance.getClass().getName() + " is null.");
        }
        if (!(fieldValue instanceof IDatabaseTester))
        {
            throw new IllegalStateException("Field '" + field.getName() + "' in "
                    + testInstance.getClass().getName()
                    + " is annotated @DbUnitTester, but its value's type ("
                    + fieldValue.getClass().getName()
                    + ") does not implement IDatabaseTester.");
        }
        return (IDatabaseTester) fieldValue;
    }

    private IDatabaseTester findTester(final Object testInstance, final TestContext testContext,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        if (configuration.getDatabaseTesterFactory() != null)
        {
            final DatabaseTesterFactory factory =
                    ReflectiveInstantiation.instantiate(configuration.getDatabaseTesterFactory(),
                            "DbUnitConfig.databaseTesterFactory");
            return ProvidedAttribute.requireProvided(factory.getDatabaseTester(),
                    DatabaseTesterFactory.class.getSimpleName(),
                    configuration.getDatabaseTesterFactory(),
                    "DbUnitConfig.databaseTesterFactory", "getDatabaseTester");
        }

        final IDatabaseTester beanTester = findTesterBean(testContext);
        if (beanTester != null)
        {
            return beanTester;
        }

        return autoScanTesterField(testInstance);
    }

    private IDatabaseTester findTesterBean(final TestContext testContext)
    {
        if (!testContext.hasApplicationContext())
        {
            return null;
        }
        final ObjectProvider<IDatabaseTester> beans =
                testContext.getApplicationContext().getBeanProvider(IDatabaseTester.class);
        final IDatabaseTester tester = beans.getIfUnique();
        if (tester != null)
        {
            log.debug("Resolved IDatabaseTester bean of type '{}' from the ApplicationContext",
                    tester.getClass().getName());
        }
        return tester;
    }

    /**
     * The original (3.5.0) field auto-scan, unchanged in spirit from
     * {@code org.dbunit.junit.jupiter.TesterResolver}'s: within the test instance's class
     * hierarchy (most-derived class first), exactly one non-static field assignable to
     * {@link IDatabaseTester} - two or more at the same declaring class is ambiguous.
     */
    private IDatabaseTester autoScanTesterField(final Object testInstance)
            throws IllegalAccessException
    {
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && clazz != Object.class)
        {
            final Field field = findTesterField(clazz);
            if (field != null)
            {
                final IDatabaseTester tester = (IDatabaseTester) readField(field, testInstance);
                if (tester == null)
                {
                    throw new IllegalStateException("IDatabaseTester field '" + field.getName()
                            + "' in " + testInstance.getClass().getName() + " is null.");
                }
                log.debug("Resolved IDatabaseTester '{}' in {}", field.getName(),
                        testInstance.getClass().getName());
                return tester;
            }
            clazz = clazz.getSuperclass();
        }

        throw new IllegalStateException("No IDatabaseTester field or bean found for "
                + testInstance.getClass().getName() + ". Declare a non-static field whose type"
                + " implements IDatabaseTester, mark it with @DbUnitTester, expose a single"
                + " IDatabaseTester bean in the ApplicationContext, or configure"
                + " @DbUnitConfig(databaseTesterFactory = ...).");
    }

    private Field findTesterField(final Class<?> clazz)
    {
        Field match = null;
        for (final Field field : clazz.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers())
                    && IDatabaseTester.class.isAssignableFrom(field.getType()))
            {
                if (match != null)
                {
                    throw new IllegalStateException("Multiple IDatabaseTester fields found in "
                            + clazz.getName() + ": '" + match.getName() + "' and '"
                            + field.getName() + "'. Declare exactly one non-static field whose"
                            + " type implements IDatabaseTester in " + clazz.getName() + ".");
                }
                match = field;
            }
        }
        return match;
    }

    /**
     * Finds a field annotated {@code marker} within {@code testClass}'s own class hierarchy
     * (most-derived class first).
     *
     * @throws IllegalStateException If more than one such field is found at the same declaring
     *             class.
     */
    private <A extends Annotation> Field findMarkedField(final Class<?> testClass,
            final Class<A> marker)
    {
        Class<?> clazz = testClass;
        while (clazz != null && clazz != Object.class)
        {
            Field match = null;
            for (final Field field : clazz.getDeclaredFields())
            {
                if (field.isAnnotationPresent(marker))
                {
                    if (match != null)
                    {
                        throw new IllegalStateException("Multiple @" + marker.getSimpleName()
                                + " fields found in " + clazz.getName() + ".");
                    }
                    match = field;
                }
            }
            if (match != null)
            {
                return match;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private Object readField(final Field field, final Object instance)
            throws IllegalAccessException
    {
        field.setAccessible(true);
        return field.get(instance);
    }

    /**
     * The resolved tester, and the test case when a {@code @DbUnitTestCase} field supplied one.
     */
    static final class Resolution
    {
        final IDatabaseTester tester;
        final PrepAndExpectedTestCase testCase;

        private Resolution(final IDatabaseTester tester, final PrepAndExpectedTestCase testCase)
        {
            this.tester = tester;
            this.testCase = testCase;
        }
    }
}
