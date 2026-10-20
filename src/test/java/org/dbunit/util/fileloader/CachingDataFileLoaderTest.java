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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * @since 3.6.0
 */
class CachingDataFileLoaderTest
{
    private static final String TEST_FILE_NAME = "/org/dbunit/util/fileloader/test.xml";

    private final DataFileLoader delegate = mock(DataFileLoader.class);
    private final CachingDataFileLoader loader = new CachingDataFileLoader(delegate);

    @Test
    void testLoad_sameFileTwice_loadsThroughTheDelegateOnce()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());

        loader.load("a.xml");
        loader.load("a.xml");

        verify(delegate, times(1)).load("a.xml");
    }

    @Test
    void testLoad_sameFileTwice_returnsTheSameDataSet()
    {
        final IDataSet dataSet = new DefaultDataSet();
        when(delegate.load("a.xml")).thenReturn(dataSet);

        final IDataSet first = loader.load("a.xml");
        final IDataSet second = loader.load("a.xml");

        assertThat(first).as("The first load returns what the delegate loaded.")
                .isSameAs(dataSet);
        assertThat(second).as("The second load returns the dataset the first one kept.")
                .isSameAs(dataSet);
    }

    @Test
    void testLoad_twoFiles_loadsEachThroughTheDelegateOnce()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        when(delegate.load("b.xml")).thenReturn(new DefaultDataSet());

        loader.load("a.xml");
        loader.load("b.xml");
        loader.load("a.xml");
        loader.load("b.xml");

        verify(delegate, times(1)).load("a.xml");
        verify(delegate, times(1)).load("b.xml");
    }

    @Test
    void testLoad_twoLoadersOverTheSameDelegate_eachKeepsItsOwnDataSets()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        final CachingDataFileLoader other = new CachingDataFileLoader(delegate);

        loader.load("a.xml");
        other.load("a.xml");

        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testLoad_nullOrEmptyFilename_asksTheDelegateEveryTimeAndKeepsNothing()
    {
        when(delegate.load(null)).thenReturn(new DefaultDataSet());
        when(delegate.load("")).thenReturn(new DefaultDataSet());

        loader.load(null);
        loader.load(null);
        loader.load("");
        loader.load("");

        verify(delegate, times(2)).load(null);
        verify(delegate, times(2)).load("");
    }

    @Test
    void testLoad_delegateFailsThenSucceeds_theFailureIsNotKept()
    {
        final IDataSet dataSet = new DefaultDataSet();
        when(delegate.load("a.xml")).thenThrow(new DatabaseUnitRuntimeException("boom"))
                .thenReturn(dataSet);

        assertThatThrownBy(() -> loader.load("a.xml"))
                .as("A failed load must reach the caller.")
                .isInstanceOf(DatabaseUnitRuntimeException.class);
        final IDataSet second = loader.load("a.xml");

        assertThat(second).as("A failed load must not be remembered, so the file is loaded again.")
                .isSameAs(dataSet);
    }

    @Test
    void testClearCache_afterLoad_loadsThroughTheDelegateAgainOnTheNextLoad()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        loader.load("a.xml");

        loader.clearCache();
        loader.load("a.xml");

        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testAddReplacementObjects_afterLoad_passesThemOnAndLoadsAgainOnTheNextLoad()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("[NULL]", null);
        loader.load("a.xml");

        loader.addReplacementObjects(replacements);
        loader.load("a.xml");

        verify(delegate).addReplacementObjects(replacements);
        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testAddReplacementSubstrings_afterLoad_passesThemOnAndLoadsAgainOnTheNextLoad()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("row", "line");
        loader.load("a.xml");

        loader.addReplacementSubstrings(replacements);
        loader.load("a.xml");

        verify(delegate).addReplacementSubstrings(replacements);
        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testRemoveAllReplacementObjects_afterLoad_passesItOnAndLoadsAgainOnTheNextLoad()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        loader.load("a.xml");

        loader.removeAllReplacementObjects();
        loader.load("a.xml");

        verify(delegate).removeAllReplacementObjects();
        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testRemoveAllReplacementSubstrings_afterLoad_passesItOnAndLoadsAgainOnTheNextLoad()
    {
        when(delegate.load("a.xml")).thenReturn(new DefaultDataSet());
        loader.load("a.xml");

        loader.removeAllReplacementSubstrings();
        loader.load("a.xml");

        verify(delegate).removeAllReplacementSubstrings();
        verify(delegate, times(2)).load("a.xml");
    }

    @Test
    void testLoadDataSet_sameUrlTwice_asksTheDelegateEveryTime() throws Exception
    {
        final URL url = getClass().getResource(TEST_FILE_NAME);
        when(delegate.loadDataSet(url)).thenReturn(new DefaultDataSet());

        loader.loadDataSet(url);
        loader.loadDataSet(url);

        verify(delegate, times(2)).loadDataSet(url);
    }

    @Test
    void testConstructor_nullDelegate_throwsIllegalArgumentException()
    {
        assertThatThrownBy(() -> new CachingDataFileLoader(null))
                .as("A caching loader without a loader to cache for is a mistake.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testLoad_concurrentFirstLoads_allGetTheSameDataSet() throws Exception
    {
        when(delegate.load("a.xml")).thenAnswer(invocation -> new DefaultDataSet());
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
                    return loader.load("a.xml");
                } catch (final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
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
                .as("Racing first loads must all end up with the one dataset the loader kept.")
                .isEqualTo(threadCount);
    }

    @Test
    void testLoad_realFlatXmlFileTwice_returnsTheSameParsedDataSet() throws Exception
    {
        final CachingDataFileLoader real = new CachingDataFileLoader(new FileExtensionDataFileLoader());

        final IDataSet first = real.load(TEST_FILE_NAME);
        final IDataSet second = real.load(TEST_FILE_NAME);

        assertThat(first.getTableNames()).as("Unexpected tables in dataset.")
                .containsExactly("TEST_TABLE");
        assertThat(second).as("The second load must hand back the dataset the first one parsed.")
                .isSameAs(first);
    }

    @Test
    void testLoad_replacementSubstringsAddedAfterALoad_nextLoadAppliesThem() throws Exception
    {
        final CachingDataFileLoader real = new CachingDataFileLoader(new FileExtensionDataFileLoader());
        final Object before = real.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");
        final Map<String, String> substrings = new HashMap<>();
        substrings.put("row", "changed");

        real.addReplacementSubstrings(substrings);
        final Object after = real.load(TEST_FILE_NAME).getTable("TEST_TABLE").getValue(0, "COL0");

        assertThat(before).as("Without replacements the file's own value is read.")
                .isEqualTo("row 0");
        assertThat(after).as("The replacement added after the first load applies to the next.")
                .isEqualTo("changed 0");
    }
}
