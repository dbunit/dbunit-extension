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
 * {@link ITable} decorator that wraps another table and enforces forward-only,
 * non-counted row access.
 *
 * @author Manuel Laflamme
 * @since Apr 9, 2003
 * @version $Revision$
 */
public class ForwardOnlyTable implements ITable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ForwardOnlyTable.class);

    private final ITable _table;
    private int _lastRow = -1;

    /**
     * Creates a forward-only decorator over the given table.
     *
     * @param table the table to decorate.
     */
    public ForwardOnlyTable(ITable table)
    {
        _table = table;
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITable interface

    public ITableMetaData getTableMetaData()
    {
        return _table.getTableMetaData();
    }

    public int getRowCount()
    {
        throw new UnsupportedOperationException();
    }

    public Object getValue(int row, String column) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), column);

        if (row < _lastRow)
        {
            throw new UnsupportedOperationException("Cannot go backward!");
        }

        _lastRow = row;
        return _table.getValue(row, column);
    }
}
