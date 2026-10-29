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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.annotation.runtime.AnnotatedTestOptIn;
import org.dbunit.annotation.runtime.AnnotationLookup;
import org.dbunit.annotation.runtime.DataFileLoaderInstances;
import org.dbunit.annotation.runtime.TestInstanceTesterResolver;
import org.dbunit.annotation.runtime.TesterFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.util.ClassUtils;

/**
 * Spring TestContext Framework {@link org.springframework.test.context.TestExecutionListener} for
 * DbUnit: drives the same {@code org.dbunit.annotation} family that
 * {@link org.dbunit.junit.jupiter.DbUnitExtension} drives for JUnit 5/6, for a test run through
 * Spring's TestContext Framework instead, with JUnit 5's {@code SpringExtension}. The annotation
 * vocabulary is identical either way.
 *
 * <p>dbUnit registers it as one of Spring's default test execution listeners, in its
 * {@code META-INF/spring.factories}, so a test class that keeps Spring's default listeners gets
 * it with no annotation. {@link DbUnitSpringTest @DbUnitSpringTest} registers it explicitly, and
 * explains which {@code @TestExecutionListeners} declarations drop it. On a Spring Framework
 * older than 6.0 it does nothing, and fails a test that uses dbUnit annotations, unless JUnit
 * Jupiter runs that test with {@code DbUnitExtension}.
 *
 * <h2>Which tests it acts on</h2>
 *
 * <p>Only a test that opts into the {@code org.dbunit.annotation} family: any of its configuring
 * annotations on the test method or class, or a
 * {@code @DbUnitTester}/{@code @DbUnitTestCase} field. For any other test it does nothing, so
 * {@link DbUnitSpringTest @DbUnitSpringTest} on a shared base class or composed annotation
 * neither needs a tester for, nor fails, the tests that use no DbUnit. A plain, unmarked
 * {@code IDatabaseTester} field is not an opt-in, and unlike {@code DbUnitExtension} there is no
 * lifecycle-only style to fall back to for it.
 *
 * <p>It also leaves alone a test that JUnit Jupiter runs with {@code DbUnitExtension}, registered
 * through {@code @DbUnitTest} or {@code @ExtendWith(DbUnitExtension.class)} on the test method,
 * the test class, a superclass or an enclosing class, or through a field of the test class, a
 * superclass or an enclosing class: a {@code @RegisterExtension} field declared as
 * {@code DbUnitExtension} or a subclass, or a field annotated
 * {@code @ExtendWith(DbUnitExtension.class)}. Then dbUnit runs once for the test, through the
 * extension. When the class registers this listener explicitly as well, it logs one warning.
 *
 * <h2>Resolving the tester or test case</h2>
 *
 * <p>First match wins: a {@code @DbUnitTestCase} field, a {@code @DbUnitTester} field,
 * {@code @DbUnitConfig(databaseTesterFactory = ...)}, then the 3.5.0-style auto-scan for exactly
 * one non-static {@code IDatabaseTester} field - the rules of {@code DbUnitExtension}, applied to
 * every enclosing test instance of a {@code @Nested} class that can be reached - and last the
 * {@code IDatabaseTester} bean of the test's {@code ApplicationContext}. The bean should be a
 * singleton.
 *
 * <h2>Shared testers</h2>
 *
 * <p>An {@code IDatabaseTester} bean is a singleton of the application context, which Spring
 * caches and shares among every test class with the same configuration. The listener only reads
 * the tester, so a shared one carries nothing from one test to the next, and it is safe for tests
 * that run at the same time as long as it hands out a connection of its own on each call.
 *
 * <h2>Not supported: parameter injection</h2>
 *
 * <p>Unlike {@code DbUnitExtension}, this listener never injects an {@code IDatabaseTester} or
 * connection as a test method parameter: Spring's {@code TestExecutionListener} has no equivalent
 * of JUnit 5's {@code ParameterResolver}. Declare a
 * {@code @DbUnitTester}/{@code @DbUnitTestCase} field, or autowire the bean, instead.
 *
 * <h2>Rolled-back transactional tests</h2>
 *
 * <p>The verification of {@code @DbUnitExpected} runs after the test-managed transaction has
 * ended, so a test whose transaction rolls back is verified without its own changes. Just before
 * such a test method runs, this listener logs one warning naming {@code @Commit} and
 * {@code @Rollback(false)} as the remedy, and if the verification then fails, attaches the same
 * hint to the failure as a suppressed exception.
 *
 * <p>How the bean candidates are chosen, how a {@code @Nested} class reaches its enclosing
 * instances, and the cases of shared testers and transactional tests are on the
 * <a href="https://dbunit.github.io/dbunit-extension/testcases/DbUnitTestExecutionListener.html">DbUnitTestExecutionListener</a>
 * site page.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 * @see DbUnitSpringTest
 */
