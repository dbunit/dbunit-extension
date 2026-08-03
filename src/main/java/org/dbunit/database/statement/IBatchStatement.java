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

package org.dbunit.database.statement;

import java.sql.SQLException;

/**
 * A JDBC statement wrapper that batches SQL statements for execution.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 15, 2002
 */
public interface IBatchStatement
{
    /**
     * Adds a SQL statement to the current batch.
     *
     * @param sql the SQL statement to add.
     * @throws SQLException if a database access error occurs.
     */
    void addBatch(String sql) throws SQLException;

    /**
     * Executes the accumulated batch of SQL statements.
     *
     * @return the number of rows affected, summed across the batch.
     * @throws SQLException if a database access error occurs.
     */
    int executeBatch() throws SQLException;

    /**
     * Discards the accumulated batch of SQL statements without executing them.
     *
     * @throws SQLException if a database access error occurs.
     */
    void clearBatch() throws SQLException;

    /**
     * Closes the underlying JDBC statement.
     *
     * @throws SQLException if a database access error occurs.
     */
    void close() throws SQLException;
}




