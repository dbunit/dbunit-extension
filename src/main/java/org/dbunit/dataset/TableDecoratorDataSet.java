/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2019, DbUnit.org
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

/**
 * Decorates a dataset to allow decorating the returned {@link ITable}s (and
 * {@link ITableMetaData}s, by extension). Intended to be used for things like
 * filtering out columns. Do not use this to filter out entire tables; use
 * {@link FilteredDataSet} for that.
 *
 * @see ColumnFilterTable
 *
 * @author rcd (rcd AT users.sourceforge.net)
 * @since 3.0.0
 */
public class TableDecoratorDataSet extends AbstractDataSet
{
    private final IDataSet _dataSet;
    private final TableDecoratorFunction _decoratorFunction;

    public TableDecoratorDataSet(final IDataSet dataSet,
            final TableDecoratorFunction decoratorFunction)
    {
        _dataSet = dataSet;
        _decoratorFunction = decoratorFunction;
    }

    @Override
    protected ITableIterator createIterator(final boolean reversed)
            throws DataSetException
    {
        return new FilterIterator(
                reversed ? _dataSet.reverseIterator() : _dataSet.iterator());
    }

    private class FilterIterator implements ITableIterator
    {
        private final ITableIterator _iterator;

        public FilterIterator(final ITableIterator _iterator)
        {
            this._iterator = _iterator;
        }

        @Override
        public boolean next() throws DataSetException
        {
            return _iterator.next();
        }

        @Override
        public ITableMetaData getTableMetaData() throws DataSetException
        {
            return getTable().getTableMetaData();
        }

        @Override
        public ITable getTable() throws DataSetException
        {
            return _decoratorFunction.apply(_iterator.getTable());
        }
    }

    @FunctionalInterface
    public interface TableDecoratorFunction
    {
        ITable apply(ITable table) throws DataSetException;
    }
}
