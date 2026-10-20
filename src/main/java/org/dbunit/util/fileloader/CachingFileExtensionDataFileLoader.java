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

import java.util.Map;

/**
 * {@link CachingDataFileLoader} around a {@link FileExtensionDataFileLoader}: it chooses the
 * format by the file extension and loads each file once, handing the dataset to every later
 * {@link #load(String)} of that file instead of loading it again for each test.
 *
 * <p>It exists for the public no-argument constructor that
 * {@code @DbUnitConfig(dataFileLoader = CachingFileExtensionDataFileLoader.class)} needs; the
 * annotation runtime shares one instance of the class among the tests of its scope, so the
 * datasets are held for the whole scope. To cache another loader, extend
 * {@link CachingDataFileLoader} with a no-argument constructor that passes it, or wrap it in a
 * {@link CachingDataFileLoader} directly outside the annotation runtime.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class CachingFileExtensionDataFileLoader extends CachingDataFileLoader
{
    /** Create new instance. */
    public CachingFileExtensionDataFileLoader()
    {
        super(new FileExtensionDataFileLoader());
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
        super(new FileExtensionDataFileLoader(ro));
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
        super(new FileExtensionDataFileLoader(ro, rs));
    }
}
