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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Collections;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.TestInstances;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbUnitExtensionTest
{
    private final DbUnitExtension extension = new DbUnitExtension();

    @Mock
    ExtensionContext context;

    // ---- beforeTestExecution: tester resolution ----
    // The resolution rules are TestInstanceTesterResolverTest's, and what the JUnit binding adds
    // to them is TesterResolverTest's; these two only prove that the extension drives the tester
    // that was resolved and lets a resolution failure reach its caller.

    @Test
    void testBeforeTestExecution_dbUnitTesterMarkerField_usesMarkedField() throws Exception
    {
        final IDatabaseTester marked = mock(IDatabaseTester.class);
        final IDatabaseTester unmarked = mock(IDatabaseTester.class);
        final HasMarkedTesterAndPlainTester testInstance =
                new HasMarkedTesterAndPlainTester(marked, unmarked);
        givenTestInstance(testInstance, HasMarkedTesterAndPlainTester.class);

        extension.beforeTestExecution(context);

        verify(marked).onSetup();
        verify(unmarked, never()).onSetup();
    }

    @Test
    void testBeforeTestExecution_noTesterResolvable_throwsIllegalStateException()
    {
        givenTestInstance(new Object(), Object.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("No resolvable tester must be rejected with a clear message.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found");
    }

    // ---- afterTestExecution: exception propagation ----

    @Test
    void testAfterTestExecution_noStoredExecutor_doesNothing()
    {
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);

        assertThatCode(() -> extension.afterTestExecution(context))
                .as("No stored executor (e.g. beforeTestExecution never ran) must not throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testAfterTestExecution_afterTestThrowsAfterTestMethodAlreadyFailed_propagatesFailure()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(store.get(DbUnitExtension.EXECUTOR_KEY, AnnotatedTestExecutor.class))
                .thenReturn(executor);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getExecutionException())
                .thenReturn(Optional.of(new RuntimeException("original test failure")));
        final RuntimeException afterTestFailure = new RuntimeException("afterTest failure");
        doThrow(afterTestFailure).when(executor).afterTest(true);

        assertThatThrownBy(() -> extension.afterTestExecution(context))
                .as("A failure from afterTest() must propagate unconditionally, even after the"
                        + " test method itself already failed - reconciling it with the"
                        + " original failure (suppressing one or the other, promoting a real"
                        + " failure over a mere abort) is the JUnit Platform engine's own"
                        + " ThrowableCollector's job, not this extension's; see"
                        + " DbUnitExtensionLifecycleTest for that behavior proven end-to-end.")
                .isSameAs(afterTestFailure);
    }

    // ---- afterEach: release when afterTestExecution never ran ----

    @Test
    void testAfterEach_noStoredExecutor_doesNothing()
    {
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);

        assertThatCode(() -> extension.afterEach(context))
                .as("No stored executor must not throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testAfterEach_storedExecutor_releasesItsConnectionIfAfterTestDidNotRun()
            throws Exception
    {
        final AnnotatedTestExecutor executor = mock(AnnotatedTestExecutor.class);
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(store.get(DbUnitExtension.EXECUTOR_KEY, AnnotatedTestExecutor.class))
                .thenReturn(executor);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);

        extension.afterEach(context);

        verify(executor).releaseIfAfterTestDidNotRun();
    }

    // ---- ParameterResolver ----

    @Test
    void testSupportsParameter_unsupportedType_returnsFalse() throws Exception
    {
        final Method method =
                ParameterHost.class.getDeclaredMethod("withUnsupported", String.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("An unsupported parameter type must not be claimed.").isFalse();
    }

    @Test
    void testSupportsParameter_supportedTypes_returnsTrue() throws Exception
    {
        doReturn(AnnotationDrivenHost.class).when(context).getRequiredTestClass();
        when(context.getEnclosingTestClasses()).thenReturn(Collections.emptyList());
        for (final Class<?> methodParam : new Class<?>[] {IDatabaseTester.class,
                PrepAndExpectedTestCase.class, IDatabaseConnection.class, Connection.class})
        {
            final Method method = ParameterHost.class
                    .getDeclaredMethod("with" + methodParam.getSimpleName(), methodParam);
            final ParameterContext parameterContext = mock(ParameterContext.class);
            when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
            when(parameterContext.getDeclaringExecutable()).thenReturn(method);
            when(context.getTestMethod()).thenReturn(Optional.of(method));

            assertThat(extension.supportsParameter(parameterContext, context))
                    .as(methodParam.getName() + " must be a supported parameter type on the"
                            + " current test method of an annotation-driven test.")
                    .isTrue();
        }
    }

    @Test
    void testSupportsParameter_bareConnectionWithInjectConnectionParameterFalse_returnsFalse()
            throws Exception
    {
        // @DbUnitConfig(injectConnectionParameter = false) yields java.sql.Connection to a
        // co-registered resolver (Spring, Testcontainers, ...) so JUnit does not fail with
        // "discovered multiple competing ParameterResolvers".
        doReturn(NoConnectionInjectionHost.class).when(context).getRequiredTestClass();
        when(context.getEnclosingTestClasses()).thenReturn(Collections.emptyList());
        final Method method = ParameterHost.class
                .getDeclaredMethod("withConnection", Connection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
        when(context.getTestMethod()).thenReturn(Optional.of(method));

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("A bare java.sql.Connection parameter must not be claimed when"
                        + " @DbUnitConfig(injectConnectionParameter = false).")
                .isFalse();
    }

    @Test
    void testSupportsParameter_iDatabaseConnectionWithInjectConnectionParameterFalse_stillReturnsTrue()
            throws Exception
    {
        doReturn(NoConnectionInjectionHost.class).when(context).getRequiredTestClass();
        when(context.getEnclosingTestClasses()).thenReturn(Collections.emptyList());
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseConnection", IDatabaseConnection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
        when(parameterContext.getDeclaringExecutable()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(Optional.of(method));

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("injectConnectionParameter = false must drop only the bare Connection"
                        + " claim; the dbUnit-specific IDatabaseConnection is still resolved.")
                .isTrue();
    }

    @Test
    void testSupportsParameter_supportedTypeButNotAnnotationDriven_returnsFalse()
            throws Exception
    {
        // A bare @ExtendWith(DbUnitExtension.class) class with only a plain, unannotated
        // IDatabaseTester field - the 3.5.0 lifecycle-only style - must not have its parameters
        // claimed, so another extension resolving a Connection on the same test stays
        // unambiguous.
        doReturn(HasTwoUnmarkedTesterFields.class).when(context).getRequiredTestClass();
        when(context.getEnclosingTestClasses()).thenReturn(Collections.emptyList());
        final Method method = ParameterHost.class
                .getDeclaredMethod("withConnection", Connection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
        when(parameterContext.getDeclaringExecutable()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(Optional.of(method));

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("A supported type on the test method of a class that opted into neither a"
                        + " @DbUnit* annotation nor a @DbUnitTester/@DbUnitTestCase field must"
                        + " not be claimed.")
                .isFalse();
    }

    @Test
    void testSupportsParameter_markerFieldButNoAnnotation_returnsTrue() throws Exception
    {
        doReturn(HasTester.class).when(context).getRequiredTestClass();
        when(context.getEnclosingTestClasses()).thenReturn(Collections.emptyList());
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseTester", IDatabaseTester.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
        when(parameterContext.getDeclaringExecutable()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(Optional.of(method));

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("A @DbUnitTester field alone - no @DbUnit* class/method annotation - still"
                        + " opts the test into parameter injection.")
                .isTrue();
    }

    @Test
    void testSupportsParameter_supportedTypeOnUnrelatedMethod_returnsFalse() throws Exception
    {
        // Simulates a matching parameter type declared on a method that is neither the
        // current test method nor a @BeforeEach method - e.g. @AfterEach - which must not be
        // claimed: see the class Javadoc on supportsParameter().
        final Method method = ParameterHost.class.getDeclaredMethod("withIDatabaseTester",
                IDatabaseTester.class);
        final Method otherMethod = ParameterHost.class.getDeclaredMethod("withConnection",
                Connection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);
        when(parameterContext.getDeclaringExecutable()).thenReturn(method);
        when(context.getTestMethod()).thenReturn(Optional.of(otherMethod));

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("A matching parameter type declared on a method that is neither the"
                        + " current test method nor annotated @BeforeEach must not be claimed.")
                .isFalse();
    }

    @Test
    void testResolveParameter_iDatabaseTesterType_returnsResolvedTester() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        givenTestInstance(new HasTester(tester), HasTester.class);
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseTester", IDatabaseTester.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        final Object resolved = extension.resolveParameter(parameterContext, context);

        assertThat(resolved).as("The resolved tester must be injected.").isSameAs(tester);
    }

    @Test
    void testResolveParameter_connectionType_returnsUnderlyingJdbcConnection() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final IDatabaseConnection databaseConnection = mock(IDatabaseConnection.class);
        final Connection jdbcConnection = mock(Connection.class);
        when(tester.getConnection()).thenReturn(databaseConnection);
        when(databaseConnection.getConnection()).thenReturn(jdbcConnection);
        givenTestInstance(new HasTester(tester), HasTester.class);
        final Method method =
                ParameterHost.class.getDeclaredMethod("withConnection", Connection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        final Object resolved = extension.resolveParameter(parameterContext, context);

        assertThat(resolved)
                .as("A Connection parameter must resolve to the IDatabaseConnection's"
                        + " underlying JDBC Connection, not the IDatabaseConnection itself.")
                .isSameAs(jdbcConnection);
    }

    @Test
    void testResolveParameter_noTestInstanceYet_throwsClearParameterResolutionException()
            throws Exception
    {
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseTester", IDatabaseTester.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThatThrownBy(() -> extension.resolveParameter(parameterContext, context))
                .as("resolveParameter() called with no test instance present - unreachable"
                        + " through JUnit Jupiter's own engine, which only calls this after"
                        + " supportsParameter() already confirmed one exists, but exercised"
                        + " directly here as a unit test of this defensive check in isolation -"
                        + " must be rejected with a clear message instead of an opaque failure"
                        + " from deep inside resolve().")
                .isInstanceOf(ParameterResolutionException.class)
                .hasMessageContaining("@BeforeAll")
                .hasMessageContaining(IDatabaseTester.class.getName());
    }

    @Test
    void testResolveParameter_connectionTypeAndTesterConnectionIsNull_throwsClearParameterResolutionException()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        when(tester.getConnection()).thenReturn(null);
        givenTestInstance(new HasTester(tester), HasTester.class);
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseConnection", IDatabaseConnection.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThatThrownBy(() -> extension.resolveParameter(parameterContext, context))
                .as("IDatabaseTester#getConnection() returning null must be reported clearly"
                        + " when injecting an IDatabaseConnection/Connection parameter, not"
                        + " left as a bare NullPointerException from calling getConnection()"
                        + " on the result.")
                .isInstanceOf(ParameterResolutionException.class)
                .cause().isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("getConnection()").hasMessageContaining("null");
    }

    @Test
    void testResolveParameter_prepAndExpectedTestCaseTypeWithNoTestCaseField_throwsClearParameterResolutionException()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        givenTestInstance(new HasTester(tester), HasTester.class);
        final Method method = ParameterHost.class.getDeclaredMethod(
                "withPrepAndExpectedTestCase", PrepAndExpectedTestCase.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThatThrownBy(() -> extension.resolveParameter(parameterContext, context))
                .as("A PrepAndExpectedTestCase parameter with no @DbUnitTestCase field to"
                        + " supply a single instance must be rejected with a clear message"
                        + " instead of an opaque failure.")
                .isInstanceOf(ParameterResolutionException.class)
                .cause().isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DbUnitTestCase");
    }

    @Test
    void testResolveParameter_resolutionFails_wrapsInParameterResolutionException()
            throws Exception
    {
        givenTestInstance(new Object(), Object.class);
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseTester", IDatabaseTester.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThatThrownBy(() -> extension.resolveParameter(parameterContext, context))
                .as("A resolution failure must be wrapped, not propagated raw.")
                .isInstanceOf(ParameterResolutionException.class);
    }

    // ---- helpers ----

    private void givenTestInstance(final Object testInstance, final Class<?> testClass)
    {
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        // lenient: only beforeTestExecution() reaches the store put(), and only once tester
        // resolution has already succeeded - several tests here exercise resolution failing
        // first, where this stub is never consulted.
        lenient().when(context.getStore(any(ExtensionContext.Namespace.class)))
                .thenReturn(store);
        doReturn(testClass).when(context).getRequiredTestClass();
        final TestInstances instances = mock(TestInstances.class);
        when(instances.getAllInstances()).thenReturn(Collections.singletonList(testInstance));
        when(context.getRequiredTestInstances()).thenReturn(instances);
        // lenient: only resolveParameter() consults this; beforeTestExecution()-based tests
        // never reach it.
        lenient().when(context.getTestInstances()).thenReturn(Optional.of(instances));
    }

    private ThrowingCallable callBeforeTestExecution()
    {
        return () -> extension.beforeTestExecution(context);
    }

    // ---- fixtures ----

    static class HasTester
    {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasTester(final IDatabaseTester databaseTester)
        {
            this.databaseTester = databaseTester;
        }
    }

    static class HasMarkedTesterAndPlainTester
    {
        @DbUnitTester
        IDatabaseTester marked;
        IDatabaseTester alsoPresentButUnmarkedIsIgnoredBecauseMarkedWins;

        HasMarkedTesterAndPlainTester(final IDatabaseTester marked,
                final IDatabaseTester unmarked)
        {
            this.marked = marked;
            this.alsoPresentButUnmarkedIsIgnoredBecauseMarkedWins = unmarked;
        }
    }

    static class HasTwoUnmarkedTesterFields
    {
        IDatabaseTester first;
        IDatabaseTester second;

        HasTwoUnmarkedTesterFields(final IDatabaseTester first, final IDatabaseTester second)
        {
            this.first = first;
            this.second = second;
        }
    }

    interface ParameterHost
    {
        void withUnsupported(String value);

        void withIDatabaseTester(IDatabaseTester tester);

        void withPrepAndExpectedTestCase(PrepAndExpectedTestCase testCase);

        void withIDatabaseConnection(IDatabaseConnection connection);

        void withConnection(Connection connection);
    }

    /** A test class carrying a @DbUnit* annotation, so its parameters are injectable. */
    @DbUnitConfig
    static class AnnotationDrivenHost
    {
    }

    /** Annotation-driven, but opts its bare java.sql.Connection parameter back out. */
    @DbUnitConfig(injectConnectionParameter = false)
    static class NoConnectionInjectionHost
    {
    }
}
