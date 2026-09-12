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

import java.util.List;

import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.annotation.runtime.AnnotatedTestOptIn;
import org.dbunit.annotation.runtime.AnnotationLookup;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Spring TestContext Framework {@link org.springframework.test.context.TestExecutionListener} for
 * DbUnit: drives the same {@code org.dbunit.annotation} family - {@code @DbUnitPrep},
 * {@code @DbUnitSetup}, {@code @DbUnitExpected}, {@code @DbUnitTearDown}, and
 * {@code @DbUnitConfig} - that {@link org.dbunit.junit.jupiter.DbUnitExtension} drives for
 * JUnit 5/6, for a test run through Spring's TestContext Framework instead (JUnit 5's
 * {@code SpringExtension}, JUnit 4's {@code SpringJUnit4ClassRunner} - deprecated since Spring
 * 7.0 in favor of {@code SpringExtension} - or Spring's TestNG support). See
 * {@code annotations.adoc} for the annotation vocabulary itself, which is identical either way.
 *
 * <p>Register it with {@link DbUnitSpringTest @DbUnitSpringTest}, or directly with
 * {@code @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class, mergeMode =
 * MERGE_WITH_DEFAULTS)}. The {@code mergeMode} is not optional, and {@code DbUnitSpringTest}
 * explains why, and what else stops it from registering the listener.
 *
 * <h2>Which tests it acts on</h2>
 *
 * <p>Only a test that opts into the {@code org.dbunit.annotation} family, by the rule
 * {@code DbUnitExtension} applies for its annotation-driven lifecycle: any of
 * {@code @DbUnitConfig}, {@code @DbUnitPrep}, {@code @DbUnitSetup}, {@code @DbUnitExpected},
 * {@code @DbUnitTearDown} or {@code @DbUnitRowCountCheck} on the test method or class, or a
 * {@code @DbUnitTester}/{@code @DbUnitTestCase} field. For any other test it does nothing, so
 * {@link DbUnitSpringTest @DbUnitSpringTest} on a shared base class or composed annotation
 * neither needs a tester for, nor fails, the tests that use no DbUnit. A plain, unmarked
 * {@code IDatabaseTester} field is not an opt-in, and unlike {@code DbUnitExtension} there is no
 * lifecycle-only style to fall back to for it.
 *
 * <h2>Resolving the tester or test case</h2>
 *
 * <p>First match wins:
 * <ol>
 *   <li>A field annotated {@code @DbUnitTestCase}, whose type implements
 *   {@link org.dbunit.PrepAndExpectedTestCase} - that instance is driven directly.</li>
 *   <li>A field annotated {@code @DbUnitTester}, whose type implements
 *   {@link org.dbunit.IDatabaseTester}.</li>
 *   <li>{@code @DbUnitConfig(databaseTesterFactory = ...)}, reflectively instantiated and asked
 *   to create a tester.</li>
 *   <li>The 3.5.0-style auto-scan: exactly one non-static field assignable to
 *   {@code IDatabaseTester}.</li>
 *   <li>Spring-specific, and last: the {@code IDatabaseTester} bean in the test's
 *   {@code ApplicationContext} - the sole one, or when several exist the one marked
 *   {@code @Primary} or the only one not marked {@code @Fallback} - so a test class needs no
 *   dbUnit-specific field at all when its test configuration already exposes one, e.g. a
 *   {@code @TestConfiguration} bean wrapping the application's own {@code DataSource} in a
 *   {@link org.dbunit.DataSourceDatabaseTester}. No bean, or several that neither
 *   {@code @Primary} nor {@code @Fallback} tells apart, is not an error here, since an unused
 *   bean of this type is an unremarkable case for a Spring context.</li>
 * </ol>
 *
 * <p>The first four tiers are {@code DbUnitExtension}'s, by the same rules: fields are found in
 * the whole class hierarchy of the test instance, and a second marked field there is ambiguous.
 * The bean should be a singleton: for a prototype-scoped bean the lookup creates a new instance
 * that the test's own {@code @Autowired} field does not share.
 *
 * <h2>Shared testers</h2>
 *
 * <p>An {@code IDatabaseTester} bean is a singleton of the application context, and Spring caches
 * a context and shares it among every test class with the same configuration, so one tester
 * serves all of them. The listener sets the dataset and the operations on it for each test and
 * restores them afterwards, and wraps its operation listener while the test runs, all without
 * synchronization. A tester shared this way - or through a {@code static}
 * {@code @DbUnitTester} field, or any instance field under
 * {@code @TestInstance(Lifecycle.PER_CLASS)} - is therefore not safe for tests that run at the
 * same time, such as under {@code junit.jupiter.execution.parallel.enabled=true}: run them
 * sequentially, or give each its own tester.
 *
 * <h2>Nested test classes</h2>
 *
 * <p>For a JUnit Jupiter {@code @Nested} test class Spring inherits the enclosing class's
 * configuration, and so does this listener, as {@code DbUnitExtension} does: the enclosing
 * class's class-level annotations apply unless the nested class or its method declares its own,
 * and the tester field is searched in the nested instance first, then in each enclosing instance
 * outward. An enclosing instance is reachable only when the nested class uses it - see the
 * {@code DbUnitTestExecutionListener} site page for how to work with that limit.
 *
 * <h2>Not supported: parameter injection</h2>
 *
 * <p>Unlike {@code DbUnitExtension}, this listener never injects an {@code IDatabaseTester},
 * {@code PrepAndExpectedTestCase}, or connection as a test method parameter - Spring's
 * {@code TestExecutionListener} has no equivalent of JUnit 5's {@code ParameterResolver}; Spring
 * tests receive dependencies through field/{@code @Autowired} injection instead. Declare a
 * {@code @DbUnitTester}/{@code @DbUnitTestCase} field, or autowire the {@code IDatabaseTester}
 * bean directly, to reach it from the test method.
 * {@code @DbUnitConfig(injectConnectionParameter)} is specific to the JUnit 5 binding and has no
 * effect here.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSpringTest
 */
