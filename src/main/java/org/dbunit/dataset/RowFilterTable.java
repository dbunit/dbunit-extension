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

import org.dbunit.dataset.filter.IRowFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters table rows by using arbitrary column values of the table to check if a row should be filtered or not.
 * <br>
 * Implemented as a decorator for {@link ITable}.
 * 
 * See dbunit feature request at <a href="https://sourceforge.net/tracker/index.php?func=detail&aid=1959771&group_id=47439&atid=449494">#1959771</a>
 * 
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class RowFilterTable implements ITable, IRowValueProvider {

	
	/** 
	 * reference to the original table being wrapped 
	 */
	private final ITable originalTable;
	/** mapping of filtered rows, i.e, each entry on this list has the value of 
            the index on the original table corresponding to the desired index. 
            For instance, if the original table is:
            row   PK  Value
            0     pk1  v1
            1     pk2  v2
            2     pk3  v3
            3     pk4  v4
            And the allowed PKs are pk2 and pk4, the new table should be:
            row   PK  Value
            0     pk2  v2
            1     pk4  v4
            Consequently, the mapping will be {1, 3}
	 */
	private final List filteredRowIndexes;
	/** 
	 * logger 
	 */
	private final Logger logger = LoggerFactory.getLogger(RowFilterTable.class);
	/** 
	 * The row that is currently checked for filtering. Used in the implementation of {@link IRowValueProvider}
	 */
	private int currentRowIdx;

	/**
	 * Creates a new {@link ITable} where some rows can be filtered out from the original table
	 * @param table The table to be wrapped
	 * @param rowFilter The row filter that checks for every row whether or not it should be filtered
	 * @throws DataSetException
	 */
	public RowFilterTable(ITable table, IRowFilter rowFilter) throws DataSetException {
		if ( table == null || rowFilter == null ) {
			throw new IllegalArgumentException( "Constructor cannot receive null arguments" );
		}
		this.originalTable = table;
		// sets the rows for the new table
		// NOTE: this conversion might be an issue for long tables, as it iterates for 
		// all values of the original table and that might take time and memory leaks.
		// So, this mapping mechanism is a candidate for improvement: another alternative
		// would be to calculate the mapping on the fly, as getValue() is called (and in
		// this case, getRowCount() would be simply the size of allowedPKs)
		this.filteredRowIndexes = setRows(rowFilter);
	}

	private List setRows(IRowFilter rowFilter) throws DataSetException {

		ITableMetaData tableMetadata = this.originalTable.getTableMetaData();
		this.logger.debug("Setting rows for table {}",  tableMetadata.getTableName() );

		int fullSize = this.originalTable.getRowCount();
		List filteredRowIndexes = new ArrayList();

		for ( int row=0; row<fullSize; row++ ) {
			this.currentRowIdx = row;
			if(rowFilter.accept(this)) {
				this.logger.debug("Adding row {}", row);
				filteredRowIndexes.add(row);
			} else {
				this.logger.debug("Discarding row {}", row);
			}
		}
		return filteredRowIndexes;   
	}


	// ITable methods

	public ITableMetaData getTableMetaData() {
		logger.debug("getTableMetaData() - start");

		return this.originalTable.getTableMetaData();
	}

	public int getRowCount() {
		logger.debug("getRowCount() - start");

		return this.filteredRowIndexes.size();
	}

	public Object getValue(int row, String column) throws DataSetException 
	{
	    if(logger.isDebugEnabled())
	        logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), column);

		int max = this.filteredRowIndexes.size();
		if ( row < max ) {
			int realRow = ((Integer) this.filteredRowIndexes.get( row )).intValue();
			Object value = this.originalTable.getValue(realRow, column);
			return value;
		} else {
			throw new RowOutOfBoundsException( "tried to access row " + row + 
					" but rowCount is " + max );
		}
	}


	/**
	 * Returns the column value for the column with the given name of the currently processed row
	 * @throws DataSetException 
	 * @see org.dbunit.dataset.IRowValueProvider#getColumnValue(java.lang.String)
	 */
	public Object getColumnValue(String columnName) throws DataSetException {
		Object valueOfCol = this.originalTable.getValue(this.currentRowIdx, columnName);
		return valueOfCol;
	}


}


