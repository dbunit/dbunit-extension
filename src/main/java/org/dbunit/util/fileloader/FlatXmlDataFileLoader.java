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

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;

/**
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public class FlatXmlDataFileLoader extends AbstractDataFileLoader {
    private FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();

    /** Create new instance. */
    public FlatXmlDataFileLoader() {
    }

    /**
     * Create new instance with replacement objects.
     * 
     * @param replacementObjects
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public FlatXmlDataFileLoader(Map ro) {
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
    public FlatXmlDataFileLoader(Map ro, Map rs) {
        super(ro, rs);
    }

    /**
     * Create new instance with replacement objects, replacement substrings, and
     * {@link org.dbunit.dataset.xml.FlatXmlDataSetBuilder}.
     * 
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param rs
     *            The replacement substrings for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param builder
     *            The {@link org.dbunit.dataset.xml.FlatXmlDataSetBuilder} to
     *            use.
     */
    public FlatXmlDataFileLoader(Map ro, Map rs, FlatXmlDataSetBuilder builder) {
        super(ro, rs);
        this.builder = builder;
    }

    /**
     * Create new instance with a
     * {@link org.dbunit.dataset.xml.FlatXmlDataSetBuilder}.
     * 
     * @param builder
     *            The {@link org.dbunit.dataset.xml.FlatXmlDataSetBuilder} to
     *            use.
     */
    public FlatXmlDataFileLoader(FlatXmlDataSetBuilder builder) {
        this.builder = builder;
    }

    /**
     * {@inheritDoc}
     */
    public IDataSet loadDataSet(URL url) throws DataSetException,
            IOException {
        IDataSet ds = builder.build(url);

        return ds;
    }

    /**
     * Get the builder.
     * 
     * @see {@link builder}.
     * 
     * @return The builder.
     */
    public FlatXmlDataSetBuilder getBuilder() {
        return builder;
    }

    /**
     * Set the builder.
     * 
     * @see {@link builder}.
     * 
     * @param builder
     *            The builder to set.
     */
    public void setBuilder(FlatXmlDataSetBuilder builder) {
        this.builder = builder;
    }
}
