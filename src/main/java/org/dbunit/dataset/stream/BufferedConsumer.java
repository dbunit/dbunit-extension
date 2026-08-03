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
package org.dbunit.dataset.stream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.xml.FlatXmlDataSet;

/**
 * Implementation of {@link IDataSetConsumer} which buffers all data
 * until the {@link #endDataSet()} event occurs.
 * This provides the possibility to append new {@link Column}s on
 * the fly which is needed for the column sensing feature in
 * {@link FlatXmlDataSet}.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
public class BufferedConsumer implements IDataSetConsumer {

	private IDataSetConsumer _wrappedConsumer;
	
	/**
	 * The table which is currently active
	 */
	private TableBuffer _activeTable;
	/**
	 * List that stores all {@link TableBuffer}s in a sorted fashion so that the 
	 * table that was added first will also be flushed first when the {@link IDataSetConsumer}
	 * is finally invoked.
	 */
	private List _tableBuffers = new ArrayList();
	/**
	 * Map that stores the table names as key and the {@link TableBuffer} as value
	 */
	private Map _tableNames = new HashMap();
	
	
	/**
	 * Creates a consumer that buffers all data until {@link #endDataSet()}, then flushes it
	 * to the given wrapped consumer.
	 *
	 * @param wrappedConsumer The consumer that is wrapped
	 */
	public BufferedConsumer(IDataSetConsumer wrappedConsumer)
	{
		if (wrappedConsumer == null) {
			throw new NullPointerException(
					"The parameter '_wrappedConsumer' must not be null");
		}
		this._wrappedConsumer = wrappedConsumer;
	}
	
	public void startDataSet() throws DataSetException 
	{
		this._wrappedConsumer.startDataSet();
	}

	public void endDataSet() throws DataSetException 
	{
	    // Flush out the whole collected dataset
	    
        // Start the table with the final metadata
	    for (Iterator iterator = _tableBuffers.iterator(); iterator.hasNext();) {
	        TableBuffer entry = (TableBuffer) iterator.next();
	        ITableMetaData metaData = (ITableMetaData) entry.getMetaData();
            
	        this._wrappedConsumer.startTable(metaData);
	        
	        List dataRows = (List) entry.getDataRows();
	        for (Iterator dataIterator = dataRows.iterator(); dataIterator.hasNext();) {
	            Object[] rowValues = (Object[]) dataIterator.next();
	            this._wrappedConsumer.row(rowValues);
	        }
            // Clear the row data for this table finally
            dataRows.clear();
	        
	        this._wrappedConsumer.endTable();
        }

	    // Finally notify consumer of the end of this DataSet
		this._wrappedConsumer.endDataSet();
	}

	public void row(Object[] values) throws DataSetException 
	{
		// Just collect/buffer the row
	    this._activeTable.getDataRows().add(values);
	}

	public void startTable(ITableMetaData metaData) throws DataSetException 
	{
		// Do nothing here - we will buffer all data in the "row" method in order to write
		// them in the "endTable" method
	    if(_tableNames.containsKey(metaData.getTableName()))
	    {
	        this._activeTable = (TableBuffer) _tableNames.get(metaData.getTableName());
	        // overwrite the metadata with the new one which potentially contains new columns
	        this._activeTable.setMetaData(metaData);
	    }
	    else
	    {
	        _activeTable = new TableBuffer(metaData);

            _tableBuffers.add(_activeTable);// add to the sorted list
            _tableNames.put(metaData.getTableName(), _activeTable);// add to the name map
	    }
	}

	public void endTable() throws DataSetException 
	{
		if(this._activeTable == null) {
			throw new IllegalStateException("The field _activeMetaData must not be null at this stage");
		}

		Column[] columns = this._activeTable.getMetaData().getColumns();
		int finalColumnCount = columns.length;

		int rowCount = this._activeTable.getDataRows().size();
		// Fill up columns that were potentially missing in this row
		for (int i=0; i < rowCount; i++) {
			// Note that this only works when new columns are always added at the end to the _activeMetaData
			Object[] rowValues = (Object[]) this._activeTable.getDataRows().get(i);
			// If this row has less columns than final metaData, fill it up with "null"s so that it matches the length
			if(rowValues.length < finalColumnCount) {
				Object[] newRowValues = new Object[finalColumnCount];
				// Put in original values and leave all missing columns on "null"
				System.arraycopy(rowValues, 0, newRowValues, 0, rowValues.length);
				this._activeTable.getDataRows().set(i, newRowValues);
			}
		}
	}

	
	private static class TableBuffer
	{
	    private ITableMetaData metaData;
	    private final ArrayList dataRows;
	    
        public TableBuffer(ITableMetaData metaData) {
            this(metaData, new ArrayList());
        }

        public TableBuffer(ITableMetaData metaData, ArrayList dataRows) {
            super();
            this.metaData = metaData;
            this.dataRows = dataRows;
        }

        public ITableMetaData getMetaData() {
            return metaData;
        }

        public void setMetaData(ITableMetaData metaData) {
            this.metaData = metaData;
        }

        public ArrayList getDataRows() {
            return dataRows;
        }

	}
}
