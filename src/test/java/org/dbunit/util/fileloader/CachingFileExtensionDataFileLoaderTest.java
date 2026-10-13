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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @since 3.6.0
 */
class CachingFileExtensionDataFileLoaderTest
{
    private static final String TEST_FILE_NAME = "/org/dbunit/util/fileloader/test.xml";

    @BeforeEach
    void setUp()
    {
        new CountingLoader().clearCache();
        new OtherCountingLoader().clearCache();
    }

    @Test
    void testLoad_sameFileTwice_parsesOnce() throws Exception
    {
        final CountingLoader loader = new CountingLoader();

        loader.load(TEST_FILE_NAME);
        final IDataSet second = loader.load(TEST_FILE_NAME);

        assertThat(loader.getParseCount())
                .as("The second load of the same file must reuse the parsed dataset.")
                .isEqualTo(1);
        assertThat(second.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
    }

    @Test
    void testLoad_twoInstancesOfTheSameLoaderClass_shareParsedDatasets() throws Exception
    {
        final CountingLoader first = new CountingLoader();
        final CountingLoader second = new CountingLoader();

        first.load(TEST_FILE_NAME);
        second.load(TEST_FILE_NAME);

        assertThat(first.getParseCount() + second.getParseCount())
                .as("The annotation runtime builds a new loader for each test, so a dataset parsed"
                        + " by one instance must be reused by the next instance of the class.")
                .isEqualTo(1);
    }

    @Test
    void testLoad_twoDifferentLoaderClasses_doNotShareParsedDatasets() throws Exception
    {
        final CountingLoader first = new CountingLoader();
        final OtherCountingLoader second = new OtherCountingLoader();

        first.load(TEST_FILE_NAME);
        second.load(TEST_FILE_NAME);

        assertThat(first.getParseCount() + second.getParseCount())
                .as("Each loader class keeps its own parsed datasets.").isEqualTo(2);
    }

    @Test
    void testLoad_differentReplacementSubstringsPerInstance_eachAppliesItsOwn() throws Exception
    {
        final Map<String, String> firstSubstrings = new HashMap<>();
        firstSubstrings.put("row", "first");
        final Map<String, String> secondSubstrings = new HashMap<>();
        secondSubstrings.put("row", "second");
        final CountingLoader first = new CountingLoader(new HashMap<>(), firstSubstrings);
        final CountingLoader second = new CountingLoader(new HashMap<>(), secondSubstrings);

        final Object firstValue =
                first.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");
        final Object secondValue =
                second.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");

        assertThat(firstValue).as("The first loader must apply its own substring.")
                .isEqualTo("first 0");
        assertThat(secondValue)
                .as("The second loader must apply its own substring to the shared parsed dataset.")
                .isEqualTo("second 0");
    }

    @Test
    void testLoadDataSet_sameUrlTwice_returnsTheSameParsedInstance() throws Exception
    {
        final CountingLoader loader = new CountingLoader();
        final URL url = getClass().getResource(TEST_FILE_NAME);

        final IDataSet first = loader.loadDataSet(url);
        final IDataSet second = loader.loadDataSet(url);

        assertThat(second).as("The cache must hand back the dataset it parsed.").isSameAs(first);
    }

    @Test
    void testLoad_missingFile_throwsDatabaseUnitRuntimeExceptionEveryTime()
    {
        final CountingLoader loader = new CountingLoader();

        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/no-such-file.xml"))
                .as("A missing file must fail the first load.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/no-such-file.xml"))
                .as("A failed load must not be remembered as a success.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
    }

    @Test
    void testLoad_malformedFile_throwsAndIsParsedAgainOnTheNextLoad()
    {
        final CountingLoader loader = new CountingLoader();

        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/malformed.xml"))
                .as("A malformed file must fail the first load.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
        assertThatThrownBy(() -> loader.load("/org/dbunit/util/fileloader/malformed.xml"))
                .as("A malformed file must fail the next load too, as the failure is not"
                        + " remembered.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);

        assertThat(loader.getParseCount())
                .as("A file that failed to parse must not be cached, so it is parsed again.")
                .isEqualTo(2);
    }

    @Test
    void testClearCache_afterLoad_parsesAgainOnTheNextLoad() throws Exception
    {
        final CountingLoader loader = new CountingLoader();
        loader.load(TEST_FILE_NAME);

        loader.clearCache();
        loader.load(TEST_FILE_NAME);

        assertThat(loader.getParseCount())
                .as("clearCache() must drop the loader class's parsed datasets.").isEqualTo(2);
    }

    @Test
    void testLoadDataSet_concurrentFirstLoads_allGetTheSameInstance() throws Exception
    {
        final URL url = getClass().getResource(TEST_FILE_NAME);
        final int threadCount = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<CompletableFuture<IDataSet>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++)
        {
            futures.add(CompletableFuture.supplyAsync(() ->
            {
                try
                {
                    start.await();
                    return new CountingLoader().loadDataSet(url);
                } catch (final Exception e)
                {
                    throw new IllegalStateException(e);
                }
            }));
        }

        start.countDown();
        final List<IDataSet> loaded = new ArrayList<>();
        for (final CompletableFuture<IDataSet> future : futures)
        {
            loaded.add(future.get());
        }

        assertThat(Collections.frequency(loaded, loaded.get(0)))
                .as("Racing first loads must all end up with the one dataset the cache kept.")
                .isEqualTo(threadCount);
    }

    @Test
    void testConstructor_withoutArguments_isPublicForDbUnitConfig() throws Exception
    {
        assertThat(CachingFileExtensionDataFileLoader.class.getConstructor())
                .as("@DbUnitConfig(dataFileLoader = ...) instantiates the loader class through its"
                        + " public no-argument constructor.")
                .isNotNull();
    }

    /** Loader that counts how often it really parses a file. */
    private static class CountingLoader extends CachingFileExtensionDataFileLoader
    {
        private int parseCount;

        CountingLoader()
        {
            super();
        }

        CountingLoader(final Map<?, ?> replacementObjects, final Map<?, ?> replacementSubstrings)
        {
            super(replacementObjects, replacementSubstrings);
        }

        @Override
        protected IDataSet loadUncachedDataSet(final URL url) throws DataSetException, IOException
        {
            parseCount++;
            return super.loadUncachedDataSet(url);
        }

        int getParseCount()
        {
            return parseCount;
        }
    }

    /** A second loader class, to show each class keeps its own cache. */
    private static class OtherCountingLoader extends CountingLoader
    {
    }
}
