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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.filter.IColumnFilter;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link ITableMetaData} that exposes only the columns accepted by an {@link IColumnFilter}.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since May 11, 2004
 */
public class FilteredTableMetaData extends AbstractTableMetaData
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(FilteredTableMetaData.class);

    private final String _tableName;
    private final Column[] _columns;
    private final Column[] _primaryKeys;

    public FilteredTableMetaData(ITableMetaData metaData,
            IColumnFilter columnFilter) throws DataSetException
    {
        _tableName = metaData.getTableName();
        _columns = getFilteredColumns(_tableName, metaData.getColumns(), columnFilter);
        _primaryKeys = getFilteredColumns(_tableName, metaData.getPrimaryKeys(), columnFilter);
    }

    public static Column[] getFilteredColumns(String tableName,
            Column[] columns, IColumnFilter columnFilter)
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("getFilteredColumns(tableName={}, columns={}, columnFilter={}) - start",
    				new Object[]{ tableName, columns, columnFilter });
    	}

        if (columns == null)
        {
            return new Column[0];
        }
        
        List columnList =  new ArrayList();
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            if (columnFilter.accept(tableName, column))
            {
                columnList.add(column);
            }
        }
        return (Column[])columnList.toArray(new Column[0]);
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableMetaData interface

    public String getTableName()
    {
        return _tableName;
    }

    public Column[] getColumns() throws DataSetException
    {
        return _columns;
    }

    public Column[] getPrimaryKeys() throws DataSetException
    {
        return _primaryKeys;
    }
}
