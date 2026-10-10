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

import org.dbunit.DatabaseUnitRuntimeException;

/**
 * A library that only some of dbUnit's features need, and so is an optional dependency, with the
 * means to say what to add when it is missing. Without this check the first use of the library
 * surfaces as a bare {@link NoClassDefFoundError} from deep inside a producer, writer or
 * comparer, naming one of the library's classes and not what to do about it.
 *
 * <p>Whether the library is present is asked of the class loader of the class that needs it,
 * since that is the loader that will resolve the library's classes.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class RequiredLibrary
{
    /** Jackson, needed to read and write JSON datasets and to compare JSON column values. */
    public static final RequiredLibrary JACKSON = new RequiredLibrary("JSON", "Jackson",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.core:jackson-databind");

    /** SnakeYAML, needed to read and write YAML datasets. */
    public static final RequiredLibrary SNAKEYAML = new RequiredLibrary("YAML", "SnakeYAML",
            "org.yaml.snakeyaml.Yaml", "org.yaml:snakeyaml");

    private final String feature;
    private final String libraryName;
    private final String probeClassName;
    private final String coordinates;

    private RequiredLibrary(final String feature, final String libraryName,
            final String probeClassName, final String coordinates)
    {
        this.feature = feature;
        this.libraryName = libraryName;
        this.probeClassName = probeClassName;
        this.coordinates = coordinates;
    }

    /**
     * Fails with a message saying what to add when the library is not on the classpath of the
     * class that needs it. A library that is present but cannot be used, such as a jar built for
     * a newer Java, is not reported as missing: loading it fails with its own, more telling
     * error.
     *
     * @param requester The class that needs the library; its class loader is the one asked.
     * @throws DataSetException If the library is not present.
     */
    public void require(final Class<?> requester) throws DataSetException
    {
        try
        {
            Class.forName(probeClassName, false, requester.getClassLoader());
        } catch (final ClassNotFoundException | NoClassDefFoundError e)
        {
            throw new DataSetException(feature + " support needs " + libraryName + ", which is"
                    + " not on the classpath. Add " + coordinates + " (an optional dependency"
                    + " of dbUnit) to the classpath.", e);
        }
    }

    /**
     * Like {@link #require(Class)}, for a constructor that declares no checked exception.
     *
     * @param requester The class that needs the library; its class loader is the one asked.
     * @throws DatabaseUnitRuntimeException If the library is not present.
     */
    public void requireUnchecked(final Class<?> requester)
    {
        try
        {
            require(requester);
        } catch (final DataSetException e)
        {
            throw new DatabaseUnitRuntimeException(e.getMessage(), e);
        }
    }
}
