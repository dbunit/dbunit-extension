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
package org.dbunit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class for writing JUnit tests with dbunit easily.
 * <br />
 * Note that there are some even more convenient classes available such
 * as {@link DBTestCase}.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since 1.0 (Feb 17, 2002)
 */
public abstract class DatabaseTestCase implements InvocationInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTestCase.class);

    private IDatabaseTester tester;

    private IOperationListener operationListener;

    private final String name;

    protected DatabaseTestCase() {
        this.name = null;
    }

    protected DatabaseTestCase(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Returns the test database connection.
     */
    protected abstract IDatabaseConnection getConnection() throws Exception;

    /**
     * Returns the test dataset.
     */
    protected abstract IDataSet getDataSet() throws Exception;

    /**
     * Creates a IDatabaseTester for this testCase.<br>
     *
     * A {@link DefaultDatabaseTester} is used by default.
     * @throws Exception
     */
    protected IDatabaseTester newDatabaseTester() throws Exception{
        logger.debug("newDatabaseTester() - start");

        final IDatabaseConnection connection = getConnection();
        getOperationListener().connectionRetrieved(connection);
        final IDatabaseTester tester = new DefaultDatabaseTester(connection);
        return tester;
    }

    /**
     * Designed to be overridden by subclasses in order to set additional configuration
     * parameters for the {@link IDatabaseConnection}.
     * @param config The settings of the current {@link IDatabaseConnection} to be configured
     */
    protected void setUpDatabaseConfig(final DatabaseConfig config)
    {
        // Designed to be overridden.
    }

    /**
     * Gets the IDatabaseTester for this testCase.<br>
     * If the IDatabaseTester is not set yet, this method calls
     * newDatabaseTester() to obtain a new instance.
     * @throws Exception
     */
    protected IDatabaseTester getDatabaseTester() throws Exception {
        if ( this.tester == null ) {
            this.tester = newDatabaseTester();
        }
        return this.tester;
    }

    /**
     * Close the specified connection. Override this method of you want to
     * keep your connection alive between tests.
     * @deprecated since 2.4.4 define a user defined {@link #getOperationListener()} in advance
     */
    @Deprecated
    protected void closeConnection(final IDatabaseConnection connection) throws Exception
    {
        logger.debug("closeConnection(connection={}) - start", connection);

        assertNotNull(getDatabaseTester(), "DatabaseTester is not set" );
        getDatabaseTester().closeConnection( connection );
    }

    /**
     * Returns the database operation executed in test setup.
     */
    protected DatabaseOperation getSetUpOperation() throws Exception
    {
        return DatabaseOperation.CLEAN_INSERT;
    }

    /**
     * Returns the database operation executed in test cleanup.
     */
    protected DatabaseOperation getTearDownOperation() throws Exception
    {
        return DatabaseOperation.NONE;
    }

    ////////////////////////////////////////////////////////////////////////////
    // TestCase class

    protected void setUp() throws Exception
    {
        logger.debug("setUp() - start");

        final IDatabaseTester databaseTester = getDatabaseTester();
        assertNotNull(databaseTester, "DatabaseTester is not set");
        databaseTester.setSetUpOperation(getSetUpOperation());
        databaseTester.setDataSet(getDataSet());
        databaseTester.setOperationListener(getOperationListener());
        databaseTester.onSetup();
    }

    protected void tearDown() throws Exception
    {
        logger.debug("tearDown() - start");

        try {
            final IDatabaseTester databaseTester = getDatabaseTester();
            assertNotNull(databaseTester, "DatabaseTester is not set");
            databaseTester.setTearDownOperation(getTearDownOperation());
            databaseTester.setDataSet(getDataSet());
            databaseTester.setOperationListener(getOperationListener());
            databaseTester.onTearDown();
        } finally {
            tester = null;
        }
    }

    /**
     * @return The {@link IOperationListener} to be used by the {@link IDatabaseTester}.
     * @since 2.4.4
     */
    protected IOperationListener getOperationListener()
    {
        logger.debug("getOperationListener() - start");
        if(this.operationListener==null){
            this.operationListener = new DefaultOperationListener(){
                @Override
                public void connectionRetrieved(final IDatabaseConnection connection) {
                    super.connectionRetrieved(connection);
                    // When a new connection has been created then invoke the setUp method
                    // so that user defined DatabaseConfig parameters can be set.
                    setUpDatabaseConfig(connection.getConfig());
                }
            };
        }
        return this.operationListener;
    }
}
