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
 * {@link FileExtensionDataFileLoader} that parses each file once and shares the parsed
 * dataset with every later {@link #load(String)} of the same resource, instead of parsing it
 * again for each test. A suite whose tests share the same reference files otherwise pays for
 * a full parse of every prep and expected file in every test: a SAX parse and the building of
 * the tables for flat XML, and much more for Excel files.
 *
 * <p>Only the parsed dataset is shared. Every load still wraps it in a new
 * {@link org.dbunit.dataset.ReplacementDataSet} built from the replacement objects and
 * substrings of the loader instance that was asked, so those stay per instance.
 *
 * <p>The annotation runtime builds a new loader instance for each test, so the parsed datasets
 * are held per loader class, not per instance, and every instance of a class reuses what the
 * others parsed. A subclass keeps its own. They stay until {@link #clearCache()} drops them or
 * the loader class is unloaded, so for the class itself, for the rest of the run. Share one
 * loader class only among tests that read the same resources, and keep the number of distinct
 * files in mind when they are large.
 *
 * <p>This suits immutable classpath fixtures: a resource that changes while the JVM keeps
 * running, as in a test daemon or an IDE runner, is not read again until
 * {@link #clearCache()}. The shared datasets must be treated as read-only, as they are shared
 * by every test.
 *
 * <p>Use it by passing the class to {@code @DbUnitConfig(dataFileLoader = ...)}, which needs
 * the public no-argument constructor, or by setting an instance on a
 * {@link org.dbunit.PrepAndExpectedTestCase}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class CachingFileExtensionDataFileLoader extends FileExtensionDataFileLoader
{
    private static final ClassValue<ConcurrentMap<String, IDataSet>> PARSED_DATA_SETS =
            new ClassValue<ConcurrentMap<String, IDataSet>>()
            {
                @Override
                protected ConcurrentMap<String, IDataSet> computeValue(final Class<?> loaderClass)
                {
                    return new ConcurrentHashMap<>();
                }
            };

    /** Create new instance. */
    public CachingFileExtensionDataFileLoader()
    {
    }

    /**
     * Create new instance with replacement objects.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public CachingFileExtensionDataFileLoader(final Map<?, ?> ro)
    {
        super(ro);
    }

    /**
     * Create new instance with replacement objects and replacement substrings.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param rs
     *            The replacement substrings for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public CachingFileExtensionDataFileLoader(final Map<?, ?> ro, final Map<?, ?> rs)
    {
        super(ro, rs);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the dataset an earlier call parsed from the same resource, parsing it with
     * {@link #loadUncachedDataSet(URL)} the first time. The cache is keyed by the URL's string
     * form, as {@link URL#equals(Object)} and {@link URL#hashCode()} can do blocking name
     * lookups. A resource that fails to parse is not remembered.
     */
    @Override
    public IDataSet loadDataSet(final URL url) throws DataSetException, IOException
    {
        final ConcurrentMap<String, IDataSet> cache = PARSED_DATA_SETS.get(getClass());
        final String key = url.toExternalForm();
        final IDataSet cached = cache.get(key);
        if (cached != null)
        {
            return cached;
        }

        final IDataSet parsed = loadUncachedDataSet(url);
        final IDataSet raced = cache.putIfAbsent(key, parsed);
        return raced == null ? parsed : raced;
    }

    /**
     * Parses the resource without consulting or filling the cache, the way
     * {@link FileExtensionDataFileLoader} does.
     *
     * @param url
     *            The resource to parse.
     * @return The parsed dataset.
     * @throws DataSetException
     *             If the resource cannot be parsed as a dataset.
     * @throws IOException
     *             If the resource cannot be read.
     */
    protected IDataSet loadUncachedDataSet(final URL url) throws DataSetException, IOException
    {
        return super.loadDataSet(url);
    }

    /**
     * Drops the parsed datasets of this loader's class, so the next load of each resource
     * parses it again. For a test that changes a resource it loads, or a daemon that outlives
     * the change.
     */
    public void clearCache()
    {
        PARSED_DATA_SETS.remove(getClass());
    }
}
