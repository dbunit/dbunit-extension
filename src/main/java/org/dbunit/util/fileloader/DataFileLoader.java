/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * Defines a dbUnit data file loader supporting replacement objects and
 * substrings with {@link org.dbunit.dataset.ReplacementDataSet}.
 * 
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public interface DataFileLoader {
    /**
     * Load the specified String filename from the classpath into a dbUnit
     * dataset. If filename == null or "", then returns an empty
     * {@link org.dbunit.dataset.DefaultDataSet}. The type of dbUnit dataset
     * created is delegated to the implementing subclass.
     * 
     * @param filename
     *            The dbUnit file to load, in the format for the loader
     *            implementation and fully qualified name with package syntax.
     * @return The dbUnit dataset of the specified file.
     * @throws DatabaseUnitRuntimeException
     *             DataSetException wrapped in a DatabaseUnitRuntimeException
     *             when file load errors occur.
     */
    IDataSet load(String fileName);

    /**
     * Load the specified URL file into a dbUnit dataset. The type of dbUnit
     * dataset created is delegated to the implementing subclass.
     * 
     * @param url
     *            The dbUnit data file url.
     * @return dbUnit dataset of the corresponding input file type.
     * @throws DataSetException
     *             On data errors.
     * @throws IOException
     *             On file errors.
     * @since 2.4.8
     */
    IDataSet loadDataSet(URL url) throws DataSetException, IOException;

    /**
     * Add the specified replacement objects to existing ones for use with
     * {@link org.dbunit.dataset.ReplacementDataSet}.
     * 
     * @param replacementObjects
     *            The replacement objects to include.
     * @since 2.4.8
     */
    void addReplacementObjects(Map replacementObjects);

    /**
     * Add the specified replacement substrings to existing ones for use with
     * {@link org.dbunit.dataset.ReplacementDataSet}.
     * 
     * @param replacementSubstrings
     *            The replacement substrings to include.
     * @since 2.4.8
     */
    void addReplacementSubstrings(Map replacementSubstrings);

    /**
     * Remove all existing replacement objects, resetting to none so no object
     * replacements occur.
     * 
     * @since 2.4.8
     */
    void removeAllReplacementObjects();

    /**
     * Remove all existing replacement substring objects, resetting to none so
     * no substring replacements occur.
     * 
     * @since 2.4.8
     */
    void removeAllReplacementSubstrings();
}
