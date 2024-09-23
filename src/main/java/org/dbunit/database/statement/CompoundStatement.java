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
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 20, 2002 
 */
public class CompoundStatement extends AbstractBatchStatement
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CompoundStatement.class);

    private StringBuilder _buffer = new StringBuilder();

    CompoundStatement(Connection connection) throws SQLException
    {
        super(connection);
    }

    public void addBatch(String sql) throws SQLException
    {
        logger.debug("addBatch(sql={}) - start", sql);

        _buffer.append(sql);
        _buffer.append(";");
    }

    public int executeBatch() throws SQLException
    {
        logger.debug("executeBatch() - start");

        return _statement.executeUpdate(_buffer.toString());
    }

    public void clearBatch() throws SQLException
    {
        logger.debug("clearBatch() - start");

        _buffer = new StringBuilder();
    }
}





