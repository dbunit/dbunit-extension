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

import org.dbunit.dataset.CachedTable;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link IResultSetTable} that eagerly loads and caches an entire {@link ResultSet} in memory.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 20, 2002
 */
public class CachedResultSetTable extends CachedTable implements IResultSetTable
{
    /**
     * @param metaData the table metadata.
     * @param resultSet the result set to load into memory.
     * @throws SQLException if reading the result set fails.
     * @throws DataSetException if metadata retrieval fails.
     * @deprecated since 2.3.0 prefer direct usage of {@link ForwardOnlyResultSetTable#ForwardOnlyResultSetTable(ITableMetaData, ResultSet)} and then invoke {@link CachedResultSetTable#CachedResultSetTable(IResultSetTable)}
     */
    public CachedResultSetTable(ITableMetaData metaData, ResultSet resultSet)
            throws SQLException, DataSetException
    {
        this(new ForwardOnlyResultSetTable(metaData, resultSet));
    }

    /**
     * @param metaData the table metadata.
     * @param connection the database connection to query.
     * @throws SQLException if executing the query fails.
     * @throws DataSetException if metadata retrieval fails.
     * @deprecated since 2.4.4 prefer direct usage of {@link ForwardOnlyResultSetTable#ForwardOnlyResultSetTable(ITableMetaData, IDatabaseConnection)} and then invoke {@link CachedResultSetTable#CachedResultSetTable(IResultSetTable)}
     */
    public CachedResultSetTable(ITableMetaData metaData,
            IDatabaseConnection connection) throws SQLException, DataSetException
    {
        this(new ForwardOnlyResultSetTable(metaData, connection));
    }

    /**
     * Creates a table that eagerly loads and caches all rows of the given table, then closes it.
     *
     * @param table the source table to load into memory.
     * @throws DataSetException if loading the rows fails.
     * @throws SQLException if reading the underlying result set fails.
     */
    public CachedResultSetTable(IResultSetTable table) throws DataSetException, SQLException
    {
        super(table.getTableMetaData());
        try
        {
            addTableRows(table);
        }
        finally
        {
            table.close();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // IResultSetTable interface

    public void close() throws DataSetException
    {
        // nothing to do, resultset already been closed
    }
}





