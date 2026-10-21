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

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitSetUpOperation;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.annotation.runtime.AnnotatedTestOptIn;
import org.dbunit.annotation.runtime.AnnotationLookup;
import org.dbunit.annotation.runtime.DataFileLoaderInstances;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.dbunit.annotation.runtime.TesterFields;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterEachCallback;
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
 * JUnit 5/6 extension for DbUnit that manages the dbUnit lifecycle around each test method, and
 * resolves {@link IDatabaseTester} and {@link IDatabaseConnection} test parameters. Add
 * {@link DbUnitTest @DbUnitTest} to register it and opt the class into the annotation-driven
 * lifecycle, or {@code @ExtendWith(DbUnitExtension.class)} to register it for the 3.5.0
 * lifecycle only; a class registered that way opts in by declaring any annotation of
 * {@code org.dbunit.annotation} ({@link DbUnitPrep}, {@link DbUnitSetUpOperation},
 * {@link DbUnitExpected}, {@link DbUnitTearDownOperation}, {@link DbUnitConfig},
 * {@link DbUnitRowCountCheck}) or a {@link DbUnitTester @DbUnitTester}/
 * {@link DbUnitTestCase @DbUnitTestCase} field.
 *
 * <h2>The two lifecycles</h2>
 *
 * <p>A test that opts in runs through a {@link PrepAndExpectedTestCase}, built around its tester
 * or supplied by a {@link DbUnitTestCase} field, whether or not it declares
 * {@link DbUnitExpected}: {@code preTest()} sets up the {@link DbUnitPrep} dataset before the
 * test method, and {@code postTest()} after it verifies the {@link DbUnitExpected} dataset, when
 * one is declared and the test method did not itself fail, then tears down. The tester is only
 * read, never written, and its own {@code onSetup()} and {@code onTearDown()} do not run.
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
 * <p>A bare {@code @ExtendWith(DbUnitExtension.class)} class with one plain, unannotated
 * {@link IDatabaseTester} field keeps the 3.5.0 lifecycle: configure the tester, its dataset
 * included, in a {@code @BeforeEach} method, and the extension calls {@code onSetup()} before and
 * {@code onTearDown()} after the test method, with the row count check around them.
 *
 * <h2>Resolving the tester or test case</h2>
 *
 * <p>First match wins: a {@link DbUnitTestCase} field whose type implements
 * {@link PrepAndExpectedTestCase}; a {@link DbUnitTester} field whose type implements
 * {@link IDatabaseTester}; {@link DbUnitConfig#databaseTesterFactory()}; then the 3.5.0
 * auto-scan for exactly one non-static field assignable to {@link IDatabaseTester}, nearest
 * declaring class first. Field discovery walks every test instance in scope, innermost first, so
 * a {@code @Nested} test class inherits its enclosing class's tester field.
 *
 * <h2>Parameter injection</h2>
 *
 * <p>A test that opts in can take {@link IDatabaseTester} and {@link IDatabaseConnection}
 * parameters on {@code @Test} and {@code @BeforeEach} methods. A bare {@link Connection} is never
 * claimed, so another extension can supply it; call {@code getConnection()} on the
 * {@link IDatabaseConnection} instead. The injected connection belongs to the driven test case,
 * which closes it: do not close it yourself.
 *
 * <p>The cases around these contracts are on the site pages
 * <a href="https://dbunit.github.io/dbunit-extension/testcases/DbUnitExtension.html">DbUnitExtension</a>
 * and
 * <a href="https://dbunit.github.io/dbunit-extension/testcases/annotations.html">DbUnit Annotations</a>:
 * what the 3.5.0 lifecycle does with the tester's listener and connections, when a
 * {@code @BeforeEach} parameter sees the prepped data, and how a tester or test case can be
 * shared by test methods that run in parallel.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 * @see DbUnitPrep
 * @see DbUnitSetUpOperation
 * @see DbUnitExpected
 * @see DbUnitTearDownOperation
 * @see DbUnitConfig
 * @see DbUnitRowCountCheck
 * @see DbUnitTest
 */
public class DbUnitExtension implements BeforeTestExecutionCallback,
        AfterTestExecutionCallback, AfterEachCallback, ParameterResolver
{
    private static final Logger log = LoggerFactory.getLogger(DbUnitExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DbUnitExtension.class);

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String EXECUTOR_KEY = "annotatedTestExecutor";

    private static final String DATA_FILE_LOADERS_KEY = "dataFileLoaders";

    private final TesterFields testerFields = new TesterFields();
    private final AnnotatedTestOptIn optIn = new AnnotatedTestOptIn(testerFields);
    private final TesterResolver testerResolver =
            new TesterResolver(new TestInstanceTesterResolver(testerFields));

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
     * Closes the connection a {@code @BeforeEach} parameter resolved when the test method never
     * ran. JUnit Jupiter skips {@link #afterTestExecution(ExtensionContext)} once a
     * {@code @BeforeEach} method fails, yet an {@code IDatabaseConnection}
     * parameter on that very method may already have opened a connection for the stored
     * {@link AnnotatedTestExecutor}; nothing else would ever close it. This callback always runs
     * for a test the extension was registered on, and does nothing when
     * {@link #afterTestExecution(ExtensionContext)} already released the connection.
     *
     * @param context The extension context for the test method.
     * @throws Exception If closing the connection fails.
     */
    @Override
    public void afterEach(final ExtensionContext context) throws Exception
    {
        final AnnotatedTestExecutor executor =
                context.getStore(NAMESPACE).get(EXECUTOR_KEY, AnnotatedTestExecutor.class);
        if (executor == null)
        {
            return;
        }

        executor.releaseIfAfterTestDidNotRun();
    }

    /**
     * Reports whether this extension resolves the given parameter: an {@link IDatabaseTester}
     * or {@link IDatabaseConnection} parameter, declared on the current test method or a
     * {@code @BeforeEach} method - the only
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
     * one of these types on the same test is not left competing with an unconditional claim
     * here. A bare {@link Connection} parameter is never claimed, annotation-driven test or
     * not.
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
        return injectable != null
                && isTestMethodOrBeforeEach(parameterContext, extensionContext)
                && isAnnotationDriven(extensionContext);
    }

    /**
     * Returns whether the current test opts into the {@code org.dbunit.annotation} family, and
     * so into parameter injection: {@link DbUnitTest @DbUnitTest}, or whatever
     * {@link AnnotatedTestOptIn} accepts - any {@code @DbUnit*} configuring annotation on the
     * test method or the class hierarchy (enclosing classes included, for {@code @Nested}), or a
     * {@link DbUnitTester}/{@link DbUnitTestCase} field anywhere in that same scope. A plain,
     * unannotated {@link IDatabaseTester} field alone is not an opt-in - that is the 3.5.0
     * lifecycle-only style, left exactly as it was.
     */
    private boolean isAnnotationDriven(final ExtensionContext context)
    {
        final AnnotationLookup lookup = new ContextAnnotationLookup(context);
        if (lookup.find(DbUnitTest.class) != null)
        {
            return true;
        }
        final List<Class<?>> testClasses = testClassesInScope(context);
        return optIn.isOptedIn(lookup, testClasses);
    }

    private List<Class<?>> testClassesInScope(final ExtensionContext context)
    {
        final List<Class<?>> testClasses = new ArrayList<>();
        testClasses.add(context.getRequiredTestClass());
        testClasses.addAll(EnclosingTestClasses.of(context));
        return testClasses;
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
     * @param parameterContext The parameter to resolve.
     * @param extensionContext The extension context for the test method.
     * @return The resolved {@link IDatabaseTester} or {@link IDatabaseConnection} value.
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
                    + type.getName() + " here; IDatabaseTester and IDatabaseConnection"
                    + " parameters are only supported for @Test and @BeforeEach methods, where"
                    + " a test instance already exists - not @BeforeAll or other static-scope"
                    + " contexts.");
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
            final TestInstanceTesterResolver.Resolution resolution =
                    testerResolver.resolve(context, configuration);
            executor = new AnnotatedTestExecutor(configuration, resolution,
                    isAnnotationDriven(context));
            store.put(EXECUTOR_KEY, executor);
        }
        return executor;
    }

    // ---- annotation resolution ----

    private AnnotatedTestConfiguration resolveConfiguration(final ExtensionContext context)
    {
        final AnnotationLookup lookup = new ContextAnnotationLookup(context);
        final DataFileLoaderInstances dataFileLoaders = dataFileLoadersOfTheRun(context);
        return AnnotatedTestConfiguration.from(lookup, dataFileLoaders);
    }

    /**
     * Returns the data file loaders the tests of the run share, one for each caching loader
     * class: they are kept in the store of the root context, which lives as long as the run.
     */
    private DataFileLoaderInstances dataFileLoadersOfTheRun(final ExtensionContext context)
    {
        final ExtensionContext.Store runStore = context.getRoot().getStore(NAMESPACE);
        return runStore.getOrComputeIfAbsent(DATA_FILE_LOADERS_KEY,
                key -> new DataFileLoaderInstances(), DataFileLoaderInstances.class);
    }

    /**
     * The parameter types this extension resolves, each paired with how to obtain its value
     * from the resolved {@link AnnotatedTestExecutor}, in resolution order.
     * {@link #supportsParameter(ParameterContext, ExtensionContext)} and
     * {@link #resolveParameter(ParameterContext, ExtensionContext)} both consult this one list,
     * so a supported type cannot be declared in one and forgotten in the other.
     */
    private enum InjectableParameter
    {
        /** The {@link IDatabaseTester} this executor drives. */
        DATABASE_TESTER(IDatabaseTester.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor)
            {
                final IDatabaseTester tester = executor.getTester();
                if (tester == null)
                {
                    throw new IllegalStateException("The @DbUnitTestCase field's test case is"
                            + " not a DefaultPrepAndExpectedTestCase, so dbUnit has no"
                            + " IDatabaseTester of it to inject.");
                }
                return tester;
            }
        },

        /** The {@link IDatabaseConnection} this test's steps share. */
        DATABASE_CONNECTION(IDatabaseConnection.class)
        {
            @Override
            Object resolve(final AnnotatedTestExecutor executor) throws Exception
            {
                return executor.getConnection();
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

        /**
         * Returns the entry whose type is exactly {@code parameterType}, or {@code null} when
         * none is. A subtype is not matched: the value injected is whatever the tester happens
         * to be, not necessarily an instance of the subtype.
         *
         * @param parameterType The declared parameter type.
         * @return The matching entry, or {@code null}.
         */
        static InjectableParameter forType(final Class<?> parameterType)
        {
            for (final InjectableParameter candidate : values())
            {
                if (candidate.parameterType == parameterType)
                {
                    return candidate;
                }
            }
            return null;
        }
    }
}
