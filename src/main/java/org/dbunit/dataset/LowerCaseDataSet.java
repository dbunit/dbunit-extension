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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Specialized IDataSet decorator that convert the table name and
 * column names to lower case. Used in DbUnit own test suite to verify that
 * operations are case insensitive.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 14, 2003
 */
public class LowerCaseDataSet extends AbstractDataSet
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(LowerCaseDataSet.class);

    private final IDataSet _dataSet;

    public LowerCaseDataSet(ITable table) throws DataSetException
    {
        this(new DefaultDataSet(table));
    }

    public LowerCaseDataSet(ITable[] tables) throws DataSetException
    {
        this(new DefaultDataSet(tables));
    }

    public LowerCaseDataSet(IDataSet dataSet) throws DataSetException
    {
        _dataSet = dataSet;
    }

    private ITable createLowerTable(ITable table) throws DataSetException
    {
        logger.debug("createLowerTable(table={}) - start", table);

        return new CompositeTable(
                new LowerCaseTableMetaData(table.getTableMetaData()), table);
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
        logger.debug("createIterator(reversed={}) - start", String.valueOf(reversed));

        return new LowerCaseIterator(reversed ?
                _dataSet.reverseIterator() : _dataSet.iterator());
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSet interface

    public String[] getTableNames() throws DataSetException
    {
        logger.debug("getTableNames() - start");

        String[] tableNames = super.getTableNames();
        for (int i = 0; i < tableNames.length; i++)
        {
            tableNames[i] = tableNames[i].toLowerCase(Locale.ENGLISH);
        }
        return tableNames;
    }

    public ITableMetaData getTableMetaData(String tableName) throws DataSetException
    {
        logger.debug("getTableMetaData(tableName={}) - start", tableName);
        return new LowerCaseTableMetaData(super.getTableMetaData(tableName));
    }

    public ITable getTable(String tableName) throws DataSetException
    {
        logger.debug("getTable(tableName={}) - start", tableName);
        return createLowerTable(super.getTable(tableName));
    }

    ////////////////////////////////////////////////////////////////////////////
    // LowerCaseIterator class

    private class LowerCaseIterator implements ITableIterator
    {

        private final ITableIterator _iterator;

        public LowerCaseIterator(ITableIterator iterator)
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
            return new LowerCaseTableMetaData(_iterator.getTableMetaData());
        }

        public ITable getTable() throws DataSetException
        {
            return createLowerTable(_iterator.getTable());
        }
    }
}
