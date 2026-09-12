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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

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
    void testBeforeTestMethod_dbUnitTesterField_runsOnSetup() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasTesterField.class)
                .beginMethod(new HasTesterField(tester), "aTestMethod");

        listener.beforeTestMethod(context);

        verify(tester).onSetup();
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
    void testAfterTestMethod_exceptionDeclaredAsExpectedByTestFramework_passesTestFailedFalseToExecutor()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(ExpectsAnException.class)
                .beginMethod(new ExpectsAnException(), "aTestMethod")
                .failWith(new IOException("the expected failure"));
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        expectingExceptionListener().afterTestMethod(context);

        verify(executor).afterTest(false);
    }

    @Test
    void testAfterTestMethod_exceptionOfAnotherTypeThanDeclared_passesTestFailedTrueToExecutor()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final FakeTestContext context = new FakeTestContext(ExpectsAnException.class)
                .beginMethod(new ExpectsAnException(), "aTestMethod")
                .failWith(new IllegalStateException("not the expected failure"));
        context.setAttribute(EXECUTOR_ATTRIBUTE, executor);

        expectingExceptionListener().afterTestMethod(context);

        verify(executor).afterTest(true);
    }

    private static DbUnitTestExecutionListener expectingExceptionListener()
    {
        return new DbUnitTestExecutionListener(DeclaredExpectedExceptionsTest.standIn());
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
    void testAfterTestMethod_priorMethodCompletedButThisMethodNeverStarted_tearsNothingDown()
            throws Exception
    {
        final IDatabaseTester firstTester = mock(IDatabaseTester.class);
        final IDatabaseTester secondTester = mock(IDatabaseTester.class);
        final FakeTestContext context = new FakeTestContext(HasTwoTestMethods.class);
        context.beginMethod(new HasTwoTestMethods(firstTester), "firstTestMethod");
        listener.beforeTestMethod(context);
        listener.afterTestMethod(context);

        context.beginMethod(new HasTwoTestMethods(secondTester), "secondTestMethod");
        listener.afterTestMethod(context);

        verify(firstTester, times(1)).onTearDown();
        verify(secondTester, never()).onTearDown();
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

    static class ExpectsAnException
    {
        @DbUnitTester
        IDatabaseTester databaseTester = mock(IDatabaseTester.class);

        @DeclaredExpectedExceptionsTest.ExpectsExceptions(expectedExceptions = IOException.class)
        void aTestMethod()
        {
        }
    }

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class HasClassAndMethodLevelTearDown
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasClassAndMethodLevelTearDown(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }

        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        void aTestMethod()
        {
        }
    }

    /**
     * Each inner class uses its enclosing instance, so {@code javac} keeps the reference to it
     * whatever the compiler target.
     */
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
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

        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
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

    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
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
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
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

        @DbUnitTearDown(operation = DbUnitOperation.TRUNCATE_TABLE)
        void firstTestMethod()
        {
        }

        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void secondTestMethod()
        {
        }
    }
}
