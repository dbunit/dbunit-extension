/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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
package org.dbunit;

import java.sql.SQLException;

import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the {@link IOperationListener}.
 * 
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.4
 */
public class DefaultOperationListener implements IOperationListener{
    
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultOperationListener.class);

    
    public void connectionRetrieved(IDatabaseConnection connection) {
        logger.debug("connectionCreated(connection={}) - start", connection);
        // Is by default a no-op
    }

    public void operationSetUpFinished(IDatabaseConnection connection) {
        logger.debug("operationSetUpFinished(connection={}) - start", connection);
        closeConnection(connection);
    }

    public void operationTearDownFinished(IDatabaseConnection connection) {
        logger.debug("operationTearDownFinished(connection={}) - start", connection);
        closeConnection(connection);
    }

    private void closeConnection(IDatabaseConnection connection)
    {
        logger.debug("closeConnection(connection={}) - start",connection);
        try {
            connection.close();
        } catch (SQLException e) {
            logger.warn("Exception while closing the connection: " + e, e);
        }
    }
    
}