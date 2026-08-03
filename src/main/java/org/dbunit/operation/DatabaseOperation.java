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

package org.dbunit.operation;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;

import java.sql.SQLException;

/**
 * Defines the interface contract for operations performed on the database.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public abstract class DatabaseOperation
{
    /**
     * No-op that does nothing to the database.
     * @see DummyOperation
     */
    public static final DatabaseOperation NONE = new DummyOperation();
    /**
     * Updates existing rows matching the dataset's primary keys.
     * @see UpdateOperation
     */
    public static final DatabaseOperation UPDATE = new UpdateOperation();
    /**
     * Inserts the dataset's rows.
     * @see InsertOperation
     */
    public static final DatabaseOperation INSERT = new InsertOperation();
    /**
     * Updates existing rows and inserts rows that do not yet exist (upsert).
     * @see RefreshOperation
     */
    public static final DatabaseOperation REFRESH = new RefreshOperation();
    /**
     * Deletes rows matching the dataset's primary keys.
     * @see DeleteOperation
     */
    public static final DatabaseOperation DELETE = new DeleteOperation();
    /**
     * Deletes all rows of the dataset's tables.
     * @see DeleteAllOperation
     */
    public static final DatabaseOperation DELETE_ALL = new DeleteAllOperation();
    /**
     * Truncates the dataset's tables.
     * @see TruncateTableOperation
     */
    public static final DatabaseOperation TRUNCATE_TABLE = new TruncateTableOperation();
    /**
     * Deletes all rows of the dataset's tables, then inserts the dataset's rows.
     * @see DeleteAllOperation
     * @see InsertOperation
     * @see CompositeOperation
     */
    public static final DatabaseOperation CLEAN_INSERT = new CompositeOperation(
            DELETE_ALL, INSERT);

    /**
     * Wraps the given operation so it runs within its own transaction.
     * @param operation the operation to wrap.
     * @return the wrapped, transactional operation.
     * @see TransactionOperation
     */
    public static final DatabaseOperation TRANSACTION(DatabaseOperation operation) {
        return new TransactionOperation(operation);
    }

    /**
     * Wraps the given operation so it closes the connection after executing.
     * @param operation the operation to wrap.
     * @return the wrapped operation.
     * @see CloseConnectionOperation
     */
    public static final DatabaseOperation CLOSE_CONNECTION(DatabaseOperation operation) {
        return new CloseConnectionOperation(operation);
    }

    /**
     * Executes this operation on the specified database using the specified
     * dataset contents.
     *
     * @param connection the database connection.
     * @param dataSet the dataset to be used by this operation.
     * @throws DatabaseUnitException if a DbUnit-specific error occurs while executing the operation.
     * @throws SQLException if a database access error occurs while executing the operation.
     */
    public abstract void execute(IDatabaseConnection connection,
            IDataSet dataSet) throws DatabaseUnitException, SQLException;

    private static class DummyOperation extends DatabaseOperation
    {
        @Override
        public void execute(IDatabaseConnection connection, IDataSet dataSet)
        {
        }
    }
}
