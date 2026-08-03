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
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Abstract base {@link IPreparedBatchStatement} implementation.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public abstract class AbstractPreparedBatchStatement implements IPreparedBatchStatement
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractPreparedBatchStatement.class);

    protected final PreparedStatement _statement;

    AbstractPreparedBatchStatement(String sql, Connection connection)
            throws SQLException
    {
        _statement = connection.prepareStatement(sql);
    }

    public void close() throws SQLException
    {
        logger.debug("close() - start");

        _statement.close();
    }
}
