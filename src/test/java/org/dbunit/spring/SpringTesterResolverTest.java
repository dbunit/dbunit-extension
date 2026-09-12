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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.dbunit.DatabaseTesterFactory;
import org.dbunit.IDatabaseTester;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.runtime.AnnotatedTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;

class SpringTesterResolverTest
{
    private final SpringTesterResolver resolver = new SpringTesterResolver();
    private final AnnotatedTestConfiguration noFactoryConfiguration = AnnotatedTestConfiguration
            .from(SpringTesterResolverTest.class, null, null, null, null, null, null);

    @AfterEach
    void resetStaticFixtureState()
    {
        RecordingFactory.next = null;
    }

    @Test
    void testResolve_dbUnitTestCaseField_usesInjectedTestCase() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final PrepAndExpectedTestCase testCase = mock(PrepAndExpectedTestCase.class);
        when(testCase.getDatabaseTester()).thenReturn(tester);
        final HasTestCaseField testInstance = new HasTestCaseField(testCase);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration);

        assertThat(resolution.tester).as("The test case's own tester must be used.")
                .isSameAs(tester);
        assertThat(resolution.testCase).as("The injected test case must be returned.")
                .isSameAs(testCase);
    }

    @Test
    void testResolve_dbUnitTesterField_usesMarkedField() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final HasTesterField testInstance = new HasTesterField(tester);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration);

        assertThat(resolution.tester).as("The marked field's tester must be used.")
                .isSameAs(tester);
        assertThat(resolution.testCase).as("No test case was injected.").isNull();
    }

    @Test
    void testResolve_bothMarkersPresent_throwsIllegalStateException() throws Exception
    {
        final HasBothMarkers testInstance = new HasBothMarkers(mock(PrepAndExpectedTestCase.class),
                mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("Declaring both @DbUnitTestCase and @DbUnitTester must be rejected.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("DbUnitTestCase")
                        .hasMessageContaining("DbUnitTester");
    }

    @Test
    void testResolve_databaseTesterFactoryConfigured_usesFactory() throws Exception
    {
        final NoFieldsTestInstance testInstance = new NoFieldsTestInstance();
        final DbUnitConfig config = WithFactory.class.getAnnotation(DbUnitConfig.class);
        final AnnotatedTestConfiguration configuration = AnnotatedTestConfiguration
                .from(SpringTesterResolverTest.class, config, null, null, null, null, null);
        RecordingFactory.next = mock(IDatabaseTester.class);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), configuration);

        assertThat(resolution.tester).as("The factory-created tester must be used.")
                .isSameAs(RecordingFactory.next);
    }

    @Test
    void testResolve_applicationContextHasUniqueBean_usesBean() throws Exception
    {
        final NoFieldsTestInstance testInstance = new NoFieldsTestInstance();
        final IDatabaseTester beanTester = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(testInstance);
        context.setApplicationContext(applicationContextWithTester(beanTester));

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.tester)
                .as("The sole IDatabaseTester bean in the ApplicationContext must be used when"
                        + " no field or factory resolves one.")
                .isSameAs(beanTester);
    }

    @Test
    void testResolve_applicationContextHasNoUniqueBean_fallsThroughToAutoScannedField()
            throws Exception
    {
        final IDatabaseTester fieldTester = mock(IDatabaseTester.class);
        final HasUnmarkedTesterField testInstance = new HasUnmarkedTesterField(fieldTester);
        final FakeTestContext context = fakeTestContext(testInstance);
        context.setApplicationContext(applicationContextWithTester(null));

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(context, noFactoryConfiguration);

        assertThat(resolution.tester)
                .as("Zero or multiple ApplicationContext bean matches must fall through to the"
                        + " plain field auto-scan, not fail.")
                .isSameAs(fieldTester);
    }

    @Test
    void testResolve_noApplicationContext_autoScansUnmarkedField() throws Exception
    {
        final IDatabaseTester fieldTester = mock(IDatabaseTester.class);
        final HasUnmarkedTesterField testInstance = new HasUnmarkedTesterField(fieldTester);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration);

        assertThat(resolution.tester).isSameAs(fieldTester);
    }

    @Test
    void testResolve_markedFieldOnSuperclassOnly_usesInheritedField() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final SubclassOfMarkedTesterSuperclass testInstance =
                new SubclassOfMarkedTesterSuperclass(tester);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration);

        assertThat(resolution.tester).isSameAs(tester);
    }

    @Test
    void testResolve_noFieldsNoFactoryNoBean_throwsIllegalStateException() throws Exception
    {
        final NoFieldsTestInstance testInstance = new NoFieldsTestInstance();

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("No IDatabaseTester field or bean found");
    }

    @Test
    void testResolve_dbUnitTesterFieldIsNull_throwsIllegalStateException() throws Exception
    {
        final HasNullTesterField testInstance = new HasNullTesterField();

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("A null @DbUnitTester field must be rejected with a clear message,"
                                + " not a bare NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("IDatabaseTester")
                        .hasMessageContaining("tester")
                        .hasMessageContaining(HasNullTesterField.class.getName());
    }

    @Test
    void testResolve_dbUnitTesterFieldWrongType_throwsIllegalStateException() throws Exception
    {
        final HasWrongTypeTesterField testInstance = new HasWrongTypeTesterField("not a tester");

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("A @DbUnitTester field whose value does not implement"
                                + " IDatabaseTester must be rejected with a clear message, not a"
                                + " raw ClassCastException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("IDatabaseTester")
                        .hasMessageContaining("tester")
                        .hasMessageContaining(HasWrongTypeTesterField.class.getName())
                        .hasMessageContaining(String.class.getName());
    }

    @Test
    void testResolve_dbUnitTestCaseFieldIsNull_throwsIllegalStateException() throws Exception
    {
        final HasNullTestCaseField testInstance = new HasNullTestCaseField();

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("A null @DbUnitTestCase field must be rejected with a clear message,"
                                + " not a bare NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PrepAndExpectedTestCase")
                        .hasMessageContaining("testCase")
                        .hasMessageContaining(HasNullTestCaseField.class.getName());
    }

    @Test
    void testResolve_dbUnitTestCaseFieldWrongType_throwsIllegalStateException() throws Exception
    {
        final HasWrongTypeTestCaseField testInstance =
                new HasWrongTypeTestCaseField("not a test case");

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("A @DbUnitTestCase field whose value does not implement"
                                + " PrepAndExpectedTestCase must be rejected with a clear message,"
                                + " not a raw ClassCastException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("PrepAndExpectedTestCase")
                        .hasMessageContaining("testCase")
                        .hasMessageContaining(HasWrongTypeTestCaseField.class.getName())
                        .hasMessageContaining(String.class.getName());
    }

    @Test
    void testResolve_twoDbUnitTesterFieldsSameClass_throwsIllegalStateException() throws Exception
    {
        final HasTwoMarkedTesterFields testInstance = new HasTwoMarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("Two @DbUnitTester fields declared directly on the same class are"
                                + " ambiguous and must be rejected, not silently resolved to"
                                + " whichever reflection happens to return first.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Multiple @DbUnitTester fields found in")
                        .hasMessageContaining(HasTwoMarkedTesterFields.class.getName());
    }

    @Test
    void testResolve_markedFieldOnBothSuperclassAndSubclass_subclassFieldShadowsSuperclass()
            throws Exception
    {
        final IDatabaseTester superclassTester = mock(IDatabaseTester.class);
        final IDatabaseTester subclassTester = mock(IDatabaseTester.class);
        final SubclassWithOwnMarkedTesterField testInstance =
                new SubclassWithOwnMarkedTesterField(superclassTester, subclassTester);

        final SpringTesterResolver.Resolution resolution =
                resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration);

        assertThat(resolution.tester)
                .as("findMarkedField() walks most-derived class first and returns as soon as a"
                        + " declaring class has exactly one marked field, so a subclass's own"
                        + " @DbUnitTester field shadows one declared on its superclass rather"
                        + " than the two being treated as ambiguous across the hierarchy.")
                .isSameAs(subclassTester);
    }

    @Test
    void testResolve_multipleUnmarkedTesterFields_throwsIllegalStateException() throws Exception
    {
        final HasTwoUnmarkedTesterFields testInstance = new HasTwoUnmarkedTesterFields(
                mock(IDatabaseTester.class), mock(IDatabaseTester.class));

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("Two unmarked IDatabaseTester fields declared directly on the same"
                                + " class are ambiguous and must be rejected, not silently"
                                + " resolved to whichever reflection happens to return first.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Multiple IDatabaseTester fields")
                        .hasMessageContaining(HasTwoUnmarkedTesterFields.class.getName());
    }

    @Test
    void testResolve_onlyStaticUnmarkedTesterField_throwsIllegalStateException() throws Exception
    {
        HasStaticUnmarkedTesterField.tester = mock(IDatabaseTester.class);
        final HasStaticUnmarkedTesterField testInstance = new HasStaticUnmarkedTesterField();

        assertThatThrownBy(
                () -> resolver.resolve(fakeTestContext(testInstance), noFactoryConfiguration))
                        .as("The plain field auto-scan (unlike the @DbUnitTester-marked field"
                                + " lookup) must skip static fields, the same as"
                                + " org.dbunit.junit.jupiter.TesterResolver's auto-scan, so a"
                                + " static-only IDatabaseTester field is not found at all.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("No IDatabaseTester field or bean found");
    }

    private static ApplicationContext applicationContextWithTester(final IDatabaseTester tester)
    {
        final ApplicationContext applicationContext = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        final ObjectProvider<IDatabaseTester> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(tester);
        when(applicationContext.getBeanProvider(IDatabaseTester.class)).thenReturn(provider);
        return applicationContext;
    }

    private static FakeTestContext fakeTestContext(final Object testInstance) throws Exception
    {
        final Method testMethod = testInstance.getClass().getDeclaredMethod("aTestMethod");
        return new FakeTestContext(testInstance, testMethod);
    }

    static class HasTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;

        HasTestCaseField(final PrepAndExpectedTestCase testCase)
        {
            this.testCase = testCase;
        }

        void aTestMethod()
        {
        }
    }

    static class HasTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;

        HasTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasBothMarkers
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;
        @DbUnitTester
        IDatabaseTester tester;

        HasBothMarkers(final PrepAndExpectedTestCase testCase, final IDatabaseTester tester)
        {
            this.testCase = testCase;
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasUnmarkedTesterField
    {
        IDatabaseTester tester;

        HasUnmarkedTesterField(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class MarkedTesterSuperclass
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class SubclassOfMarkedTesterSuperclass extends MarkedTesterSuperclass
    {
        SubclassOfMarkedTesterSuperclass(final IDatabaseTester tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class NoFieldsTestInstance
    {
        void aTestMethod()
        {
        }
    }

    static class HasNullTesterField
    {
        @DbUnitTester
        IDatabaseTester tester;

        void aTestMethod()
        {
        }
    }

    static class HasWrongTypeTesterField
    {
        @DbUnitTester
        Object tester;

        HasWrongTypeTesterField(final Object tester)
        {
            this.tester = tester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasNullTestCaseField
    {
        @DbUnitTestCase
        PrepAndExpectedTestCase testCase;

        void aTestMethod()
        {
        }
    }

    static class HasWrongTypeTestCaseField
    {
        @DbUnitTestCase
        Object testCase;

        HasWrongTypeTestCaseField(final Object testCase)
        {
            this.testCase = testCase;
        }

        void aTestMethod()
        {
        }
    }

    static class HasTwoMarkedTesterFields
    {
        @DbUnitTester
        IDatabaseTester tester;
        @DbUnitTester
        IDatabaseTester anotherTester;

        HasTwoMarkedTesterFields(final IDatabaseTester tester, final IDatabaseTester anotherTester)
        {
            this.tester = tester;
            this.anotherTester = anotherTester;
        }

        void aTestMethod()
        {
        }
    }

    static class MarkedTesterFieldSuperclassToBeShadowed
    {
        @DbUnitTester
        IDatabaseTester tester;
    }

    static class SubclassWithOwnMarkedTesterField
            extends MarkedTesterFieldSuperclassToBeShadowed
    {
        @DbUnitTester
        IDatabaseTester subclassTester;

        SubclassWithOwnMarkedTesterField(final IDatabaseTester superclassTester,
                final IDatabaseTester subclassTester)
        {
            this.tester = superclassTester;
            this.subclassTester = subclassTester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasTwoUnmarkedTesterFields
    {
        IDatabaseTester tester;
        IDatabaseTester anotherTester;

        HasTwoUnmarkedTesterFields(final IDatabaseTester tester,
                final IDatabaseTester anotherTester)
        {
            this.tester = tester;
            this.anotherTester = anotherTester;
        }

        void aTestMethod()
        {
        }
    }

    static class HasStaticUnmarkedTesterField
    {
        static IDatabaseTester tester;

        void aTestMethod()
        {
        }
    }

    static class RecordingFactory implements DatabaseTesterFactory
    {
        static IDatabaseTester next;

        @Override
        public IDatabaseTester getDatabaseTester()
        {
            return next;
        }
    }

    @DbUnitConfig(databaseTesterFactory = RecordingFactory.class)
    private static class WithFactory
    {
    }

    /** A minimal, settable-{@code ApplicationContext} {@link TestContext} - simpler and clearer
     * here than mocking a stateful interface. */
    private static class FakeTestContext implements TestContext
    {
        private final Object testInstance;
        private final Method testMethod;
        private ApplicationContext applicationContext;

        FakeTestContext(final Object testInstance, final Method testMethod)
        {
            this.testInstance = testInstance;
            this.testMethod = testMethod;
        }

        void setApplicationContext(final ApplicationContext applicationContext)
        {
            this.applicationContext = applicationContext;
        }

        @Override
        public boolean hasApplicationContext()
        {
            return applicationContext != null;
        }

        @Override
        public ApplicationContext getApplicationContext()
        {
            return applicationContext;
        }

        @Override
        public Class<?> getTestClass()
        {
            return testInstance.getClass();
        }

        @Override
        public Object getTestInstance()
        {
            return testInstance;
        }

        @Override
        public Method getTestMethod()
        {
            return testMethod;
        }

        @Override
        public Throwable getTestException()
        {
            return null;
        }

        @Override
        public void markApplicationContextDirty(final DirtiesContext.HierarchyMode hierarchyMode)
        {
        }

        @Override
        public void updateState(final Object testInstance, final Method testMethod,
                final Throwable testException)
        {
        }

        @Override
        public void setAttribute(final String name, final Object value)
        {
        }

        @Override
        public Object getAttribute(final String name)
        {
            return null;
        }

        @Override
        public Object removeAttribute(final String name)
        {
            return null;
        }

        @Override
        public boolean hasAttribute(final String name)
        {
            return false;
        }

        @Override
        public String[] attributeNames()
        {
            return new String[0];
        }
    }
}
