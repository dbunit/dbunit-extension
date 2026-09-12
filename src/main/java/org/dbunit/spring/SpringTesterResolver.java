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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Resolves, for one test, the {@link IDatabaseTester} and (when present) the
 * {@link org.dbunit.PrepAndExpectedTestCase} instance {@link DbUnitTestExecutionListener}
 * drives, in the order that class's Javadoc documents: marked field, database tester factory,
 * unmarked field, and last the {@code ApplicationContext} bean.
 *
 * <p>The field rules - which fields count, and what is ambiguous - are
 * {@link TestInstanceTesterResolver}'s, shared with the JUnit 5 binding, so a mistake gets the
 * same message under either. The test instances they search are those of a
 * {@link TestInstanceChain}: the one Spring's {@link TestContext} holds and, for a
 * {@code @Nested} class, the enclosing instances reachable from it, innermost first.
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
     * @throws Exception If a field is null or the wrong type, a factory fails, no tester can be
     *             found, or a tester field of an enclosing class cannot be reached.
     */
    TestInstanceTesterResolver.Resolution resolve(final TestContext testContext,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        final Object testInstance = testContext.getTestInstance();
        final Class<?> testClass = testContext.getTestClass();
        final TestInstanceChain chain = TestInstanceChain.of(testInstance);
        rejectUnreachableTesterFields(chain, testClass);
        final List<Object> instances = chain.getInnermostFirst();
        return TestInstanceTesterResolver.resolve(instances,
                () -> findTester(instances, testContext, configuration));
    }

    private IDatabaseTester findTester(final List<Object> instances,
            final TestContext testContext, final AnnotatedTestConfiguration configuration)
            throws Exception
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

        final ApplicationContext applicationContext = findApplicationContext(testContext);
        if (applicationContext != null)
        {
            final IDatabaseTester beanTester = findTesterBean(applicationContext);
            if (beanTester != null)
            {
                return beanTester;
            }
        }

        final Class<?> testClass = testContext.getTestClass();
        final boolean contextConsulted = applicationContext != null;
        throw new IllegalStateException(noTesterMessage(testClass, contextConsulted));
    }

    /**
     * Returns the test's {@code ApplicationContext}, or {@code null} when there is none to ask.
     * A context Spring has not loaded yet - no listener has needed it so far, for example when
     * the {@code DependencyInjectionTestExecutionListener} was dropped - is loaded here if the
     * test class declares a context configuration to load it from.
     */
    private ApplicationContext findApplicationContext(final TestContext testContext)
    {
        if (testContext.hasApplicationContext()
                || declaresContextConfiguration(testContext.getTestClass()))
        {
            return testContext.getApplicationContext();
        }
        return null;
    }

    private boolean declaresContextConfiguration(final Class<?> testClass)
    {
        return TestContextAnnotationUtils.hasAnnotation(testClass, ContextConfiguration.class)
                || TestContextAnnotationUtils.hasAnnotation(testClass, ContextHierarchy.class);
    }

    private IDatabaseTester findTesterBean(final ApplicationContext applicationContext)
    {
        final ObjectProvider<IDatabaseTester> beans =
                applicationContext.getBeanProvider(IDatabaseTester.class);
        final IDatabaseTester tester = beans.getIfUnique();
        if (tester != null)
        {
            log.debug("Resolved IDatabaseTester bean of type '{}' from the ApplicationContext",
                    tester.getClass().getName());
        }
        return tester;
    }

    private String noTesterMessage(final Class<?> testClass, final boolean contextConsulted)
    {
        final String message = "No IDatabaseTester field or bean found for " + testClass.getName()
                + " or its superclasses/enclosing classes. Declare a non-static field whose type"
                + " implements IDatabaseTester, mark it with @DbUnitTester, expose a single"
                + " IDatabaseTester bean in the ApplicationContext, or configure"
                + " @DbUnitConfig(databaseTesterFactory = ...).";
        if (contextConsulted)
        {
            return message;
        }
        return message + " The ApplicationContext was not loaded for this test, so no bean was"
                + " looked up: the test class declares no @ContextConfiguration or"
                + " @ContextHierarchy, or @TestExecutionListeners replaced Spring's default"
                + " listeners, dropping the DependencyInjectionTestExecutionListener that loads"
                + " the context - add mergeMode = MERGE_WITH_DEFAULTS.";
    }

    /**
     * Fails when a tester field sits on an enclosing class whose instance could not be reached,
     * rather than carry on and silently drive a different tester - e.g. an
     * {@code ApplicationContext} bean - than the one the test class declares.
     */
    private void rejectUnreachableTesterFields(final TestInstanceChain chain,
            final Class<?> testClass)
    {
        for (final Class<?> enclosingClass : chain.getUnreachableEnclosingClasses())
        {
            final Field field = findTesterField(enclosingClass);
            if (field != null)
            {
                throw new IllegalStateException("The enclosing class " + enclosingClass.getName()
                        + " declares the tester field '" + field.getName()
                        + "', but the enclosing instance of the @Nested class "
                        + testClass.getName() + " cannot be reached: since JDK 18 javac omits an"
                        + " inner class's reference to its enclosing instance unless the class"
                        + " uses it. Use the enclosing instance somewhere in the @Nested class,"
                        + " declare the tester field in the @Nested class itself, or expose an"
                        + " IDatabaseTester bean in the ApplicationContext.");
            }
        }
    }

    /**
     * Finds a field anywhere in {@code clazz}'s hierarchy the field rules would treat as the
     * tester: marked {@link DbUnitTester} or {@link DbUnitTestCase}, or a non-static field
     * assignable to {@link IDatabaseTester}.
     */
    private Field findTesterField(final Class<?> clazz)
    {
        Class<?> current = clazz;
        while (current != null && current != Object.class)
        {
            for (final Field field : current.getDeclaredFields())
            {
                if (field.isAnnotationPresent(DbUnitTester.class)
                        || field.isAnnotationPresent(DbUnitTestCase.class)
                        || (!Modifier.isStatic(field.getModifiers())
                                && IDatabaseTester.class.isAssignableFrom(field.getType())))
                {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
