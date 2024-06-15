/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.sql.SQLException;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author: gommma $
 * @version $Revision: 789 $ $Date: 2008-08-15 16:45:18 +0200 (Fr, 15. Aug 2008)
 * @since 2.4.3
 */
class DBTestCaseIT
{
    /**
     * Tests whether the user can simply change the {@link DatabaseConfig} by
     * overriding the method
     * {@link DatabaseTestCase#setUpDatabaseConfig(DatabaseConfig)}.
     * 
     * @throws Exception
     */
    @Test
    void testConfigureConnection() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection conn = dbEnv.getConnection();
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(conn);
        final DatabaseOperation operation = new DatabaseOperation()
        {
            @Override
            public void execute(final IDatabaseConnection connection,
                    final IDataSet dataSet)
                    throws DatabaseUnitException, SQLException
            {
                assertThat(connection.getConfig()
                        .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                                .isEqualTo(Integer.valueOf(97));
                assertThat(connection.getConfig()
                        .getProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                                .isEqualTo(true);
            }
        };

        final DBTestCase testSubject = new DBTestCase()
        {
            /**
             * method under test
             */
            @Override
            protected void setUpDatabaseConfig(final DatabaseConfig config)
            {
                config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE,
                        Integer.valueOf(97));
                config.setProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS,
                        true);
            }

            @Override
            protected IDatabaseTester newDatabaseTester() throws Exception
            {
                return tester;
            }

            @Override
            protected DatabaseOperation getSetUpOperation() throws Exception
            {
                return operation;
            }

            @Override
            protected DatabaseOperation getTearDownOperation() throws Exception
            {
                return operation;
            }

            @Override
            protected IDataSet getDataSet() throws Exception
            {
                return null;
            }
        };

        // Simulate JUnit which first of all calls the "setUp" method
        testSubject.setUp();

        final IDatabaseConnection actualConn = testSubject.getConnection();
        assertThat(actualConn.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));
        assertSame(conn, actualConn);

        final IDatabaseConnection actualConn2 =
                testSubject.getDatabaseTester().getConnection();
        assertThat(actualConn2.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));
        assertSame(tester, testSubject.getDatabaseTester());
        assertSame(conn, testSubject.getDatabaseTester().getConnection());
    }

    /**
     * Tests the simple setup/teardown invocations while keeping the
     * DatabaseConnection open.
     * 
     * @throws Exception
     */
    @Test
    void testExecuteSetUpTearDown() throws Exception
    {
        // TODO implement this
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        // Retrieve one single connection which is
        final IDatabaseConnection conn = dbEnv.getConnection();
        try
        {
            final DefaultDatabaseTester tester =
                    new DefaultDatabaseTester(conn);
            final IDataSet dataset = new FlatXmlDataSetBuilder()
                    .build(FlatXmlDataSetTest.DATASET_FILE);

            // Connection should not be closed during setUp/tearDown because of
            // userDefined IOperationListener
            final DBTestCase testSubject = new DBTestCase()
            {
                @Override
                protected IDatabaseTester newDatabaseTester() throws Exception
                {
                    return tester;
                }

                @Override
                protected DatabaseOperation getSetUpOperation() throws Exception
                {
                    return DatabaseOperation.CLEAN_INSERT;
                }

                @Override
                protected DatabaseOperation getTearDownOperation()
                        throws Exception
                {
                    return DatabaseOperation.DELETE_ALL;
                }

                @Override
                protected IDataSet getDataSet() throws Exception
                {
                    return dataset;
                }

                @Override
                protected IOperationListener getOperationListener()
                {
                    return new DefaultOperationListener()
                    {
                        @Override
                        public void operationSetUpFinished(
                                final IDatabaseConnection connection)
                        {
                            // Do not invoke the "super" method to avoid that
                            // the connection is closed
                            // Just do nothing
                        }

                        @Override
                        public void operationTearDownFinished(
                                final IDatabaseConnection connection)
                        {
                            // Do not invoke the "super" method to avoid that
                            // the connection is closed
                            // Just do nothing
                        }
                    };
                }
            };

            // Simulate JUnit which first of all calls the "setUp" method
            testSubject.setUp();
            // The connection should still be open so we should be able to
            // select from the DB
            final ITable testTableAfterSetup = conn.createTable("TEST_TABLE");
            assertThat(testTableAfterSetup.getRowCount()).isEqualTo(6);
            assertFalse(conn.getConnection().isClosed());

            // Simulate JUnit and invoke "tearDown"
            testSubject.tearDown();
            // The connection should still be open so we should be able to
            // select from the DB
            final ITable testTableAfterTearDown =
                    conn.createTable("TEST_TABLE");
            assertThat(testTableAfterTearDown.getRowCount()).isEqualTo(0);
            assertFalse(conn.getConnection().isClosed());
        } finally
        {
            // Ensure that the connection is closed again so that
            // it can be established later by subsequent test cases
            dbEnv.closeConnection();
        }
    }
}
