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
