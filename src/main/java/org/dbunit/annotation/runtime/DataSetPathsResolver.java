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
import org.dbunit.util.fileloader.DataSetPathsProvider;

/**
 * Resolves one {@code @DbUnitPrep} or {@code @DbUnitExpected} dataset attribute - its inline
 * {@code value()} paths, or the {@link DataSetPathsProvider} its {@code provider()} names - into
 * the absolute classpath resource paths {@link DataFileLoader#load(String)} expects, normalizing
 * each through {@link DataSetResourcePathResolver}.
 *
 * <p>The "both {@code value()} and {@code provider()} set", "provider returned {@code null}", and
 * "provider returned nothing" rules go through {@link ProvidedAttribute}, so every "inline value
 * or provider class" attribute in {@code org.dbunit.annotation} reports them the same way.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class DataSetPathsResolver
{
    private final DataSetResourcePathResolver pathResolver = new DataSetResourcePathResolver();

    /**
     * Resolves one dataset attribute.
     *
     * @param annotationName The declaring annotation's simple name - {@code "DbUnitPrep"} or
     *            {@code "DbUnitExpected"} - for diagnostics.
     * @param testClass The test class, used to resolve a package-relative path.
     * @param dataSetBaseDir The configured {@code @DbUnitConfig.dataSetBaseDir()}, or empty.
     * @param value The attribute's inline {@code value()} paths, or {@code null} when the
     *            annotation is absent.
     * @param provider The attribute's {@code provider()} class, or {@link DataSetPathsProvider}
     *            itself when unset.
     * @return The resolved absolute classpath paths; empty when the annotation is absent.
     * @throws IllegalStateException If both {@code value()} and {@code provider()} are set, or a
     *             named provider cannot be instantiated, returns {@code null}, or returns
     *             nothing.
     */
    String[] resolve(final String annotationName, final Class<?> testClass,
            final String dataSetBaseDir, final String[] value,
            final Class<? extends DataSetPathsProvider> provider)
    {
        if (value == null)
        {
            return new String[0];
        }
        final boolean providerSet = provider != DataSetPathsProvider.class;
        ProvidedAttribute.rejectBothSet(value.length > 0 && providerSet,
                "@" + annotationName + " on " + testClass.getName(), "value()", "provider()",
                null);
        final String[] paths = providerSet ? resolveProvidedPaths(annotationName, provider)
                : value;
        final String[] resolved = new String[paths.length];
        for (int i = 0; i < paths.length; i++)
        {
            resolved[i] = pathResolver.resolve(paths[i], testClass, dataSetBaseDir);
        }
        return resolved;
    }

    private static String[] resolveProvidedPaths(final String annotationName,
            final Class<? extends DataSetPathsProvider> provider)
    {
        final String namedBy = "@" + annotationName + ".provider";
        final String[] paths = ProvidedAttribute.requireProvided(
                ReflectiveInstantiation.instantiate(provider, namedBy).getDataSetPaths(),
                DataSetPathsProvider.class.getSimpleName(), provider, namedBy, "getDataSetPaths");
        ProvidedAttribute.rejectEmptyProvider(paths.length == 0,
                DataSetPathsProvider.class.getSimpleName(), provider, namedBy, "getDataSetPaths",
                "A provider is wired to supply dataset paths; supplying none is a"
                        + " misconfiguration - drop @" + annotationName + " for a test that"
                        + " needs no dataset, rather than pointing it at an empty provider.");
        return paths;
    }
}
