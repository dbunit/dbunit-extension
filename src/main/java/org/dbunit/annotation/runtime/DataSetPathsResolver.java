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

import org.dbunit.util.fileloader.DataFileLoader;

/**
 * Resolves one {@code @DbUnitPrep} or {@code @DbUnitExpected} dataset attribute - its
 * {@code value()} paths - into the absolute classpath resource paths
 * {@link DataFileLoader#load(String)} expects, normalizing each through
 * {@link DataSetResourcePathResolver}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class DataSetPathsResolver
{
    private final DataSetResourcePathResolver pathResolver = new DataSetResourcePathResolver();

    /**
     * Resolves one dataset attribute.
     *
     * @param testClass The class declaring the annotation, used to resolve a package-relative
     *            path.
     * @param dataSetBaseDir The configured {@code @DbUnitConfig.dataSetBaseDir()}, or empty.
     * @param value The attribute's {@code value()} paths, or {@code null} when the annotation
     *            is absent.
     * @return The resolved absolute classpath paths; empty when the annotation is absent.
     */
    String[] resolve(final Class<?> testClass, final String dataSetBaseDir,
            final String[] value)
    {
        if (value == null)
        {
            return new String[0];
        }
        final String[] resolved = new String[value.length];
        for (int i = 0; i < value.length; i++)
        {
            resolved[i] = pathResolver.resolve(value[i], testClass, dataSetBaseDir);
        }
        return resolved;
    }
}
