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
package org.dbunit.annotation.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.LoggerFactory;

/**
 * A class loader that defines the named classes itself, from the same class files the test's own
 * loader reads, and delegates everything else to that loader. A class it defines is a different
 * {@link Class} from the one the test sees, and becomes unreachable, with this loader, once
 * nothing references them - which lets a test check that code does not keep a class alive.
 */
final class IsolatedClassLoader extends ClassLoader
{
    static
    {
        // The logging framework starts its own background threads the first time anything asks
        // for a logger. If that first request came from a class this loader defined, those
        // threads would keep the loader reachable and a test run on its own, in a fresh JVM,
        // would wrongly find it never collected.
        LoggerFactory.getLogger(IsolatedClassLoader.class);
    }

    private final Set<String> isolatedClassNames;

    IsolatedClassLoader(final Class<?>... isolatedClasses)
    {
        super(IsolatedClassLoader.class.getClassLoader());
        this.isolatedClassNames = new HashSet<>();
        for (final Class<?> isolatedClass : Arrays.asList(isolatedClasses))
        {
            isolatedClassNames.add(isolatedClass.getName());
        }
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
            throws ClassNotFoundException
    {
        if (!isolatedClassNames.contains(name))
        {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name))
        {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null)
            {
                loaded = define(name);
            }
            if (resolve)
            {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> define(final String name) throws ClassNotFoundException
    {
        final String resourceName = name.replace('.', '/') + ".class";
        try (InputStream stream = getParent().getResourceAsStream(resourceName))
        {
            if (stream == null)
            {
                throw new ClassNotFoundException(name);
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int read = stream.read(chunk);
            while (read != -1)
            {
                buffer.write(chunk, 0, read);
                read = stream.read(chunk);
            }
            final byte[] bytes = buffer.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (final IOException e)
        {
            throw new ClassNotFoundException(name, e);
        }
    }
}
