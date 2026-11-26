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

package org.dbunit.util.fileloader.subclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.util.fileloader.CachingFileExtensionDataFileLoader;
import org.junit.jupiter.api.Test;

/**
 * Tests a subclass of {@link CachingFileExtensionDataFileLoader} that lives in another package
 * than the loader, so a file name relative to the subclass differs from one relative to the
 * loader.
 *
 * @since 3.6.0
 */
class CachingFileExtensionDataFileLoaderSubclassTest
{
    private static final String RELATIVE_FILE_NAME = "subclass-relative.xml";
    private static final String ABSOLUTE_FILE_NAME =
            "/org/dbunit/util/fileloader/subclass/subclass-relative.xml";

    /** A subclass whose package holds the file it loads by a relative name. */
    public static class SubclassLoader extends CachingFileExtensionDataFileLoader
    {
    }

    @Test
    void testLoad_relativeFileNameInTheSubclassPackage_loadsTheFile() throws Exception
    {
        final SubclassLoader loader = new SubclassLoader();

        final IDataSet dataSet = loader.load(RELATIVE_FILE_NAME);

        assertThat(dataSet.getTableNames())
                .as("A relative file name must resolve against the package of the subclass.")
                .containsExactly("SUBCLASS_TABLE");
    }

    @Test
    void testLoad_relativeFileNameTwice_returnsTheSameDataSet()
    {
        final SubclassLoader loader = new SubclassLoader();

        final IDataSet first = loader.load(RELATIVE_FILE_NAME);
        final IDataSet second = loader.load(RELATIVE_FILE_NAME);

        assertThat(second).as("The file found by a relative name must be cached.")
                .isSameAs(first);
    }

    @Test
    void testLoad_absoluteFileName_loadsTheFile() throws Exception
    {
        final SubclassLoader loader = new SubclassLoader();

        final IDataSet dataSet = loader.load(ABSOLUTE_FILE_NAME);

        assertThat(dataSet.getTableNames())
                .as("A name that starts with a slash must resolve from the classpath root.")
                .containsExactly("SUBCLASS_TABLE");
    }

    @Test
    void testLoad_relativeFileNameOnlyInTheLoaderPackage_throwsDatabaseUnitRuntimeException()
    {
        final SubclassLoader loader = new SubclassLoader();

        assertThatThrownBy(() -> loader.load("test.xml"))
                .as("A relative name must not resolve against the package of the loader class.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
    }

    @Test
    void testLoad_nullFileName_returnsAnEmptyDataSet() throws Exception
    {
        final SubclassLoader loader = new SubclassLoader();

        final IDataSet dataSet = loader.load(null);

        assertThat(dataSet.getTableNames()).as("A null file name must load an empty dataset.")
                .isEmpty();
    }
}
