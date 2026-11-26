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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
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
 * <p>A binding that cannot get the instance of an enclosing class can still hand over the class
 * itself: such a class is searched after every instance, outermost last, and only for its
 * {@code static} marked fields, which need no instance. The same rules apply to it.
 *
 * <p>Public so a binding outside {@code org.dbunit.annotation.runtime} - such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener} - can reach it too.
 *
 * <p>This type is internal to dbUnit's bindings; it is public only for use across
 * dbUnit's packages and may change in a minor release.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class TestInstanceTesterResolver
{
    private static final Logger log = LoggerFactory.getLogger(TestInstanceTesterResolver.class);

    private static final ClassValue<SharedFactory> FACTORIES = new ClassValue<SharedFactory>()
    {
        /**
         * {@inheritDoc}
         * <p>
         * Creates the holder of the factory the first time a caller asks for the class, and the
         * {@link ClassValue} keeps it from then on. The holder goes away with the factory class
         * and its class loader.
         */
        @Override
        protected SharedFactory computeValue(final Class<?> factoryClass)
        {
            return new SharedFactory(factoryClass.asSubclass(DatabaseTesterFactory.class));
        }
    };

    private final TesterFields testerFields;

    /**
     * Creates a resolver that finds tester fields by the given rules.
     *
     * @param testerFields The rules for which fields are tester fields; share the instance with
     *            whatever else of the binding applies them.
     */
    public TestInstanceTesterResolver(final TesterFields testerFields)
    {
        this.testerFields = testerFields;
    }

    /**
     * Resolves the tester and test case from the marked fields of {@code instances}, falling
     * back to {@code unmarkedTester} when no instance declares a marked field. A
     * {@code @DbUnitTestCase} field supplies its test case and that test case's own tester: it is
     * used as its owner built it.
     *
     * @param instances The test instances in scope, innermost first.
     * @param unmarkedTester Supplies the tester when no marked field does; must throw rather
     *            than return {@code null} when it finds none.
     * @return The resolved tester, and the test case when a {@code @DbUnitTestCase} field
     *         supplied one.
     * @throws IllegalStateException If a marked field is ambiguous, null, or of the wrong type,
     *             or a {@link DefaultPrepAndExpectedTestCase} it holds has no tester.
     * @throws Exception If {@code unmarkedTester} fails.
     */
    public Resolution resolve(final List<Object> instances,
            final Callable<IDatabaseTester> unmarkedTester) throws Exception
    {
        final List<Class<?>> noClasses = Collections.emptyList();
        return resolve(instances, noClasses, unmarkedTester);
    }

    /**
     * Resolves the tester and test case as {@link #resolve(List, Callable)} does, and also from
     * the {@code static} marked fields of enclosing classes whose instances the binding could
     * not get. Such a class ranks after every instance in {@code instances}, in the order given,
     * and only its {@code static} marked fields count, as a field of an instance needs the
     * instance. The binding decides what an instance field of such a class means.
     *
     * @param instances The test instances in scope, innermost first.
     * @param classesWithoutInstance The enclosing classes in scope whose instances are not
     *            available, innermost first, and that rank after the instances.
     * @param unmarkedTester Supplies the tester when no marked field does; must throw rather
     *            than return {@code null} when it finds none.
     * @return The resolved tester, and the test case when a {@code @DbUnitTestCase} field
     *         supplied one.
     * @throws IllegalStateException If a marked field is ambiguous, null, or of the wrong type,
     *             or a {@link DefaultPrepAndExpectedTestCase} it holds has no tester.
     * @throws Exception If {@code unmarkedTester} fails.
     */
    public Resolution resolve(final List<Object> instances,
            final List<Class<?>> classesWithoutInstance,
            final Callable<IDatabaseTester> unmarkedTester) throws Exception
    {
        final MarkedFields marked = findMarkedFields(instances, classesWithoutInstance);
        if (marked.testCaseField != null)
        {
            return resolveFromTestCaseField(marked.testCaseField);
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
     * Creates the tester {@link DbUnitConfig#databaseTesterFactory()} names. The first test that
     * names a factory class instantiates it, and the one instance serves every test of the run
     * that names the class, whichever binding resolves it, so it must be thread-safe. Each call
     * asks that instance for a tester, so a factory decides whether every test gets a new
     * tester or all of them share one.
     *
     * @param configuration The resolved configuration.
     * @return The factory-created tester, or {@code null} when no factory is configured.
     * @throws IllegalStateException If the factory cannot be instantiated or returns
     *             {@code null}. A factory whose constructor throws leaves no instance behind, so
     *             the next test that names it tries again.
     * @throws Exception If the factory fails creating the tester.
     */
    public IDatabaseTester fromFactory(final AnnotatedTestConfiguration configuration)
            throws Exception
    {
        final Class<? extends DatabaseTesterFactory> factoryClass =
                configuration.getDatabaseTesterFactory();
        if (factoryClass == null)
        {
            return null;
        }
        final SharedFactory sharedFactory = FACTORIES.get(factoryClass);
        final DatabaseTesterFactory factory = sharedFactory.instance();
        final IDatabaseTester created = factory.getDatabaseTester();
        if (created == null)
        {
            throw new IllegalStateException("DatabaseTesterFactory " + factoryClass.getName()
                    + ", named by DbUnitConfig.databaseTesterFactory, returned null from"
                    + " getDatabaseTester().");
        }
        return created;
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
    public IDatabaseTester fromUnmarkedField(final List<Object> instances)
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

    private Resolution resolveFromTestCaseField(final FieldMatch testCaseField)
            throws Exception
    {
        final Object value = testCaseField.value();
        if (value == null)
        {
            throw new IllegalStateException("PrepAndExpectedTestCase field '"
                    + testCaseField.field.getName() + "' in " + testCaseField.scopeClassName()
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
        if (!(testCase instanceof DefaultPrepAndExpectedTestCase))
        {
            // driven as it is: it tells dbUnit of no tester
            return new Resolution(null, testCase);
        }
        final DefaultPrepAndExpectedTestCase defaultTestCase =
                (DefaultPrepAndExpectedTestCase) testCase;
        final IDatabaseTester tester = defaultTestCase.getDatabaseTester();
        if (tester == null)
        {
            throw new IllegalStateException(fieldDescription + " is annotated @DbUnitTestCase, but"
                    + " its DefaultPrepAndExpectedTestCase has no IDatabaseTester. dbUnit uses the"
                    + " instance as its owner built it: give it a tester where it is built.");
        }
        return new Resolution(tester, testCase);
    }

    private IDatabaseTester readTesterField(final FieldMatch testerField)
            throws IllegalAccessException
    {
        final Object value = testerField.value();
        if (value == null)
        {
            throw new IllegalStateException("IDatabaseTester field '"
                    + testerField.field.getName() + "' in " + testerField.scopeClassName()
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
     * Then it checks the enclosing classes without an instance in the same way, for their
     * {@code static} marked fields only.
     *
     * @throws IllegalStateException If one instance or class declares both markers, or more than
     *             one field for the same marker within one instance's or class's hierarchy.
     */
    private MarkedFields findMarkedFields(final List<Object> instances,
            final List<Class<?>> classesWithoutInstance)
    {
        for (final Object instance : instances)
        {
            final MarkedFields marked = findMarkedFieldsOf(instance.getClass(), instance);
            if (marked != null)
            {
                return marked;
            }
        }
        for (final Class<?> clazz : classesWithoutInstance)
        {
            final MarkedFields marked = findMarkedFieldsOf(clazz, null);
            if (marked != null)
            {
                return marked;
            }
        }
        return new MarkedFields(null, null);
    }

    /**
     * Finds the marked fields of one class, with its superclasses, read from {@code instance}
     * or, when that is {@code null}, from the class itself, which leaves out the fields that need
     * an instance.
     *
     * @return The marked fields, or {@code null} when the class declares none.
     */
    private MarkedFields findMarkedFieldsOf(final Class<?> scope, final Object instance)
    {
        final FieldMatch testCaseField = findMarkedField(scope, instance, DbUnitTestCase.class);
        final FieldMatch testerField = findMarkedField(scope, instance, DbUnitTester.class);
        if (testCaseField != null && testerField != null)
        {
            throw new IllegalStateException(
                    "Both @DbUnitTestCase and @DbUnitTester fields are declared in "
                            + scope.getName() + "; declare at most one.");
        }
        if (testCaseField != null || testerField != null)
        {
            return new MarkedFields(testCaseField, testerField);
        }
        return null;
    }

    /**
     * Finds a field annotated {@code marker} within one class's whole hierarchy, leaving out the
     * fields that need an instance when {@code instance} is {@code null}.
     *
     * @throws IllegalStateException If more than one such field is found.
     */
    private FieldMatch findMarkedField(final Class<?> scope, final Object instance,
            final Class<? extends Annotation> marker)
    {
        final List<Field> matches = testerFields.findAll(scope,
                field -> field.isAnnotationPresent(marker)
                        && (instance != null || Modifier.isStatic(field.getModifiers())));
        if (matches.size() > 1)
        {
            final Field first = matches.get(0);
            final Field second = matches.get(1);
            throw new IllegalStateException("Multiple @" + marker.getSimpleName()
                    + " fields found in " + scope.getName() + ": '"
                    + first.getDeclaringClass().getSimpleName() + "." + first.getName()
                    + "' and '" + second.getDeclaringClass().getSimpleName() + "."
                    + second.getName() + "'. Mark only one field @" + marker.getSimpleName()
                    + ".");
        }
        if (matches.isEmpty())
        {
            return null;
        }
        final Field match = matches.get(0);
        match.setAccessible(true);
        return new FieldMatch(match, instance, scope);
    }

    private Field findUnmarkedTesterField(final Class<?> clazz, final Object instance)
    {
        final List<Field> matches = testerFields.declaredBy(clazz, testerFields::isUnmarkedTester);
        if (matches.size() > 1)
        {
            throw new IllegalStateException("Multiple IDatabaseTester fields found in "
                    + clazz.getName() + ": '" + matches.get(0).getName() + "' and '"
                    + matches.get(1).getName() + "'. Declare exactly one non-static field whose"
                    + " type implements IDatabaseTester in " + instance.getClass().getName()
                    + ".");
        }
        if (matches.isEmpty())
        {
            return null;
        }
        return matches.get(0);
    }

    private IDatabaseTester readUnmarkedTesterField(final Field field,
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
     *
     * <p>This type is internal to dbUnit's bindings; it is public only for use across
     * dbUnit's packages and may change in a minor release.
     */
    public static class Resolution
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
         * @return The resolved tester; {@code null} for a {@code @DbUnitTestCase} that is not a
         *         {@link DefaultPrepAndExpectedTestCase}, which tells dbUnit of none.
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

    private static class FieldMatch
    {
        private final Field field;
        private final Object instance;
        private final Class<?> scope;

        private FieldMatch(final Field field, final Object instance, final Class<?> scope)
        {
            this.field = field;
            this.instance = instance;
            this.scope = scope;
        }

        /** Reads the field; {@code instance} is {@code null} for a {@code static} one. */
        private Object value() throws IllegalAccessException
        {
            return field.get(instance);
        }

        private String scopeClassName()
        {
            return scope.getName();
        }

        private String describe()
        {
            return "Field '" + field.getName() + "' in " + scopeClassName();
        }
    }

    private static class MarkedFields
    {
        private final FieldMatch testCaseField;
        private final FieldMatch testerField;

        private MarkedFields(final FieldMatch testCaseField, final FieldMatch testerField)
        {
            this.testCaseField = testCaseField;
            this.testerField = testerField;
        }
    }

    /**
     * Holds the one instance of a {@link DatabaseTesterFactory} class and instantiates it when
     * the first test asks. The {@link ClassValue} can compute its value more than once when tests
     * that run in parallel ask for the same class together, and keeps only one of the results, so
     * the factory is not instantiated by the {@link ClassValue} itself: a factory whose
     * constructor builds something costly, such as a pool, would build it once for each racing
     * test and discard all but one. This holder instantiates it under a lock instead.
     */
    private static final class SharedFactory
    {
        private final Class<? extends DatabaseTesterFactory> factoryClass;
        private DatabaseTesterFactory factory;

        private SharedFactory(final Class<? extends DatabaseTesterFactory> factoryClass)
        {
            this.factoryClass = factoryClass;
        }

        /**
         * Returns the factory, instantiating it first when no test has yet. A constructor that
         * throws leaves nothing behind, so the next call tries again.
         */
        private synchronized DatabaseTesterFactory instance()
        {
            if (factory == null)
            {
                factory = new ReflectiveInstantiation().instantiate(factoryClass,
                        "DbUnitConfig.databaseTesterFactory");
            }
            return factory;
        }
    }
}
