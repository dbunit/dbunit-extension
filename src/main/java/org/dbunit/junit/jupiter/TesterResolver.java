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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.DefaultMethodOverrideCheck;
import org.dbunit.annotation.runtime.ProvidedAttribute;
import org.dbunit.annotation.runtime.ReflectiveInstantiation;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves, for one test, the {@link IDatabaseTester} and (when present) the
 * {@link PrepAndExpectedTestCase} instance {@link DbUnitExtension} drives - from a
 * {@link DbUnitTestCase @DbUnitTestCase} field, a {@link DbUnitTester @DbUnitTester} field,
 * {@link AnnotatedTestConfiguration#getDatabaseTesterFactory()}, or the 3.5.0 unmarked-field
 * auto-scan, first match wins, walking every test instance in scope innermost first for
 * {@code @Nested} support. See {@link DbUnitExtension}'s class Javadoc for the user-facing
 * precedence contract.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class TesterResolver
{
    private static final Logger log = LoggerFactory.getLogger(TesterResolver.class);

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
    Resolution resolve(final ExtensionContext context,
            final AnnotatedTestConfiguration configuration) throws Exception
    {
        final Class<?> testClass = context.getRequiredTestClass();
        final List<Object> instances = innermostFirst(context.getRequiredTestInstances());

        final MarkedFields marked = findMarkedFields(instances);
        final FieldMatch testCaseField = marked.testCaseField;
        final FieldMatch testerField = marked.testerField;

        if (testCaseField != null)
        {
            final Object testCaseFieldValue = testCaseField.value();
            if (testCaseFieldValue == null)
            {
                throw new IllegalStateException("PrepAndExpectedTestCase field '"
                        + testCaseField.field.getName() + "' in "
                        + testCaseField.instance.getClass().getName() + " is null.");
            }
            if (!(testCaseFieldValue instanceof PrepAndExpectedTestCase))
            {
                throw new IllegalStateException("Field '" + testCaseField.field.getName()
                        + "' in " + testCaseField.instance.getClass().getName()
                        + " is annotated @DbUnitTestCase, but its value's type ("
                        + testCaseFieldValue.getClass().getName()
                        + ") does not implement PrepAndExpectedTestCase.");
            }
            final PrepAndExpectedTestCase testCase =
                    (PrepAndExpectedTestCase) testCaseFieldValue;
            IDatabaseTester tester = testCase.getDatabaseTester();
            if (tester == null)
            {
                // testCase does not override getDatabaseTester()/setDatabaseTester(), or does
                // and was simply built without a tester yet (e.g. the no-arg-tester constructor
                // form) - fall back to the same resolution a bare @DbUnitTestCase-less test
                // would use, rather than leaving Resolution#tester null.
                tester = findTester(instances, testClass, configuration);
                // The executor drives this exact, already-injected instance directly (see
                // AnnotatedTestExecutor#beforeExpectedTest()) rather than constructing a fresh
                // one, so the fallback tester must be wired onto it here - otherwise it keeps
                // whatever databaseTester it reports for an implementation that does not
                // override setDatabaseTester().
                testCase.setDatabaseTester(tester);
                if (testCase.getDatabaseTester() != tester)
                {
                    if (overridesSetDatabaseTester(testCase))
                    {
                        // Opted into automatic wiring by overriding setDatabaseTester(), so a
                        // round-trip failure means the override itself is broken - fail fast the
                        // same way every other @DbUnitConfig-driven setter does for a silent
                        // no-op, rather than let @DbUnitSetup/@DbUnitTearDown operations quietly
                        // target a tester this test case never actually uses.
                        throw new IllegalStateException("Field '" + testCaseField.field.getName()
                                + "' in " + testCaseField.instance.getClass().getName()
                                + " is annotated @DbUnitTestCase; its value's type ("
                                + testCase.getClass().getName() + ") overrides"
                                + " setDatabaseTester(), but getDatabaseTester() does not return"
                                + " the same instance right after being given it."
                                + " @DbUnitSetup/@DbUnitTearDown operations set on the resolved"
                                + " IDatabaseTester would silently never reach the operations"
                                + " this test case actually runs. Fix setDatabaseTester()/"
                                + "getDatabaseTester() to round-trip the same instance.");
                    }
                    // A test case that never overrides setDatabaseTester() at all is the
                    // documented self-managed-connection pattern, not a bug - see
                    // annotations.adoc - so this stays a diagnostic log, not a failure.
                    log.debug("PrepAndExpectedTestCase {} does not round-trip"
                            + " getDatabaseTester()/setDatabaseTester(); @DbUnitSetup/"
                            + "@DbUnitTearDown operations set on the resolved IDatabaseTester"
                            + " may not reach the operations this test case actually runs"
                            + " unless it independently uses the same tester instance.",
                            testCase.getClass().getName());
                }
            }
            return new Resolution(tester, testCase);
        }

        if (testerField != null)
        {
            final Object testerFieldValue = testerField.value();
            if (testerFieldValue == null)
            {
                throw new IllegalStateException("IDatabaseTester field '"
                        + testerField.field.getName() + "' in "
                        + testerField.instance.getClass().getName() + " is null.");
            }
            if (!(testerFieldValue instanceof IDatabaseTester))
            {
                throw new IllegalStateException("Field '" + testerField.field.getName() + "' in "
                        + testerField.instance.getClass().getName()
                        + " is annotated @DbUnitTester, but its value's type ("
                        + testerFieldValue.getClass().getName()
                        + ") does not implement IDatabaseTester.");
            }
            return new Resolution((IDatabaseTester) testerFieldValue, null);
        }

        return new Resolution(findTester(instances, testClass, configuration), null);
    }

    /**
     * Returns whether {@code testCase}'s runtime type overrides
     * {@link PrepAndExpectedTestCase#setDatabaseTester(IDatabaseTester)}, rather than
     * inheriting the interface's own no-op default body - the same distinction
     * {@code org.dbunit.annotation.runtime.InjectedTestCaseConfigurer} makes for its
     * {@code @DbUnitConfig}-driven setters. Used by
     * {@link #resolve(ExtensionContext, AnnotatedTestConfiguration)} to tell a
     * {@link PrepAndExpectedTestCase} that deliberately manages its own tester - never
     * overriding this method, a documented and supported pattern - apart from one that opted
     * into automatic wiring by overriding it, but whose override does not actually work. See
     * {@link DefaultMethodOverrideCheck#overridesDefaultMethod} for this check's own known
     * limitation.
     *
     * @param testCase The instance to check.
     * @return True when {@code testCase}'s type overrides {@code setDatabaseTester()}.
     */
    private boolean overridesSetDatabaseTester(final PrepAndExpectedTestCase testCase)
    {
        return DefaultMethodOverrideCheck.overridesDefaultMethod(testCase.getClass(),
                PrepAndExpectedTestCase.class, "setDatabaseTester", IDatabaseTester.class);
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
        if (configuration.getDatabaseTesterFactory() != null)
        {
            final DatabaseTesterFactory factory = ReflectiveInstantiation.instantiate(
                    configuration.getDatabaseTesterFactory(),
                    "DbUnitConfig.databaseTesterFactory");
            return ProvidedAttribute.requireProvided(factory.getDatabaseTester(),
                    DatabaseTesterFactory.class.getSimpleName(),
                    configuration.getDatabaseTesterFactory(),
                    "DbUnitConfig.databaseTesterFactory", "getDatabaseTester");
        }
        return autoScanTesterField(instances, testClass);
    }

    /**
     * Finds the innermost test instance declaring a {@link DbUnitTestCase} or
     * {@link DbUnitTester} field, checking both markers together on each instance in turn - the
     * same innermost-instance-wins precedent {@link #autoScanTesterField} already establishes
     * for the plain, unannotated field - so a match on one instance shadows an outer instance's
     * marked field entirely, regardless of whether the outer field uses the same marker or the
     * other one, rather than the two being resolved independently and compared across instances.
     *
     * @throws IllegalStateException If one instance declares both markers, or more than one
     *             field for the same marker within one instance's class hierarchy.
     */
    private MarkedFields findMarkedFields(final List<Object> instances)
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
     * Finds a field annotated {@code marker} within one test instance's class hierarchy.
     *
     * @throws IllegalStateException If more than one such field is found.
     */
    private <A extends Annotation> FieldMatch findMarkedField(final Object instance,
            final Class<A> marker)
    {
        final List<Field> fields =
                AnnotationSupport.findAnnotatedFields(instance.getClass(), marker);
        if (fields.size() > 1)
        {
            throw new IllegalStateException("Multiple @" + marker.getSimpleName()
                    + " fields found in " + instance.getClass().getName() + ".");
        }
        if (fields.isEmpty())
        {
            return null;
        }

        final Field field = fields.get(0);
        field.setAccessible(true);
        return new FieldMatch(field, instance);
    }

    /**
     * The original (3.5.0) field auto-scan, unchanged: within one instance's class hierarchy
     * (most-derived class first), exactly one non-static field assignable to
     * {@link IDatabaseTester} - two or more at the same declaring class is ambiguous. Now also
     * tried across every test instance in scope, innermost first, for {@code @Nested} support.
     */
    private IDatabaseTester autoScanTesterField(final List<Object> instances,
            final Class<?> testClass) throws IllegalAccessException
    {
        for (final Object instance : instances)
        {
            Class<?> clazz = instance.getClass();
            while (clazz != null && clazz != Object.class)
            {
                final Field field = findTesterField(clazz, instance);
                if (field != null)
                {
                    field.setAccessible(true);
                    final IDatabaseTester tester = (IDatabaseTester) field.get(instance);
                    if (tester == null)
                    {
                        throw new IllegalStateException("IDatabaseTester field '"
                                + field.getName() + "' in " + instance.getClass().getName()
                                + " is null.");
                    }
                    log.debug("Resolved IDatabaseTester '{}' in {}", field.getName(),
                            instance.getClass().getName());
                    return tester;
                }
                clazz = clazz.getSuperclass();
            }
        }

        throw new IllegalStateException("No IDatabaseTester field found in "
                + testClass.getName() + " or its superclasses/enclosing classes. Declare a"
                + " non-static field whose type implements IDatabaseTester, mark it with"
                + " @DbUnitTester, or configure @DbUnitConfig(databaseTesterFactory = ...).");
    }

    private Field findTesterField(final Class<?> clazz, final Object instance)
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
                            + field.getName() + "'. Declare exactly one non-static field"
                            + " whose type implements IDatabaseTester in "
                            + instance.getClass().getName() + ".");
                }
                match = field;
            }
        }
        return match;
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
