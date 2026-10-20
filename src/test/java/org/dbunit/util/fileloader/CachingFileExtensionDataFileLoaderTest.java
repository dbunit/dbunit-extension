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

package org.dbunit.util.fileloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * @since 3.6.0
 */
class CachingFileExtensionDataFileLoaderTest
{
    private static final String TEST_FILE_NAME = "/org/dbunit/util/fileloader/test.xml";

    @Test
    void testLoad_sameFileTwice_returnsTheSameParsedDataSet() throws Exception
    {
        final CachingFileExtensionDataFileLoader loader = new CachingFileExtensionDataFileLoader();

        final IDataSet first = loader.load(TEST_FILE_NAME);
        final IDataSet second = loader.load(TEST_FILE_NAME);

        assertThat(first.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
        assertThat(second).as("The second load of the same file must reuse the parsed dataset.")
                .isSameAs(first);
    }

    @Test
    void testLoad_twoInstances_doNotShareParsedDataSets()
    {
        final CachingFileExtensionDataFileLoader first = new CachingFileExtensionDataFileLoader();
        final CachingFileExtensionDataFileLoader second = new CachingFileExtensionDataFileLoader();

        final IDataSet fromFirst = first.load(TEST_FILE_NAME);
        final IDataSet fromSecond = second.load(TEST_FILE_NAME);

        assertThat(fromSecond)
                .as("The datasets are held by the instance, so a second instance parses for"
                        + " itself.")
                .isNotSameAs(fromFirst);
    }

    @Test
    void testLoad_replacementSubstringsGivenToTheConstructor_eachInstanceAppliesItsOwn()
            throws Exception
    {
        final Map<String, String> firstSubstrings = new HashMap<>();
        firstSubstrings.put("row", "first");
        final Map<String, String> secondSubstrings = new HashMap<>();
        secondSubstrings.put("row", "second");
        final CachingFileExtensionDataFileLoader first =
                new CachingFileExtensionDataFileLoader(new HashMap<>(), firstSubstrings);
        final CachingFileExtensionDataFileLoader second =
                new CachingFileExtensionDataFileLoader(new HashMap<>(), secondSubstrings);

        final Object firstValue =
                first.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");
        final Object secondValue =
                second.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");

        assertThat(firstValue).as("The first loader must apply its own substring.")
                .isEqualTo("first 0");
        assertThat(secondValue).as("The second loader must apply its own substring.")
                .isEqualTo("second 0");
    }

    @Test
    void testLoad_replacementObjectsGivenToTheConstructor_areApplied() throws Exception
    {
        final Map<String, String> objects = new HashMap<>();
        objects.put("row 0", "replaced");
        final CachingFileExtensionDataFileLoader loader =
                new CachingFileExtensionDataFileLoader(objects);

        final Object value = loader.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");

        assertThat(value).as("The replacement object must replace the whole value.")
                .isEqualTo("replaced");
    }

    @Test
    void testLoad_missingFile_throwsDatabaseUnitRuntimeExceptionEveryTime()
    {
        final CachingFileExtensionDataFileLoader loader = new CachingFileExtensionDataFileLoader();

        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/no-such-file.xml"))
                .as("A missing file must fail the first load.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/no-such-file.xml"))
                .as("A failed load must not be remembered as a success.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
    }

    @Test
    void testLoad_malformedFile_throwsDatabaseUnitRuntimeExceptionEveryTime()
    {
        final CachingFileExtensionDataFileLoader loader = new CachingFileExtensionDataFileLoader();

        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/malformed.xml"))
                .as("A malformed file must fail the first load.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/malformed.xml"))
                .as("A file that failed to parse must not be remembered, so it fails again.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
    }

    @Test
    void testClearCache_afterLoad_parsesAgainOnTheNextLoad()
    {
        final CachingFileExtensionDataFileLoader loader = new CachingFileExtensionDataFileLoader();
        final IDataSet before = loader.load(TEST_FILE_NAME);

        loader.clearCache();
        final IDataSet after = loader.load(TEST_FILE_NAME);

        assertThat(after).as("clearCache() must drop the parsed datasets.").isNotSameAs(before);
    }

    @Test
    void testConstructor_withoutArguments_isPublicForDbUnitConfig() throws Exception
    {
        assertThat(CachingFileExtensionDataFileLoader.class.getConstructor())
                .as("@DbUnitConfig(dataFileLoader = ...) instantiates the loader class through its"
                        + " public no-argument constructor.")
                .isNotNull();
    }
}
