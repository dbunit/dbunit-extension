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

import org.dbunit.database.IDatabaseConnection;

import java.sql.SQLException;

/**
 * Factory that creates the JDBC statement wrapper used to execute a database operation.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public interface IStatementFactory
{
    /**
     * Creates a batch statement for the given connection.
     *
     * @param connection the database connection to create the statement on.
     * @return the new batch statement.
     * @throws SQLException if creating the statement fails.
     */
    IBatchStatement createBatchStatement(IDatabaseConnection connection)
            throws SQLException;

    /**
     * Creates a prepared batch statement for the given SQL and connection.
     *
     * @param sql the SQL statement to prepare.
     * @param connection the database connection to create the statement on.
     * @return the new prepared batch statement.
     * @throws SQLException if creating the statement fails.
     */
    IPreparedBatchStatement createPreparedBatchStatement(String sql,
            IDatabaseConnection connection) throws SQLException;
}