public class DbUnitTestExecutionListener extends AbstractTestExecutionListener
{
    private static final Logger log = LoggerFactory.getLogger(DbUnitTestExecutionListener.class);

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

    private static final String ROLLED_BACK_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".rolledBack";

    private static final String DATA_FILE_LOADERS_ATTRIBUTE =
            DbUnitTestExecutionListener.class.getName() + ".dataFileLoaders";

    private static final String ROLLBACK_HINT = "The test's transaction rolled back before"
            + " @DbUnitExpected was verified, so the database no longer holds the changes the"
            + " test made and the expected dataset cannot match them. Add @Commit (or"
            + " @Rollback(false)) to the test, or leave it non-transactional.";

    /**
     * JUnit Jupiter's {@code @ExtendWith}, found by name so that this listener needs no JUnit
     * Jupiter on the class path.
     */
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";

    /**
     * JUnit Jupiter's {@code @RegisterExtension}, found by name for the same reason.
     */
    private static final String REGISTER_EXTENSION =
            "org.junit.jupiter.api.extension.RegisterExtension";

    private static final String DBUNIT_EXTENSION = "org.dbunit.junit.jupiter.DbUnitExtension";

    private static final String DBUNIT_TEST = "org.dbunit.junit.jupiter.DbUnitTest";

    /**
     * JUnit Jupiter's container of repeated {@code @ExtendWith} annotations, found by name.
     */
    private static final String EXTENSIONS = "org.junit.jupiter.api.extension.Extensions";

    private static final AtomicBoolean LOGGED_OLDER_SPRING = new AtomicBoolean();

    /**
     * The newest Spring Framework type this listener calls, {@code MergedAnnotations.Search} of
     * Spring Framework 6.0, which the listener checks for before it calls any other.
     */
    private static final String NEWEST_SPRING_TYPE =
            "org.springframework.core.annotation.MergedAnnotations$Search";

    private final TesterFields testerFields = new TesterFields();
    private final AnnotatedTestOptIn optIn = new AnnotatedTestOptIn(testerFields);
    private final SpringTesterResolver testerResolver = new SpringTesterResolver(testerFields,
            new TestInstanceTesterResolver(testerFields));

    private final BooleanSupplier testTransactionRollsBack;

    private final BooleanSupplier springIsRecentEnough;

