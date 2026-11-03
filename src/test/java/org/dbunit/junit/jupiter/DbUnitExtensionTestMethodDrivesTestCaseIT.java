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

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseUnitException;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.VerifyTableDefinition;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test of a test method that drives the {@code @DbUnitTestCase}
 * instance itself, with {@code runTest()}, while the extension drives it around the method as
 * well. The instance would run its setup, its verification and its cleanup twice, the second time
 * against a database that the first cleaned up, so the extension cleans up without verifying and
 * fails the test with a message that says what to change, rather than leaving a failure
 * about rows that no code under test removed, or, with a teardown that removes nothing, a test
 * that passes after all its work ran twice.
 */
class DbUnitExtensionTestMethodDrivesTestCaseIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    private static final String[] PREP = {"/org/dbunit/junit/jupiter/annotation-it-prep.xml"};

    private static final String[] EXPECTED =
            {"/org/dbunit/junit/jupiter/annotation-it-expected.xml"};

    @Test
    void testAfterTestExecution_testMethodRunsTheMarkedInstanceAndTeardownDeletesAllRows_failsSayingItWasDrivenTwice()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            environment.deleteAllRows(TEST_TABLE);
            final IDatabaseConnection connection = environment.getConnection();
            RunTestSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(),
                    testerWithTearDown(connection, DatabaseOperation.DELETE_ALL), false);

            final Throwable failure = singleFailureOf(EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(RunTestSample.class)).execute());

            assertThat(failure)
                    .as("The second verification would have failed with a row count of 0 where"
                            + " the expected dataset holds 1; the test fails with the cause"
                            + " instead.")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The test method configured the @DbUnitTestCase"
                            + " instance of " + DefaultPrepAndExpectedTestCase.class.getName()
                            + " again")
                    .hasMessageContaining("remove @DbUnitTestCase from the field");
            assertThat(rowCount(connection))
                    .as("The cleanup ran, so the test leaves no row behind.")
                    .isZero();
        } finally
        {
            environment.deleteAllRows(TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_testMethodRunsTheMarkedInstanceAndTeardownDoesNothing_failsSayingItWasDrivenTwice()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            environment.deleteAllRows(TEST_TABLE);
            final IDatabaseConnection connection = environment.getConnection();
            RunTestSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(),
                    testerWithTearDown(connection, DatabaseOperation.NONE), false);

            final Throwable failure = singleFailureOf(EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(RunTestSample.class)).execute());

            assertThat(failure)
                    .as("With a teardown that removes nothing the test used to pass after its setup,"
                            + " verification and cleanup had all run twice; it fails now.")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The test method configured the @DbUnitTestCase"
                            + " instance of");
        } finally
        {
            environment.deleteAllRows(TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testAfterTestExecution_markedInstanceWhosePrepNamesAMissingTable_failsWithTheSetUpFailureAlone()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            environment.deleteAllRows(TEST_TABLE);
            final IDatabaseConnection connection = environment.getConnection();
            PrepNamesMissingTableSample.testCase = new DefaultPrepAndExpectedTestCase(
                    new FlatXmlDataFileLoader(),
                    testerWithTearDown(connection, DatabaseOperation.DELETE_ALL), false);

            final Throwable failure = singleFailureOf(EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(PrepNamesMissingTableSample.class)).execute());

            assertThat(failure)
                    .as("The set up failed, so the test method never ran and could not have driven"
                            + " the instance: the failure is the set up's, not a complaint about"
                            + " a test method.")
                    .isInstanceOf(DatabaseUnitException.class)
                    .hasMessageContaining("NO_SUCH_TABLE_FOR_IT");
            assertThat(failure.getSuppressed())
                    .as("Nothing blames a test method that did not run.")
                    .noneMatch(suppressed -> String.valueOf(suppressed.getMessage())
                            .contains("configured the @DbUnitTestCase instance"));
        } finally
        {
            environment.deleteAllRows(TEST_TABLE);
            environment.closeConnection();
        }
    }

    private static IDatabaseTester testerWithTearDown(final IDatabaseConnection connection,
            final DatabaseOperation tearDownOperation)
    {
        final IDatabaseTester tester = new DefaultDatabaseTester(connection);
        tester.setTearDownOperation(tearDownOperation);
        return tester;
    }

    private static Throwable singleFailureOf(final EngineExecutionResults results)
    {
        final List<Throwable> failures = results.testEvents().failed().stream()
                .map(event -> event.getPayload(TestExecutionResult.class)
                        .flatMap(TestExecutionResult::getThrowable).orElse(null))
                .collect(Collectors.toList());
        assertThat(failures).as("The one test of the sample fails.").hasSize(1);
        return failures.get(0);
    }

    private static int rowCount(final IDatabaseConnection connection) throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + TEST_TABLE))
        {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class PrepNamesMissingTableSample
    {
        @DbUnitTestCase
        static DefaultPrepAndExpectedTestCase testCase;

        @Test
        @DbUnitPrep("annotation-it-missing-table-prep.xml")
        void neverRuns()
        {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class RunTestSample
    {
        @DbUnitTestCase
        static DefaultPrepAndExpectedTestCase testCase;

        @Test
        void mutateTheSeededRowThroughRunTest() throws Exception
        {
            final VerifyTableDefinition[] definitions = {new VerifyTableDefinition(TEST_TABLE,
                    new String[0], new String[] {"COLUMN0", "COLUMN1"})};

            testCase.runTest(definitions, PREP, EXPECTED, () ->
            {
                final IDatabaseConnection connection =
                        testCase.getDatabaseTester().getConnection();
                try (Statement statement = connection.getConnection().createStatement())
                {
                    statement.execute("UPDATE " + TEST_TABLE
                            + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
                }
                return null;
            });
        }
    }
}
