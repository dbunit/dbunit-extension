/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;

/**
 * This class is a wrapper for another table with the condition that only a subset
 * of the original table will be available - the subset is defined by the set of 
 * primary keys that are allowed in the new table.
 * 
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Sep 9, 2005
 */
public class PrimaryKeyFilteredTableWrapper implements ITable {
  
  /** reference to the original table being wrapped */
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
  private final List filteredRowsMapping;  
  /** logger */
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  
  /**
   * Creates a PKFilteredTable given an original table and the allowed primary keys
   * for that table.
   * @param table original table
   * @param allowedPKs primary keys allowed on the new table
   * @throws DataSetException if something happened while getting the information
   */
  public PrimaryKeyFilteredTableWrapper(ITable table, Set allowedPKs) throws DataSetException {
    if ( table == null || allowedPKs == null ) {
      throw new IllegalArgumentException( "Constructor cannot receive null arguments" );
    }
    this.originalTable = table;
    // sets the rows for the new table
    // NOTE: this conversion might be an issue for long tables, as it iterates for 
    // all values of the original table and that might take time and memory leaks.
    // So, this mapping mechanism is a candidate for improvement: another alternative
    // would be to calculate the mapping on the fly, as getValue() is called (and in
    // this case, getRowCount() would be simply the sise of allowedPKs)
    this.filteredRowsMapping = setRows( allowedPKs );
  }

  /**
   * This method is used to calculate the mapping between the rows of the original
   * and the filtered tables. 
   * @param allowedPKs primary keys allowed in the new table
   * @return list of rows for the new table
   * @throws DataSetException
   */
  private List setRows(Set allowedPKs) throws DataSetException {
    if ( this.logger.isDebugEnabled() ) {
      this.logger.debug( "Setting rows for table " + 
          this.originalTable.getTableMetaData().getTableName() );
    }
    int allowedSize = allowedPKs.size();
    int fullSize = this.originalTable.getRowCount();
    List mapping = new ArrayList( allowedSize );
    // TODO: support multi-columns PKs
    String pkColumn = this.originalTable.getTableMetaData().getPrimaryKeys()[0].getColumnName();
    for ( int row=0; row<fullSize; row++ ) {
      Object pk = this.originalTable.getValue( row, pkColumn );
      if ( allowedPKs.contains(pk) ) {
        if ( this.logger.isDebugEnabled() ) {
          this.logger.debug( "Adding row " + row + " (pk=" + pk + ")" );
        }
        mapping.add(row);
      } else {
        if ( this.logger.isDebugEnabled() ) {
          this.logger.debug("Discarding row " + row + " (pk=" + pk + ")" );        
        }
      }
    }
    return mapping;   
  }
  
  // ITable methods

  public ITableMetaData getTableMetaData() {
    return this.originalTable.getTableMetaData();
  }

  public int getRowCount() {
    return this.filteredRowsMapping.size();
  }

  public Object getValue(int row, String column) throws DataSetException 
  {
      if(logger.isDebugEnabled())
          logger.debug("getValue(row={}, columnName={}) - start", Integer.toString(row), column);

    int max = this.filteredRowsMapping.size();
    if ( row < max ) {
      int realRow = ((Integer) this.filteredRowsMapping.get( row )).intValue();
      Object value = this.originalTable.getValue(realRow, column);
      return value;
    } else {
      throw new RowOutOfBoundsException( "tried to access row " + row + 
          " but rowCount is " + max );
    }
  }

}
