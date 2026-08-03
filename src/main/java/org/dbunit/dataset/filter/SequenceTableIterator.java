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
package org.dbunit.dataset.filter;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ITableIterator} that returns a dataset's tables in a caller-specified
 * name order.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Apr 6, 2003
 */
public class SequenceTableIterator implements ITableIterator
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SequenceTableIterator.class);

    private final String[] _tableNames;
    private final IDataSet _dataSet;
    private int _index = -1;

    /**
     * Creates an iterator that returns the given dataset's tables in the given name order.
     *
     * @param tableNames the table names, in the order they should be returned.
     * @param dataSet the dataset providing the tables.
     */
    public SequenceTableIterator(String[] tableNames, IDataSet dataSet)
    {
        _tableNames = tableNames;
        _dataSet = dataSet;
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableIterator interface

    public boolean next() throws DataSetException
    {
        logger.debug("next() - start");

        _index++;
        return _index < _tableNames.length;
    }

    public ITableMetaData getTableMetaData() throws DataSetException
    {
        logger.debug("getTableMetaData() - start");

        return _dataSet.getTableMetaData(_tableNames[_index]);
    }

    public ITable getTable() throws DataSetException
    {
        logger.debug("getTable() - start");

        return _dataSet.getTable(_tableNames[_index]);
    }
}
