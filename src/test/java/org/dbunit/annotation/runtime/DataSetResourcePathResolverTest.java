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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DataSetResourcePathResolverTest
{
    private final DataSetResourcePathResolver resolver = new DataSetResourcePathResolver();

    @Test
    void testResolve_nullPath_throwsIllegalStateException()
    {
        assertThatThrownBy(() -> resolver.resolve(null, DataSetResourcePathResolverTest.class,
                ""))
                        .as("A null path - e.g. a DataSetPathsProvider returning an array"
                                + " containing a null element - must be rejected clearly"
                                + " rather than throwing a raw NullPointerException.")
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(DataSetResourcePathResolverTest.class.getName());
    }

    @Test
    void testResolve_leadingSlashPath_returnsPathUnchanged()
    {
        final String resolved =
                resolver.resolve("/dbunit/users.xml", DataSetResourcePathResolverTest.class, "");

        assertThat(resolved).as("An absolute classpath path must be used as-is.")
                .isEqualTo("/dbunit/users.xml");
    }

    @Test
    void testResolve_relativePath_prefixesTestClassPackage()
    {
        final String resolved =
                resolver.resolve("users.xml", DataSetResourcePathResolverTest.class, "");

        assertThat(resolved).as("A relative path must be prefixed with the test class package.")
                .isEqualTo("/org/dbunit/annotation/runtime/users.xml");
    }

    @Test
    void testResolve_nestedTestClass_prefixesEnclosingPackage()
    {
        final String resolved =
                resolver.resolve("users.xml", NestedTestClass.class, "");

        assertThat(resolved)
                .as("A @Nested test class shares its enclosing class's package.")
                .isEqualTo("/org/dbunit/annotation/runtime/users.xml");
    }

    @Test
    void testResolve_baseDirWithoutLeadingSlash_stillResolvesToAnAbsolutePath()
    {
        final String resolved = resolver.resolve("users.xml",
                DataSetResourcePathResolverTest.class, "dbunit/accounts");

        assertThat(resolved)
                .as("A base dir missing its leading slash must still resolve to an absolute"
                        + " classpath path, matching what DataFileLoader#load(String)"
                        + " requires - not one resolved relative to"
                        + " org/dbunit/util/fileloader/.")
                .isEqualTo("/dbunit/accounts/users.xml");
    }

    @Test
    void testResolve_baseDirConfiguredAndRelativePath_prefixesBaseDir()
    {
        final String resolved = resolver.resolve("users.xml",
                DataSetResourcePathResolverTest.class, "/dbunit/accounts/");

        assertThat(resolved).as("A configured base dir must be prefixed to a relative path.")
                .isEqualTo("/dbunit/accounts/users.xml");
    }

    @Test
    void testResolve_baseDirConfiguredAndLeadingSlashPath_returnsPathUnchanged()
    {
        final String resolved = resolver.resolve("/dbunit/users.xml",
                DataSetResourcePathResolverTest.class, "/dbunit/accounts/");

        assertThat(resolved)
                .as("A leading slash must win over a configured base dir.")
                .isEqualTo("/dbunit/users.xml");
    }

    @Test
    void testResolve_baseDirWithoutTrailingSlash_stillJoinsCleanly()
    {
        final String resolved = resolver.resolve("users.xml",
                DataSetResourcePathResolverTest.class, "/dbunit/accounts");

        assertThat(resolved)
                .as("A base dir missing its trailing slash must still join cleanly.")
                .isEqualTo("/dbunit/accounts/users.xml");
    }

    @Test
    void testResolve_baseDirNull_treatedSameAsNotSet()
    {
        final String resolved =
                resolver.resolve("users.xml", DataSetResourcePathResolverTest.class, null);

        assertThat(resolved)
                .as("A null dataSetBaseDir - documented as equivalent to empty/not set - must"
                        + " fall back to resolving relative to the test class's package, not"
                        + " throw a NullPointerException.")
                .isEqualTo("/org/dbunit/annotation/runtime/users.xml");
    }

    @Test
    void testResolve_testClassInDefaultPackage_resolvesToRoot() throws ClassNotFoundException
    {
        // Class.forName(), not an import, since a default-package class is not importable
        // from this (named-package) test class - see DefaultPackageMarker's own Javadoc.
        final Class<?> defaultPackageClass = Class.forName("DefaultPackageMarker");

        final String resolved = resolver.resolve("users.xml", defaultPackageClass, "");

        assertThat(resolved)
                .as("A test class in the default (unnamed) package - where Class#getPackage()"
                        + " returns null - must still resolve to a root-level classpath path,"
                        + " not throw deriving the package from Class#getName() instead.")
                .isEqualTo("/users.xml");
    }

    static class NestedTestClass
    {
    }
}
