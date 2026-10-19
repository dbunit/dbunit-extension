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
import java.util.function.BooleanSupplier;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.annotation.runtime.AnnotatedTestOptIn;
import org.dbunit.annotation.runtime.AnnotationLookup;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.util.ClassUtils;

/**
 * Spring TestContext Framework {@link org.springframework.test.context.TestExecutionListener} for
 * DbUnit: drives the same {@code org.dbunit.annotation} family - {@code @DbUnitPrep},
 * {@code @DbUnitSetup}, {@code @DbUnitExpected}, {@code @DbUnitTearDown}, and
 * {@code @DbUnitConfig} - that {@link org.dbunit.junit.jupiter.DbUnitExtension} drives for
 * JUnit 5/6, for a test run through Spring's TestContext Framework instead, with JUnit 5's
 * {@code SpringExtension}. See {@code annotations.adoc} for the annotation vocabulary itself,
 * which is identical either way.
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
 *   {@code ApplicationContext} - the sole one, or when several exist the one Spring's candidate
 *   rules single out: the one marked {@code @Primary}, the only one not marked
 *   {@code @Fallback}, or the only default candidate - so a test class needs no dbUnit-specific
 *   field at all when its test configuration already exposes one, e.g. a
 *   {@code @TestConfiguration} bean wrapping the application's own {@code DataSource} in a
 *   {@link org.dbunit.DataSourceDatabaseTester}. No bean, or several that those rules do not tell
 *   apart, is not an error here, since an unused bean of this type is an unremarkable case for a
 *   Spring context.</li>
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
 * {@code TestExecutionListener} has no equivalent of JUnit 5's {@code ParameterResolver}. Declare
 * a {@code @DbUnitTester}/{@code @DbUnitTestCase} field, or autowire the {@code IDatabaseTester}
 * bean, to reach it from the test method; {@code SpringExtension} itself resolves
 * {@code @Autowired} test method parameters, so the bean can be a parameter too.
 * {@code @DbUnitConfig(injectConnectionParameter)} is specific to the JUnit 5 binding and has no
 * effect here.
 *
 * <h2>Rolled-back transactional tests</h2>
 *
 * <p>The verification of {@code @DbUnitExpected} runs after the test-managed transaction has
 * ended, so a test whose transaction rolls back is verified without its own changes. Just before
 * such a test method runs, this listener logs one warning naming {@code @Commit} and
 * {@code @Rollback(false)} as the remedy, and if the verification then fails, attaches the same
 * hint to the failure as a suppressed exception. Without {@code spring-tx} on the classpath the
 * test cannot be transactional, and neither is done.
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

    private static final Logger log = LoggerFactory.getLogger(DbUnitTestExecutionListener.class);

    /** Package-visible so tests can reference it instead of duplicating the literal. */
    static final String EXECUTOR_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".executor";

    private static final String ROLLED_BACK_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".rolledBack";

    private static final String ROLLBACK_HINT = "The test's transaction rolled back before"
            + " @DbUnitExpected was verified, so the database no longer holds the changes the"
            + " test made and the expected dataset cannot match them. Add @Commit (or"
            + " @Rollback(false)) to the test, or leave it non-transactional.";

    private final SpringTesterResolver testerResolver = new SpringTesterResolver();

    private final BooleanSupplier testTransactionRollsBack;

    /** Creates the listener; Spring instantiates it through this constructor. */
    public DbUnitTestExecutionListener()
    {
        this(newTestTransactionRollbackCheck(DbUnitTestExecutionListener.class.getClassLoader()));
    }

    /**
     * Creates the listener with its own answer to whether the current test transaction is set
     * to roll back, so a test needs no transaction to exercise what the listener does with it.
     */
    DbUnitTestExecutionListener(final BooleanSupplier testTransactionRollsBack)
    {
        this.testTransactionRollsBack = testTransactionRollsBack;
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
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, resolution, true);
        testContext.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        executor.beforeTest();
    }

    /**
     * Warns, just before the test method runs, when the test declares {@code @DbUnitExpected} and
     * its test-managed transaction is set to roll back: the verification runs after the rollback
     * and cannot see what the test changed. Does nothing for a test this listener does not act
     * on.
     *
     * @param testContext The test context.
     */
    @Override
    public void beforeTestExecution(final TestContext testContext)
    {
        if (!isVerifiedInRollingBackTransaction(testContext))
        {
            return;
        }
        log.warn("{}.{}() declares @DbUnitExpected, but its test transaction is set to roll"
                + " back, so the verification will not see the test's changes. Add @Commit (or"
                + " @Rollback(false)) to the test, or leave it non-transactional.",
                testContext.getTestClass().getSimpleName(), testContext.getTestMethod().getName());
    }

    /**
     * Remembers whether the test declares {@code @DbUnitExpected} and its test-managed
     * transaction is set to roll back, for {@link #afterTestMethod(TestContext)} to explain a
     * failed verification. Runs while the transaction is still active, so it sees the final
     * decision, including a {@code TestTransaction.flagForCommit()} the test made.
     *
     * @param testContext The test context.
     */
    @Override
    public void afterTestExecution(final TestContext testContext)
    {
        if (isVerifiedInRollingBackTransaction(testContext))
        {
            testContext.setAttribute(ROLLED_BACK_ATTRIBUTE, Boolean.TRUE);
        }
    }

    /**
     * Removes the {@link AnnotatedTestExecutor} {@link #beforeTestMethod(TestContext)} kept for
     * this test method and runs its after-test steps: the verification of
     * {@code @DbUnitExpected} and the teardown operation. Does nothing when
     * {@link #beforeTestMethod(TestContext)} never ran for this method, so it never tears down
     * what another method set up. The executor is removed before its after-test steps run, so it
     * is gone even when they fail. Once they have run, or failed, a tester the test's injected
     * {@code @DbUnitTestCase} instance was only given for this test is taken off it again.
     *
     * <p>Runs in Spring's after-test-method phase, so after JUnit's {@code @AfterEach} methods
     * and after a {@code @Transactional} test's transaction has rolled back or committed: a
     * verification sees only what that transaction committed.
     *
     * <p>Verification is skipped when the test failed - an exception from the test method, from a
     * {@code @BeforeEach} or {@code @AfterEach} method, or from an extension - so a difference
     * never masks the real failure. A verification that fails after the test's transaction rolled
     * back carries a hint to add {@code @Commit} as a suppressed exception.
     *
     * @param testContext The test context.
     * @throws Exception If the after-test steps fail.
     */
    @Override
    public void afterTestMethod(final TestContext testContext) throws Exception
    {
        final AnnotatedTestExecutor executor =
                (AnnotatedTestExecutor) testContext.removeAttribute(EXECUTOR_ATTRIBUTE);
        final Object rolledBack = testContext.removeAttribute(ROLLED_BACK_ATTRIBUTE);
        if (executor == null)
        {
            return;
        }
        final Throwable testException = testContext.getTestException();
        final boolean testFailed = testException != null;
        try
        {
            executor.afterTest(testFailed);
        } catch (final Throwable verificationFailure)
        {
            if (rolledBack != null)
            {
                verificationFailure.addSuppressed(new DatabaseUnitRuntimeException(ROLLBACK_HINT));
            }
            throw verificationFailure;
        } finally
        {
            executor.unbindTesterFromInjectedTestCase();
        }
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

    private boolean isVerifiedInRollingBackTransaction(final TestContext testContext)
    {
        if (testContext.getAttribute(EXECUTOR_ATTRIBUTE) == null)
        {
            return false;
        }
        final AnnotationLookup lookup = new SpringAnnotationLookup(testContext);
        if (lookup.find(DbUnitExpected.class) == null)
        {
            return false;
        }
        return testTransactionRollsBack.getAsBoolean();
    }

    /**
     * Returns the check for a test transaction set to roll back, or one that never finds
     * one when {@code spring-tx}, which Spring's {@code TestTransaction} needs, is not visible
     * from {@code classLoader}. Spring's {@code spring-test} does not itself require it.
     * Package-visible so a test can pass a class loader that hides {@code spring-tx}.
     */
    static BooleanSupplier newTestTransactionRollbackCheck(final ClassLoader classLoader)
    {
        if (ClassUtils.isPresent("org.springframework.transaction.PlatformTransactionManager",
                classLoader))
        {
            return new TestTransactionRollbackCheck();
        }
        return () -> false;
    }

    /**
     * Asks Spring's {@link TestTransaction}. A class of its own so that it is loaded, and with
     * it {@code spring-tx}, only when {@code spring-tx} is present.
     */
    static class TestTransactionRollbackCheck implements BooleanSupplier
    {
        /**
         * {@inheritDoc}
         * <p>
         * Returns {@code true} when {@link TestTransaction#isActive()} and
         * {@link TestTransaction#isFlaggedForRollback()} both return {@code true}.
         */
        @Override
        public boolean getAsBoolean()
        {
            return TestTransaction.isActive() && TestTransaction.isFlaggedForRollback();
        }
    }

    private boolean isOptedIn(final TestContext testContext, final AnnotationLookup lookup)
    {
        final List<Class<?>> testClasses =
                TestInstanceChain.classesInScope(testContext.getTestClass());
        return AnnotatedTestOptIn.isOptedIn(lookup, testClasses);
    }
}
