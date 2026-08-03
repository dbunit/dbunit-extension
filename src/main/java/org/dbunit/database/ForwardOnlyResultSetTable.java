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
import org.dbunit.dataset.RowOutOfBoundsException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link IResultSetTable} implementation backed by a forward-only, non-cached {@link ResultSet}.
 *
 * @author Manuel Laflamme
 * @since Apr 10, 2003
 * @version $Revision$
 */
public class ForwardOnlyResultSetTable extends AbstractResultSetTable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ForwardOnlyResultSetTable.class);

    private int _lastRow = -1;
    private boolean _eot = false; // End of table flag

    public ForwardOnlyResultSetTable(ITableMetaData metaData,
            ResultSet resultSet) throws SQLException, DataSetException
    {
        super(metaData, resultSet);
    }

    public ForwardOnlyResultSetTable(ITableMetaData metaData,
            IDatabaseConnection connection) throws DataSetException, SQLException
    {
        super(metaData, connection);
    }

    public ForwardOnlyResultSetTable(String tableName, String selectStatement,
            IDatabaseConnection connection) throws DataSetException, SQLException
    {
        super(tableName, selectStatement, connection);
    }

    // //////////////////////////////////////////////////////////////////////////
    // ITable interface

    public int getRowCount()
    {
        throw new UnsupportedOperationException();
    }

    public Object getValue(int row, String columnName) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), columnName);

        try
        {
            // Move cursor forward up to specified row
            while (!_eot && row > _lastRow)
            {
                _eot = !_resultSet.next();
                _lastRow++;
            }

            if (row < _lastRow)
            {
                throw new UnsupportedOperationException("Cannot go backward!");
            }

            if (_eot || row > _lastRow)
            {
                // Proactively close the resultset
                close();
                throw new RowOutOfBoundsException(row + " > " + _lastRow);
            }

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
        sb.append("_eot=[").append(_eot).append("], ");
        sb.append("_lastRow=[").append(_lastRow).append("]");
        sb.append("]");

        return sb.toString();
    }
}
