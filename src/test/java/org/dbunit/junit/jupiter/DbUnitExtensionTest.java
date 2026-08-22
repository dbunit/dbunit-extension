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
import org.dbunit.DatabaseTesterFactory;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestExecutor;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.TestInstances;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbUnitExtensionTest {
    private final DbUnitExtension extension = new DbUnitExtension();

    @Mock
    ExtensionContext context;

    @AfterEach
    void resetStaticFixtureState() {
        HasStaticMarkedTester.tester = null;
        RecordingFactory.next = null;
    }

    // ---- field resolution: markers and ambiguity ----

    @Test
    void testBeforeTestExecution_dbUnitTesterMarkerField_usesMarkedField() throws Exception {
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
    void testBeforeTestExecution_dbUnitTesterMarkerFieldOnSuperclassOnly_usesInheritedField()
            throws Exception {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final SubclassOfMarkedTesterSuperclass testInstance =
                new SubclassOfMarkedTesterSuperclass(tester);
        givenTestInstance(testInstance, SubclassOfMarkedTesterSuperclass.class);

        extension.beforeTestExecution(context);

        // AnnotationSupport.findAnnotatedFields() traverses superclasses, so a @DbUnitTester
        // field declared only on a superclass must still be found through a subclass instance
        // - the same inheritance findTesterField()'s own unmarked-field auto-scan already
        // provides, but exercised through the marked-field path instead.
        verify(tester).onSetup();
    }

    @Test
    void testBeforeTestExecution_bothMarkersPresent_throwsIllegalStateException() {
        final HasBothMarkers testInstance = new HasBothMarkers(mock(IDatabaseTester.class));
        givenTestInstance(testInstance, HasBothMarkers.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("Declaring both @DbUnitTestCase and @DbUnitTester must be rejected.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DbUnitTestCase")
                .hasMessageContaining("DbUnitTester");
    }

    @Test
    void testBeforeTestExecution_twoDbUnitTesterFieldsInSameClass_throwsIllegalStateException() {
        final HasTwoMarkedTesterFields testInstance = new HasTwoMarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));
        givenTestInstance(testInstance, HasTwoMarkedTesterFields.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("Two @DbUnitTester fields within the same class's own hierarchy are"
                        + " ambiguous and must be rejected - unlike two fields at different"
                        + " nesting levels, where the innermost shadows the outer one.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple @DbUnitTester fields found in")
                .hasMessageContaining(HasTwoMarkedTesterFields.class.getName());
    }

    @Test
    void testBeforeTestExecution_dbUnitTesterMarkerFieldsInSuperclassAndSubclass_throwsIllegalStateException() {
        final HasMarkedTesterFieldInheritingAnother testInstance =
                new HasMarkedTesterFieldInheritingAnother(mock(IDatabaseTester.class),
                        mock(IDatabaseTester.class));
        givenTestInstance(testInstance, HasMarkedTesterFieldInheritingAnother.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A @DbUnitTester field on a superclass and another declared directly on the"
                        + " subclass are ambiguous within the same instance's class hierarchy -"
                        + " AnnotationSupport.findAnnotatedFields() finds both - and must be"
                        + " rejected the same as two fields declared directly on one class, not"
                        + " silently resolved to whichever one reflection happens to return"
                        + " first.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple @DbUnitTester fields found in")
                .hasMessageContaining(HasMarkedTesterFieldInheritingAnother.class.getName());
    }

    @Test
    void testBeforeTestExecution_multipleUnmarkedTesterFields_throwsIllegalStateException() {
        final HasTwoUnmarkedTesterFields testInstance = new HasTwoUnmarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));
        givenTestInstance(testInstance, HasTwoUnmarkedTesterFields.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("Two unmarked IDatabaseTester fields in the same class are ambiguous and"
                        + " must be rejected, not silently resolved to whichever reflection"
                        + " happens to return first.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple IDatabaseTester fields")
                .hasMessageContaining(HasTwoUnmarkedTesterFields.class.getName());
    }

    @Test
    void testBeforeTestExecution_dbUnitTestCaseFieldIsNull_throwsIllegalStateException() {
        givenTestInstance(new HasNullTestCaseField(), HasNullTestCaseField.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A null @DbUnitTestCase field must be rejected, not silently substituted"
                        + " with a newly constructed PrepAndExpectedTestCase.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PrepAndExpectedTestCase")
                .hasMessageContaining("testCase")
                .hasMessageContaining(HasNullTestCaseField.class.getName());
    }

    @Test
    void testBeforeTestExecution_dbUnitTesterFieldIsNull_throwsIllegalStateException() {
        givenTestInstance(new HasNullTesterField(), HasNullTesterField.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A null @DbUnitTester field must be rejected with a clear message, not a"
                        + " bare NullPointerException deep inside AnnotatedTestExecutor.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDatabaseTester")
                .hasMessageContaining("tester")
                .hasMessageContaining(HasNullTesterField.class.getName());
    }

    @Test
    void testBeforeTestExecution_unmarkedTesterFieldIsNull_throwsIllegalStateException() {
        givenTestInstance(new HasNullUnmarkedTesterField(), HasNullUnmarkedTesterField.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A null, unmarked IDatabaseTester field found by the plain field auto-scan"
                        + " must be rejected with a clear message, not a bare"
                        + " NullPointerException - the same contract the marked-field case"
                        + " already has, but exercised through a different code path"
                        + " (autoScanTesterField(), not resolve()'s own null check).")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDatabaseTester")
                .hasMessageContaining("databaseTester")
                .hasMessageContaining(HasNullUnmarkedTesterField.class.getName());
    }

    @Test
    void testBeforeTestExecution_testCaseFieldTesterIsNull_wiresFallbackTesterOntoTestCase()
            throws Exception {
        final IDatabaseTester autoScanned = mock(IDatabaseTester.class);
        final DefaultPrepAndExpectedTestCase testCase = mock(DefaultPrepAndExpectedTestCase.class);
        final HasTesterlessTestCaseFieldAndPlainTester testInstance =
                new HasTesterlessTestCaseFieldAndPlainTester(testCase, autoScanned);
        givenTestInstance(testInstance, HasTesterlessTestCaseFieldAndPlainTester.class);

        extension.beforeTestExecution(context);

        // A @DbUnitTestCase field built without a tester (e.g. the no-arg constructor) must
        // have the fallback-resolved tester wired onto the actual instance the executor
        // drives, not only recorded in a throwaway Resolution.
        verify(testCase).setDatabaseTester(autoScanned);
    }

    @Test
    void testBeforeTestExecution_testerFactoryReturnsNull_throwsIllegalStateException() {
        givenTestInstance(new WithFactory(), WithFactory.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A DatabaseTesterFactory returning null must be rejected with a clear"
                        + " message, not a raw NullPointerException deep inside"
                        + " AnnotatedTestExecutor.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DatabaseTesterFactory")
                .hasMessageContaining(RecordingFactory.class.getName())
                .hasMessageContaining("createDatabaseTester");
    }

    @Test
    void testBeforeTestExecution_testerFactoryConfigured_usesFactoryProducedTester()
            throws Exception {
        final IDatabaseTester fromFactory = mock(IDatabaseTester.class);
        RecordingFactory.next = fromFactory;
        givenTestInstance(new WithFactory(), WithFactory.class);

        extension.beforeTestExecution(context);

        verify(fromFactory).onSetup();
    }

    @Test
    void testBeforeTestExecution_staticMarkerField_usesField() throws Exception {
        HasStaticMarkedTester.tester = mock(IDatabaseTester.class);
        givenTestInstance(new HasStaticMarkedTester(), HasStaticMarkedTester.class);

        extension.beforeTestExecution(context);

        verify(HasStaticMarkedTester.tester).onSetup();
    }

    @Test
    void testBeforeTestExecution_noTesterResolvable_throwsIllegalStateException() {
        givenTestInstance(new Object(), Object.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("No resolvable tester must be rejected with a clear message.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found");
    }

    // ---- field resolution: auto-scan (unmarked field, unchanged 3.5.0 behavior) ----

    @Test
    void testBeforeTestExecution_unmarkedTesterInSuperclass_usesIt() throws Exception {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final SubclassWithNoOwnTester testInstance = new SubclassWithNoOwnTester(tester);
        givenTestInstance(testInstance, SubclassWithNoOwnTester.class);

        extension.beforeTestExecution(context);

        verify(tester).onSetup();
    }

    @Test
    void testBeforeTestExecution_unmarkedTesterInGrandparentClass_usesIt() throws Exception {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final GrandchildWithNoOwnTester testInstance = new GrandchildWithNoOwnTester(tester);
        givenTestInstance(testInstance, GrandchildWithNoOwnTester.class);

        extension.beforeTestExecution(context);

        verify(tester).onSetup();
    }

    @Test
    void testBeforeTestExecution_unmarkedTesterInBothSubclassAndSuperclass_subclassFieldWins()
            throws Exception {
        final IDatabaseTester subclassTester = mock(IDatabaseTester.class);
        final IDatabaseTester superclassTester = mock(IDatabaseTester.class);
        final SubclassWithOwnUnmarkedTester testInstance =
                new SubclassWithOwnUnmarkedTester(subclassTester, superclassTester);
        givenTestInstance(testInstance, SubclassWithOwnUnmarkedTester.class);

        extension.beforeTestExecution(context);

        verify(subclassTester).onSetup();
        verify(superclassTester, never()).onSetup();
    }

    @Test
    void testBeforeTestExecution_onlyStaticUnmarkedTesterField_throwsIllegalStateException() {
        givenTestInstance(new HasStaticUnmarkedTesterOnly(), HasStaticUnmarkedTesterOnly.class);

        assertThatThrownBy(callBeforeTestExecution())
                .as("A static IDatabaseTester field must not satisfy the plain auto-scan - only"
                        + " @DbUnitTester/@DbUnitTestCase fields may be static.")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No IDatabaseTester field found");
    }

    // ---- afterTestExecution: exception propagation ----

    @Test
    void testAfterTestExecution_noStoredExecutor_doesNothing() {
        final ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);

        assertThatCode(() -> extension.afterTestExecution(context))
                .as("No stored executor (e.g. beforeTestExecution never ran) must not throw.")
                .doesNotThrowAnyException();
    }

    @Test
    void testAfterTestExecution_afterTestThrowsAfterTestMethodAlreadyFailed_propagatesFailure()
            throws Exception {
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

    // ---- ParameterResolver ----

    @Test
    void testSupportsParameter_unsupportedType_returnsFalse() throws Exception {
        final Method method =
                ParameterHost.class.getDeclaredMethod("withUnsupported", String.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThat(extension.supportsParameter(parameterContext, context))
                .as("An unsupported parameter type must not be claimed.").isFalse();
    }

    @Test
    void testSupportsParameter_supportedTypes_returnsTrue() throws Exception {
        for (final Class<?> methodParam : new Class<?>[] {IDatabaseTester.class,
                PrepAndExpectedTestCase.class, IDatabaseConnection.class, Connection.class}) {
            final Method method = ParameterHost.class
                    .getDeclaredMethod("with" + methodParam.getSimpleName(), methodParam);
            final ParameterContext parameterContext = mock(ParameterContext.class);
            when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

            assertThat(extension.supportsParameter(parameterContext, context))
                    .as(methodParam.getName() + " must be a supported parameter type.")
                    .isTrue();
        }
    }

    @Test
    void testResolveParameter_iDatabaseTesterType_returnsResolvedTester() throws Exception {
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
    void testResolveParameter_connectionType_returnsUnderlyingJdbcConnection() throws Exception {
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
            throws Exception {
        final Method method = ParameterHost.class
                .getDeclaredMethod("withIDatabaseTester", IDatabaseTester.class);
        final ParameterContext parameterContext = mock(ParameterContext.class);
        when(parameterContext.getParameter()).thenReturn(method.getParameters()[0]);

        assertThatThrownBy(() -> extension.resolveParameter(parameterContext, context))
                .as("Injecting a dbUnit parameter into a static-scope method (e.g."
                        + " @BeforeAll), where no test instance exists yet, must be rejected"
                        + " with a clear message instead of an opaque failure from deep"
                        + " inside resolve().")
                .isInstanceOf(ParameterResolutionException.class)
                .hasMessageContaining("@BeforeAll")
                .hasMessageContaining(IDatabaseTester.class.getName());
    }

    @Test
    void testResolveParameter_connectionTypeAndTesterConnectionIsNull_throwsClearParameterResolutionException()
            throws Exception {
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
            throws Exception {
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
            throws Exception {
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

    private void givenTestInstance(final Object testInstance, final Class<?> testClass) {
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

    private ThrowingCallable callBeforeTestExecution() {
        return () -> extension.beforeTestExecution(context);
    }

    // ---- fixtures ----

    static class HasTester {
        @DbUnitTester
        IDatabaseTester databaseTester;

        HasTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class HasMarkedTesterAndPlainTester {
        @DbUnitTester
        IDatabaseTester marked;
        IDatabaseTester alsoPresentButUnmarkedIsIgnoredBecauseMarkedWins;

        HasMarkedTesterAndPlainTester(final IDatabaseTester marked,
                final IDatabaseTester unmarked) {
            this.marked = marked;
            this.alsoPresentButUnmarkedIsIgnoredBecauseMarkedWins = unmarked;
        }
    }

    static class HasTwoUnmarkedTesterFields {
        IDatabaseTester first;
        IDatabaseTester second;

        HasTwoUnmarkedTesterFields(final IDatabaseTester first, final IDatabaseTester second) {
            this.first = first;
            this.second = second;
        }
    }

    static class HasTwoMarkedTesterFields {
        @DbUnitTester
        IDatabaseTester first;
        @DbUnitTester
        IDatabaseTester second;

        HasTwoMarkedTesterFields(final IDatabaseTester first, final IDatabaseTester second) {
            this.first = first;
            this.second = second;
        }
    }

    static class MarkedTesterSuperclass {
        @DbUnitTester
        IDatabaseTester databaseTester;

        MarkedTesterSuperclass(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class SubclassOfMarkedTesterSuperclass extends MarkedTesterSuperclass {
        SubclassOfMarkedTesterSuperclass(final IDatabaseTester databaseTester) {
            super(databaseTester);
        }
    }

    static class HasMarkedTesterFieldInheritingAnother extends MarkedTesterSuperclass {
        @DbUnitTester
        IDatabaseTester subclassField;

        HasMarkedTesterFieldInheritingAnother(final IDatabaseTester superclassField,
                final IDatabaseTester subclassField) {
            super(superclassField);
            this.subclassField = subclassField;
        }
    }

    static class SuperclassWithUnmarkedTester {
        IDatabaseTester databaseTester;

        SuperclassWithUnmarkedTester(final IDatabaseTester databaseTester) {
            this.databaseTester = databaseTester;
        }
    }

    static class SubclassWithNoOwnTester extends SuperclassWithUnmarkedTester {
        SubclassWithNoOwnTester(final IDatabaseTester databaseTester) {
            super(databaseTester);
        }
    }

    static class GrandchildWithNoOwnTester extends SubclassWithNoOwnTester {
        GrandchildWithNoOwnTester(final IDatabaseTester databaseTester) {
            super(databaseTester);
        }
    }

    static class SubclassWithOwnUnmarkedTester extends SuperclassWithUnmarkedTester {
        IDatabaseTester databaseTester;

        SubclassWithOwnUnmarkedTester(final IDatabaseTester subclassTester,
                final IDatabaseTester superclassTester) {
            super(superclassTester);
            this.databaseTester = subclassTester;
        }
    }

    static class HasStaticUnmarkedTesterOnly {
        static IDatabaseTester databaseTester;
    }

    static class HasBothMarkers {
        @DbUnitTester
        IDatabaseTester tester;
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase = mock(PrepAndExpectedTestCase.class);

        HasBothMarkers(final IDatabaseTester tester) {
            this.tester = tester;
        }
    }

    static class HasNullTestCaseField {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
    }

    static class HasNullTesterField {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class HasNullUnmarkedTesterField {
        IDatabaseTester databaseTester;
    }

    static class HasTesterlessTestCaseFieldAndPlainTester {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
        IDatabaseTester databaseTester;

        HasTesterlessTestCaseFieldAndPlainTester(final DefaultPrepAndExpectedTestCase testCase,
                final IDatabaseTester databaseTester) {
            this.testCase = testCase;
            this.databaseTester = databaseTester;
        }
    }

    static class HasStaticMarkedTester {
        @DbUnitTester
        static IDatabaseTester tester;
    }

    @DbUnitConfig(databaseTesterFactory = RecordingFactory.class)
    static class WithFactory {
    }

    static class RecordingFactory implements DatabaseTesterFactory {
        static IDatabaseTester next;

        @Override
        public IDatabaseTester createDatabaseTester() {
            return next;
        }
    }

    interface ParameterHost {
        void withUnsupported(String value);

        void withIDatabaseTester(IDatabaseTester tester);

        void withPrepAndExpectedTestCase(PrepAndExpectedTestCase testCase);

        void withIDatabaseConnection(IDatabaseConnection connection);

        void withConnection(Connection connection);
    }
}
