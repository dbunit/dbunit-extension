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

import java.util.ArrayList;
import java.util.List;

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.OrderedTableNameMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This filter expose a specified table sequence and can be used to reorder
 * tables in a dataset. This implementation does not support duplicate table names.
 * Thus you cannot specify the same table name more than once in this filter
 * and the filtered dataset must not contains duplicate table names. This is
 * the default filter used by the {@link org.dbunit.dataset.FilteredDataSet}.
 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Mar 7, 2003
 */
public class SequenceTableFilter implements ITableFilter
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SequenceTableFilter.class);

    private final OrderedTableNameMap _tableNameMap;

    /**
     * Creates a new SequenceTableFilter with specified table names sequence.
     * @throws AmbiguousTableNameException If the given array contains ambiguous names
     */
    public SequenceTableFilter(String[] tableNames) 
    throws AmbiguousTableNameException
    {
        this(tableNames, false);
    }

    /**
     * Creates a new SequenceTableFilter with specified table names sequence.
     * @param tableNames
     * @param caseSensitiveTableNames
     * @throws AmbiguousTableNameException If the given array contains ambiguous names
     * @since 2.4.2
     */
    public SequenceTableFilter(String[] tableNames, boolean caseSensitiveTableNames) 
    throws AmbiguousTableNameException
    {
        // Gather all tables in the OrderedTableNameMap which also makes the duplicate check
        _tableNameMap = new OrderedTableNameMap(caseSensitiveTableNames);
        for (int i = 0; i < tableNames.length; i++)
        {
            _tableNameMap.add(tableNames[i], null);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableFilter interface

    public boolean accept(String tableName) throws DataSetException
    {
        logger.debug("accept(tableName={}) - start", tableName);

        return _tableNameMap.containsTable(tableName);
    }

    public String[] getTableNames(IDataSet dataSet) throws DataSetException
    {
        logger.debug("getTableNames(dataSet={}) - start", dataSet);

        List nameList = new ArrayList();
        String[] tableNames = _tableNameMap.getTableNames();
        for (int i = 0; i < tableNames.length; i++)
        {
            try
            {
                // Use the table name from the filtered dataset. This ensure
                // that table names are having the same case (lower/upper) from
                // getTableNames() and getTables() methods.
                ITableMetaData metaData = dataSet.getTableMetaData(tableNames[i]);
                nameList.add(metaData.getTableName());
            }
            catch (NoSuchTableException e)
            {
                logger.debug("Table '{}' not found in filtered dataset {}", tableNames[i], dataSet);
                // Skip this table name because the filtered dataset does not
                // contains it.
            }
        }

        return (String[])nameList.toArray(new String[0]);
    }

    public ITableIterator iterator(IDataSet dataSet, boolean reversed)
            throws DataSetException
    {
    	if(logger.isDebugEnabled())
    		logger.debug("iterator(dataSet={}, reversed={}) - start", dataSet, String.valueOf(reversed));

        String[] tableNames = getTableNames(dataSet);
        return new SequenceTableIterator(reversed ?
                DataSetUtils.reverseStringArray(tableNames) : tableNames, dataSet);
    }
    
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("_tableNameMap=").append(_tableNameMap);
        sb.append("]");
        return sb.toString();
    }
}

