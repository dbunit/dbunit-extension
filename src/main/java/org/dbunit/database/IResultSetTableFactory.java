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

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Factory that creates {@link IResultSetTable} instances for a given query.
 *
 * @author manuel.laflamme
 * @since Jul 17, 2003
 * @version $Revision$
 */
public interface IResultSetTableFactory
{
    /**
     * Creates a table from a table name and a SQL select statement.
     *
     * @param tableName the table name.
     * @param selectStatement the SQL select statement.
     * @param connection the database connection.
     * @return The table based on the SQL result set.
     * @throws SQLException if executing the query fails.
     * @throws DataSetException if metadata retrieval fails.
     */
    public IResultSetTable createTable(String tableName, String selectStatement,
            IDatabaseConnection connection) throws SQLException, DataSetException;

    /**
     * Creates a table from a metadata descriptor and a connection.
     *
     * @param metaData the table metadata.
     * @param connection the database connection.
     * @return The table based on the SQL result set.
     * @throws SQLException if executing the query fails.
     * @throws DataSetException if metadata retrieval fails.
     */
    public IResultSetTable createTable(ITableMetaData metaData,
            IDatabaseConnection connection) throws SQLException, DataSetException;

    /**
     * Creates a table from a preparedStatement
     * @param tableName the table name.
     * @param preparedStatement the prepared statement to execute.
     * @param connection the database connection.
     * @return The table based on a SQL result set
     * @throws SQLException if executing the statement fails.
     * @throws DataSetException if metadata retrieval fails.
     * @since 2.4.4
     */
    public IResultSetTable createTable(String tableName, PreparedStatement preparedStatement,
            IDatabaseConnection connection) throws SQLException, DataSetException;


}
