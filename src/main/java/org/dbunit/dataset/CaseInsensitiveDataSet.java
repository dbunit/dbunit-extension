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

import java.util.Locale;

import org.dbunit.database.AmbiguousTableNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows access to a decorated dataset in a case insensitive way. Dataset
 * implementations provided by the framework are case sensitive. This class
 * allows using them in situation where case sensitiveness is not desirable.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @deprecated All IDataSet implementations are case insensitive since DbUnit 1.5 - may change again since tablenames on RDBMSes can be case sensitive
 * @since 1.0
 */
public class CaseInsensitiveDataSet extends AbstractDataSet
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CaseInsensitiveDataSet.class);

    private final IDataSet _dataSet;
    private OrderedTableNameMap orderedTableMap;

    public CaseInsensitiveDataSet(IDataSet dataSet) throws AmbiguousTableNameException, DataSetException
    {
        _dataSet = dataSet;
        
        // Check for duplicates using the OrderedTableNameMap as helper
        orderedTableMap = new OrderedTableNameMap(false);
        ITableIterator tableIterator = _dataSet.iterator();
        while(tableIterator.next()) {
            ITable table = (ITable) tableIterator.getTable();
            String tableName = table.getTableMetaData().getTableName();
            orderedTableMap.add(tableName.toUpperCase(Locale.ENGLISH), tableName);
        }
    }

    private String getInternalTableName(String tableName) throws DataSetException
    {
        logger.debug("getInternalTableName(tableName={}) - start", tableName);

        String originalTableName = (String)orderedTableMap.get(tableName.toUpperCase(Locale.ENGLISH));
        if(originalTableName==null){
            throw new NoSuchTableException(tableName);
        }
        else {
            return originalTableName;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("createIterator(reversed={}) - start", String.valueOf(reversed));
        
        return new CaseInsensitiveIterator(reversed ?
                _dataSet.reverseIterator() : _dataSet.iterator());
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSet interface

    public String[] getTableNames() throws DataSetException
    {
        return _dataSet.getTableNames();
    }

    public ITableMetaData getTableMetaData(String tableName)
            throws DataSetException
    {
        logger.debug("getTableMetaData(tableName={}) - start", tableName);
        return _dataSet.getTableMetaData(getInternalTableName(tableName));
    }

    public ITable getTable(String tableName) throws DataSetException
    {
        logger.debug("getTable(tableName={}) - start", tableName);
        ITable table = _dataSet.getTable(getInternalTableName(tableName));
        return new CaseInsensitiveTable(table);
    }

    ////////////////////////////////////////////////////////////////////////////
    // CaseInsensitiveIterator class

    private class CaseInsensitiveIterator implements ITableIterator
    {
        private final ITableIterator _iterator;

        public CaseInsensitiveIterator(ITableIterator iterator)
        {
            _iterator = iterator;
        }

        ////////////////////////////////////////////////////////////////////////
        // ITableIterator interface

        public boolean next() throws DataSetException
        {
            return _iterator.next();
        }

        public ITableMetaData getTableMetaData() throws DataSetException
        {
            return _iterator.getTableMetaData();
        }

        public ITable getTable() throws DataSetException
        {
            return new CaseInsensitiveTable(_iterator.getTable());
        }
    }
}
