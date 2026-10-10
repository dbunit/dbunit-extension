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

import java.sql.Connection;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.PrepAndExpectedTestCase;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.dbunit.util.fileloader.FullXmlDataFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test proving the extension leaves alone what an injected
 * {@code @DbUnitTestCase} instance was built with when {@code @DbUnitConfig} says nothing about
 * it. The instance here is built to keep its connection open, as a caller does when the
 * connection comes from a pool or {@code CachingConnectionProvider} that outlives one test;
 * nothing in {@code @DbUnitConfig} asks otherwise, so the connection must still be open
 * afterward. {@link DatabaseEnvironment#getConnection()} silently reopens a closed connection,
 * so this asserts on the very connection object handed to the tester.
 */
class DbUnitExtensionInjectedTestCaseSettingsIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_injectedTestCaseBuiltToKeepItsConnection_isNotMadeToCloseIt()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            environment.deleteAllRows(TEST_TABLE);
            final IDatabaseConnection connection = environment.getConnection();
            KeepsConnectionSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(), new DefaultDatabaseTester(connection), false);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(KeepsConnectionSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(connection.getConnection().isClosed())
                    .as("The instance was built with closeConnectionAfterTest=false and"
                            + " @DbUnitConfig does not set it, so the extension must not turn it"
                            + " back on and close the connection.")
                    .isFalse();
        } finally
        {
            environment.deleteAllRows(TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_injectedTestCaseBuiltToKeepItsConnectionAndAConnectionParameter_isNotMadeToCloseIt()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            KeepsConnectionWithParameterSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(), new DefaultDatabaseTester(connection), false);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(KeepsConnectionWithParameterSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(connection.getConnection().isClosed())
                    .as("The instance was built with closeConnectionAfterTest=false, so the"
                            + " connection the extension injected as a parameter - the"
                            + " instance's own - must stay open too.")
                    .isFalse();
        } finally
        {
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_injectedTestCaseWithItsOwnLoaderAndNoVerifySpec_derivesTablesWithThatLoader()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            OwnLoaderSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FullXmlDataFileLoader(), new DefaultDatabaseTester(connection), false);

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(OwnLoaderSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        } finally
        {
            environment.closeConnection();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class KeepsConnectionSample
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE, include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow() throws Exception
        {
            final IDatabaseConnection connection = testCase.getDatabaseTester().getConnection();
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class KeepsConnectionWithParameterSample
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE, include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow(final Connection connection) throws Exception
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class OwnLoaderSample
    {
        @DbUnitTestCase
        static PrepAndExpectedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-full-prep.xml")
        @DbUnitExpected("annotation-it-full-expected.xml")
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void mutateTheSeededRow() throws Exception
        {
            final IDatabaseConnection connection = testCase.getDatabaseTester().getConnection();
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }
}
