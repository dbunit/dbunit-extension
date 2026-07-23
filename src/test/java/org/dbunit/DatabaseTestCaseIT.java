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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
class DatabaseTestCaseIT
{

    @Test
    void testTearDownExceptionDoesNotObscureTestException() throws Exception
    {
        final IDatabaseConnection conn =
                DatabaseEnvironment.getInstance().getConnection();

        final SQLException tearDownFailure =
                new SQLException("Simulated tear-down operation failure.");
        final DatabaseOperation failingTearDownOperation =
                new DatabaseOperation()
                {
                    @Override
                    public void execute(final IDatabaseConnection connection,
                            final IDataSet dataSet) throws SQLException
                    {
                        throw tearDownFailure;
                    }
                };

        final DatabaseTestCase testSubject =
                makeTestSubject(conn, failingTearDownOperation);
        testSubject.setUp();

        final RuntimeException testFailure =
                new RuntimeException("Simulated test body failure.");

        final Throwable propagated =
                catchThrowable(() -> testSubject.tearDown(testFailure));

        assertThat(propagated)
                .as("The original test failure must propagate, not the tear-down failure.")
                .isSameAs(testFailure);
        assertThat(propagated.getSuppressed())
                .as("The tear-down failure must be attached as suppressed, not lost.")
                .containsExactly(tearDownFailure);
    }

    @Test
    void testTearDownErrorDoesNotObscureTestException() throws Exception
    {
        final IDatabaseConnection conn =
                DatabaseEnvironment.getInstance().getConnection();

        final AssertionError tearDownFailure = new AssertionError(
                "Simulated tear-down operation failure (an Error, not an Exception).");
        final DatabaseOperation failingTearDownOperation =
                new DatabaseOperation()
                {
                    @Override
                    public void execute(final IDatabaseConnection connection,
                            final IDataSet dataSet)
                    {
                        throw tearDownFailure;
                    }
                };

        final DatabaseTestCase testSubject =
                makeTestSubject(conn, failingTearDownOperation);
        testSubject.setUp();

        final RuntimeException testFailure =
                new RuntimeException("Simulated test body failure.");

        final Throwable propagated =
                catchThrowable(() -> testSubject.tearDown(testFailure));

        assertThat(propagated)
                .as("The original test failure must propagate, not the tear-down Error.")
                .isSameAs(testFailure);
        assertThat(propagated.getSuppressed())
                .as("The tear-down Error must be attached as suppressed, not lost.")
                .containsExactly(tearDownFailure);
    }

    private DatabaseTestCase makeTestSubject(final IDatabaseConnection conn,
            final DatabaseOperation tearDownOperation)
    {
        return new DatabaseTestCase()
        {
            @Override
            protected IDatabaseConnection getConnection() throws Exception
            {
                return conn;
            }

            @Override
            protected IDataSet getDataSet() throws Exception
            {
                return null;
            }

            @Override
            protected DatabaseOperation getSetUpOperation() throws Exception
            {
                return DatabaseOperation.NONE;
            }

            @Override
            protected DatabaseOperation getTearDownOperation() throws Exception
            {
                return tearDownOperation;
            }
        };
    }

    /**
     * Tests whether the user can simply change the {@link DatabaseConfig} by
     * overriding the method
     * {@link DatabaseTestCase#setUpDatabaseConfig(DatabaseConfig)}.
     * 
     * @throws Exception
     */
    @Test
    void testConfigureConnection_withCustomBatchSize_setsConfigPropertyOnConnection() throws Exception
    {
        final DatabaseEnvironment dbEnv = DatabaseEnvironment.getInstance();
        final IDatabaseConnection conn = dbEnv.getConnection();

        final DatabaseTestCase testSubject = new DatabaseTestCase()
        {

            /**
             * method under test
             */
            @Override
            protected void setUpDatabaseConfig(final DatabaseConfig config)
            {
                config.setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE,
                        Integer.valueOf(97));
            }

            @Override
            protected IDatabaseConnection getConnection() throws Exception
            {
                return conn;
            }

            @Override
            protected IDataSet getDataSet() throws Exception
            {
                return null;
            }

            @Override
            protected DatabaseOperation getSetUpOperation() throws Exception
            {
                return DatabaseOperation.NONE;
            }

            @Override
            protected DatabaseOperation getTearDownOperation() throws Exception
            {
                return DatabaseOperation.NONE;
            }
        };

        // Simulate JUnit which first of all calls the "setUp" method
        testSubject.setUp();

        final IDatabaseConnection actualConn = testSubject.getConnection();
        assertThat(actualConn.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));

        final IDatabaseConnection actualConn2 =
                testSubject.getDatabaseTester().getConnection();
        assertThat(actualConn2.getConfig()
                .getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE))
                        .isEqualTo(Integer.valueOf(97));
    }
}
