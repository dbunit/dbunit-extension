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

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * {@link DataFileLoader} that loads each file once through another loader and hands the dataset
 * it got to every later {@link #load(String)} of the same file, instead of loading it again for
 * each test. A suite whose tests share the same reference files otherwise pays for a full parse
 * of every prep and expected file in every test: a SAX parse and the building of the tables for
 * flat XML, and much more for Excel files.
 *
 * <p>The datasets are held by this instance, for as long as it lives, and a dataset returned by
 * one call is the one every other call for that file gets, so it must be treated as read-only.
 * They stay until {@link #clearCache()} drops them or this loader is discarded. Keep the number
 * of distinct files in mind when they are large, and use it for immutable classpath fixtures: a
 * resource that changes while the JVM keeps running, as in a test daemon or an IDE runner, is not
 * read again until {@link #clearCache()}.
 *
 * <p>Whatever the wrapped loader applies to a loaded file, such as replacement objects and
 * substrings, is part of what is kept. A change to the replacements made through this loader
 * therefore drops the datasets it holds, so the next load applies it. A failed load is not kept.
 *
 * <p>The annotation runtime shares one instance of a loader class named in
 * {@code @DbUnitConfig(dataFileLoader = ...)} among all the tests of its scope, so a subclass
 * with a public no-argument constructor that passes the wrapped loader to this class, such as
 * {@link CachingFileExtensionDataFileLoader}, caches for the whole scope. Outside the annotation
 * runtime, share the instance among the test cases that should share the datasets, for example by
 * setting it on each {@link org.dbunit.PrepAndExpectedTestCase}.
 *
 * <p>Safe to use from several threads at once, as far as the wrapped loader is.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class CachingDataFileLoader implements DataFileLoader
{
    private final DataFileLoader delegate;
    private final ConcurrentMap<String, IDataSet> loadedDataSets = new ConcurrentHashMap<>();

    /**
     * Create new instance.
     *
     * @param delegate
     *            The loader that loads a file the first time it is asked for.
     * @throws IllegalArgumentException
     *             If the delegate is {@code null}.
     */
    public CachingDataFileLoader(final DataFileLoader delegate)
    {
        if (delegate == null)
        {
            throw new IllegalArgumentException("The delegate loader is null.");
        }
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the dataset an earlier call loaded for the same file name, loading it through
     * the wrapped loader the first time. A {@code null} or empty file name is not kept: the
     * wrapped loader answers it, with an empty dataset, every time.
     */
    @Override
    public IDataSet load(final String filename)
    {
        if (filename == null || filename.isEmpty())
        {
            return delegate.load(filename);
        }

        final IDataSet cached = loadedDataSets.get(filename);
        if (cached != null)
        {
            return cached;
        }

        final IDataSet loaded = delegate.load(filename);
        final IDataSet raced = loadedDataSets.putIfAbsent(filename, loaded);
        if (raced == null)
        {
            return loaded;
        }
        return raced;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Passed on to the wrapped loader every time: only {@link #load(String)} keeps what it
     * returns.
     */
    @Override
    public IDataSet loadDataSet(final URL url) throws DataSetException, IOException
    {
        return delegate.loadDataSet(url);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the datasets this loader holds, so the next load applies the new replacements.
     */
    @Override
    public void addReplacementObjects(final Map replacementObjects)
    {
        delegate.addReplacementObjects(replacementObjects);
        clearCache();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the datasets this loader holds, so the next load applies the new replacements.
     */
    @Override
    public void addReplacementSubstrings(final Map replacementSubstrings)
    {
        delegate.addReplacementSubstrings(replacementSubstrings);
        clearCache();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the datasets this loader holds, so the next load applies no replacements.
     */
    @Override
    public void removeAllReplacementObjects()
    {
        delegate.removeAllReplacementObjects();
        clearCache();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the datasets this loader holds, so the next load applies no replacements.
     */
    @Override
    public void removeAllReplacementSubstrings()
    {
        delegate.removeAllReplacementSubstrings();
        clearCache();
    }

    /**
     * Drops the datasets this loader holds, so the next load of each file loads it again. For a
     * test that changes a resource it loads, or a daemon that outlives the change.
     */
    public void clearCache()
    {
        loadedDataSets.clear();
    }
}
