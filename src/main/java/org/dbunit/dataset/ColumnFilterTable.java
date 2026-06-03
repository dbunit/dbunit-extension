/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import org.dbunit.dataset.filter.IColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A table that filters some columns out from the original table.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class ColumnFilterTable implements ITable 
{
    /** 
     * logger 
     */
    private final Logger logger = LoggerFactory.getLogger(ColumnFilterTable.class);

    /** 
     * reference to the original table being wrapped 
     */
    private final ITable originalTable;
    
    /**
     * The filtered table metadata
     */
    private final ITableMetaData tableMetaData;

    
    /**
     * @param table The table from which some columns should be filtered
     * @param columnFilter The filter defining which columns to be filtered
     * @throws DataSetException
     */
    public ColumnFilterTable(ITable table, IColumnFilter columnFilter) 
    throws DataSetException
    {
        if (columnFilter == null) {
            throw new NullPointerException(
                    "The parameter 'columnFilter' must not be null");
        }
        if (table == null) {
            throw new NullPointerException(
                    "The parameter 'table' must not be null");
        }
        
        this.tableMetaData = new FilteredTableMetaData(
                table.getTableMetaData(), columnFilter);
        this.originalTable = table;
    }

    
    public int getRowCount() 
    {
        logger.debug("getRowCount() - start");
        return this.originalTable.getRowCount();
    }

    public ITableMetaData getTableMetaData() 
    {
        logger.debug("getTableMetaData() - start");
        return this.tableMetaData;
    }

    public Object getValue(int row, String column) throws DataSetException 
    {
        if(logger.isDebugEnabled())
            logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), column);

        return this.originalTable.getValue(row, column);
    }

    public ITableMetaData getOriginalMetaData() 
    {
        logger.debug("getOriginalMetaData() - start");
        return this.originalTable.getTableMetaData();
    }

    public String toString() 
    {
    	return this.originalTable.toString();
    }
}
