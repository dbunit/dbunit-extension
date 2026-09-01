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

import org.dbunit.util.fileloader.DataSetPathsProvider;
import org.junit.jupiter.api.Test;

class DataSetPathsResolverTest
{
    private final DataSetPathsResolver resolver = new DataSetPathsResolver();

    @Test
    void testResolve_annotationAbsent_returnsEmpty()
    {
        final String[] resolved = resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "", null, DataSetPathsProvider.class);

        assertThat(resolved).as("A null value() means the annotation was not declared.")
                .isEmpty();
    }

    @Test
    void testResolve_inlineValue_resolvesEachRelativeToTheTestClassPackage()
    {
        final String[] resolved = resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "", new String[] {"a.xml", "b.xml"}, DataSetPathsProvider.class);

        assertThat(resolved).containsExactly(
                "/org/dbunit/annotation/runtime/a.xml",
                "/org/dbunit/annotation/runtime/b.xml");
    }

    @Test
    void testResolve_dataSetBaseDirSet_prefixesEachRelativePath()
    {
        final String[] resolved = resolver.resolve("DbUnitExpected",
                DataSetPathsResolverTest.class, "datasets", new String[] {"a.xml"},
                DataSetPathsProvider.class);

        assertThat(resolved).containsExactly("/datasets/a.xml");
    }

    @Test
    void testResolve_absolutePath_isUsedAsIs()
    {
        final String[] resolved = resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "datasets", new String[] {"/shared/a.xml"}, DataSetPathsProvider.class);

        assertThat(resolved).as("A leading slash wins over dataSetBaseDir and the package.")
                .containsExactly("/shared/a.xml");
    }

    @Test
    void testResolve_provider_resolvesThePathsItSupplies()
    {
        final String[] resolved = resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "", new String[0], SuppliesOnePath.class);

        assertThat(resolved).containsExactly("/org/dbunit/annotation/runtime/from-provider.xml");
    }

    @Test
    void testResolve_bothInlineValueAndProviderSet_throwsNamingTheAnnotationAndTestClass()
    {
        assertThatThrownBy(() -> resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "", new String[] {"a.xml"}, SuppliesOnePath.class))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("@DbUnitPrep on " + DataSetPathsResolverTest.class
                                .getName())
                        .hasMessageContaining("value()")
                        .hasMessageContaining("provider()");
    }

    @Test
    void testResolve_providerReturnsNull_throwsNamingTheProvider()
    {
        assertThatThrownBy(() -> resolver.resolve("DbUnitPrep", DataSetPathsResolverTest.class,
                "", new String[0], ReturnsNull.class))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(ReturnsNull.class.getName())
                        .hasMessageContaining("DbUnitPrep.provider");
    }

    @Test
    void testResolve_providerReturnsNothing_throwsWithTheDropTheAnnotationHint()
    {
        assertThatThrownBy(() -> resolver.resolve("DbUnitExpected",
                DataSetPathsResolverTest.class, "", new String[0], ReturnsNothing.class))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("returned nothing")
                        .hasMessageContaining("drop @DbUnitExpected");
    }

    static class SuppliesOnePath implements DataSetPathsProvider
    {
        @Override
        public String[] getDataSetPaths()
        {
            return new String[] {"from-provider.xml"};
        }
    }

    static class ReturnsNull implements DataSetPathsProvider
    {
        @Override
        public String[] getDataSetPaths()
        {
            return null;
        }
    }

    static class ReturnsNothing implements DataSetPathsProvider
    {
        @Override
        public String[] getDataSetPaths()
        {
            return new String[0];
        }
    }
}
