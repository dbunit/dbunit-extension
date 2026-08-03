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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;

import java.sql.SQLException;

/**
 * {@link IStatementFactory} that creates {@link SimplePreparedStatement} instances.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 20, 2002
 */
public class PreparedStatementFactory extends AbstractStatementFactory
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(PreparedStatementFactory.class);

    public IBatchStatement createBatchStatement(IDatabaseConnection connection)
            throws SQLException
    {

    	logger.debug("createBatchStatement(connection={}) - start", connection);

        if (supportBatchStatement(connection))
        {
            return new BatchStatement(connection.getConnection());
        }
        else
        {
            return new SimpleStatement(connection.getConnection());
        }
    }

    public IPreparedBatchStatement createPreparedBatchStatement(String sql,
            IDatabaseConnection connection) throws SQLException
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("createPreparedBatchStatement(sql={}, connection={}) - start", sql, connection);
    	}
    	
    	Integer batchSize = (Integer)connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);

        IPreparedBatchStatement statement = null;
        if (supportBatchStatement(connection))
        {
            statement = new PreparedBatchStatement(sql, connection.getConnection());
        }
        else
        {
            statement = new SimplePreparedStatement(sql, connection.getConnection());
        }
        return new AutomaticPreparedBatchStatement(statement, batchSize.intValue());
    }
}

