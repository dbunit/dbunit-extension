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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link IBatchStatement} that accumulates plain SQL statements using the JDBC
 * {@code addBatch()}/{@code executeBatch()} mechanism.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 20, 2002
 */
public class BatchStatement extends AbstractBatchStatement
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(BatchStatement.class);

    BatchStatement(Connection connection) throws SQLException
    {
        super(connection);
    }

    public void addBatch(String sql) throws SQLException
    {
        logger.debug("addBatch(sql={}) - start", sql);

        _statement.addBatch(sql);
    }

    public int executeBatch() throws SQLException
    {
        logger.debug("executeBatch() - start");

        int[] results = _statement.executeBatch();
        int result = 0;
        for (int i = 0; i < results.length; i++)
        {
            result += results[i];
        }
        return result;
    }

    public void clearBatch() throws SQLException
    {
        logger.debug("clearBatch() - start");

        _statement.clearBatch();
    }

}






