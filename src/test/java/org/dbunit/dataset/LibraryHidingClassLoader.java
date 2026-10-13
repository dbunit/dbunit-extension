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
package org.dbunit.dataset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A class loader that behaves like a classpath without one library: it refuses to load any
 * class of the hidden package, and defines the classes of the named packages itself, from the
 * same class files the test's own loader reads, so that they resolve the library through it
 * rather than through the test's loader, which has it.
 */
final class LibraryHidingClassLoader extends ClassLoader
{
    private final String hiddenPrefix;
    private final String[] definedPrefixes;

    LibraryHidingClassLoader(final String hiddenPrefix, final String... definedPrefixes)
    {
        super(LibraryHidingClassLoader.class.getClassLoader());
        this.hiddenPrefix = hiddenPrefix;
        this.definedPrefixes = definedPrefixes.clone();
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
            throws ClassNotFoundException
    {
        if (name.startsWith(hiddenPrefix))
        {
            throw new ClassNotFoundException(name + " is hidden from this class loader");
        }
        if (!isDefinedHere(name))
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

    private boolean isDefinedHere(final String name)
    {
        for (final String prefix : definedPrefixes)
        {
            if (name.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
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
