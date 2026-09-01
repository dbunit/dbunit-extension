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

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test proving an {@code @DbUnitProperty} value actually reaches
 * {@link IDatabaseConnection#getConfig()} on both the setup/teardown path (the {@code
 * IOperationListener} wiring described in the annotations plan's execution model) and the
 * prep/expected path ({@code DefaultPrepAndExpectedTestCase}'s own connection resolution,
 * which never goes through that listener) - neither is something
 * {@code AnnotatedTestExecutorTest}'s mocked connection can prove.
 */
class DbUnitConfigPropertiesIT
{
    @Test
    void testBeforeTestExecution_dbUnitPropertyDeclared_reachesConnectionConfig()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            PropertySample.databaseTester = new DefaultDatabaseTester(connection);
            PropertySample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(PropertySample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(
                    connection.getConfig().getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                            .as("The @DbUnitProperty value must have been applied to the real"
                                    + " connection's DatabaseConfig.")
                            .isTrue();
        } finally
        {
            // The connection is shared/cached across every IT class; discard it rather than
            // leaving the FEATURE_BATCHED_STATEMENTS mutation on it for whichever test runs
            // next.
            environment.closeConnection();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the environment's shared
    // connection (see the finally block above), not one owned by this one test.
    @DbUnitConfig(closeConnectionAfterTest = false,
            properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
    @DbUnitPrep("empty.xml")
    static class PropertySample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void testPropertyIsAppliedBeforeTheTestRuns()
        {
        }
    }

    @Test
    void testBeforeTestExecution_dbUnitPropertyDeclaredOnExpectedPath_reachesConnectionConfig()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final IDatabaseConnection connection = environment.getConnection();
            connection.getConfig().setFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, false);
            ExpectedPathPropertySample.databaseTester = new DefaultDatabaseTester(connection);
            ExpectedPathPropertySample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExpectedPathPropertySample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(
                    connection.getConfig().getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS))
                            .as("The @DbUnitProperty value must reach the connection"
                                    + " DefaultPrepAndExpectedTestCase actually uses for"
                                    + " setupData()/verifyData()/cleanupData() - the"
                                    + " setup/teardown path's IOperationListener wiring is never"
                                    + " triggered by those methods.")
                            .isTrue();
        } finally
        {
            // The connection is shared/cached across every IT class; discard it rather than
            // leaving the FEATURE_BATCHED_STATEMENTS mutation on it for whichever test runs
            // next.
            environment.closeConnection();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the environment's shared
    // connection (see the finally block above), not one owned by this one test.
    @DbUnitConfig(closeConnectionAfterTest = false,
            properties = @DbUnitProperty(name = "batchedStatements", value = "true"))
    @DbUnitExpected("empty.xml")
    static class ExpectedPathPropertySample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        void testPropertyIsAppliedBeforeVerification()
        {
        }
    }
}
