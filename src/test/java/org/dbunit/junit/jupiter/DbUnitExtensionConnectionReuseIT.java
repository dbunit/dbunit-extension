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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.DefaultPrepAndExpectedTestCase;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitRowCountCheck;
import org.dbunit.annotation.DbUnitTestCase;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.util.fileloader.FlatXmlDataFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database (hsqldb) integration test of an annotation-driven test under the
 * exact combination that broke a downstream build three separate ways (issues #962/#964/#965):
 * a single {@link DefaultPrepAndExpectedTestCase} reused across test methods through a
 * {@code @DbUnitTestCase} field, its tester built over a shared {@link CachingConnectionProvider},
 * itself built with {@code closeConnectionAfterTest = false} so the connection is pinned across
 * methods, and {@code @DbUnitRowCountCheck} active - then the pinned connection is force-closed
 * between two methods, as a pool max-lifetime reap or a database idle-in-transaction timeout
 * would do.
 *
 * <p>The mocked tester in {@code AnnotatedTestExecutorTest} and the direct-drive
 * {@code DatabaseTesterConnectionReuseIT} (which never goes through {@link DbUnitExtension}) cannot
 * prove the annotation layer stacked on top of that machinery also recovers. This does:
 * every repetition after the kill must re-acquire a live connection rather than fail on the dead
 * one, and every repetition's teardown operation must have actually committed.
 *
 * <p>Matrix rows 8 + G-c5 (see {@code plan-docs/annotation-branch-connection-matrix.adoc}).
 */
class DbUnitExtensionConnectionReuseIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_reusedInjectedTestCaseWhoseCachedConnectionDiesMidRun_reacquiresAndKeepsPassing()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        deleteAllRowsQuietly(environment);
        ReusedInstanceSample.testCase = newTestCase(environment.getProfile(), provider);
        ReusedInstanceSample.reset();
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ReusedInstanceSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(5).succeeded(5));

            final List<Connection> seen = ReusedInstanceSample.rawConnectionsSeen;
            assertThat(seen)
                    .as("Every one of the 5 repetitions must have run, including the three"
                            + " after the pinned connection was force-closed between #2 and"
                            + " #3 - proving the annotation path re-acquires a dropped"
                            + " connection (#962) rather than reusing the dead one or failing"
                            + " in preTest()/setupData().")
                    .hasSize(5);
            assertThat(seen.subList(0, 2))
                    .as("Repetitions before the kill share the one connection the"
                            + " CachingConnectionProvider cached and closeConnectionAfterTest=false"
                            + " pinned.")
                    .containsOnly(seen.get(0));
            assertThat(seen.subList(2, 5))
                    .as("Repetitions after the kill share a single replacement connection - one"
                            + " re-acquisition, not a fresh connection per method.")
                    .containsOnly(seen.get(2));
            assertThat(seen.get(0))
                    .as("The replacement must be a genuinely different physical connection, not"
                            + " the dead one handed back again.")
                    .isNotSameAs(seen.get(2));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("Each repetition's DELETE_ALL teardown operation must have"
                            + " committed:"
                            + " a separate connection must see TEST_TABLE empty after the run,"
                            + " not the rows a never-committed teardown would leave behind"
                            + " (#965).")
                    .isZero();
        } finally
        {
            deleteAllRowsQuietly(environment);
            provider.close();
            environment.closeConnection();
        }
    }

    /**
     * Builds the one instance the sample reuses for the whole class, with everything dbUnit
     * uses it as built: its loader, its tester over the shared provider with the teardown
     * operation, and the pinned connection.
     */
    private static DefaultPrepAndExpectedTestCase newTestCase(final DatabaseProfile profile,
            final CachingConnectionProvider provider) throws Exception
    {
        final IDatabaseTester tester = new JdbcDatabaseTester(profile.getDriverClass(),
                profile.getConnectionUrl(), profile.getUser(), profile.getPassword(),
                profile.getSchema(), provider);
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        return new DefaultPrepAndExpectedTestCase(new FlatXmlDataFileLoader(), tester, false);
    }

    private static int rowCount(final IDatabaseConnection connection, final String tableName)
            throws Exception
    {
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + tableName))
        {
            resultSet.next();
            return resultSet.getInt(1);
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

    @ExtendWith(DbUnitExtension.class)
    @ClearRowCountCheckSystemProperties
    @DbUnitRowCountCheck
    static class ReusedInstanceSample
    {
        static final List<Connection> rawConnectionsSeen = new ArrayList<>();
        static int repetitionsCompleted;

        // One instance for the whole class - the reuse-across-methods scenario #962 is about.
        // It carries its own tester, so all repetitions share the one tester and the one
        // CachingConnectionProvider behind it.
        @DbUnitTestCase
        static DefaultPrepAndExpectedTestCase testCase;

        static void reset()
        {
            rawConnectionsSeen.clear();
            repetitionsCompleted = 0;
        }

        @RepeatedTest(5)
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        void mutateTheSeededRow() throws Exception
        {
            final IDatabaseConnection connection = reusableConnection();
            rawConnectionsSeen.add(connection.getConnection());
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }

        @AfterEach
        void dropTheCachedConnectionBetweenTheSecondAndThirdRepetition() throws Exception
        {
            repetitionsCompleted++;
            if (repetitionsCompleted == 2)
            {
                // The 2nd repetition has fully finished - its @DbUnitExpected verify, the
                // DELETE_ALL teardown operation and @DbUnitRowCountCheck all ran. Now the
                // pool or the server drops the idle connection, exactly as an Agroal max-lifetime
                // reap or a PostgreSQL idle_in_transaction_session_timeout would.
                reusableConnection().getConnection().close();
            }
        }

        private static IDatabaseConnection reusableConnection() throws Exception
        {
            return testCase.getReusableConnection();
        }
    }
}