public class DbUnitTestExecutionListener extends AbstractTestExecutionListener
{
    /**
     * Runs between Spring's {@code DirtiesContextTestExecutionListener} (order 3000) and its
     * {@code TransactionalTestExecutionListener} (order 4000), in the same before-test-method and
     * after-test-method phases the latter starts and ends a {@code @Transactional} test's
     * transaction in. Spring runs the before-callbacks in ascending and the after-callbacks in
     * reverse order, so this listener's prep commits before that transaction opens, and its
     * verify and teardown run after the transaction has ended but before {@code @DirtiesContext}
     * closes the application context.
     *
     * <p>Its database work is therefore never part of that transaction: it neither joins it nor
     * is rolled back by it. See the {@code DbUnitTestExecutionListener} site page for what that
     * means for a test.
     */
    public static final int ORDER = 3500;

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String EXECUTOR_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".executor";

    private final SpringTesterResolver testerResolver = new SpringTesterResolver();
    private final DeclaredExpectedExceptions declaredExpectedExceptions;

    /** Creates the listener; Spring instantiates it through this constructor. */
    public DbUnitTestExecutionListener()
    {
        this(DeclaredExpectedExceptions.testNg());
    }

    DbUnitTestExecutionListener(final DeclaredExpectedExceptions declaredExpectedExceptions)
    {
        this.declaredExpectedExceptions = declaredExpectedExceptions;
    }