    private final AtomicBoolean warnedOfBothBindings = new AtomicBoolean();

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
        this(testTransactionRollsBack,
                newSpringVersionCheck(DbUnitTestExecutionListener.class.getClassLoader()));
    }

    /**
     * Creates the listener with its own answers to whether the current test transaction rolls
     * back and whether the Spring Framework on the class path is recent enough for it, so a test
     * can exercise what the listener does on an older one.
     */
    DbUnitTestExecutionListener(final BooleanSupplier testTransactionRollsBack,
            final BooleanSupplier springIsRecentEnough)
    {
        this.testTransactionRollsBack = testTransactionRollsBack;
        this.springIsRecentEnough = springIsRecentEnough;
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
     * costs the tests that use no DbUnit nothing, not even a tester. Does nothing either for a
     * test that JUnit Jupiter runs with {@code DbUnitExtension}, which runs dbUnit for it.
     *
     * <p>Does nothing when the test context already holds an executor, as another instance of
     * this listener has set the test up. A class hierarchy whose {@code @TestExecutionListeners}
     * declarations replace Spring's default listeners registers one instance for each
     * declaration that names this listener, and the instances share the test context. The first
     * sets the test up, and the first to run {@link #afterTestMethod(TestContext)} finishes it,
     * so dbUnit runs once.
     *
     * @param testContext The test context.
     * @throws Exception If resolving the tester or test case, or running the before-test steps,
     *             fails.
     */
    @Override
    public void beforeTestMethod(final TestContext testContext) throws Exception
    {
        if (!springIsRecentEnough.getAsBoolean())
        {
            requireNoDbUnitOnOlderSpring(testContext);
            return;
        }
        if (testContext.hasAttribute(EXECUTOR_ATTRIBUTE))
        {
            return;
        }
        final AnnotationLookup lookup = new SpringAnnotationLookup(testContext);
        if (!isOptedIn(testContext, lookup))
        {
            return;
        }
        if (registersDbUnitExtension(testContext))
        {
            leaveToDbUnitExtension(testContext.getTestClass());
            return;
        }
        final DataFileLoaderInstances dataFileLoaders = dataFileLoadersOfTheTestClass(testContext);
        final AnnotatedTestConfiguration configuration =
                AnnotatedTestConfiguration.from(lookup, dataFileLoaders);
        final TestInstanceTesterResolver.Resolution resolution =
                testerResolver.resolve(testContext, configuration);
        final AnnotatedTestExecutor executor =
                new AnnotatedTestExecutor(configuration, resolution, true);
        testContext.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        executor.beforeTest();
    }

    /**
     * Returns the data file loaders the test methods of the test class share, one for each
     * caching loader class: they are kept as an attribute of the test context, which lives as
     * long as the test class.
     */
    private DataFileLoaderInstances dataFileLoadersOfTheTestClass(final TestContext testContext)
    {
        return testContext.computeAttribute(DATA_FILE_LOADERS_ATTRIBUTE,
                name -> new DataFileLoaderInstances());
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
     * is gone even when they fail.
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
        } catch (final Throwable afterTestFailure)
        {
            final boolean rolledBackBeforeVerification = rolledBack != null;
            final boolean failedVerification = isFailedVerification(afterTestFailure, testFailed);
            if (rolledBackBeforeVerification && failedVerification)
            {
                afterTestFailure.addSuppressed(new DatabaseUnitRuntimeException(ROLLBACK_HINT));
            }
            throw afterTestFailure;
        }
    }

    /**
     * Returns whether a failure of the after-test steps is the failure of the
     * {@code @DbUnitExpected} comparison, the only one a rolled-back transaction explains: an
     * {@link AssertionError} of a test that did not fail itself, since verification is skipped
     * for one that did. A failure of the teardown operation, of a connection or of the row count
     * check is none, and neither is any failure of the after-test steps of a failed test.
     */
    private static boolean isFailedVerification(final Throwable afterTestFailure,
            final boolean testFailed)
    {
        return !testFailed && afterTestFailure instanceof AssertionError;
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
     * Returns the check for whether the Spring Framework visible from {@code classLoader} is
     * recent enough for this listener: whether it has {@code MergedAnnotations.Search}, the
     * newest type the listener calls, which Spring Framework 6.0 added.
     */
    static BooleanSupplier newSpringVersionCheck(final ClassLoader classLoader)
    {
        final boolean recentEnough = ClassUtils.isPresent(NEWEST_SPRING_TYPE, classLoader);
        return () -> recentEnough;
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

    /**
     * Fails a test that uses dbUnit annotations when the Spring Framework on the class path is
     * older than this listener needs, and otherwise logs once for the run that the listener does
     * nothing. Spring runs this listener as one of its default listeners for every test of a
     * project that has dbUnit on its class path, also one that runs dbUnit only with JUnit
     * Jupiter on an older Spring Framework, so this method uses plain reflection, which every
     * version allows, and none of the newer Spring Framework API.
     */
    private void requireNoDbUnitOnOlderSpring(final TestContext testContext)
    {
        final Class<?> testClass = testContext.getTestClass();
        if (asksForThisListener(testClass, testContext.getTestMethod()))
        {
            throw new IllegalStateException(testClass.getName() + " uses dbUnit annotations,"
                    + " but DbUnitTestExecutionListener needs Spring Framework 6.2 or later, and"
                    + " the Spring Framework on the class path is older. Upgrade Spring"
                    + " Framework, or let DbUnitExtension run dbUnit for the class with"
                    + " @DbUnitTest.");
        }
        if (LOGGED_OLDER_SPRING.compareAndSet(false, true))
        {
            log.info("DbUnitTestExecutionListener needs Spring Framework 6.2 or later, and the"
                    + " Spring Framework on the class path is older, so the listener does"
                    + " nothing.");
        }
    }

    /**
     * Returns whether a test asks for this listener, as far as plain reflection tells: through
     * {@code @DbUnitSpringTest} or an {@code org.dbunit.annotation} annotation on the test method,
     * or on the test class, a superclass, an interface it implements or an enclosing class of an
     * inner class, directly or in a composed annotation however deep, or through a
     * {@code @DbUnitTester} or {@code @DbUnitTestCase} field of the test class, a superclass or
     * an enclosing class. A test that registers {@code DbUnitExtension} in those places, or
     * through a {@code @RegisterExtension} or {@code @ExtendWith} field, does not, as the
     * extension runs dbUnit for it.
     */
    private boolean asksForThisListener(final Class<?> testClass, final Method testMethod)
    {
        final List<Annotation> annotations = new ArrayList<>();
        addWithTheOnesTheyCompose(testMethod.getDeclaredAnnotations(), annotations);
        boolean usesDbUnit = false;
        for (final Class<?> inScope : testClassAndEnclosingClasses(testClass))
        {
            if (declaresDbUnitExtensionField(inScope))
            {
                return false;
            }
            addFromTypeHierarchy(inScope, annotations);
            if (testerFields.declaresMarkedField(inScope))
            {
                usesDbUnit = true;
            }
        }
        for (final Annotation annotation : annotations)
        {
            if (registersDbUnitExtensionByName(annotation))
            {
                return false;
            }
            if (isDbUnitAnnotation(annotation))
            {
                usesDbUnit = true;
            }
        }
        return usesDbUnit;
    }

    /**
     * Returns the test class and, for an inner class, each class enclosing it, innermost first.
     */
    private static List<Class<?>> testClassAndEnclosingClasses(final Class<?> testClass)
    {
        final List<Class<?>> classes = new ArrayList<>();
        Class<?> current = testClass;
        while (current != null)
        {
            classes.add(current);
            current = enclosingClassOfInnerClass(current);
        }
        return classes;
    }

    /**
     * Adds the annotations of the class, of the interfaces it implements and of its superclasses,
     * each with the ones it composes.
     */
    private static void addFromTypeHierarchy(final Class<?> type,
            final List<Annotation> annotations)
    {
        if (type == null || type == Object.class)
        {
            return;
        }
        addWithTheOnesTheyCompose(type.getDeclaredAnnotations(), annotations);
        for (final Class<?> implemented : type.getInterfaces())
        {
            addFromTypeHierarchy(implemented, annotations);
        }
        addFromTypeHierarchy(type.getSuperclass(), annotations);
    }

    private static void addWithTheOnesTheyCompose(final Annotation[] declared,
            final List<Annotation> annotations)
    {
        addWithTheOnesTheyCompose(declared, annotations, new HashSet<>());
    }

    /**
     * Adds the annotations with the ones they compose, however deep. It enters each annotation
     * type once, so an annotation that composes itself does not loop, and it leaves out the
     * annotations of {@code java.lang.annotation}, as they compose nothing that matters here.
     */
    private static void addWithTheOnesTheyCompose(final Annotation[] declared,
            final List<Annotation> annotations, final Set<Class<? extends Annotation>> visited)
    {
        for (final Annotation annotation : declared)
        {
            final Class<? extends Annotation> type = annotation.annotationType();
            if (isInJavaLangAnnotation(type))
            {
                continue;
            }
            annotations.add(annotation);
            if (visited.add(type))
            {
                addWithTheOnesTheyCompose(type.getDeclaredAnnotations(), annotations, visited);
            }
        }
    }

    private static boolean isInJavaLangAnnotation(final Class<? extends Annotation> type)
    {
        return type.getName().startsWith("java.lang.annotation.");
    }

    private static boolean isDbUnitAnnotation(final Annotation annotation)
    {
        final Class<? extends Annotation> type = annotation.annotationType();
        final String dbUnitAnnotations = DbUnitExpected.class.getPackage().getName();
        return type == DbUnitSpringTest.class
                || type.getPackage().getName().equals(dbUnitAnnotations);
    }

    /**
     * Returns whether an annotation registers {@code DbUnitExtension}, found by name: a
     * {@code @DbUnitTest}, an {@code @ExtendWith} that names the extension, or the
     * {@code @Extensions} container of repeated {@code @ExtendWith} annotations holding one.
     */
    private static boolean registersDbUnitExtensionByName(final Annotation annotation)
    {
        final String type = annotation.annotationType().getName();
        if (type.equals(DBUNIT_TEST))
        {
            return true;
        }
        if (type.equals(EXTEND_WITH))
        {
            final Class<?>[] extensions = (Class<?>[]) valueOf(annotation);
            return Arrays.stream(extensions)
                    .anyMatch(extension -> extension.getName().equals(DBUNIT_EXTENSION));
        }
        if (type.equals(EXTENSIONS))
        {
            final Annotation[] repeated = (Annotation[]) valueOf(annotation);
            return Arrays.stream(repeated)
                    .anyMatch(DbUnitTestExecutionListener::registersDbUnitExtensionByName);
        }
        return false;
    }

    private static Object valueOf(final Annotation annotation)
    {
        try
        {
            final Method value = annotation.annotationType().getMethod("value");
            return value.invoke(annotation);
        } catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot read the value of " + annotation + ".", e);
        }
    }

    private boolean isOptedIn(final TestContext testContext, final AnnotationLookup lookup)
    {
        final List<Class<?>> testClasses =
                TestInstanceChain.classesInScope(testContext.getTestClass());
        return optIn.isOptedIn(lookup, testClasses);
    }

    /**
     * Returns whether JUnit Jupiter registers {@code DbUnitExtension} for the test, so that the
     * extension runs dbUnit for it: through {@code @DbUnitTest} or
     * {@code @ExtendWith(DbUnitExtension.class)}, directly or in a composed annotation, on the
     * test method, or on the test class, a superclass, an interface it implements or an enclosing
     * class of a {@code @Nested} class, or through a field of the test class, a superclass or an
     * enclosing class: a {@code @RegisterExtension} field declared as {@code DbUnitExtension} or
     * a subclass, or a field annotated {@code @ExtendWith(DbUnitExtension.class)}. Every
     * enclosing class counts, as JUnit Jupiter hands a {@code @Nested} class the extensions of
     * the classes around it, whatever {@code @NestedTestConfiguration} says.
     */
    private boolean registersDbUnitExtension(final TestContext testContext)
    {
        final MergedAnnotations onTestMethod =
                MergedAnnotations.from(testContext.getTestMethod(), SearchStrategy.DIRECT);
        if (extendsWithDbUnitExtension(onTestMethod))
        {
            return true;
        }
        Class<?> testClass = testContext.getTestClass();
        while (testClass != null)
        {
            final MergedAnnotations onTestClass =
                    MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY);
            if (extendsWithDbUnitExtension(onTestClass))
            {
                return true;
            }
            if (declaresDbUnitExtensionField(testClass))
            {
                return true;
            }
            testClass = enclosingClassOfInnerClass(testClass);
        }
        return false;
    }

    private static boolean extendsWithDbUnitExtension(final MergedAnnotations annotations)
    {
        return annotations.stream(EXTEND_WITH).anyMatch(
                DbUnitTestExecutionListener::namesDbUnitExtension);
    }

    private static boolean namesDbUnitExtension(final MergedAnnotation<?> extendWith)
    {
        final Class<?>[] extensions = extendWith.getClassArray(MergedAnnotation.VALUE);
        return Arrays.stream(extensions)
                .anyMatch(extension -> extension.getName().equals(DBUNIT_EXTENSION));
    }

    /**
     * Returns whether the class or a superclass declares a field through which JUnit Jupiter
     * registers {@code DbUnitExtension}: a {@code @RegisterExtension} field declared as
     * {@code DbUnitExtension} or a subclass, or a field annotated, directly or in a composed
     * annotation, {@code @ExtendWith(DbUnitExtension.class)}. It does not recognize a
     * {@code @RegisterExtension} field declared as a supertype, such as {@code Extension}, as
     * without a test instance it knows only the declared type. Plain reflection serves every
     * Spring Framework version, so the older-Spring check uses this too.
     */
    private boolean declaresDbUnitExtensionField(final Class<?> clazz)
    {
        final Field field = testerFields.findFirst(clazz,
                DbUnitTestExecutionListener::registersDbUnitExtensionByField);
        return field != null;
    }

    private static boolean registersDbUnitExtensionByField(final Field field)
    {
        final List<Annotation> annotations = new ArrayList<>();
        addWithTheOnesTheyCompose(field.getDeclaredAnnotations(), annotations);
        final boolean declaredAsDbUnitExtension = isDbUnitExtensionType(field.getType());
        for (final Annotation annotation : annotations)
        {
            if (registersDbUnitExtensionByName(annotation))
            {
                return true;
            }
            final String type = annotation.annotationType().getName();
            if (type.equals(REGISTER_EXTENSION) && declaredAsDbUnitExtension)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isDbUnitExtensionType(final Class<?> fieldType)
    {
        Class<?> type = fieldType;
        while (type != null)
        {
            if (type.getName().equals(DBUNIT_EXTENSION))
            {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /**
     * Returns the class that encloses an inner class, or {@code null} for a top-level class or a
     * static nested one, which JUnit Jupiter runs without the extensions of any enclosing class.
     */
    private static Class<?> enclosingClassOfInnerClass(final Class<?> testClass)
    {
        final boolean isInnerClass =
                testClass.isMemberClass() && !Modifier.isStatic(testClass.getModifiers());
        if (isInnerClass)
        {
            return testClass.getEnclosingClass();
        }
        return null;
    }

    /**
     * Leaves a test to {@code DbUnitExtension}, and warns once, for the first test of the class,
     * when the class also registers this listener explicitly, as it then asked for both.
     */
    private void leaveToDbUnitExtension(final Class<?> testClass)
    {
        if (!registersThisListener(testClass))
        {
            log.debug("{} registers DbUnitExtension, which runs dbUnit for its tests, so this"
                    + " listener leaves them alone.", testClass.getName());
            return;
        }
        if (warnedOfBothBindings.compareAndSet(false, true))
        {
            log.warn("{} registers both DbUnitExtension, through @DbUnitTest,"
                    + " @ExtendWith(DbUnitExtension.class) or a @RegisterExtension field, and"
                    + " DbUnitTestExecutionListener, through @DbUnitSpringTest or"
                    + " @TestExecutionListeners. DbUnitExtension runs dbUnit for its tests and the"
                    + " listener leaves them alone, so dbUnit runs once. Keep one of the two:"
                    + " @DbUnitSpringTest to take the tester from the application context, or"
                    + " @DbUnitTest to have IDatabaseTester and IDatabaseConnection test method"
                    + " parameters injected.",
                    testClass.getName());
        }
    }

    /**
     * Returns whether the class names this listener in a {@code @TestExecutionListeners}
     * declaration, such as the one {@link DbUnitSpringTest @DbUnitSpringTest} carries, on itself,
     * a superclass or an enclosing class.
     */
    private static boolean registersThisListener(final Class<?> testClass)
    {
        final MergedAnnotations declarations = MergedAnnotations
                .search(SearchStrategy.TYPE_HIERARCHY)
                .withEnclosingClasses(TestContextAnnotationUtils::searchEnclosingClass)
                .from(testClass);
        return declarations.stream(TestExecutionListeners.class)
                .anyMatch(DbUnitTestExecutionListener::namesThisListener);
    }

    private static boolean namesThisListener(
            final MergedAnnotation<TestExecutionListeners> declaration)
    {
        final Class<?>[] listeners = declaration.getClassArray("listeners");
        return Arrays.asList(listeners).contains(DbUnitTestExecutionListener.class);
    }
}
