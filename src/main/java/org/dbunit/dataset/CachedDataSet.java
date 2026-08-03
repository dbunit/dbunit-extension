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

package org.dbunit.dataset;

import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hold copy of another dataset or a consumed provider content.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.x (Apr 18, 2003)
 */
public class CachedDataSet extends AbstractDataSet implements IDataSetConsumer
{
    private static final Logger logger = LoggerFactory.getLogger(CachedDataSet.class);

    private DefaultTable _activeTable;

    /**
     * Default constructor.
     *
     * @throws DataSetException if initialization fails.
     */
    public CachedDataSet() throws DataSetException {
        super();
        initialize();
    }

    /**
     * Creates a copy of the specified dataset.
     *
     * @param dataSet the dataset to copy.
     * @throws DataSetException if copying the dataset fails.
     */
    public CachedDataSet(IDataSet dataSet) throws DataSetException
    {
        super(dataSet.isCaseSensitiveTableNames());
        initialize();

        final ITableIterator iterator = dataSet.iterator();
        while (iterator.next())
         {
            final ITable table = iterator.getTable();
            _orderedTableNameMap.add(table.getTableMetaData().getTableName(),
                    new CachedTable(table));
        }
    }

    /**
     * Creates a CachedDataSet that synchronously consume the specified producer.
     *
     * @param producer the producer to consume.
     * @throws DataSetException if consuming the producer fails.
     */
    public CachedDataSet(IDataSetProducer producer) throws DataSetException
    {
        this(producer, false);
    }

    /**
     * Creates a CachedDataSet that synchronously consume the specified producer.
     * @param producer the producer to consume.
     * @param caseSensitiveTableNames Whether or not case sensitive table names should be used
     * @throws DataSetException if consuming the producer fails.
     */
    public CachedDataSet(IDataSetProducer producer, boolean caseSensitiveTableNames) throws DataSetException
    {
        super(caseSensitiveTableNames);
        initialize();

        producer.setConsumer(this);
        producer.produce();
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("createIterator(reversed={}) - start", String.valueOf(reversed));

        ITable[] tables = (ITable[])_orderedTableNameMap.orderedValues().toArray(new ITable[0]);
        return new DefaultTableIterator(tables, reversed);
    }

    ////////////////////////////////////////////////////////////////////////
    // IDataSetConsumer interface

    public void startDataSet() throws DataSetException
    {
        logger.debug("startDataSet() - start");
        _orderedTableNameMap = super.createTableNameMap();
    }

    public void endDataSet() throws DataSetException
    {
        logger.debug("endDataSet() - start");
        logger.debug("endDataSet() - the final tableMap is: " + _orderedTableNameMap);
    }

    public void startTable(ITableMetaData metaData) throws DataSetException
    {
        logger.debug("startTable(metaData={}) - start", metaData);
        _activeTable = new DefaultTable(metaData);
    }

    public void endTable() throws DataSetException
    {
        logger.debug("endTable() - start");
        String tableName = _activeTable.getTableMetaData().getTableName();
        // Check whether the table appeared once before
        if(_orderedTableNameMap.containsTable(tableName))
        {
            DefaultTable existingTable = (DefaultTable)_orderedTableNameMap.get(tableName);
            // Add all newly collected rows to the existing table
            existingTable.addTableRows(_activeTable);
        }
        else
        {
            _orderedTableNameMap.add(tableName, _activeTable);
        }
        _activeTable = null;
    }

    public void row(Object[] values) throws DataSetException
    {
        logger.debug("row(values={}) - start", values);
        _activeTable.addRow(values);
    }
}
