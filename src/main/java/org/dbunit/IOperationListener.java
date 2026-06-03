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

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for {@link IDatabaseConnection} events.
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.4
 */
public interface IOperationListener {

    /**
     * Is invoked immediately after a connection was newly created or an existing
     * connection is retrieved to do some work on it. It should be used to initialize the 
     * {@link DatabaseConfig} of the connection with user defined parameters.
     * @param connection The database connection 
     * @since 2.4.4
     */
    public void connectionRetrieved(IDatabaseConnection connection);
    /**
     * Notification of the completion of the {@link IDatabaseTester#onSetup()} method.
     * Should close the given connection if desired.
     * @param connection The database connection 
     * @since 2.4.4
     */
    public void operationSetUpFinished(IDatabaseConnection connection);
    /**
     * Notification of the completion of the {@link IDatabaseTester#onTearDown()} method
     * Should close the given connection if desired.
     * @param connection The database connection 
     * @since 2.4.4
     */
    public void operationTearDownFinished(IDatabaseConnection connection);

    
    
    /**
     * Simple implementation of the {@link IOperationListener} that does <b>not</b> close
     * the database connection after setUp and tearDown.
     * Can be used via {@link IDatabaseTester#setOperationListener(IOperationListener)} to avoid that connections are closed.
     * @since 2.4.5
     */
    public static final IOperationListener NO_OP_OPERATION_LISTENER = new IOperationListener() 
    {
        private final Logger logger = LoggerFactory.getLogger(IDatabaseTester.class);
        
        public void connectionRetrieved(IDatabaseConnection connection) {
            logger.trace("connectionCreated(connection={}) - start", connection);
        }
        public void operationSetUpFinished(IDatabaseConnection connection) {
            logger.trace("operationSetUpDone(connection={}) - start", connection);
        }
        public void operationTearDownFinished(IDatabaseConnection connection) {
            logger.trace("operationTearDownDone(connection={}) - start", connection);
        }
    };

}
