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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.annotation.runtime.ReflectiveInstantiation;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5/6 extension for DbUnit that manages the dbUnit lifecycle around each test method,
 * driven by the {@code org.dbunit.annotation} family: {@link DbUnitPrep}, {@link DbUnitSetup},
 * {@link DbUnitExpected}, {@link DbUnitTearDown}, and {@link DbUnitConfig}. Add
 * {@link DbUnitTest @DbUnitTest} (or {@code @ExtendWith(DbUnitExtension.class)} directly) to
 * opt a test class in.
 *
 * <h2>The two paths</h2>
 *
 * <p>Without {@link DbUnitExpected}, this is the simple setup/teardown path: the
 * {@link DbUnitPrep} dataset (if any) and {@link DbUnitSetup} operation are applied, then
 * {@link IDatabaseTester#onSetup()} runs before the test method and
 * {@link IDatabaseTester#onTearDown()} after it.
 *
 * <p>With {@link DbUnitExpected} present, the test switches onto the
 * {@link PrepAndExpectedTestCase} prep/expected path: {@code configureTest()} and
 * {@code preTest()} run before the test method, {@code postTest()} after it - skipping
 * verification when the test method itself already failed, so a verification failure never
 * masks the real cause.
 *
 * <pre>{@code
 * @DbUnitTest
 * class AccountRepositoryTest {
 *     IDatabaseTester databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *
 *     @Test
 *     @DbUnitPrep("accounts-prep.xml")
 *     @DbUnitExpected(value = "accounts-expected.xml", verifyTables = {"ACCOUNT"})
 *     void testWithdraw_sufficientBalance_decrementsBalance() { ... }
 * }
 * }</pre>
 *
 * <h2>Resolving the tester or test case</h2>
 *
 * <p>First match wins:
 * <ol>
 *   <li>A field annotated {@link DbUnitTestCase}, whose type implements
 *   {@link PrepAndExpectedTestCase} - that instance is driven directly.</li>
 *   <li>A field annotated {@link DbUnitTester}, whose type implements
 *   {@link IDatabaseTester}.</li>
 *   <li>{@link DbUnitConfig#databaseTesterFactory()}, reflectively instantiated and asked to
 *   create a tester.</li>
 *   <li>The original (3.5.0) auto-scan: exactly one non-static field assignable to
 *   {@link IDatabaseTester}, nearest declaring class wins - unchanged, so every 3.5.0 test
 *   keeps working untouched.</li>
 * </ol>
 *
 * <p>Field discovery walks every test instance in scope, innermost first, so a {@code @Nested}
 * test class inherits its enclosing class's tester field.
 *
 * <h2>Programmatic setup</h2>
 *
 * <p>Without annotations, configure the tester - including its dataset - in a
 * {@code @BeforeEach} method; those run before this extension's setup callback:
 *
 * <pre>{@code
 * @ExtendWith(DbUnitExtension.class)
 * class MyDatabaseTest {
 *     IDatabaseTester databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *
 *     @BeforeEach
 *     void loadDataset() throws Exception {
 *         databaseTester.setDataSet(new FlatXmlDataSetBuilder().build(...));
 *     }
 *
 *     @Test
 *     void testSomething() { ... }
 * }
 * }</pre>
 *
 * <h2>Parameter injection</h2>
 *
 * <p>Also a {@link ParameterResolver} for {@link IDatabaseTester}, {@link PrepAndExpectedTestCase},
 * {@link IDatabaseConnection}, and {@link Connection} parameters on {@code @Test} and
 * {@code @BeforeEach} methods. The injected {@link IDatabaseConnection} is owned by the
 * tester's operation listener; do not close it.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 * @see DbUnitPrep
 * @see DbUnitSetup
 * @see DbUnitExpected
 * @see DbUnitTearDown
 * @see DbUnitConfig
 * @see DbUnitTest
 */
public class DbUnitExtension
        implements BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String EXECUTOR_KEY = "annotatedTestExecutor";

    /** Creates the extension. */
    public DbUnitExtension() {
    }

    /**
     * Resolves the configured {@link AnnotatedTestExecutor} - reusing one already resolved for
     * this test by parameter injection into a {@code @BeforeEach} method, if any - and runs its
     * before-test steps.
     *
     * @param context The extension context for the test method.
     * @throws Exception If resolving the tester or test case, or running the before-test
     *             steps, fails.
     */
    @Override
    public void beforeTestExecution(final ExtensionContext context) throws Exception {
        resolveExecutor(context).beforeTest();
    }

    /**
     * Runs the stored {@link AnnotatedTestExecutor}'s after-test steps.
     *
     * <p>A failure here is simply left to propagate rather than caught and reconciled by hand:
     * the JUnit Platform engine invokes every {@code AfterTestExecutionCallback} through the
     * same {@code ThrowableCollector} that already collected the test method's own outcome (if
     * any), so the engine itself already applies the correct precedence - a genuine failure
     * here is promoted over a mere test <em>abort</em> from the test method (e.g. a failed
     * {@code Assumptions.assumeTrue()}), with the abort attached as a suppressed exception on
     * the promoted failure instead of the failure being silently swallowed into the discarded
     * abort; against a real test method failure, this failure is the one attached as
     * suppressed, so JUnit still reports the original failure as the cause. Reimplementing
     * that precedence here would only risk getting it wrong.
     *
     * @param context The extension context for the test method.
     * @throws Exception If the after-test steps fail.
     */
    @Override
    public void afterTestExecution(final ExtensionContext context) throws Exception {
        final AnnotatedTestExecutor executor =
                context.getStore(NAMESPACE).get(EXECUTOR_KEY, AnnotatedTestExecutor.class);
        if (executor == null) {
            return;
        }

        final boolean testFailed = context.getExecutionException().isPresent();
        executor.afterTest(testFailed);
    }

    /**
     * Reports whether this extension resolves the given parameter.
     *
     * @param parameterContext The parameter to resolve.
     * @param extensionContext The extension context for the test method.
     * @return {@code true} for {@link IDatabaseTester}, {@link PrepAndExpectedTestCase},
     *         {@link IDatabaseConnection}, and {@link Connection} parameters.
     * @throws ParameterResolutionException Never thrown directly; declared by the
     *             {@link ParameterResolver} contract.
     */
    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
            final ExtensionContext extensionContext) throws ParameterResolutionException {
        final Class<?> type = parameterContext.getParameter().getType();
        return IDatabaseTester.class.isAssignableFrom(type)
                || PrepAndExpectedTestCase.class.isAssignableFrom(type)
                || IDatabaseConnection.class.isAssignableFrom(type)
                || Connection.class.isAssignableFrom(type);
    }

    /**
     * Resolves a parameter from the same {@link AnnotatedTestExecutor} that
     * {@link #beforeTestExecution(ExtensionContext)} drives - reusing it (creating and caching
     * it on first use, for a {@code @BeforeEach} parameter resolved before
     * {@code beforeTestExecution} runs) rather than resolving a second tester or test case
     * independently.
     *
     * <p>A {@link PrepAndExpectedTestCase} parameter requires a {@link DbUnitTestCase @DbUnitTestCase}
     * field: without one, the executor may not have constructed its own instance yet - see
     * {@link AnnotatedTestExecutor#getPrepAndExpectedTestCase()} - so there is no single
     * instance to inject.
     *
     * @param parameterContext The parameter to resolve.
     * @param extensionContext The extension context for the test method.
     * @return The resolved {@link IDatabaseTester}, {@link PrepAndExpectedTestCase},
     *         {@link IDatabaseConnection}, or {@link Connection} value.
     * @throws ParameterResolutionException If no test instance exists yet (e.g. a static-scope
     *             context such as {@code @BeforeAll}), or if resolution otherwise fails.
     */
    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
            final ExtensionContext extensionContext) throws ParameterResolutionException {
        final Class<?> type = parameterContext.getParameter().getType();
        if (!extensionContext.getTestInstances().isPresent()) {
            throw new ParameterResolutionException("Cannot inject a dbUnit parameter of type "
                    + type.getName() + " here; IDatabaseTester, PrepAndExpectedTestCase,"
                    + " IDatabaseConnection, and Connection parameters are only supported for"
                    + " @Test and @BeforeEach methods, where a test instance already exists -"
                    + " not @BeforeAll or other static-scope contexts.");
        }
        try {
            final AnnotatedTestExecutor executor = resolveExecutor(extensionContext);
            if (PrepAndExpectedTestCase.class.isAssignableFrom(type)) {
                final PrepAndExpectedTestCase testCase = executor.getPrepAndExpectedTestCase();
                if (testCase == null) {
                    throw new IllegalStateException("No PrepAndExpectedTestCase available to"
                            + " inject; declare a @DbUnitTestCase field so there is a single"
                            + " instance to use.");
                }
                return testCase;
            }
            final IDatabaseTester tester = executor.getTester();
            if (IDatabaseTester.class.isAssignableFrom(type)) {
                return tester;
            }
            final IDatabaseConnection connection = executor.getConnection();
            if (connection == null) {
                throw new IllegalStateException("IDatabaseTester#getConnection() returned"
                        + " null; cannot inject an IDatabaseConnection or Connection"
                        + " parameter.");
            }
            if (IDatabaseConnection.class.isAssignableFrom(type)) {
                return connection;
            }
            return connection.getConnection();
        } catch (final Exception e) {
            throw new ParameterResolutionException("Failed to resolve a dbUnit parameter of"
                    + " type " + type.getName() + ".", e);
        }
    }

    /**
     * Resolves and caches the {@link AnnotatedTestExecutor} for the current test, so
     * {@link #beforeTestExecution(ExtensionContext)} and
     * {@link #resolveParameter(ParameterContext, ExtensionContext)} - whichever runs first,
     * since a {@code @BeforeEach} parameter resolves before {@code beforeTestExecution} does -
     * both drive the same tester and test case instead of each resolving their own.
     */
    private AnnotatedTestExecutor resolveExecutor(final ExtensionContext context)
            throws Exception {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        AnnotatedTestExecutor executor = store.get(EXECUTOR_KEY, AnnotatedTestExecutor.class);
        if (executor == null) {
            final AnnotatedTestConfiguration configuration = resolveConfiguration(context);
            final Resolution resolution = resolve(context, configuration);
            executor = new AnnotatedTestExecutor(configuration, resolution.tester,
                    resolution.testCase);
            store.put(EXECUTOR_KEY, executor);
        }
        return executor;
    }

    // ---- annotation resolution ----

    private AnnotatedTestConfiguration resolveConfiguration(final ExtensionContext context) {
        final Class<?> testClass = context.getRequiredTestClass();
        final DbUnitConfig config = findAnnotation(context, DbUnitConfig.class);
        final DbUnitPrep prep = findAnnotation(context, DbUnitPrep.class);
        final DbUnitSetup setup = findAnnotation(context, DbUnitSetup.class);
        final DbUnitExpected expected = findAnnotation(context, DbUnitExpected.class);
        final DbUnitTearDown tearDown = findAnnotation(context, DbUnitTearDown.class);
        return AnnotatedTestConfiguration.from(testClass, config, prep, setup, expected,
                tearDown);
    }

    /**
     * Finds {@code annotationType}, trying the test method first and the class hierarchy -
     * including enclosing classes, for {@code @Nested} support - second, so a method-level
     * annotation continues to win over a class-level one.
     */
    private <A extends Annotation> A findAnnotation(final ExtensionContext context,
            final Class<A> annotationType) {
        final Optional<Method> method = context.getTestMethod();
        if (method.isPresent()) {
            final Optional<A> onMethod =
                    AnnotationSupport.findAnnotation(method.get(), annotationType);
            if (onMethod.isPresent()) {
                return onMethod.get();
            }
        }
        return AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), annotationType,
                        context.getEnclosingTestClasses())
                .orElse(null);
    }

    // ---- tester / test case resolution ----

    private Resolution resolve(final ExtensionContext context,
            final AnnotatedTestConfiguration configuration) throws Exception {
        final Class<?> testClass = context.getRequiredTestClass();
        final List<Object> instances = innermostFirst(context.getRequiredTestInstances());

        final MarkedFields marked = findMarkedFields(instances);
        final FieldMatch testCaseField = marked.testCaseField;
        final FieldMatch testerField = marked.testerField;

        if (testCaseField != null) {
            final PrepAndExpectedTestCase testCase =
                    (PrepAndExpectedTestCase) testCaseField.value();
            if (testCase == null) {
                throw new IllegalStateException("PrepAndExpectedTestCase field '"
                        + testCaseField.field.getName() + "' in "
                        + testCaseField.instance.getClass().getName() + " is null.");
            }
            IDatabaseTester tester = testCase instanceof DefaultPrepAndExpectedTestCase
                    ? ((DefaultPrepAndExpectedTestCase) testCase).getDatabaseTester() : null;
            if (tester == null) {
                // Either testCase is not a DefaultPrepAndExpectedTestCase, or it is one built
                // without a tester (e.g. the no-arg-tester constructor form) - fall back to the
                // same resolution a bare @DbUnitTestCase-less test would use, rather than
                // leaving Resolution#tester null.
                tester = findTester(instances, testClass, configuration);
                if (testCase instanceof DefaultPrepAndExpectedTestCase) {
                    // The executor drives this exact, already-injected instance directly (see
                    // AnnotatedTestExecutor#beforeExpectedTest()) rather than constructing a
                    // fresh one, so the fallback tester must be wired onto it here - otherwise
                    // it keeps the null databaseTester it was built with.
                    ((DefaultPrepAndExpectedTestCase) testCase).setDatabaseTester(tester);
                }
            }
            return new Resolution(tester, testCase);
        }

        if (testerField != null) {
            final IDatabaseTester tester = (IDatabaseTester) testerField.value();
            if (tester == null) {
                throw new IllegalStateException("IDatabaseTester field '"
                        + testerField.field.getName() + "' in "
                        + testerField.instance.getClass().getName() + " is null.");
            }
            return new Resolution(tester, null);
        }

        return new Resolution(findTester(instances, testClass, configuration), null);
    }

    private List<Object> innermostFirst(final TestInstances instances) {
        final List<Object> all = new ArrayList<>(instances.getAllInstances());
        Collections.reverse(all);
        return all;
    }

    private IDatabaseTester findTester(final List<Object> instances, final Class<?> testClass,
            final AnnotatedTestConfiguration configuration) throws Exception {
        if (configuration.getDatabaseTesterFactory() != null) {
            final DatabaseTesterFactory factory = ReflectiveInstantiation.instantiate(
                    configuration.getDatabaseTesterFactory(), "DbUnitConfig.databaseTesterFactory");
            final IDatabaseTester tester = factory.createDatabaseTester();
            if (tester == null) {
                throw new IllegalStateException("DatabaseTesterFactory "
                        + configuration.getDatabaseTesterFactory().getName()
                        + ", named by DbUnitConfig.databaseTesterFactory, returned null from"
                        + " createDatabaseTester().");
            }
            return tester;
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
     * @throws IllegalStateException if one instance declares both markers, or more than one
     *             field for the same marker within one instance's class hierarchy.
     */
    private MarkedFields findMarkedFields(final List<Object> instances) {
        for (final Object instance : instances) {
            final FieldMatch testCaseField = findMarkedField(instance, DbUnitTestCase.class);
            final FieldMatch testerField = findMarkedField(instance, DbUnitTester.class);
            if (testCaseField != null && testerField != null) {
                throw new IllegalStateException(
                        "Both @DbUnitTestCase and @DbUnitTester fields are declared in "
                                + instance.getClass().getName() + "; declare at most one.");
            }
            if (testCaseField != null || testerField != null) {
                return new MarkedFields(testCaseField, testerField);
            }
        }

        return new MarkedFields(null, null);
    }

    /**
     * Finds a field annotated {@code marker} within one test instance's class hierarchy.
     *
     * @throws IllegalStateException if more than one such field is found.
     */
    private <A extends Annotation> FieldMatch findMarkedField(final Object instance,
            final Class<A> marker) {
        final List<Field> fields =
                AnnotationSupport.findAnnotatedFields(instance.getClass(), marker);
        if (fields.size() > 1) {
            throw new IllegalStateException("Multiple @" + marker.getSimpleName()
                    + " fields found in " + instance.getClass().getName() + ".");
        }
        if (fields.isEmpty()) {
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
            final Class<?> testClass) throws IllegalAccessException {
        for (final Object instance : instances) {
            Class<?> clazz = instance.getClass();
            while (clazz != null && clazz != Object.class) {
                final Field field = findTesterField(clazz, instance);
                if (field != null) {
                    field.setAccessible(true);
                    final IDatabaseTester tester = (IDatabaseTester) field.get(instance);
                    if (tester == null) {
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

    private Field findTesterField(final Class<?> clazz, final Object instance) {
        Field match = null;
        for (final Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && IDatabaseTester.class.isAssignableFrom(field.getType())) {
                if (match != null) {
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

    private static final class Resolution {
        private final IDatabaseTester tester;
        private final PrepAndExpectedTestCase testCase;

        private Resolution(final IDatabaseTester tester,
                final PrepAndExpectedTestCase testCase) {
            this.tester = tester;
            this.testCase = testCase;
        }
    }

    private static final class FieldMatch {
        private final Field field;
        private final Object instance;

        private FieldMatch(final Field field, final Object instance) {
            this.field = field;
            this.instance = instance;
        }

        private Object value() throws IllegalAccessException {
            return field.get(instance);
        }
    }

    private static final class MarkedFields {
        private final FieldMatch testCaseField;
        private final FieldMatch testerField;

        private MarkedFields(final FieldMatch testCaseField, final FieldMatch testerField) {
            this.testCaseField = testCaseField;
            this.testerField = testerField;
        }
    }
}
