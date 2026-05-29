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
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;

import java.sql.SQLException;

/**
 * Truncate tables present in the specified dataset. If the dataset does not
 * contains a particular table, but that table exists in the database,
 * the database table is not affected. Table are truncated in
 * reverse sequence.
 * <p>
 * This operation has the same effect of as {@link DeleteAllOperation}.
 * TruncateTableOperation is faster, and it is non-logged, meaning it cannot be
 * rollback. DeleteAllOperation is more portable because not all database vendor
 * support TRUNCATE_TABLE TABLE statement.
 *
 * @author Manuel Laflamme
 * @since Apr 10, 2003
 * @version $Revision$
 * @see DeleteAllOperation
 */
public class TruncateTableOperation extends DeleteAllOperation
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(TruncateTableOperation.class);

    TruncateTableOperation()
    {
    }

    ////////////////////////////////////////////////////////////////////////////
    // DeleteAllOperation class

    protected String getDeleteAllCommand()
    {
        return "truncate table ";
    }

    @Override
    protected String getDeleteAllCommandSuffix(IDatabaseConnection connection) throws SQLException
    {
        java.sql.Connection jdbcConnection = connection.getConnection();
        if (jdbcConnection == null)
        {
            return "";
        }
        String productName = jdbcConnection.getMetaData().getDatabaseProductName();
        if (productName != null && productName.toLowerCase().startsWith("db2"))
        {
            return " IMMEDIATE";
        }
        return "";
    }

    ////////////////////////////////////////////////////////////////////////////
    // DatabaseOperation class

    public void execute(IDatabaseConnection connection, IDataSet dataSet)
            throws DatabaseUnitException, SQLException
    {
        logger.debug("execute(connection={}, dataSet={}) - start", connection, dataSet);

        // Patch to make it work with MS SQL Server
        DatabaseConfig config = connection.getConfig();
        boolean oldValue = config.getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS);
        try
        {
            config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            super.execute(connection, dataSet);
        }
        finally
        {
            config.setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, oldValue);
        }
    }
}
