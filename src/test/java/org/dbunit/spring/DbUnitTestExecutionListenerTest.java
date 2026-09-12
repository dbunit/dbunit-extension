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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.dbunit.IDatabaseTester;
import org.dbunit.annotation.DbUnitTester;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContext;

class DbUnitTestExecutionListenerTest
{
    private final DbUnitTestExecutionListener listener = new DbUnitTestExecutionListener();

    @Test
    void testGetOrder_returnsOrderConstant()
    {
        assertThat(listener.getOrder()).isEqualTo(DbUnitTestExecutionListener.ORDER);
    }

    @Test
    void testBeforeTestExecution_dbUnitTesterField_runsOnSetup() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(new HasTesterField(tester));

        listener.beforeTestExecution(context);

        verify(tester).onSetup();
    }

    @Test
    void testBeforeAndAfterTestExecution_sameTestContext_reuseOneExecutorAndRunOnTearDown()
            throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(new HasTesterField(tester));

        listener.beforeTestExecution(context);
        listener.afterTestExecution(context);

        verify(tester).onSetup();
        verify(tester).onTearDown();
    }

    @Test
    void testAfterTestExecution_noPriorBeforeTestExecution_doesNothing() throws Exception
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        final FakeTestContext context = fakeTestContext(new HasTesterField(tester));

        assertThatCode(() -> listener.afterTestExecution(context))
                .as("afterTestExecution() must be a no-op when beforeTestExecution() never ran"
                        + " for this test - e.g. a class-level failure before the test method"
                        + " itself started.")
                .doesNotThrowAnyException();
        verify(tester, never()).onTearDown();
    }

    private static FakeTestContext fakeTestContext(final Object testInstance) throws Exception
    {
        final Method testMethod = testInstance.getClass().getDeclaredMethod("aTestMethod");
        return new FakeTestContext(testInstance, testMethod);
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

    /** A minimal, map-backed {@link TestContext} - simpler and clearer here than coaxing
     * stateful attribute storage out of a Mockito mock. */
    private static class FakeTestContext implements TestContext
    {
        private final Object testInstance;
        private final Method testMethod;
        private final Map<String, Object> attributes = new HashMap<>();

        FakeTestContext(final Object testInstance, final Method testMethod)
        {
            this.testInstance = testInstance;
            this.testMethod = testMethod;
        }

        @Override
        public boolean hasApplicationContext()
        {
            return false;
        }

        @Override
        public ApplicationContext getApplicationContext()
        {
            throw new IllegalStateException("No ApplicationContext configured.");
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
            attributes.put(name, value);
        }

        @Override
        public Object getAttribute(final String name)
        {
            return attributes.get(name);
        }

        @Override
        public Object removeAttribute(final String name)
        {
            return attributes.remove(name);
        }

        @Override
        public boolean hasAttribute(final String name)
        {
            return attributes.containsKey(name);
        }

        @Override
        public String[] attributeNames()
        {
            return attributes.keySet().toArray(new String[0]);
        }
    }
}
