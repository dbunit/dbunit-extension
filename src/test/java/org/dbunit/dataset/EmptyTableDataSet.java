package org.dbunit.dataset;

/**
 * <p>
 * Copyright (c) 2003 OZ.COM. All Rights Reserved.
 * </p>
 * 
 * @author manuel.laflamme
 * @since Mar 30, 2004
 */
public class EmptyTableDataSet extends AbstractDataSet
{
    private final IDataSet _dataSet;

    public EmptyTableDataSet(IDataSet dataSet)
    {
        _dataSet = dataSet;
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
        return _dataSet.getTableMetaData(tableName);
    }

    public ITable getTable(String tableName) throws DataSetException
    {
        return new DefaultTable(_dataSet.getTableMetaData(tableName));
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
        return new EmptyTableIterator(
                reversed ? _dataSet.reverseIterator() : _dataSet.iterator());
    }

    public static class EmptyTableIterator implements ITableIterator
    {
        private final ITableIterator _iterator;

        public EmptyTableIterator(ITableIterator iterator)
        {
            _iterator = iterator;
        }

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
            return new DefaultTable(_iterator.getTableMetaData());
        }
    }
}
