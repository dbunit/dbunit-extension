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
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
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
 * <p>Without {@link DbUnitExpected}, this is the setup/teardown path: the
 * {@link DbUnitPrep} dataset (if any) and {@link DbUnitSetup} operation are applied, then
 * {@link IDatabaseTester#onSetup()} runs before the test method and
 * {@link IDatabaseTester#onTearDown()} after it. The test method asserts results however it
 * likes.
 *
 * <p>With {@link DbUnitExpected} present, the test switches onto the
 * {@link PrepAndExpectedTestCase} prep/expected path: {@code configureTest()} and
 * {@code preTest()} run before the test method, {@code postTest()} after it - {@code postTest()}
 * compares the database against the {@link DbUnitExpected} dataset, so the verifying is done
 * for you, skipping it when the test method itself already failed so a verification failure
 * never masks the real cause. Neither path is a reduced form of the other; they run different
 * lifecycles.
 *
 * <pre>{@code
 * @DbUnitTest
 * class AccountRepositoryTest {
 *     IDatabaseTester databaseTester;
 *
 *     AccountRepositoryTest() throws ClassNotFoundException {
 *         databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *     }
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
 *     IDatabaseTester databaseTester;
 *
 *     MyDatabaseTest() throws ClassNotFoundException {
 *         databaseTester = new JdbcDatabaseTester("driver", "url", "user", "pass");
 *     }
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
 * <p>This zero-annotation style is the 3.5.0 lifecycle: the extension calls only
 * {@code onSetup()}/{@code onTearDown()} on the tester and runs the row count check around
 * them, without replacing the tester's {@link org.dbunit.IOperationListener}. The connection it
 * resolves for the row count baseline is never closed before {@code onSetup()} - a tester that
 * returns one fixed connection from every call (e.g. a {@code DefaultDatabaseTester} built from
 * a fixed connection) would otherwise run {@code onSetup()} against a closed one - and is
 * closed after the test when {@code closeConnectionAfterTest} allows and the tester's listener
 * is not {@code NO_OP}. Adding any {@code @DbUnit*} annotation or a
 * {@link DbUnitTester @DbUnitTester}/{@link DbUnitTestCase @DbUnitTestCase} field opts the test
 * into the fuller annotation-driven lifecycle described above.
 *
 * <h2>Parameter injection</h2>
 *
 * <p>Also a {@link ParameterResolver} for {@link IDatabaseTester}, {@link PrepAndExpectedTestCase},
 * {@link IDatabaseConnection}, and {@link Connection} parameters on {@code @Test} and
 * {@code @BeforeEach} methods - but only for a test that opts into the
 * {@code org.dbunit.annotation} family: one carrying {@link DbUnitTest @DbUnitTest} or any
 * {@code @DbUnit*} annotation ({@link DbUnitConfig}, {@link DbUnitPrep}, {@link DbUnitSetup},
 * {@link DbUnitExpected}, {@link DbUnitTearDown}, {@link DbUnitRowCountCheck}) on the class or
 * method, or a {@link DbUnitTester @DbUnitTester}/{@link DbUnitTestCase @DbUnitTestCase} field.
 * A bare {@code @ExtendWith(DbUnitExtension.class)} class with only a plain, unannotated
 * {@link IDatabaseTester} field keeps the 3.5.0 lifecycle behaviour untouched and has no
 * parameter claimed here, so another extension resolving a {@link Connection} (or any of the
 * other three types) on the same test stays unambiguous. Switch such a class to
 * {@link DbUnitTest @DbUnitTest}, or mark its field {@link DbUnitTester @DbUnitTester}, to opt
 * its parameters in.
 *
 * <p><strong>On an annotation-driven test this extension claims <em>every</em>
 * {@link Connection} parameter</strong> on a {@code @Test}/{@code @BeforeEach} method. Another
 * extension on the same test that also resolves {@code java.sql.Connection} - Spring's
 * {@code SpringExtension}, Testcontainers, a JPA harness - then collides with JUnit's
 * "discovered multiple competing ParameterResolvers". Set
 * {@link DbUnitConfig#injectConnectionParameter() @DbUnitConfig(injectConnectionParameter = false)}
 * to yield the bare {@code Connection} to that extension; the dbUnit-specific
 * {@link IDatabaseConnection} parameter is still resolved (no other framework claims that
 * type) - inject it and call {@code getConnection()}.
 *
 * <p>The injected {@link IDatabaseConnection}/{@link Connection} is managed for the test's
 * duration and closed afterward - by this extension on the setup/teardown path, by the
 * driven {@link PrepAndExpectedTestCase} on the prep/expected path; do not close it
 * yourself. A {@code @BeforeEach} parameter resolves
 * before {@link #beforeTestExecution(ExtensionContext)} runs - JUnit Jupiter always calls
 * {@code @BeforeEach} methods first - so it sees the tester/connection exactly as they exist
 * before this extension's own setup: {@link DbUnitPrep}'s dataset, if any, is not loaded onto
 * it yet.
 *
 * <p>Not safe for concurrent execution of test methods that resolve to the same tester or test
 * case instance - e.g. a {@code static} {@link DbUnitTester @DbUnitTester}/
 * {@link DbUnitTestCase @DbUnitTestCase} field, or any instance field at all under
 * {@code @TestInstance(Lifecycle.PER_CLASS)} - since this extension applies annotations and
 * drives the dbUnit lifecycle against it without synchronization. Run such test methods
 * sequentially (JUnit Jupiter's own default) rather than under
 * {@code junit.jupiter.execution.parallel.enabled=true}, or give each test method's own tester
 * its own non-shared field.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 * @see DbUnitPrep
 * @see DbUnitSetup
 * @see DbUnitExpected
 * @see DbUnitTearDown
 * @see DbUnitConfig
 * @see DbUnitRowCountCheck
 * @see DbUnitTest
 */
public class DbUnitExtension
        implements BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver
{
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String EXECUTOR_KEY = "annotatedTestExecutor";

    private final TesterResolver testerResolver = new TesterResolver();

    /** Creates the extension. */
    public DbUnitExtension()
    {
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
    public void beforeTestExecution(final ExtensionContext context) throws Exception
    {
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
    public void afterTestExecution(final ExtensionContext context) throws Exception
    {
        final AnnotatedTestExecutor executor =
                context.getStore(NAMESPACE).get(EXECUTOR_KEY, AnnotatedTestExecutor.class);
        if (executor == null)
        {
            return;
        }

        final boolean testFailed = context.getExecutionException().isPresent();
        executor.afterTest(testFailed);
    }

    /**
     * Reports whether this extension resolves the given parameter: an {@link IDatabaseTester},
     * {@link PrepAndExpectedTestCase}, {@link IDatabaseConnection}, or {@link Connection}
     * parameter, declared on the current test method or a {@code @BeforeEach} method - the only
     * two contexts {@link #afterTestExecution(ExtensionContext)} still runs after, so a
     * connection this executor resolves is guaranteed to still be open, and eventually closed,
     * for the parameter's entire lifetime. A matching parameter on any other method
     * (constructor, {@code @BeforeAll}, {@code @AfterEach}, {@code @AfterAll}) is declined here
     * rather than handed out already-closed - or, worse, opened for the first time with nothing
     * left to ever close it - since {@code @AfterEach} and {@code @AfterAll} run after
     * {@link #afterTestExecution(ExtensionContext)} already has.
     *
     * <p>Also declined - regardless of type or method - unless the test opts into the
     * {@code org.dbunit.annotation} family (see {@link #isAnnotationDriven(ExtensionContext)}).
     * A bare {@code @ExtendWith(DbUnitExtension.class)} class with only a plain, unannotated
     * {@link IDatabaseTester} field is the 3.5.0 lifecycle-only style: it never claimed a
     * parameter before this method existed, and does not now, so another extension resolving
     * one of these types - {@link Connection} especially - on the same test is not left
     * competing with an unconditional claim here. An annotation-driven test can still opt its
     * bare {@link Connection} parameter back out with
     * {@link DbUnitConfig#injectConnectionParameter() @DbUnitConfig(injectConnectionParameter = false)}
     * while keeping the other three types.
     *
     * @param parameterContext The parameter to resolve.
     * @param extensionContext The extension context for the test method.
     * @return {@code true} for a matching parameter type on the test method or a
     *         {@code @BeforeEach} method of an annotation-driven test.
     * @throws ParameterResolutionException Never thrown directly; declared by the
     *             {@link ParameterResolver} contract.
     */
    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
            final ExtensionContext extensionContext) throws ParameterResolutionException
    {
        final Class<?> type = parameterContext.getParameter().getType();
        final InjectableParameter injectable = InjectableParameter.forType(type);
        final boolean typeSupported = injectable != null
                && (injectable != InjectableParameter.JDBC_CONNECTION
                        || claimsBareConnectionParameter(extensionContext));
        return typeSupported
                && isTestMethodOrBeforeEach(parameterContext, extensionContext)
                && isAnnotationDriven(extensionContext);
    }

    /**
     * Returns whether this extension resolves a bare {@link Connection} parameter for the
     * current test - {@link DbUnitConfig#injectConnectionParameter()}, {@code true} by default.
     * Set {@code false} to yield {@code java.sql.Connection} to a co-registered resolver
     * (Spring, Testcontainers, ...); the {@link IDatabaseConnection} parameter is unaffected.
     */
    private boolean claimsBareConnectionParameter(final ExtensionContext context)
    {
        final DbUnitConfig config = findAnnotation(context, DbUnitConfig.class);
        return config == null || config.injectConnectionParameter();
    }

    /** The class/method annotations any one of which opts a test into parameter injection. */
    private static final List<Class<? extends Annotation>> OPT_IN_ANNOTATIONS = Arrays.asList(
            DbUnitTest.class, DbUnitConfig.class, DbUnitPrep.class, DbUnitSetup.class,
            DbUnitExpected.class, DbUnitTearDown.class, DbUnitRowCountCheck.class);

    /**
     * Returns whether the current test opts into the {@code org.dbunit.annotation} family, and
     * so into parameter injection: any {@link #OPT_IN_ANNOTATIONS} annotation on the test method
     * or the class hierarchy (enclosing classes included, for {@code @Nested}), or a
     * {@link DbUnitTester}/{@link DbUnitTestCase} field anywhere in that same scope. A plain,
     * unannotated {@link IDatabaseTester} field alone is not an opt-in - that is the 3.5.0
     * lifecycle-only style, left exactly as it was.
     */
    private boolean isAnnotationDriven(final ExtensionContext context)
    {
        for (final Class<? extends Annotation> annotationType : OPT_IN_ANNOTATIONS)
        {
            if (findAnnotation(context, annotationType) != null)
            {
                return true;
            }
        }
        return hasMarkerField(context.getRequiredTestClass())
                || context.getEnclosingTestClasses().stream().anyMatch(this::hasMarkerField);
    }

    /**
     * Returns whether {@code testClass} or a superclass declares a {@link DbUnitTester} or
     * {@link DbUnitTestCase} field.
     */
    private boolean hasMarkerField(final Class<?> testClass)
    {
        return !AnnotationSupport.findAnnotatedFields(testClass, DbUnitTester.class).isEmpty()
                || !AnnotationSupport.findAnnotatedFields(testClass, DbUnitTestCase.class)
                        .isEmpty();
    }

    /**
     * Returns whether {@code parameterContext}'s declaring method is the current test method or
     * a {@code @BeforeEach} method - see {@link #supportsParameter(ParameterContext, ExtensionContext)}.
     */
    private boolean isTestMethodOrBeforeEach(final ParameterContext parameterContext,
            final ExtensionContext extensionContext)
    {
        final Executable declaringExecutable = parameterContext.getDeclaringExecutable();
        if (!(declaringExecutable instanceof Method))
        {
            return false;
        }
        final Method method = (Method) declaringExecutable;
        return extensionContext.getTestMethod().map(method::equals).orElse(false)
                || AnnotationSupport.isAnnotated(method, BeforeEach.class);
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
     * @throws ParameterResolutionException If no test instance exists yet, or if resolution
     *             otherwise fails. The first is not reachable through JUnit Jupiter's own
     *             engine for a parameter this class actually claims - a static-scope context
     *             such as {@code @BeforeAll} is already declined by
     *             {@link #supportsParameter(ParameterContext, ExtensionContext)}, so the
     *             engine never calls this method for it at all - only a caller invoking this
     *             method directly, without checking that first, could still hit it.
     */
    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
            final ExtensionContext extensionContext) throws ParameterResolutionException
    {
        final Class<?> type = parameterContext.getParameter().getType();
        if (!extensionContext.getTestInstances().isPresent())
        {
            throw new ParameterResolutionException("Cannot inject a dbUnit parameter of type "
                    + type.getName() + " here; IDatabaseTester, PrepAndExpectedTestCase,"
                    + " IDatabaseConnection, and Connection parameters are only supported for"
                    + " @Test and @BeforeEach methods, where a test instance already exists -"
                    + " not @BeforeAll or other static-scope contexts.");
        }
        try
        {
            final AnnotatedTestExecutor executor = resolveExecutor(extensionContext);
            final InjectableParameter injectable = InjectableParameter.forType(type);
            if (injectable == null)
            {
                throw new IllegalStateException("resolveParameter() reached for unsupported"
                        + " type " + type.getName() + "; supportsParameter() should have"
                        + " declined it.");
            }
            return injectable.resolve(executor);
        } catch (final Exception e)
        {
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
            throws Exception
    {
        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        AnnotatedTestExecutor executor = store.get(EXECUTOR_KEY, AnnotatedTestExecutor.class);
        if (executor == null)
        {
            final AnnotatedTestConfiguration configuration = resolveConfiguration(context);
            final TesterResolver.Resolution resolution =
                    testerResolver.resolve(context, configuration);
            executor = new AnnotatedTestExecutor(configuration, resolution.tester,
                    resolution.testCase, isAnnotationDriven(context));
            store.put(EXECUTOR_KEY, executor);
        }
        return executor;
    }

    // ---- annotation resolution ----

    private AnnotatedTestConfiguration resolveConfiguration(final ExtensionContext context)
    {
        final Class<?> testClass = context.getRequiredTestClass();
        final DbUnitConfig config = findAnnotation(context, DbUnitConfig.class);
        final DbUnitPrep prep = findAnnotation(context, DbUnitPrep.class);
        final DbUnitSetup setup = findAnnotation(context, DbUnitSetup.class);
        final DbUnitExpected expected = findAnnotation(context, DbUnitExpected.class);
        final DbUnitTearDown tearDown = findAnnotation(context, DbUnitTearDown.class);
        final DbUnitRowCountCheck rowCountCheck =
                findAnnotation(context, DbUnitRowCountCheck.class);
        return AnnotatedTestConfiguration.from(testClass, config, prep, setup, expected,
                tearDown, rowCountCheck);
    }

    /**
     * Finds {@code annotationType}, trying the test method first and the class hierarchy -
     * including enclosing classes, for {@code @Nested} support - second, so a method-level
     * annotation continues to win over a class-level one.
     */
    private <A extends Annotation> A findAnnotation(final ExtensionContext context,
            final Class<A> annotationType)
    {
        final Optional<Method> method = context.getTestMethod();
        if (method.isPresent())
        {
            final Optional<A> onMethod =
                    AnnotationSupport.findAnnotation(method.get(), annotationType);
            if (onMethod.isPresent())
            {
                return onMethod.get();
            }
        }
        return AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), annotationType,
                        context.getEnclosingTestClasses())
                .orElse(null);
    }

    /**
     * The parameter types this extension resolves, each paired with how to obtain its value
     * from the resolved {@link AnnotatedTestExecutor}, in resolution order.
     * {@link #supportsParameter(ParameterContext, ExtensionContext)} and
     * {@link #resolveParameter(ParameterContext, ExtensionContext)} both consult this one list,
     * so a supported type cannot be declared in one and forgotten in the other. The bare
     * {@link Connection} opt-out ({@link #claimsBareConnectionParameter(ExtensionContext)}) is a
     * separate gate layered on top, since it turns one entry off per test rather than adding or
     * removing a type.
     */
    private enum InjectableParameter
    {
        /** A {@link PrepAndExpectedTestCase}, from the {@code @DbUnitTestCase} field. */
        PREP_AND_EXPECTED_TEST_CASE(PrepAndExpectedTestCase.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor)
            {
                final PrepAndExpectedTestCase testCase = executor.getPrepAndExpectedTestCase();
                if (testCase == null)
                {
                    throw new IllegalStateException("No PrepAndExpectedTestCase available to"
                            + " inject; declare a @DbUnitTestCase field so there is a single"
                            + " instance to use.");
                }
                return testCase;
            }
        },

        /** The {@link IDatabaseTester} this executor drives. */
        DATABASE_TESTER(IDatabaseTester.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor)
            {
                return executor.getTester();
            }
        },

        /** The {@link IDatabaseConnection} this test's steps share. */
        DATABASE_CONNECTION(IDatabaseConnection.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor) throws Exception
            {
                return requireConnection(executor);
            }
        },

        /** The underlying JDBC {@link Connection}, unwrapped from the shared {@link IDatabaseConnection}. */
        JDBC_CONNECTION(Connection.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor) throws Exception
            {
                return requireConnection(executor).getConnection();
            }
        };

        private final Class<?> parameterType;

        InjectableParameter(final Class<?> parameterType)
        {
            this.parameterType = parameterType;
        }

        /**
         * Obtains this parameter's value from the resolved executor.
         *
         * @param executor The executor driving the current test.
         * @return The value to inject.
         * @throws Exception If resolving the value fails.
         */
        abstract Object resolve(AnnotatedTestExecutor executor) throws Exception;

        private static IDatabaseConnection requireConnection(final AnnotatedTestExecutor executor)
                throws Exception
        {
            final IDatabaseConnection connection = executor.getConnection();
            if (connection == null)
            {
                throw new IllegalStateException("IDatabaseTester#getConnection() returned"
                        + " null; cannot inject an IDatabaseConnection or Connection"
                        + " parameter.");
            }
            return connection;
        }

        /**
         * Returns the entry whose type {@code parameterType} is assignable to, or {@code null}
         * when none matches. The first match wins, so an entry earlier in declaration order
         * shadows a later one for a type assignable to both.
         *
         * @param parameterType The declared parameter type.
         * @return The matching entry, or {@code null}.
         */
        static InjectableParameter forType(final Class<?> parameterType)
        {
            for (final InjectableParameter candidate : values())
            {
                if (candidate.parameterType.isAssignableFrom(parameterType))
                {
                    return candidate;
                }
            }
            return null;
        }
    }
}
