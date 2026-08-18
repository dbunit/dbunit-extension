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

import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.util.fileloader.DataFileLoader;

/**
 * Normalizes a dataset path from {@code org.dbunit.annotation}'s {@code DbUnitPrep} or
 * {@code DbUnitExpected} annotations into an absolute classpath resource path, the form
 * {@link DataFileLoader#load(String)} expects.
 *
 * <p>{@link DataFileLoader#load(String)} itself resolves a path without a leading {@code /}
 * relative to {@code org/dbunit/util/fileloader/}, not to the test class, so this
 * resolution has to happen before the loader is called at all.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>A path already starting with {@code /} - an absolute classpath path, used as-is.
 *   Always wins, so a shared file outside the base directory stays reachable.</li>
 *   <li>{@link DbUnitConfig#dataSetBaseDir()}, when set - prefixed to the path.</li>
 *   <li>Otherwise - resolved relative to the test class's package.</li>
 * </ol>
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class DataSetResourcePathResolver {
    /**
     * Resolves {@code path} to an absolute classpath resource path.
     *
     * @param path the path to resolve, as written on {@code @DbUnitPrep} or
     *            {@code @DbUnitExpected}.
     * @param testClass the test class; used to resolve a path relative to its package.
     * @param dataSetBaseDir the configured base directory, or {@code null}/empty when not
     *            set.
     * @return The absolute classpath resource path.
     * @throws IllegalStateException if {@code path} is {@code null} - e.g. a
     *             {@code DataSetPathsProvider} returning an array containing a {@code null}
     *             element.
     */
    public String resolve(final String path, final Class<?> testClass,
            final String dataSetBaseDir) {
        if (path == null) {
            throw new IllegalStateException("A @DbUnitPrep/@DbUnitExpected dataset path for "
                    + testClass.getName() + " is null.");
        }
        if (path.startsWith("/")) {
            return path;
        }
        if (dataSetBaseDir != null && !dataSetBaseDir.isEmpty()) {
            final String absoluteBaseDir =
                    dataSetBaseDir.startsWith("/") ? dataSetBaseDir : "/" + dataSetBaseDir;
            return join(absoluteBaseDir, path);
        }
        return join(packagePath(testClass), path);
    }

    /**
     * Returns {@code testClass}'s package as an absolute classpath directory, tolerating the
     * default (unnamed) package - where {@link Class#getPackage()} returns {@code null} - by
     * deriving it from {@link Class#getName()} instead.
     */
    private String packagePath(final Class<?> testClass) {
        final String className = testClass.getName();
        final int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return "/";
        }
        return "/" + className.substring(0, lastDot).replace('.', '/');
    }

    private String join(final String baseDir, final String path) {
        return baseDir.endsWith("/") ? baseDir + path : baseDir + "/" + path;
    }
}
