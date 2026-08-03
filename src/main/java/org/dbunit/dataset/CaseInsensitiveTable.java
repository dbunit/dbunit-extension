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

/**
 * Decorates an {@link ITable}, resolving column names case-insensitively.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 27, 2002
 * @deprecated All IDataSet implementations are case insensitive since DbUnit 1.5
 */
public class CaseInsensitiveTable implements ITable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CaseInsensitiveTable.class);

    private final ITable _table;

    /**
     * Creates a case-insensitive view of the given table.
     *
     * @param table the table to decorate.
     */
    public CaseInsensitiveTable(ITable table)
    {
        _table = table;
    }

    private String getInternalColumnName(String columnName)
            throws DataSetException
    {
        logger.debug("getInternalColumnName(columnName={}) - start", columnName);

        Column[] columns = _table.getTableMetaData().getColumns();

        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            if (columnName.equalsIgnoreCase(column.getColumnName()))
            {
                return column.getColumnName();
            }
        }

        throw new NoSuchColumnException(_table.getTableMetaData().getTableName(), columnName);
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITable interface

    public ITableMetaData getTableMetaData()
    {
        return _table.getTableMetaData();
    }

    public int getRowCount()
    {
        return _table.getRowCount();
    }

    public Object getValue(int row, String column) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), column);

        return _table.getValue(row, getInternalColumnName(column));
    }
}



