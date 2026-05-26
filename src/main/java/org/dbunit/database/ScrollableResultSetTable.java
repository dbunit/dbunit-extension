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

package org.dbunit.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public class ScrollableResultSetTable extends AbstractResultSetTable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ScrollableResultSetTable.class);

    private final int _rowCount;

    public ScrollableResultSetTable(ITableMetaData metaData, ResultSet resultSet)
            throws SQLException, DataSetException
    {
        super(metaData, resultSet);

        try
        {
            if (_resultSet.getType() == ResultSet.TYPE_FORWARD_ONLY)
            {
                throw new SQLException("Forward only ResultSet not supported");
            }

            _resultSet.last();
            _rowCount = _resultSet.getRow();
        }
        catch (SQLException e)
        {
            close();
            throw e;
        }
    }

    public ScrollableResultSetTable(ITableMetaData metaData,
            IDatabaseConnection connection) throws DataSetException, SQLException
    {
        super(metaData, connection, ResultSet.TYPE_SCROLL_INSENSITIVE);

        try
        {
            if (_resultSet.getType() == ResultSet.TYPE_FORWARD_ONLY)
            {
                throw new SQLException("Forward only ResultSet not supported");
            }

            _resultSet.last();
            _rowCount = _resultSet.getRow();
        }
        catch (SQLException e)
        {
            close();
            throw e;
        }
    }

    public ScrollableResultSetTable(String tableName, String selectStatement,
            IDatabaseConnection connection) throws DataSetException, SQLException
    {
        super(tableName, selectStatement, connection, false, ResultSet.TYPE_SCROLL_INSENSITIVE);

        try
        {
            if (_resultSet.getType() == ResultSet.TYPE_FORWARD_ONLY)
            {
                throw new SQLException("Forward only ResultSet not supported");
            }

            _resultSet.last();
            _rowCount = _resultSet.getRow();
        }
        catch (SQLException e)
        {
            close();
            throw e;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITable interface

    public int getRowCount()
    {
        return _rowCount;
    }

    public Object getValue(int row, String columnName) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), columnName);

        assertValidRowIndex(row);

        try
        {
            _resultSet.absolute(row + 1);

            int columnIndex = getColumnIndex(columnName);
            Column column = _metaData.getColumns()[columnIndex];
            return column.getDataType().getSqlValue(columnIndex + 1, _resultSet);
        }
        catch (SQLException e)
        {
            throw new DataSetException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder(2000);

        sb.append(super.toString());
        sb.append(", ");
        sb.append(getClass().getName()).append("[");
        sb.append("_rowCount=[").append(_rowCount).append("]");
        sb.append("]");

        return sb.toString();
    }
}
