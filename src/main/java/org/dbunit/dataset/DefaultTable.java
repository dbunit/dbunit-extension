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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default table implementation backed by a simple java in-memory list.
 * 
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public class DefaultTable extends AbstractTable
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultTable.class);

    private final ITableMetaData _metaData;
    private final List _rowList;

    /**
     * Creates a new empty table with specified metadata and values.
     * @deprecated Use public mutators to initialize table values instead
     */
    public DefaultTable(ITableMetaData metaData, List list)
    {
        _metaData = metaData;
        _rowList = list;
    }

    /**
     * Creates a new empty table having the specified name.
     */
    public DefaultTable(String tableName)
    {
        _metaData = new DefaultTableMetaData(tableName, new Column[0]);
        _rowList = new ArrayList();
    }

    /**
     * Creates a new empty table with specified metadata and values.
     * @deprecated Use public mutators to initialize table values instead
     */
    public DefaultTable(String tableName, Column[] columns, List list)
    {
        _metaData = new DefaultTableMetaData(tableName, columns);
        _rowList = list;
    }

    /**
     * Creates a new empty table with specified metadata.
     */
    public DefaultTable(String tableName, Column[] columns)
    {
        _metaData = new DefaultTableMetaData(tableName, columns);
        _rowList = new ArrayList();
    }

    public DefaultTable(ITableMetaData metaData)
    {
        _metaData = metaData;
        _rowList = new ArrayList();
    }

    /**
     * Inserts a new empty row. You can add values with {@link #setValue}.
     */
    public void addRow() throws DataSetException
    {
        logger.debug("addRow() - start");

        int columnCount = _metaData.getColumns().length;
        _rowList.add(new Object[columnCount]);
    }

    /**
     * Inserts a new row initialized with specified array of values.
     * @param values The array of values. Each value correspond to the column at the
     * same index from {@link ITableMetaData#getColumns}.
     * @see #getTableMetaData
     */
    public void addRow(Object[] values) throws DataSetException
    {
        logger.debug("addRow(values={}) - start", values);

        _rowList.add(values);
    }

    /**
     * Inserts all rows from the specified table.
     * @param table The source table.
     */
    public void addTableRows(ITable table) throws DataSetException
    {
        logger.debug("addTableRows(table={}) - start", table);

        try
        {
            Column[] columns = _metaData.getColumns();
            if (columns.length <= 0)
            {
            	logger.warn("The table '" + table + "' does not have any columns. Cannot add table rows. This should never happen...");
            	return;
            }
            
            for (int i = 0; ; i++)
            {
                Object[] rowValues = new Object[columns.length];
                for (int j = 0; j < columns.length; j++)
                {
                    Column column = columns[j];
                    rowValues[j] = table.getValue(i, column.getColumnName());
                }
                _rowList.add(rowValues);
            }
        }
        catch(RowOutOfBoundsException e)
        {
            // end of table
        	// ignore error.
        }
    }

    /**
     * Replaces the value at the specified position in this table with the specified value.
     * @param row The row index
     * @param column The column name
     * @param value The value to store at the specified location
     * @return the value previously at the specified location
     * @throws RowOutOfBoundsException if the row index is out of range
     * @throws NoSuchColumnException if the column does not exist
     * @throws DataSetException if an unexpected error occurs
     */
    public Object setValue(int row, String column, Object value)
            throws RowOutOfBoundsException, NoSuchColumnException, DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("setValue(row={}, column={}, value={}) - start", row, column, value);

        assertValidRowIndex(row);

        Object[] rowValues = (Object[])_rowList.get(row);
        int columnIndex = getColumnIndex(column);
        Object oldValue = rowValues[columnIndex];
        rowValues[columnIndex] = value;
        return oldValue;
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITable interface

    public ITableMetaData getTableMetaData()
    {
        return _metaData;
    }

    public int getRowCount()
    {
        return _rowList.size();
    }

    public Object getValue(int row, String column) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, column={}) - start", Integer.toString(row), column);

        assertValidRowIndex(row);

        Object[] rowValues = (Object[])_rowList.get(row);
        return rowValues[getColumnIndex(column)];
    }

    public String toString()
    {
    	final StringBuilder sb = new StringBuilder();
    	sb.append(getClass().getName()).append("[");
    	sb.append("_metaData=").append(this._metaData == null ? "null" : this._metaData.toString());
    	sb.append(", _rowList.size()=").append(this._rowList == null ? "null" : ""+this._rowList.size());
    	sb.append("]");
    	return sb.toString();
    }
}





