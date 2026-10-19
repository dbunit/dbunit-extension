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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class DbUnitTestExecutionListenerTest
{
    private static final String EXECUTOR_ATTRIBUTE =
            DbUnitTestExecutionListener.EXECUTOR_ATTRIBUTE;

    private final DbUnitTestExecutionListener listener = new DbUnitTestExecutionListener();

    @Test
    void testGetOrder_defaultListener_returnsOrderConstant()
    {
        assertThat(listener.getOrder())
                .as("getOrder() must report the public ORDER constant.")
                .isEqualTo(DbUnitTestExecutionListener.ORDER);
    }

    @Test
    void testGetOrder_springsDirtiesContextAndTransactionalListeners_fallsBetweenThem()
    {
        final int dirtiesContextOrder = new DirtiesContextTestExecutionListener().getOrder();
        final int transactionalOrder = new TransactionalTestExecutionListener().getOrder();

        assertThat(listener.getOrder())
                .as("Before the test-managed transaction opens, so prep commits first and"
                        + " teardown runs after it ended, yet after @DirtiesContext, so the"
                        + " application context is not closed under verify and teardown.")
                .isGreaterThan(dirtiesContextOrder).isLessThan(transactionalOrder);
    }

    @Test
    void testBeforeAndAfterTestMethod_oneTestMethod_runsOnSetupThenOnTearDownOnSameTester()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).onSetup();
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTestMethod_noPriorBeforeTestMethod_doesNothing() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        assertThatCode(() -> listener.afterTestMethod(context))
                .as("afterTestMethod() must be a no-op when beforeTestMethod() never ran"
                        + " for this test - e.g. a class-level failure before the test method"
                        + " itself started.")
                .doesNotThrowAnyException();
        verify(tester, never()).onTearDown();
    }

    @Test
    void testAfterTestMethod_testExceptionPresent_passesTestFailedTrueToExecutor()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod")
                .failWith(new RuntimeException("test method failed"));
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        listener.afterTestMethod(context);

        verify(executor).afterTest(true);
    }

    @Test
    void testAfterTestMethod_noTestException_passesTestFailedFalseToExecutor() throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        listener.afterTestMethod(context);

        verify(executor).afterTest(false);
    }

    @Test
    void testAfterTestMethod_executorStored_unbindsTheTesterFromTheInjectedTestCaseAfterTheAfterTestSteps()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        listener.afterTestMethod(context);

        final InOrder inOrder = inOrder(executor);
        inOrder.verify(executor).afterTest(false);
        inOrder.verify(executor).unbindTesterFromInjectedTestCase();
    }

    @Test
    void testAfterTestMethod_afterTestStepsFail_stillUnbindsTheTesterFromTheInjectedTestCase()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        doThrow(new IllegalStateException("verification failed")).when(executor)
                .afterTest(false);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        assertThatThrownBy(() -> listener.afterTestMethod(context))
                .as("The failure of the after-test steps is the one reported.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("verification failed");
        verify(executor).unbindTesterFromInjectedTestCase();
    }

    @Test
    void testBeforeAndAfterTestMethod_methodAndClassLevelDbUnitTearDown_methodLevelWins()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasClassAndMethodLevelTearDown.class)
                .beginMethod(new HasClassAndMethodLevelTearDown(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).setTearDownOperation(DatabaseOperation.TRUNCATE_TABLE);
        verify(tester, never()).setTearDownOperation(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testBeforeAndAfterTestMethod_nestedClassWithoutOwnAnnotations_usesEnclosingClassesTesterAndTearDown()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown(tester);
        final EnclosingWithTearDown.Nested nested = enclosing.new Nested();
        final FakeTestContext context = new FakeTestContext(EnclosingWithTearDown.Nested.class)
                .beginMethod(nested, "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).onSetup();
        verify(tester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(tester).onTearDown();
    }

    @Test
    void testBeforeAndAfterTestMethod_nestedClassWithOwnTearDown_nestedTearDownWinsOverEnclosing()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final EnclosingWithTearDown enclosing = new EnclosingWithTearDown(tester);
        final EnclosingWithTearDown.NestedWithTearDown nested = enclosing.new NestedWithTearDown();
        final FakeTestContext context =
                new FakeTestContext(EnclosingWithTearDown.NestedWithTearDown.class)
                        .beginMethod(nested, "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).setTearDownOperation(DatabaseOperation.TRUNCATE_TABLE);
        verify(tester, never()).setTearDownOperation(DatabaseOperation.DELETE_ALL);
    }

    @Test
    void testBeforeAndAfterTestMethod_onlyUnmarkedTesterFieldAndNoDbUnitAnnotation_leavesTesterAlone()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasUnmarkedTesterFieldOnly.class)
                .beginMethod(new HasUnmarkedTesterFieldOnly(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester, never()).onSetup();
        verify(tester, never()).onTearDown();
        assertThat(context.hasAttribute(EXECUTOR_ATTRIBUTE))
                .as("A test that did not opt in must not get an executor.").isFalse();
    }

    @Test
    void testBeforeAndAfterTestMethod_dbUnitSpringTestOnlyAndNoTesterAnywhere_doesNothing()
            throws Exception
    {
        final FakeTestContext context = new FakeTestContext(DbUnitSpringTestOnly.class)
                .beginMethod(new DbUnitSpringTestOnly(), "aTestMethod");

        assertThatCode(() ->
        {
            listener.beforeTestMethod(context);
            listener.afterTestMethod(context);
        }).as("@DbUnitSpringTest alone, e.g. inherited from a base class, is no reason to look"
                + " for a tester: a test that uses no DbUnit must not fail for want of one.")
                .doesNotThrowAnyException();
    }

    @Test
    void testBeforeAndAfterTestMethod_dbUnitAnnotationOnClassAndUnmarkedTesterField_runsLifecycle()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(ClassLevelTearDownOnly.class)
                .beginMethod(new ClassLevelTearDownOnly(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).onSetup();
        verify(tester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(tester).onTearDown();
    }

    @Test
    void testBeforeAndAfterTestMethod_composedAnnotationBundlingDbUnitAnnotation_runsLifecycle()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(ComposedTearDownOnly.class)
                .beginMethod(new ComposedTearDownOnly(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).onSetup();
        verify(tester).onTearDown();
    }

    @Test
    void testBeforeAndAfterTestMethod_markedTesterFieldOnlyOnEnclosingClass_runsLifecycleForNestedTest()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final EnclosingWithMarkedTester enclosing = new EnclosingWithMarkedTester(tester);
        final EnclosingWithMarkedTester.Nested nested = enclosing.new Nested();
        final FakeTestContext context = new FakeTestContext(EnclosingWithMarkedTester.Nested.class)
                .beginMethod(nested, "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(tester).onSetup();
        verify(tester).onTearDown();
    }

    @Test
    void testBeforeAndAfterTestMethod_twoMethodsSharingOneTestContext_eachUsesItsOwnTesterAndTearDown()
            throws Exception
    {
        final IDatabaseTester firstTester = mock(IDatabaseTester.class);
        final IDatabaseTester secondTester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasTwoTestMethods.class);

        context.beginMethod(new HasTwoTestMethods(firstTester), "firstTestMethod");
        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);
        context.beginMethod(new HasTwoTestMethods(secondTester), "secondTestMethod");
        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        verify(firstTester).onSetup();
        verify(firstTester).onTearDown();
        verify(firstTester).setTearDownOperation(DatabaseOperation.TRUNCATE_TABLE);
        verify(firstTester, never()).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(secondTester).onSetup();
        verify(secondTester).onTearDown();
        verify(secondTester).setTearDownOperation(DatabaseOperation.DELETE_ALL);
        verify(secondTester, never()).setTearDownOperation(DatabaseOperation.TRUNCATE_TABLE);
    }

    @Test
    void testAfterTestMethod_methodCompleted_leavesNoExecutorOnTestContext() throws Exception
    {
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");

        listener.beforeTestMethod(context);
        assertThat(context.hasAttribute(EXECUTOR_ATTRIBUTE))
                .as("The executor must be on the test context while the test method runs.")
                .isTrue();
        listener.afterTestMethod(context);

        assertThat(context.hasAttribute(EXECUTOR_ATTRIBUTE))
                .as("No executor may outlive afterTestMethod(), since the test context serves"
                        + " every later method of the class too.")
                .isFalse();
    }

    @Test
    void testAfterTestMethod_afterTestThrows_removesExecutorAttributeAnyway() throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        doThrow(new IllegalStateException("tear down failed")).when(executor).afterTest(false);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        assertThatThrownBy(() -> listener.afterTestMethod(context))
                .as("A failing after-test step must propagate.")
                .isInstanceOf(IllegalStateException.class).hasMessage("tear down failed");

        assertThat(context.hasAttribute(EXECUTOR_ATTRIBUTE))
                .as("The executor must be removed even when its after-test steps fail, so it"
                        + " cannot leak into the next method.")
                .isFalse();
    }

    @Test
    void testAfterTestMethod_onSetupFailedInBeforeTestMethod_stillRunsOnTearDown()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        doThrow(new IllegalStateException("set up failed")).when(tester).onSetup();
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        assertThatThrownBy(() -> listener.beforeTestMethod(context))
                .as("A failing setup must propagate.").isInstanceOf(IllegalStateException.class)
                .hasMessage("set up failed");
        listener.afterTestMethod(context);

        verify(tester).onTearDown();
    }

    @Test
    void testBeforeAndAfterTestMethod_testerSharedByTwoMethods_putsItsOwnListenerBackAfterEach()
            throws Exception
    {
        final IOperationListener testersOwn = mock(IOperationListener.class);
        final InMemoryTester tester = InMemoryTester.working();
        tester.setOperationListener(testersOwn);
        final FakeTestContext context = new FakeTestContext(HasTwoTestMethods.class);

        context.beginMethod(new HasTwoTestMethods(tester), "firstTestMethod");
        listener.beforeTestMethod(context);
        final IOperationListener whileFirstMethodRuns = tester.getOperationListener();
        listener.afterTestMethod(context);
        final IOperationListener afterFirstMethod = tester.getOperationListener();
        context.beginMethod(new HasTwoTestMethods(tester), "secondTestMethod");
        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        assertThat(whileFirstMethodRuns)
                .as("While a test method runs, the tester carries the executor's own listener.")
                .isNotSameAs(testersOwn);
        assertThat(afterFirstMethod)
                .as("A tester shared by several methods - or, as a bean, by several test classes -"
                        + " must be left as found after each, not carry the last method's"
                        + " listener to the next test or the next user of the tester.")
                .isSameAs(testersOwn);
        assertThat(tester.getOperationListener())
                .as("The listener must be put back after the second method too, not stacked on"
                        + " the first's.")
                .isSameAs(testersOwn);
    }

    @Test
    void testBeforeAndAfterTestMethod_testerHadItsDefaultListener_leavesItBack() throws Exception
    {
        final InMemoryTester tester = InMemoryTester.working();
        final IOperationListener testersDefault = tester.getOperationListener();
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        assertThat(tester.getOperationListener())
                .as("A tester that kept its default listener must not be left holding the"
                        + " executor's.")
                .isSameAs(testersDefault);
    }

    @Test
    void testAfterTestMethod_tearDownFails_stillPutsTestersOwnListenerBack() throws Exception
    {
        final IOperationListener testersOwn = mock(IOperationListener.class);
        final InMemoryTester tester = InMemoryTester.failingOnTearDown();
        tester.setOperationListener(testersOwn);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");
        listener.beforeTestMethod(context);

        assertThatThrownBy(() -> listener.afterTestMethod(context))
                .as("A failing tear down must propagate.")
                .isInstanceOf(IllegalStateException.class).hasMessage("tear down failed");

        assertThat(tester.getOperationListener())
                .as("A failing tear down must not leave the executor's listener on a tester that"
                        + " outlives the test.")
                .isSameAs(testersOwn);
    }

    @Test
    void testAfterTestMethod_onSetupFailedInBeforeTestMethod_stillPutsTestersOwnListenerBack()
            throws Exception
    {
        final IOperationListener testersOwn = mock(IOperationListener.class);
        final InMemoryTester tester = InMemoryTester.failingOnSetUp();
        tester.setOperationListener(testersOwn);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        final Throwable setUpFailure = catchThrowable(() -> listener.beforeTestMethod(context));
        final IOperationListener afterFailedSetUp = tester.getOperationListener();
        context.failWith(setUpFailure);
        listener.afterTestMethod(context);

        assertThat(setUpFailure).as("A failing setup must propagate.")
                .isInstanceOf(IllegalStateException.class).hasMessage("set up failed");
        assertThat(afterFailedSetUp)
                .as("The executor's own listener is on the tester when its setup fails, which"
                        + " makes the next assertion meaningful.")
                .isNotSameAs(testersOwn);
        assertThat(tester.getOperationListener())
                .as("A failing setup must not leave the executor's listener on a tester that"
                        + " outlives the test.")
                .isSameAs(testersOwn);
    }

    @Test
    void testBeforeTestExecution_expectedDeclaredAndTestTransactionRollsBack_warnsOnceNamingTheRemedy()
            throws Exception
    {
        final DbUnitTestExecutionListener rollingBack =
                new DbUnitTestExecutionListener(() -> true);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, mock(AnnotatedTestExecutor.class));

        final List<ILoggingEvent> warnings = warningsLoggedBy(() ->
        {
            rollingBack.beforeTestExecution(context);
        });

        assertThat(warnings)
                .as("A test verified after its own transaction rolled back must be warned about"
                        + " once, naming how to keep its changes.")
                .singleElement()
                .satisfies(warning -> assertThat(warning.getFormattedMessage())
                        .contains("HasExpectedDeclared.aTestMethod()")
                        .contains("@Commit")
                        .contains("@Rollback(false)"));
    }

    @Test
    void testBeforeTestExecution_testTransactionDoesNotRollBack_warnsNothing() throws Exception
    {
        final DbUnitTestExecutionListener committing =
                new DbUnitTestExecutionListener(() -> false);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, mock(AnnotatedTestExecutor.class));

        final List<ILoggingEvent> warnings = warningsLoggedBy(() ->
        {
            committing.beforeTestExecution(context);
        });

        assertThat(warnings)
                .as("A test that commits, or has no test transaction, has nothing to warn about.")
                .isEmpty();
    }

    @Test
    void testBeforeTestExecution_noExpectedDeclared_warnsNothing() throws Exception
    {
        final DbUnitTestExecutionListener rollingBack =
                new DbUnitTestExecutionListener(() -> true);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(mock(IDatabaseTester.class)), "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, mock(AnnotatedTestExecutor.class));

        final List<ILoggingEvent> warnings = warningsLoggedBy(() ->
        {
            rollingBack.beforeTestExecution(context);
        });

        assertThat(warnings)
                .as("Without @DbUnitExpected nothing is verified after the rollback, so a"
                        + " rolled-back transaction costs the test nothing.")
                .isEmpty();
    }

    @Test
    void testBeforeTestExecution_listenerNotActingOnTheTest_warnsNothing() throws Exception
    {
        final DbUnitTestExecutionListener rollingBack =
                new DbUnitTestExecutionListener(() -> true);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");

        final List<ILoggingEvent> warnings = warningsLoggedBy(() ->
        {
            rollingBack.beforeTestExecution(context);
        });

        assertThat(warnings)
                .as("A test this listener did not prepare, with no executor on its context,"
                        + " is none of its business.")
                .isEmpty();
    }

    @Test
    void testAfterTestMethod_verificationFailsAfterTheTransactionRolledBack_attachesTheHintAsSuppressed()
            throws Exception
    {
        final DbUnitTestExecutionListener rollingBack =
                new DbUnitTestExecutionListener(() -> true);
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final AssertionError verificationFailure = new AssertionError("tables differ");
        doThrow(verificationFailure).when(executor).afterTest(false);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        rollingBack.afterTestExecution(context);

        final Throwable thrown = catchThrowable(() -> rollingBack.afterTestMethod(context));

        assertThat(thrown)
                .as("The verification failure itself must be the one reported.")
                .isSameAs(verificationFailure);
        assertThat(thrown.getSuppressed())
                .as("The failure must explain that the test's transaction rolled back.")
                .singleElement()
                .satisfies(hint -> assertThat(hint.getMessage())
                        .contains("rolled back")
                        .contains("@Commit")
                        .contains("@Rollback(false)"));
    }

    @Test
    void testAfterTestMethod_verificationFailsAndTheTransactionDidNotRollBack_attachesNoHint()
            throws Exception
    {
        final DbUnitTestExecutionListener committing =
                new DbUnitTestExecutionListener(() -> false);
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final AssertionError verificationFailure = new AssertionError("tables differ");
        doThrow(verificationFailure).when(executor).afterTest(false);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        committing.afterTestExecution(context);

        final Throwable thrown = catchThrowable(() -> committing.afterTestMethod(context));

        assertThat(thrown.getSuppressed())
                .as("A verification failure not caused by a rollback needs no rollback hint.")
                .isEmpty();
    }

    @Test
    void testAfterTestMethod_theRollbackHintOfOneTest_doesNotReachTheNextTest() throws Exception
    {
        final DbUnitTestExecutionListener rollingBack =
                new DbUnitTestExecutionListener(() -> true);
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(HasExpectedDeclared.class)
                .beginMethod(new HasExpectedDeclared(mock(IDatabaseTester.class)),
                        "aTestMethod");
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);
        rollingBack.afterTestExecution(context);
        rollingBack.afterTestMethod(context);

        final AnnotatedTestExecutor nextExecutor = mock(AnnotatedTestExecutor.class);
        final AssertionError nextFailure = new AssertionError("tables differ");
        doThrow(nextFailure).when(nextExecutor).afterTest(false);
        context.setAttribute(EXECUTOR_ATTRIBUTE, nextExecutor);
        final Throwable thrown = catchThrowable(() -> rollingBack.afterTestMethod(context));

        assertThat(thrown.getSuppressed())
                .as("The test context serves every method of a class, so what one method"
                        + " learned about its transaction must not outlive it.")
                .isEmpty();
    }

    @Test
    void testNewTestTransactionRollbackCheck_springTxVisible_returnsTheCheckOfSpringsTestTransaction()
    {
        final BooleanSupplier check = DbUnitTestExecutionListener
                .newTestTransactionRollbackCheck(getClass().getClassLoader());

        assertThat(check)
                .as("With spring-tx present, the check must ask Spring's TestTransaction.")
                .isInstanceOf(DbUnitTestExecutionListener.TestTransactionRollbackCheck.class);
        assertThat(check.getAsBoolean())
                .as("Outside a test-managed transaction nothing rolls back.")
                .isFalse();
    }

    @Test
    void testNewTestTransactionRollbackCheck_springTxHidden_returnsACheckThatNeverAsksSpring()
    {
        final ClassLoader hidingSpringTx = new ClassLoader(getClass().getClassLoader())
        {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve)
                    throws ClassNotFoundException
            {
                if (name.startsWith("org.springframework.transaction."))
                {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        final BooleanSupplier check =
                DbUnitTestExecutionListener.newTestTransactionRollbackCheck(hidingSpringTx);

        assertThat(check)
                .as("Without spring-tx, which Spring's TestTransaction needs, the check must not"
                        + " touch it.")
                .isNotInstanceOf(DbUnitTestExecutionListener.TestTransactionRollbackCheck.class);
        assertThat(check.getAsBoolean()).isFalse();
    }

    /**
     * Returns the warnings the listener logs while {@code action} runs.
     */
    private static List<ILoggingEvent> warningsLoggedBy(final ThrowingAction action)
            throws Exception
    {
        final Logger listenerLogger =
                (Logger) LoggerFactory.getLogger(DbUnitTestExecutionListener.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        listenerLogger.addAppender(appender);
        try
        {
            action.run();
        } finally
        {
            listenerLogger.detachAppender(appender);
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .collect(java.util.stream.Collectors.toList());
    }

    @FunctionalInterface
    private interface ThrowingAction
    {
        void run() throws Exception;
    }

    /**
     * A real tester over a fresh in-memory database, so its own {@link IOperationListener}
     * behaves as it does for a user, unlike a mock's. It can be made to fail its set up or its
     * tear down.
     */
    private static class InMemoryTester extends AbstractDatabaseTester
    {
        private final boolean failOnSetUp;
        private final boolean failOnTearDown;

        static InMemoryTester working()
        {
            return new InMemoryTester(false, false);
        }

        static InMemoryTester failingOnSetUp()
        {
            return new InMemoryTester(true, false);
        }

        static InMemoryTester failingOnTearDown()
        {
            return new InMemoryTester(false, true);
        }

        private InMemoryTester(final boolean failOnSetUp, final boolean failOnTearDown)
        {
            this.failOnSetUp = failOnSetUp;
            this.failOnTearDown = failOnTearDown;
            setDataSet(new DefaultDataSet());
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception
        {
            return InMemoryDatabaseConnection.create();
        }

        @Override
        public void onSetup() throws Exception
        {
            if (failOnSetUp)
            {
                throw new IllegalStateException("set up failed");
            }
            super.onSetup();
        }

        @Override
        public void onTearDown() throws Exception
        {
            if (failOnTearDown)
            {
                throw new IllegalStateException("tear down failed");
            }
            super.onTearDown();
        }
    }

    static class HasTesterField
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasTesterField(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasExpectedDeclared
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasExpectedDeclared(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        @DbUnitExpected("expected.xml")
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class HasClassAndMethodLevelTearDown
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasClassAndMethodLevelTearDown(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    /**
     * Each inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class EnclosingWithTearDown
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        EnclosingWithTearDown(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithTearDown.this;
            }

            void aTestMethod()
            {
            }
        }

        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        class NestedWithTearDown
        {
            Object enclosing()
            {
                return EnclosingWithTearDown.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class HasUnmarkedTesterFieldOnly
    {
        IDatabaseTester databaseTester;

        HasUnmarkedTesterFieldOnly(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        void aTestMethod()
        {
        }
    }

    @DbUnitSpringTest
    static class DbUnitSpringTestOnly
    {
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class ClassLevelTearDownOnly
    {
        IDatabaseTester databaseTester;

        ClassLevelTearDownOnly(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        void aTestMethod()
        {
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    @interface DeletesAllRows
    {
    }

    @DeletesAllRows
    static class ComposedTearDownOnly
    {
        IDatabaseTester databaseTester;

        ComposedTearDownOnly(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        void aTestMethod()
        {
        }
    }

    /**
     * The inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    static class EnclosingWithMarkedTester
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        EnclosingWithMarkedTester(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        class Nested
        {
            Object enclosing()
            {
                return EnclosingWithMarkedTester.this;
            }

            void aTestMethod()
            {
            }
        }
    }

    static class HasTwoTestMethods
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasTwoTestMethods(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        @DbUnitTearDownOperation(DbUnitOperation.TRUNCATE_TABLE)
        void firstTestMethod()
        {
        }

        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void secondTestMethod()
        {
        }
    }
}
