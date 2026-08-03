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


/**
 * Represents table metadata.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public interface ITableMetaData
{
    /**
     * Returns this table name.
     * @return this table name
     */
    public String getTableName();

    /**
     * Returns this table columns as recognized by dbunit. In cases where columns are resolved 
     * using database metadata it can happen that an empty array is returned when a table does
     * not have a single column that is recognized by the configured 
     * {@link org.dbunit.dataset.datatype.IDataTypeFactory}.
     * Note that it is <b>not</b> an exceptional case within dbunit when a {@link ITableMetaData}
     * does not have a column. 
     * @return The columns for this table
     * @throws DataSetException if the columns cannot be determined.
     */
    public Column[] getColumns() throws DataSetException;

    /**
     * Returns this table primary key columns.
     * @return this table primary key columns.
     * @throws DataSetException if the primary key columns cannot be determined.
     */
    public Column[] getPrimaryKeys() throws DataSetException;

	/**
	 * Returns the column's array index of the column with the given name within this table metadata.
	 * @param columnName The name of the column that is searched
	 * @return The index of the given column within this metadata, starting with 0 for the first column
	 * @throws NoSuchColumnException if the given column has not been found
	 * @throws DataSetException if something goes wrong when trying to retrieve the columns
	 * @since 2.3.0
	 */
	public int getColumnIndex(String columnName) throws DataSetException;
}
