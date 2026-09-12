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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.CacheAwareContextLoaderDelegate;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.DefaultTestContext;

/**
 * A real Spring {@link DefaultTestContext} over a mocked
 * {@link CacheAwareContextLoaderDelegate}, so a unit test sees the {@code TestContext} semantics
 * the listener meets under Spring's own runners: one context serves every test method of a
 * class, its attributes survive {@link #updateState(Object, Method, Throwable)}, only the test
 * instance, method and exception change from one method to the next, and
 * {@link #hasApplicationContext()} means the {@code ApplicationContext} is already loaded.
 */
class FakeTestContext extends DefaultTestContext
{
    /**
     * Creates a context whose {@code ApplicationContext} is not loaded.
     *
     * @param testClass The test class the context serves.
     */
    FakeTestContext(final Class<?> testClass)
    {
        this(testClass, null);
    }

    /**
     * Creates a context whose {@code ApplicationContext} is already loaded.
     *
     * @param testClass The test class the context serves.
     * @param applicationContext The loaded context, or {@code null} for one that is not loaded.
     */
    FakeTestContext(final Class<?> testClass, final ApplicationContext applicationContext)
    {
        this(testClass, applicationContext, applicationContext != null);
    }

    /**
     * Creates a context that can supply an {@code ApplicationContext}, loaded or not.
     *
     * @param testClass The test class the context serves.
     * @param applicationContext The context Spring would load, or {@code null} for none.
     * @param loaded Whether Spring has loaded it yet, which is what
     *            {@link #hasApplicationContext()} reports; {@link #getApplicationContext()}
     *            loads it either way.
     */
    FakeTestContext(final Class<?> testClass, final ApplicationContext applicationContext,
            final boolean loaded)
    {
        super(testClass, new MergedContextConfiguration(testClass, null, null, null, null),
                loaderDelegate(applicationContext, loaded));
    }

    private static CacheAwareContextLoaderDelegate loaderDelegate(
            final ApplicationContext applicationContext, final boolean loaded)
    {
        final CacheAwareContextLoaderDelegate delegate =
                mock(CacheAwareContextLoaderDelegate.class);
        if (applicationContext != null)
        {
            when(delegate.isContextLoaded(any())).thenReturn(loaded);
            when(delegate.loadContext(any())).thenReturn(applicationContext);
        }
        return delegate;
    }

    /**
     * Starts a test method the way Spring's {@code TestContextManager} does: the context now
     * serves {@code testInstance} and the no-argument method named {@code testMethodName}, with
     * no test exception, and every attribute is left as it was.
     *
     * @param testInstance The test instance the method runs on.
     * @param testMethodName The name of the no-argument test method declared by the instance's
     *            class.
     * @return This context.
     * @throws NoSuchMethodException If the instance's class declares no such method.
     */
    FakeTestContext beginMethod(final Object testInstance, final String testMethodName)
            throws NoSuchMethodException
    {
        final Method testMethod = testInstance.getClass().getDeclaredMethod(testMethodName);
        updateState(testInstance, testMethod, null);
        return this;
    }

    /**
     * Records that the current test method threw {@code testException}, the way Spring's
     * {@code TestContextManager} hands it to the after-callbacks.
     *
     * @param testException The exception the test method threw.
     * @return This context.
     */
    FakeTestContext failWith(final Throwable testException)
    {
        updateState(getTestInstance(), getTestMethod(), testException);
        return this;
    }
}
