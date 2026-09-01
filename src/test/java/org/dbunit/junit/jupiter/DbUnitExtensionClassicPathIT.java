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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database (hsqldb) integration test of the <em>classic</em> {@link DbUnitExtension}
 * path: {@code @ExtendWith(DbUnitExtension.class)} on its own, one plain unannotated
 * {@link IDatabaseTester} field, and a {@code @BeforeEach} method configuring the dataset and
 * the teardown operation - no {@code org.dbunit.annotation} annotation anywhere. This is the
 * 3.5.0 style, still supported after the annotation rewrite routed every path through
 * {@code AnnotatedTestExecutor}; {@code DbUnitExtensionLifecycleTest}'s {@code CallLoggingTester}
 * returns a {@code null} connection, so only a real database proves {@code onSetup()} seeds the
 * {@code @BeforeEach} dataset and {@code onTearDown()} still runs the {@code @BeforeEach}-set
 * operation rather than a reset {@code NONE}.
 */
class DbUnitExtensionClassicPathIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testClassicPath_beforeEachSetsDatasetAndTeardownOperation_seedsThenTearsDown()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        ClassicSample.profile = environment.getProfile();
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassicSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The @BeforeEach method's databaseTester.setTearDownOperation(DELETE_ALL)"
                            + " must survive to onTearDown() - the classic path must not reset the"
                            + " tester's teardown operation to NONE when no @DbUnitTearDown is"
                            + " declared.")
                    .isZero();
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    @Test
    void testClassicPath_fixedConnectionTester_onSetupRunsAgainstAnOpenConnection()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final DatabaseProfile profile = environment.getProfile();
        deleteAllRowsQuietly(environment);

        final Connection fixedConnection = DriverManager.getConnection(
                profile.getConnectionUrl(), profile.getUser(), profile.getPassword());
        FixedConnectionSample.databaseConnection =
                new DatabaseConnection(fixedConnection, profile.getSchema());
        try
        {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(FixedConnectionSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("The classic path must not close a DefaultDatabaseTester's fixed"
                            + " connection before onSetup(): onSetup()'s CLEAN_INSERT then runs"
                            + " against a closed connection and seeds nothing.")
                    .isEqualTo(1);
        } finally
        {
            closeQuietly(fixedConnection);
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
        }
    }

    private static void closeQuietly(final Connection connection)
    {
        try
        {
            if (!connection.isClosed())
            {
                connection.close();
            }
        } catch (final Exception e)
        {
            // best-effort
        }
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
    static class ClassicSample
    {
        static DatabaseProfile profile;

        final IDatabaseTester databaseTester;

        ClassicSample() throws Exception
        {
            databaseTester = new JdbcDatabaseTester(profile.getDriverClass(),
                    profile.getConnectionUrl(), profile.getUser(), profile.getPassword(),
                    profile.getSchema());
        }

        @BeforeEach
        void configureTester() throws Exception
        {
            databaseTester.setDataSet(new FlatXmlDataSetBuilder()
                    .build(getClass().getResource("annotation-it-prep.xml")));
            databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        }

        @Test
        void seededRowIsVisibleBeforeTheTestBody() throws Exception
        {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM "
                            + TEST_TABLE + " WHERE COLUMN0 = 'row0'"))
            {
                resultSet.next();
                assertThat(resultSet.getInt(1))
                        .as("onSetup()'s default CLEAN_INSERT must seed the @BeforeEach dataset"
                                + " before the test body runs on the classic path.")
                        .isEqualTo(1);
            }
        }
    }

    // A DefaultDatabaseTester built from one fixed IDatabaseConnection: getConnection() returns
    // that same object to the row count baseline probe and to onSetup(). The classic path must
    // leave it open through onSetup(), not close it after resolving the (disabled) baseline.

    @ExtendWith(DbUnitExtension.class)
    static class FixedConnectionSample
    {
        static IDatabaseConnection databaseConnection;

        final IDatabaseTester databaseTester;

        FixedConnectionSample()
        {
            databaseTester = new DefaultDatabaseTester(databaseConnection);
        }

        @BeforeEach
        void configureTester() throws Exception
        {
            databaseTester.setDataSet(new FlatXmlDataSetBuilder()
                    .build(getClass().getResource("annotation-it-prep.xml")));
            databaseTester.setTearDownOperation(DatabaseOperation.NONE);
        }

        @Test
        void seedsThroughOnSetup()
        {
            // The seeded row is asserted through a separate connection in the enclosing test -
            // this fixed connection is closed by the enclosing test's cleanup.
        }
    }
}
