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
import java.sql.Statement;

/**
 * Abstract base {@link IBatchStatement} implementation.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 15, 2002
 */
public abstract class AbstractBatchStatement implements IBatchStatement
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractBatchStatement.class);

    /**
     * The wrapped JDBC statement that batched SQL is accumulated on and
     * executed through.
     */
    protected final Statement _statement;

    AbstractBatchStatement(Connection connection) throws SQLException
    {
        try
        {
            _statement = connection.createStatement();
        }
        catch (SQLException e)
        {
            logger.error("getConnection(): ", e);
            throw e;
        }
    }

    public void close() throws SQLException
    {
        logger.debug("close() - start");

        _statement.close();
    }


}
