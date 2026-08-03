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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Decorates an operation and executes within the context of a transaction.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 21, 2002
 */
public class TransactionOperation extends DatabaseOperation
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(TransactionOperation.class);

    private final DatabaseOperation _operation;

    /**
     * Creates a TransactionOperation that decorates the specified operation.
     *
     * @param operation the operation to decorate.
     */
    public TransactionOperation(DatabaseOperation operation)
    {
        _operation = operation;
    }

    ////////////////////////////////////////////////////////////////////////////
    // DatabaseOperation class

    public void execute(IDatabaseConnection connection, IDataSet dataSet)
            throws DatabaseUnitException, SQLException
    {
        logger.debug("execute(connection={}, dataSet={}) - start", connection, dataSet);

        IDatabaseConnection databaseConnection = connection;
        Connection jdbcConnection = databaseConnection.getConnection();

        if (jdbcConnection.getAutoCommit() == false)
        {
            throw new ExclusiveTransactionException();
        }

        jdbcConnection.setAutoCommit(false);
        try
        {
            _operation.execute(databaseConnection, dataSet);
            jdbcConnection.commit();
        }
        catch (DatabaseUnitException e)
        {
            handleException(jdbcConnection, e);
            throw e;
        }
        catch (SQLException e)
        {
            handleException(jdbcConnection, e);
            throw e;
        }
        catch (RuntimeException e)
        {
            handleException(jdbcConnection, e);
            throw e;
        }
        catch (Error e)
        {
            handleException(jdbcConnection, e);
            throw e;
        }
        finally
        {
            jdbcConnection.setAutoCommit(true);
        }
    }

    /**
     * Rolls back the given connection, attaching a rollback failure to the
     * given exception as suppressed instead of letting it replace and hide
     * the exception that triggered the rollback.
     *
     * @param jdbcConnection
     *            The connection to roll back.
     * @param e
     *            The exception that triggered the rollback.
     */
    private static void handleException(Connection jdbcConnection, Throwable e)
    {
        try
        {
            jdbcConnection.rollback();
        }
        catch (SQLException rollbackFailure)
        {
            e.addSuppressed(rollbackFailure);
        }
    }
}
