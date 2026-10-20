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

import org.dbunit.dataset.IDataSet;

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
 * <p>A file name that does not start with a slash is looked up relative to the package of the
 * class of this loader, so a subclass finds the files in its own package, as a subclass of
 * {@link FileExtensionDataFileLoader} does.
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

    /**
     * {@inheritDoc}
     *
     * <p>Looks a file name that does not start with a slash up relative to the package of the
     * class of this loader, which is the package of a subclass for one.
     */
    @Override
    public IDataSet load(final String filename)
    {
        if (filename == null || filename.isEmpty() || filename.startsWith("/"))
        {
            return super.load(filename);
        }

        final String className = getClass().getName();
        final int lastDot = className.lastIndexOf('.');
        final String packagePath = className.substring(0, lastDot + 1).replace('.', '/');
        return super.load("/" + packagePath + filename);
    }
}
