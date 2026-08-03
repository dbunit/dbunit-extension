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
 * Represents a collection of tables.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public interface IDataSet
{
    /**
     * Returns names of tables in this dataset in proper sequence. Multiple
     * occurrence of the same name may be returned if multiple tables having
     * the same name are present in the dataset.
     *
     * @return the table names, in proper sequence.
     * @throws DataSetException if the table names cannot be determined.
     */
    public String[] getTableNames() throws DataSetException;

    /**
     * Returns the specified table metadata.
     *
     * @param tableName the name of the table to look up.
     * @return the metadata of the specified table.
     * @throws org.dbunit.database.AmbiguousTableNameException if dataset contains multiple tables
     *      having the specified name. Use {@link #iterator} to access
     *      to all tables.
     * @throws NoSuchTableException if dataset do not contains the specified
     *      table
     */
    public ITableMetaData getTableMetaData(String tableName)
            throws DataSetException;

    /**
     * Returns the specified table.
     *
     * @param tableName the name of the table to look up.
     * @return the specified table.
     * @throws org.dbunit.database.AmbiguousTableNameException if dataset contains multiple tables
     *      having the specified name. Use {@link #iterator} to access
     *      to all tables.
     * @throws NoSuchTableException if dataset do not contains the specified
     *      table
     */
    public ITable getTable(String tableName) throws DataSetException;

    /**
     * Returns tables in this dataset in proper sequence. Multiple tables having
     * the same name but different data may be returned.
     *
     * @return the tables in this dataset, in proper sequence.
     * @throws DataSetException if the tables cannot be determined.
     * @deprecated Use {@link #iterator} or {@link #reverseIterator} instead.
     */
    public ITable[] getTables() throws DataSetException;

    /**
     * Returns an iterator over the tables in this dataset in proper sequence.
     *
     * @return an iterator over the tables in this dataset.
     * @throws DataSetException if the iterator cannot be created.
     */
    public ITableIterator iterator() throws DataSetException;

    /**
     * Returns an iterator over the tables in this dataset in reverse sequence.
     *
     * @return an iterator over the tables in this dataset, in reverse sequence.
     * @throws DataSetException if the iterator cannot be created.
     */
    public ITableIterator reverseIterator() throws DataSetException;
    
    /**
     * Whether or not this dataset handles table names in a case sensitive way or not.
     * @return <code>true</code> if the case sensitivity of table names is used in this dataset.
     * @since 2.4.2
     */
    public boolean isCaseSensitiveTableNames();

}






