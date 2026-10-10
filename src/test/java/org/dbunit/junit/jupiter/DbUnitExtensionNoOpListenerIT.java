/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
package org.dbunit.junit.jupiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test proving a tester whose {@link IOperationListener} is the
 * no-op listener - the established signal that its connection is managed elsewhere - keeps
 * that connection open on both lifecycle paths, which is what {@code @DbUnitConfig}'s
 * {@code closeConnectionAfterTest} documents. {@link DatabaseEnvironment#getConnection()}
 * silently reopens a closed connection, so these tests assert on the very connection object
 * handed to the tester instead of re-asking the environment for one.
 */
class DbUnitExtensionNoOpListenerIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_prepExpectedPathWithNoOpTester_leavesTheTestersConnectionOpen()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            ExpectedPathSample.databaseTester = newNoOpTester(connection);

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(ExpectedPathSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(connection.getConnection().isClosed())
                    .as("The prep/expected path must not close a connection whose tester has the"
                            + " no-op listener, because that connection is managed elsewhere.")
                    .isFalse();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_setupTeardownPathWithNoOpTester_leavesTheTestersConnectionOpen()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            SetupTeardownPathSample.databaseTester = newNoOpTester(connection);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(SetupTeardownPathSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(connection.getConnection().isClosed())
                    .as("The setup/teardown path must not close a connection whose tester has"
                            + " the no-op listener, because that connection is managed"
                            + " elsewhere.")
                    .isFalse();
        } finally
        {
            environment.closeConnection();
        }
    }

    private static IDatabaseTester newNoOpTester(final IDatabaseConnection connection)
    {
        final IDatabaseTester tester = new DefaultDatabaseTester(connection);
        tester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        return tester;
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitPrep("annotation-it-prep.xml")
    @DbUnitExpected(value = "annotation-it-expected.xml",
            verify = @DbUnitVerifyTable(value = TEST_TABLE, include = {"COLUMN0", "COLUMN1"}))
    @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
    static class ExpectedPathSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void mutateTheSeededRow() throws Exception
        {
            final IDatabaseConnection connection = databaseTester.getConnection();
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitPrep("empty.xml")
    static class SetupTeardownPathSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void doNothing()
        {
        }
    }
}
