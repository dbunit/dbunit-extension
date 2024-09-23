/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

package org.dbunit.dataset.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.dbunit.dataset.AbstractDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableIterator;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Apr 4, 2002)
 */
public class FlatDtdDataSet extends AbstractDataSet implements IDataSetConsumer
{
    private static final Logger logger = LoggerFactory.getLogger(FlatDtdDataSet.class);

    private boolean _ready = false;

    public FlatDtdDataSet()
    {
        initialize();
    }

    public FlatDtdDataSet(InputStream in) throws DataSetException, IOException
    {
        this(new FlatDtdProducer(new InputSource(in)));
    }

    public FlatDtdDataSet(Reader reader) throws DataSetException, IOException
    {
        this(new FlatDtdProducer(new InputSource(reader)));
    }

    public FlatDtdDataSet(IDataSetProducer producer) throws DataSetException
    {
        initialize();
        producer.setConsumer(this);
        producer.produce();
    }

    protected void initialize()
    {
        if(_orderedTableNameMap == null) {
            _orderedTableNameMap = super.createTableNameMap();
        }
    }

    /**
     * Write the specified dataset to the specified output stream as DTD.
     * @see FlatDtdWriter
     */
    public static void write(IDataSet dataSet, OutputStream out)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}) - start", dataSet, out);
        write(dataSet, new OutputStreamWriter(out));
    }

    /**
     * Write the specified dataset to the specified writer as DTD.
     * @see FlatDtdWriter
     */
    public static void write(IDataSet dataSet, Writer out)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}) - start", dataSet, out);
        FlatDtdWriter datasetWriter = new FlatDtdWriter(out);
        datasetWriter.write(dataSet);
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
    	logger.debug("createIterator(reversed={}) - start", reversed);

        // Verify producer notifications completed
        if (!_ready)
        {
            throw new IllegalStateException("Not ready!");
        }

        String[] names = _orderedTableNameMap.getTableNames();
        ITable[] tables = new ITable[names.length];
        for (int i = 0; i < names.length; i++)
        {
            String tableName = names[i];
            ITable table = (ITable)_orderedTableNameMap.get(tableName);
            if (table == null)
            {
                throw new NoSuchTableException(tableName);
            }

            tables[i] = table;
        }

        return new DefaultTableIterator(tables, reversed);
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSet interface

    public String[] getTableNames() throws DataSetException
    {
        logger.debug("getTableNames() - start");

        // Verify producer notifications completed
        if (!_ready)
        {
            throw new IllegalStateException("Not ready!");
        }

        return _orderedTableNameMap.getTableNames();
    }

    public ITableMetaData getTableMetaData(String tableName) throws DataSetException
    {
        logger.debug("getTableMetaData(tableName={}) - start", tableName);

        // Verify producer notifications completed
        if (!_ready)
        {
            throw new IllegalStateException("Not ready!");
        }

        return super.getTableMetaData(tableName);
    }

    public ITable getTable(String tableName) throws DataSetException
    {
        logger.debug("getTable(tableName={}) - start", tableName);

        // Verify producer notifications completed
        if (!_ready)
        {
            throw new IllegalStateException("Not ready!");
        }

        return super.getTable(tableName);
    }

    ////////////////////////////////////////////////////////////////////////
    // IDataSetConsumer interface

    public void startDataSet() throws DataSetException
    {
        logger.debug("startDataSet() - start");

        _ready = false;
    }

    public void endDataSet() throws DataSetException
    {
        logger.debug("endDataSet() - start");

        _ready = true;
    }

    public void startTable(ITableMetaData metaData) throws DataSetException
    {
        logger.debug("startTable(metaData={}) - start", metaData);

        String tableName = metaData.getTableName();
        _orderedTableNameMap.add(tableName, new DefaultTable(metaData));
    }

    public void endTable() throws DataSetException
    {
        // no op
    }

    public void row(Object[] values) throws DataSetException
    {
        // no op
    }

    public String toString()
    {
    	final StringBuilder sb = new StringBuilder();
    	sb.append(getClass().getName()).append("[");
    	sb.append("_ready=").append(this._ready);
    	sb.append(", _orderedTableNameMap=").append(this._orderedTableNameMap);
    	sb.append("]");
    	return sb.toString();
    }
}