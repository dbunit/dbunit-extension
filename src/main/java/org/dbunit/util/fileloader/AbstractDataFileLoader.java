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
import java.util.HashMap;
import java.util.Map;

import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class with common implementation for dbUnit data file loaders.
 * 
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public abstract class AbstractDataFileLoader implements DataFileLoader {
    private final Logger LOG =
            LoggerFactory.getLogger(AbstractDataFileLoader.class);

    private Map replacementObjects;
    private Map replacementSubstrings;

    /** Create new instance. */
    public AbstractDataFileLoader() {
        this(new HashMap(), new HashMap());
    }

    /**
     * Create new instance with replacement objects.
     * 
     * @param replacementObjects
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public AbstractDataFileLoader(Map ro) {
        this(ro, new HashMap());
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
    public AbstractDataFileLoader(Map ro, Map rs) {
        if (ro == null) {
            throw new IllegalArgumentException(
                    "Replacement object map is null.");
        }

        if (rs == null) {
            throw new IllegalArgumentException(
                    "Replacement substrings map is null.");
        }

        this.replacementObjects = ro;
        this.replacementSubstrings = rs;
    }

    /**
     * {@inheritDoc}
     */
    public IDataSet load(String filename) throws DatabaseUnitRuntimeException {
        IDataSet ds = new DefaultDataSet();

        LOG.debug("load: processing file={}", filename);

        if (filename == null || "".equals(filename)) {
            final String msg =
                    "load: filename is null or empty string,"
                            + " using DefaultDataSet()";
            LOG.debug(msg);
        } else {
            URL url = this.getClass().getResource(filename);

            if (url == null) {
                final String msg = "Could not find file named=" + filename;
                throw new DatabaseUnitRuntimeException(msg);
            }

            try {
                ds = loadDataSet(url);
                ds = processReplacementTokens(ds);
            } catch (DataSetException e) {
                final String msg =
                        "DataSetException occurred loading data set file name='"
                                + filename + "', msg='"
                                + e.getLocalizedMessage() + "'";
                throw new DatabaseUnitRuntimeException(msg, e);
            } catch (IOException e) {
                final String msg =
                        "IOException occurred loading data set file name='"
                                + filename + '\'' + ", msg='"
                                + e.getLocalizedMessage() + "'";
                throw new DatabaseUnitRuntimeException(msg, e);
            }
        }

        return ds;
    }

    /** 
     * Add the replacements in the maps (objects and substrings) to the
     * specified dataset.
     * 
     * @param ds
     *            The dataset to wrap with a <code>ReplacementDataSet</code> and
     *            process replacement tokens on.
     * @return The specified dataset decorated with
     *         <code>ReplacementDataSet</code> and processed with the tokens in
     *         the replacement maps.
     * @since 2.4.8
     */
    protected ReplacementDataSet processReplacementTokens(IDataSet ds) {
        ReplacementDataSet rds =
                new ReplacementDataSet(ds, replacementObjects,
                        replacementSubstrings);

        return rds;
    }

    /**
     * {@inheritDoc}
     */
    public void addReplacementObjects(Map ro) {
        this.replacementObjects.putAll(ro);
    }

    /**
     * {@inheritDoc}
     */
    public void addReplacementSubstrings(Map rs) {
        this.replacementSubstrings.putAll(rs);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAllReplacementObjects() {
        this.replacementObjects.clear();
    }

    /**
     * {@inheritDoc}
     */
    public void removeAllReplacementSubstrings() {
        this.replacementSubstrings.clear();
    }
}
