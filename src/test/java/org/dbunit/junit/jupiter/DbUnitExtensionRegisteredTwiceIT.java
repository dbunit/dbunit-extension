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

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test of {@link DbUnitExtension} registered twice on one test class,
 * once declaratively with {@link DbUnitTest} and once with a {@code @RegisterExtension} field. JUnit
 * Jupiter ignores a duplicate declarative registration but keeps a registered instance, so two
 * extension instances share the one executor of the test: dbUnit must still set up, verify and
 * tear down once, not verify a second time against the database that the teardown emptied, and
 * one instance must resolve an {@link IDatabaseConnection} parameter, not both.
 */
class DbUnitExtensionRegisteredTwiceIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_registeredByDbUnitTestAndAField_runsDbUnitOnce() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            RegisteredTwiceSample.databaseTester = new DefaultDatabaseTester(connection);
            RegisteredTwiceSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(RegisteredTwiceSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_registeredByDbUnitTestOnly_runsDbUnitOnce() throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            RegisteredOnceSample.databaseTester = new DefaultDatabaseTester(connection);
            RegisteredOnceSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(RegisteredOnceSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    private static void deleteAllRowsQuietly(final DatabaseEnvironment environment)
    {
        try (Statement statement =
                environment.getConnection().getConnection().createStatement())
        {
            statement.execute("DELETE FROM " + TEST_TABLE);
        } catch (final Exception e)
        {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    @DbUnitTest
    static class RegisteredOnceSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow() throws Exception
        {
            updateTheSeededRow(databaseTester);
        }
    }

    @DbUnitTest
    static class RegisteredTwiceSample
    {
        @RegisterExtension
        static DbUnitExtension dbUnit = new DbUnitExtension();

        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow(final IDatabaseConnection connection) throws Exception
        {
            updateTheSeededRow(connection);
        }
    }

    private static void updateTheSeededRow(final IDatabaseTester databaseTester) throws Exception
    {
        updateTheSeededRow(databaseTester.getConnection());
    }

    private static void updateTheSeededRow(final IDatabaseConnection connection) throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement())
        {
            statement.execute(
                    "UPDATE " + TEST_TABLE + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
        }
    }
}