    /**
     * Resolves a fresh {@link AnnotatedTestExecutor} for this test method, keeps it on the
     * {@link TestContext} for {@link #afterTestMethod(TestContext)}, and runs its before-test
     * steps: the prep dataset and setup operation.
     *
     * <p>Runs in Spring's before-test-method phase, so ahead of JUnit's {@code @BeforeEach}
     * methods and of the test-managed transaction of a {@code @Transactional} test, and after
     * dependency injection - an {@code @Autowired} tester field is populated by now, but one
     * assigned in a {@code @BeforeEach} method is not.
     *
     * <p>Spring's {@link TestContext}, attributes included, serves every test method of a class,
     * so the executor is built anew for each method and removed again after it, never reused. It
     * is stored before its before-test steps run, so a setup that fails part way is still
     * released by {@link #afterTestMethod(TestContext)}.
     *
     * <p>Does nothing for a test that does not opt into the {@code org.dbunit.annotation}
     * family - one with none of its configuring annotations on the method or class, and no
     * {@code @DbUnitTester}/{@code @DbUnitTestCase} field - so
     * {@link DbUnitSpringTest @DbUnitSpringTest} on a shared base class or composed annotation
     * costs the tests that use no DbUnit nothing, not even a tester.
     *
     * @param testContext The test context.
     * @throws Exception If resolving the tester or test case, or running the before-test steps,
     *             fails.
     */
    @Override
    public void beforeTestMethod(final TestContext testContext) throws Exception
    {
        final AnnotationLookup lookup = new SpringAnnotationLookup(testContext);
        if (!isOptedIn(testContext, lookup))
        {
            return;
        }
        final Class<?> testClass = testContext.getTestClass();
        final AnnotatedTestConfiguration configuration =
                AnnotatedTestConfiguration.from(testClass, lookup);
        final TestInstanceTesterResolver.Resolution resolution =
                testerResolver.resolve(testContext, configuration);
        final IDatabaseTester tester = resolution.getTester();
        final PrepAndExpectedTestCase testCase = resolution.getTestCase();
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, tester, testCase);
        testContext.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        executor.beforeTest();
    }

    /**
     * Removes the {@link AnnotatedTestExecutor} {@link #beforeTestMethod(TestContext)} kept for
     * this test method and runs its after-test steps: the verification of
     * {@code @DbUnitExpected} and the teardown operation. Does nothing when
     * {@link #beforeTestMethod(TestContext)} never ran for this method, so it never tears down
     * what another method set up. The executor is removed before its after-test steps run, so it
     * is gone even when they fail.
     *
     * <p>Runs in Spring's after-test-method phase, so after JUnit's {@code @AfterEach} methods
     * and after a {@code @Transactional} test's transaction has rolled back or committed: a
     * verification sees only what that transaction committed.
     *
     * <p>Verification is skipped when the test method failed, so a difference never masks the real
     * failure. A test that threw an exception its own framework expects has not failed: JUnit 4's
     * runner consumes the exception of a {@code @Test(expected = ...)} before Spring sees it, and
     * the {@code expectedExceptions} of TestNG's {@code @Test} are recognized here, so such a
     * test is verified like any other passing test.
     *
     * @param testContext The test context.
     * @throws Exception If the after-test steps fail.
     */
    @Override
    public void afterTestMethod(final TestContext testContext) throws Exception
    {
        final AnnotatedTestExecutor executor =
                (AnnotatedTestExecutor) testContext.removeAttribute(EXECUTOR_ATTRIBUTE);
        if (executor == null)
        {
            return;
        }
        final boolean testFailed = hasFailed(testContext);
        executor.afterTest(testFailed);
    }

    /**
     * Returns {@link #ORDER}.
     *
     * @return {@link #ORDER}.
     */
    @Override
    public int getOrder()
    {
        return ORDER;
    }

    /**
     * Returns whether the test method failed: it threw, and the exception is not one the test
     * framework counts as the expected, passing outcome - see {@link DeclaredExpectedExceptions}.
     */
    private boolean hasFailed(final TestContext testContext)
    {
        final Throwable testException = testContext.getTestException();
        if (testException == null)
        {
            return false;
        }
        return !declaredExpectedExceptions.expects(testContext.getTestMethod(), testException);
    }

    private boolean isOptedIn(final TestContext testContext, final AnnotationLookup lookup)
    {
        final List<Class<?>> testClasses =
                TestInstanceChain.classesInScope(testContext.getTestClass());
        return AnnotatedTestOptIn.isOptedIn(lookup, testClasses);
    }
}
