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
import java.lang.reflect.Method;

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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

/**
 * Spring TestContext Framework {@link TestExecutionListener} for DbUnit: drives the same
 * {@code org.dbunit.annotation} family - {@link DbUnitPrep}, {@link DbUnitSetup},
 * {@link DbUnitExpected}, {@link DbUnitTearDown}, and {@link DbUnitConfig} -
 * {@link org.dbunit.junit.jupiter.DbUnitExtension} drives for JUnit 5/6, for a test run through
 * Spring's TestContext Framework instead (JUnit 4's {@code SpringJUnit4ClassRunner}, JUnit 5's
 * {@code SpringExtension}, or Spring's TestNG support). See {@code annotations.adoc} for the
 * annotation vocabulary itself, which is identical either way.
 *
 * <p>Register it with {@link org.dbunit.spring.DbUnitSpringTest @DbUnitSpringTest}, or directly
 * with {@code @TestExecutionListeners(listeners = DbUnitTestExecutionListener.class, mergeMode =
 * MERGE_WITH_DEFAULTS)} - the {@code mergeMode} is not optional: {@code @TestExecutionListeners}
 * without it <em>replaces</em> Spring's default listeners, silently dropping
 * {@link DependencyInjectionTestExecutionListener} ({@code @Autowired} stops working) and
 * {@link TransactionalTestExecutionListener} ({@code @Transactional} stops working).
 *
 * <h2>Resolving the tester or test case</h2>
 *
 * <p>See {@link SpringTesterResolver} for the full precedence; the difference from
 * {@code DbUnitExtension} is an added tier: when no field resolves a tester, exactly one
 * {@link IDatabaseTester} bean in the test's {@code ApplicationContext} is used, so a test class
 * needs no dbUnit-specific field at all when its test configuration already exposes one - e.g. a
 * {@code @TestConfiguration} bean wrapping the application's own {@code DataSource} in a
 * {@link org.dbunit.DataSourceDatabaseTester}.
 *
 * <h2>Not supported: parameter injection</h2>
 *
 * <p>Unlike {@code DbUnitExtension}, this listener never injects an {@link IDatabaseTester},
 * {@link PrepAndExpectedTestCase}, or connection as a test method parameter - Spring's
 * {@code TestExecutionListener} has no equivalent of JUnit 5's {@code ParameterResolver}; Spring
 * tests receive dependencies through field/{@code @Autowired} injection instead. Declare a
 * {@link DbUnitTester @DbUnitTester}/{@link DbUnitTestCase @DbUnitTestCase} field, or autowire
 * the {@link IDatabaseTester} bean directly, to reach it from the test method.
 * {@link DbUnitConfig#injectConnectionParameter()} is specific to the JUnit 5 binding and has no
 * effect here.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSpringTest
 * @see SpringTesterResolver
 */
public class DbUnitTestExecutionListener extends AbstractTestExecutionListener
{
    /**
     * Runs after {@link DependencyInjectionTestExecutionListener} (order 2000), so an
     * {@code @Autowired} field this listener's own resolution looks for is already populated,
     * and before {@link TransactionalTestExecutionListener} (order 4000): Spring's
     * {@code TestContextManager} wraps listeners in registration order - an earlier-ordered
     * listener's before-callback runs first and its after-callback runs last - so this ordering
     * makes this listener's own prep commit, through its own {@link IDatabaseTester} connection,
     * before a {@code @Transactional} test's managed transaction opens (so that transaction sees
     * the prepared rows), and this listener's verify/teardown run only after that transaction has
     * committed or rolled back (so they see final state) - rather than the prep and the test
     * method silently running on two different, unsynchronized connections.
     */
    public static final int ORDER = 3000;

    private static final String EXECUTOR_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".executor";

    private final SpringTesterResolver testerResolver = new SpringTesterResolver();

    /**
     * Resolves (and caches, for {@link #afterTestExecution(TestContext)}) this test's
     * {@link AnnotatedTestExecutor} and runs its before-test steps.
     *
     * @param testContext The test context.
     * @throws Exception If resolving the tester or test case, or running the before-test steps,
     *             fails.
     */
    @Override
    public void beforeTestExecution(final TestContext testContext) throws Exception
    {
        resolveExecutor(testContext).beforeTest();
    }

    /**
     * Runs the {@link AnnotatedTestExecutor} {@link #beforeTestExecution(TestContext)} resolved
     * for this test's after-test steps.
     *
     * @param testContext The test context.
     * @throws Exception If the after-test steps fail.
     */
    @Override
    public void afterTestExecution(final TestContext testContext) throws Exception
    {
        final AnnotatedTestExecutor executor =
                (AnnotatedTestExecutor) testContext.getAttribute(EXECUTOR_ATTRIBUTE);
        if (executor == null)
        {
            return;
        }
        executor.afterTest(testContext.getTestException() != null);
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

    private AnnotatedTestExecutor resolveExecutor(final TestContext testContext) throws Exception
    {
        AnnotatedTestExecutor executor =
                (AnnotatedTestExecutor) testContext.getAttribute(EXECUTOR_ATTRIBUTE);
        if (executor == null)
        {
            final AnnotatedTestConfiguration configuration = resolveConfiguration(testContext);
            final SpringTesterResolver.Resolution resolution =
                    testerResolver.resolve(testContext, configuration);
            executor = new AnnotatedTestExecutor(configuration, resolution.tester,
                    resolution.testCase, true);
            testContext.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        }
        return executor;
    }

    private AnnotatedTestConfiguration resolveConfiguration(final TestContext testContext)
    {
        final Class<?> testClass = testContext.getTestClass();
        final Method testMethod = testContext.getTestMethod();
        final DbUnitConfig config = findAnnotation(testMethod, testClass, DbUnitConfig.class);
        final DbUnitPrep prep = findAnnotation(testMethod, testClass, DbUnitPrep.class);
        final DbUnitSetup setup = findAnnotation(testMethod, testClass, DbUnitSetup.class);
        final DbUnitExpected expected =
                findAnnotation(testMethod, testClass, DbUnitExpected.class);
        final DbUnitTearDown tearDown =
                findAnnotation(testMethod, testClass, DbUnitTearDown.class);
        final DbUnitRowCountCheck rowCountCheck =
                findAnnotation(testMethod, testClass, DbUnitRowCountCheck.class);
        return AnnotatedTestConfiguration.from(testClass, config, prep, setup, expected, tearDown,
                rowCountCheck);
    }

    /**
     * Finds {@code annotationType}, trying the test method first and the class second - including
     * meta-annotations and {@code @Inherited} superclasses, via {@link AnnotatedElementUtils} -
     * so a method-level annotation continues to win over a class-level one.
     */
    private <A extends Annotation> A findAnnotation(final Method testMethod,
            final Class<?> testClass, final Class<A> annotationType)
    {
        final A onMethod = AnnotatedElementUtils.findMergedAnnotation(testMethod, annotationType);
        if (onMethod != null)
        {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(testClass, annotationType);
    }
}
