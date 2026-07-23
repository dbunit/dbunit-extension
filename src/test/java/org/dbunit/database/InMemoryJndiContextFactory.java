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
package org.dbunit.database;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.spi.InitialContextFactory;

/**
 * Test-only {@link InitialContextFactory} backed by a simple in-memory name-to-object map, so
 * {@link org.dbunit.JndiDatabaseTester} can be unit tested through a real {@link javax.naming.InitialContext}
 * without a real JNDI provider or an extra test dependency.
 *
 * <p>
 * Register an object under a unique name with {@link #bind(Object)}, then
 * pass {@link #environment()} (or an environment built from it) as the
 * <code>environment</code> argument of the
 * {@link org.dbunit.JndiDatabaseTester} constructors so JNDI resolves this
 * factory. Bindings are held in static maps for the JVM's lifetime; call
 * {@link #unbind(String)} from test cleanup (e.g. an {@code @AfterEach}
 * method) for every name {@link #bind(Object)} returned, so a long test run
 * does not accumulate bound objects it no longer needs.
 *
 * @since 3.4.0
 */
public final class InMemoryJndiContextFactory implements InitialContextFactory
{
    private static final Map<String, Object> BINDINGS = new ConcurrentHashMap<>();

    private static final Map<String, AtomicInteger> LOOKUP_COUNTS = new ConcurrentHashMap<>();

    private static final AtomicInteger NAME_COUNTER = new AtomicInteger();

    /**
     * Binds the given object under a fresh, unique name.
     *
     * @param value The object {@link javax.naming.Context#lookup(String)}
     *            should return.
     * @return The unique name the object was bound under.
     */
    public static String bind(final Object value)
    {
        final String name = "test/binding-" + NAME_COUNTER.incrementAndGet();
        BINDINGS.put(name, value);
        LOOKUP_COUNTS.put(name, new AtomicInteger());
        return name;
    }

    /**
     * Removes the given bound name and its lookup counter, so the previously
     * bound object - and anything it holds, e.g. a test
     * {@link javax.sql.DataSource} - becomes eligible for garbage collection.
     * Call from test cleanup for every name {@link #bind(Object)} returned.
     *
     * @param name A name previously returned by {@link #bind(Object)}.
     */
    public static void unbind(final String name)
    {
        BINDINGS.remove(name);
        LOOKUP_COUNTS.remove(name);
    }

    /**
     * Returns how many times {@link javax.naming.Context#lookup(String)} has
     * been called for the given bound name.
     *
     * @param name A name previously returned by {@link #bind(Object)}.
     * @return The number of lookups observed so far.
     */
    public static int lookupCount(final String name)
    {
        return LOOKUP_COUNTS.get(name).get();
    }

    /**
     * Returns a JNDI environment that resolves
     * {@link javax.naming.InitialContext} to this factory.
     *
     * @return A fresh, mutable {@link Properties} instance.
     */
    public static Properties environment()
    {
        final Properties environment = new Properties();
        environment.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                InMemoryJndiContextFactory.class.getName());
        return environment;
    }

    /**
     * Returns a proxy {@link Context} whose {@code lookup(String)} resolves
     * names against {@link #BINDINGS} (incrementing the matching
     * {@link #LOOKUP_COUNTS} entry) and whose {@code close()} is a no-op; any
     * other {@link Context} method throws
     * {@link UnsupportedOperationException}.
     *
     * @param environment Ignored; this factory only ever serves the
     *            in-memory bindings.
     * @return The proxy {@link Context}.
     */
    @Override
    public Context getInitialContext(final Hashtable<?, ?> environment)
    {
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("lookup".equals(method.getName()) && args.length == 1)
            {
                final String name = (String) args[0];
                if (!BINDINGS.containsKey(name))
                {
                    throw new NameNotFoundException(name);
                }
                LOOKUP_COUNTS.get(name).incrementAndGet();
                return BINDINGS.get(name);
            }
            if ("close".equals(method.getName()))
            {
                return null;
            }
            throw new UnsupportedOperationException(
                    "InMemoryJndiContextFactory's fake Context does not implement " + method.getName());
        };
        return (Context) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Context.class}, handler);
    }
}
