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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dbunit.util.fileloader.CachingDataFileLoader;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * The {@link DataFileLoader} instances of one scope. A loader class that is made to be shared, a
 * {@link CachingDataFileLoader} or a subclass of it, is built with its no-arg constructor on the
 * first request and handed to every later request of the scope, so the tests of the scope share
 * the datasets it keeps, as those of
 * {@link org.dbunit.util.fileloader.CachingFileExtensionDataFileLoader} do. A loader of any other
 * class, the default {@link org.dbunit.util.fileloader.FileExtensionDataFileLoader} included, is
 * built for every request: its replacement objects and substrings can be changed by whatever it
 * is given to, such as a test case a test builds, and a change must not reach another test.
 *
 * <p>The scope is the binding's choice: the whole run for the JUnit Jupiter extension, a test
 * class for the Spring listener. Thread safe, as tests of one scope may run in parallel.
 *
 * <p>Not intended for direct use by test code; machinery consumed by a binding such as
 * {@code DbUnitExtension} or a Spring {@code TestExecutionListener}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class DataFileLoaderInstances
{
    private final ReflectiveInstantiation instantiation = new ReflectiveInstantiation();
    private final ConcurrentMap<Class<? extends DataFileLoader>, DataFileLoader> loaders =
            new ConcurrentHashMap<>();

    /**
     * Returns the loader of the given class: the one instance of this scope, built on the first
     * request, for a {@link CachingDataFileLoader} class, and a new instance for any other class.
     *
     * @param loaderClass The loader class, with a no-arg constructor.
     * @return The one instance of the class in this scope when it is a
     *         {@link CachingDataFileLoader}, a new instance otherwise.
     * @throws IllegalStateException If the class has no accessible no-arg constructor, or the
     *             constructor throws. Nothing is kept then, so a later request tries again.
     */
    public DataFileLoader get(final Class<? extends DataFileLoader> loaderClass)
    {
        final boolean isMadeToBeShared = CachingDataFileLoader.class.isAssignableFrom(loaderClass);
        if (isMadeToBeShared)
        {
            return loaders.computeIfAbsent(loaderClass, this::instantiate);
        }
        return instantiate(loaderClass);
    }

    private DataFileLoader instantiate(final Class<? extends DataFileLoader> loaderClass)
    {
        return instantiation.instantiate(loaderClass, "DbUnitConfig.dataFileLoader");
    }
}
