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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.Callable;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The binding-neutral part of resolving, for one test, the {@link IDatabaseTester} and (when
 * present) the {@link PrepAndExpectedTestCase} an {@link AnnotatedTestExecutor} drives: what the
 * test instances' fields and {@link DbUnitConfig#databaseTesterFactory()} say. Written against
 * plain {@code java.lang.reflect}, so {@code DbUnitExtension} (JUnit 5/6) and
 * {@code DbUnitTestExecutionListener} (Spring) share one set of rules and one set of messages
 * for the same mistakes, whichever test framework or annotation-scanning utility each binding
 * itself uses.
 *
 * <p>A binding passes the test instances in scope, innermost first - for a {@code @Nested} test
 * class, its own instance, then its enclosing class's instance, and so on outward - and supplies
 * only what differs between frameworks: where to look when no field names a tester. Resolution
 * is first match wins:
 * <ol>
 *   <li>A field annotated {@link DbUnitTestCase}, whose type implements
 *   {@link PrepAndExpectedTestCase} - that instance is driven directly.</li>
 *   <li>A field annotated {@link DbUnitTester}, whose type implements
 *   {@link IDatabaseTester}.</li>
 *   <li>Whatever the binding's {@code unmarkedTester} supplies, normally built from
 *   {@link #fromFactory(AnnotatedTestConfiguration)} and
 *   {@link #fromUnmarkedField(List)} plus the binding's own tiers.</li>
 * </ol>
 *
 * <p>The first instance, innermost first, that declares either marker supplies the marked field
 * and shadows every outer instance entirely, whichever marker the outer one uses. Within one
 * instance, the whole class hierarchy is searched, and a marked field is ambiguous - rejected
 * with an {@link IllegalStateException} - when it is declared more than once there, on one class
 * or across a class and its superclass, or when both markers are declared.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can reach it too.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class TestInstanceTesterResolver
{
    private static final Logger log = LoggerFactory.getLogger(TestInstanceTesterResolver.class);

    private TestInstanceTesterResolver()
    {
    }

    /**
     * Resolves the tester and test case from the marked fields of {@code instances}, falling
     * back to {@code unmarkedTester} when no instance declares a marked field - and for the
     * tester a {@code @DbUnitTestCase} field's test case does not carry itself.
     *
     * @param instances The test instances in scope, innermost first.
     * @param unmarkedTester Supplies the tester when no {@code @DbUnitTester} field does; must
     *            throw rather than return {@code null} when it finds none.
     * @return The resolved tester, and the test case when a {@code @DbUnitTestCase} field
     *         supplied one.
     * @throws IllegalStateException If a marked field is ambiguous, null, or of the wrong type.
     * @throws Exception If {@code unmarkedTester} fails.
     */
    public static Resolution resolve(final List<Object> instances,
            final Callable<IDatabaseTester> unmarkedTester) throws Exception
    {
        final MarkedFields marked = findMarkedFields(instances);
        if (marked.testCaseField != null)
        {
            return resolveFromTestCaseField(marked.testCaseField, unmarkedTester);
        }
        if (marked.testerField != null)
        {
            final IDatabaseTester markedTester = readTesterField(marked.testerField);
            return new Resolution(markedTester, null);
        }
        final IDatabaseTester tester = unmarkedTester.call();
        return new Resolution(tester, null);
    }

    /**
     * Creates the tester {@link DbUnitConfig#databaseTesterFactory()} names.
     *
     * @param configuration The resolved configuration.
     * @return The factory-created tester, or {@code null} when no factory is configured.
     * @throws IllegalStateException If the factory cannot be instantiated or returns
     *             {@code null}.
     * @throws Exception If the factory fails creating the tester.
     */
    public static IDatabaseTester fromFactory(final AnnotatedTestConfiguration configuration)
            throws Exception
    {
        final Class<? extends DatabaseTesterFactory> factoryClass =
                configuration.getDatabaseTesterFactory();
        if (factoryClass == null)
        {
            return null;
        }
        final DatabaseTesterFactory factory =
                ReflectiveInstantiation.instantiate(factoryClass,
                        "DbUnitConfig.databaseTesterFactory");
        final IDatabaseTester created = factory.getDatabaseTester();
        return ProvidedAttribute.requireProvided(created,
                DatabaseTesterFactory.class.getSimpleName(), factoryClass,
                "DbUnitConfig.databaseTesterFactory", "getDatabaseTester");
    }

    /**
     * The original (3.5.0) field auto-scan: within each instance, innermost first, and within its
     * class hierarchy (most-derived class first), the first class declaring exactly one
     * non-static field assignable to {@link IDatabaseTester}; two or more at the same declaring
     * class are ambiguous.
     *
     * @param instances The test instances in scope, innermost first.
     * @return The tester that field holds, or {@code null} when no instance declares such a
     *         field.
     * @throws IllegalStateException If a class declares more than one such field, or the one
     *             found holds {@code null}.
     * @throws IllegalAccessException If the field cannot be read.
     */
    public static IDatabaseTester fromUnmarkedField(final List<Object> instances)
            throws IllegalAccessException
    {
        for (final Object instance : instances)
        {
            Class<?> clazz = instance.getClass();
            while (clazz != null && clazz != Object.class)
            {
                final Field field = findUnmarkedTesterField(clazz, instance);
                if (field != null)
                {
                    return readUnmarkedTesterField(field, instance);
                }
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static Resolution resolveFromTestCaseField(final FieldMatch testCaseField,
            final Callable<IDatabaseTester> unmarkedTester) throws Exception
    {
        final Object value = testCaseField.value();
        if (value == null)
        {
            throw new IllegalStateException("PrepAndExpectedTestCase field '"
                    + testCaseField.field.getName() + "' in " + testCaseField.instanceClassName()
                    + " is null.");
        }
        final String fieldDescription = testCaseField.describe();
        if (!(value instanceof PrepAndExpectedTestCase))
        {
            throw new IllegalStateException(fieldDescription
                    + " is annotated @DbUnitTestCase, but its value's type ("
                    + value.getClass().getName() + ") does not implement PrepAndExpectedTestCase.");
        }
        final PrepAndExpectedTestCase testCase = (PrepAndExpectedTestCase) value;
        // testCase does not override getDatabaseTester()/setDatabaseTester(), or does and was
        // simply built without a tester yet (e.g. the no-arg-tester constructor form) - the
        // fallback resolves the same tester a bare @DbUnitTestCase-less test would use, rather
        // than leaving the resolution's tester null.
        final IDatabaseTester tester = InjectedTestCaseTesterBinding.resolveTester(testCase,
                fieldDescription, unmarkedTester);
        return new Resolution(tester, testCase);
    }

    private static IDatabaseTester readTesterField(final FieldMatch testerField)
            throws IllegalAccessException
    {
        final Object value = testerField.value();
        if (value == null)
        {
            throw new IllegalStateException("IDatabaseTester field '"
                    + testerField.field.getName() + "' in " + testerField.instanceClassName()
                    + " is null.");
        }
        if (!(value instanceof IDatabaseTester))
        {
            throw new IllegalStateException(testerField.describe()
                    + " is annotated @DbUnitTester, but its value's type ("
                    + value.getClass().getName() + ") does not implement IDatabaseTester.");
        }
        return (IDatabaseTester) value;
    }

    /**
     * Finds the innermost test instance declaring a {@link DbUnitTestCase} or
     * {@link DbUnitTester} field, checking both markers together on each instance in turn - the
     * same innermost-instance-wins precedent {@link #fromUnmarkedField(List)} establishes for the
     * plain, unannotated field - so a match on one instance shadows an outer instance's marked
     * field entirely, regardless of whether the outer field uses the same marker or the other
     * one, rather than the two being resolved independently and compared across instances.
     *
     * @throws IllegalStateException If one instance declares both markers, or more than one
     *             field for the same marker within one instance's class hierarchy.
     */
    private static MarkedFields findMarkedFields(final List<Object> instances)
    {
        for (final Object instance : instances)
        {
            final FieldMatch testCaseField = findMarkedField(instance, DbUnitTestCase.class);
            final FieldMatch testerField = findMarkedField(instance, DbUnitTester.class);
            if (testCaseField != null && testerField != null)
            {
                throw new IllegalStateException(
                        "Both @DbUnitTestCase and @DbUnitTester fields are declared in "
                                + instance.getClass().getName() + "; declare at most one.");
            }
            if (testCaseField != null || testerField != null)
            {
                return new MarkedFields(testCaseField, testerField);
            }
        }
        return new MarkedFields(null, null);
    }

    /**
     * Finds a field annotated {@code marker} within one test instance's whole class hierarchy.
     *
     * @throws IllegalStateException If more than one such field is found.
     */
    private static FieldMatch findMarkedField(final Object instance,
            final Class<? extends Annotation> marker)
    {
        Field match = null;
        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class)
        {
            for (final Field field : clazz.getDeclaredFields())
            {
                if (field.isAnnotationPresent(marker))
                {
                    if (match != null)
                    {
                        throw new IllegalStateException("Multiple @" + marker.getSimpleName()
                                + " fields found in " + instance.getClass().getName() + ".");
                    }
                    match = field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        if (match == null)
        {
            return null;
        }
        match.setAccessible(true);
        return new FieldMatch(match, instance);
    }

    private static Field findUnmarkedTesterField(final Class<?> clazz, final Object instance)
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
                            + " type implements IDatabaseTester in "
                            + instance.getClass().getName() + ".");
                }
                match = field;
            }
        }
        return match;
    }

    private static IDatabaseTester readUnmarkedTesterField(final Field field,
            final Object instance) throws IllegalAccessException
    {
        field.setAccessible(true);
        final IDatabaseTester tester = (IDatabaseTester) field.get(instance);
        if (tester == null)
        {
            throw new IllegalStateException("IDatabaseTester field '" + field.getName() + "' in "
                    + instance.getClass().getName() + " is null.");
        }
        log.debug("Resolved IDatabaseTester '{}' in {}", field.getName(),
                instance.getClass().getName());
        return tester;
    }

    /**
     * The resolved tester, and the test case when a {@code @DbUnitTestCase} field supplied one.
     */
    public static final class Resolution
    {
        private final IDatabaseTester tester;
        private final PrepAndExpectedTestCase testCase;

        private Resolution(final IDatabaseTester tester, final PrepAndExpectedTestCase testCase)
        {
            this.tester = tester;
            this.testCase = testCase;
        }

        /**
         * Returns the tester to drive.
         *
         * @return The resolved tester.
         */
        public IDatabaseTester getTester()
        {
            return tester;
        }

        /**
         * Returns the test case a {@code @DbUnitTestCase} field supplied.
         *
         * @return The test case, or {@code null} when no such field exists.
         */
        public PrepAndExpectedTestCase getTestCase()
        {
            return testCase;
        }
    }

    private static final class FieldMatch
    {
        private final Field field;
        private final Object instance;

        private FieldMatch(final Field field, final Object instance)
        {
            this.field = field;
            this.instance = instance;
        }

        private Object value() throws IllegalAccessException
        {
            return field.get(instance);
        }

        private String instanceClassName()
        {
            return instance.getClass().getName();
        }

        private String describe()
        {
            return "Field '" + field.getName() + "' in " + instanceClassName();
        }
    }

    private static final class MarkedFields
    {
        private final FieldMatch testCaseField;
        private final FieldMatch testerField;

        private MarkedFields(final FieldMatch testCaseField, final FieldMatch testerField)
        {
            this.testCaseField = testCaseField;
            this.testerField = testerField;
        }
    }
}
