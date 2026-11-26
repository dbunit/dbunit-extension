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

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DatabaseEnvironment;
import org.dbunit.IDatabaseTester;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test that the annotation runtime never holds more database
 * connections at once than the 3.5.0 lifecycle did on the classic path: the row count check
 * baseline is taken from the connection {@code onSetup()} opens, which the tester's own listener
 * closes as it always has, so no connection is open while another is, with the row count check
 * enabled or not. A bounded pool sized for the 3.5.0 lifecycle, one connection, suffices.
 *
 * <p>Uses {@link CountingDataSource} with a hard cap so a runtime that tries to hold too many
 * fails fast rather than deadlocking. Matrix rows 1 & 2 under pool pressure (G-c3).
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionBoundedPoolIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_classicPathRowCountCheckOnPoolOfOne_succeedsHoldingAtMostOne()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource pool = new CountingDataSource(environment.getProfile(), 1);
        deleteAllRowsQuietly(environment);
        try
        {
            ClassicRowCountCheckSample.pool = pool;
            ClassicRowCountCheckSample.schema = environment.getProfile().getSchema();

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassicRowCountCheckSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(pool.peakConcurrent())
                    .as("The classic path with the row count check enabled takes its baseline"
                            + " from the connection onSetup() opens, which the tester's listener"
                            + " closes, and verifies on a connection opened after onTearDown()'s"
                            + " closed - a pool of one must be enough, and must actually be"
                            + " used.")
                    .isEqualTo(1);
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
            assertThat(pool.leaked())
                    .as("Every connection the run opened must have been closed.").isZero();
        }
    }

    @Test
    void testAfterTestExecution_classicPathRowCountCheckOffPoolOfOne_succeedsHoldingAtMostOne()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource pool = new CountingDataSource(environment.getProfile(), 1);
        deleteAllRowsQuietly(environment);
        try
        {
            ClassicNoRowCountCheckSample.pool = pool;
            ClassicNoRowCountCheckSample.schema = environment.getProfile().getSchema();

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassicNoRowCountCheckSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(pool.peakConcurrent())
                    .as("With the row count check disabled, the classic path opens no connection"
                            + " of its own: only onSetup()'s and onTearDown()'s, one after the"
                            + " other, as in the 3.5.0 lifecycle - a pool of one must be enough,"
                            + " and is actually used.")
                    .isEqualTo(1);
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
            assertThat(pool.leaked())
                    .as("Every connection the run opened must have been closed.").isZero();
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

    // The classic path: a bare, unannotated, non-static IDatabaseTester field (the 3.5.0
    // lifecycle-only style). It must be non-static for the extension's field auto-scan, so it is
    // built in the constructor from the static pool/schema the enclosing test sets.

    @ExtendWith(DbUnitExtension.class)
    static class ClassicRowCountCheckSample
    {
        static CountingDataSource pool;
        static String schema;

        final IDatabaseTester databaseTester;

        ClassicRowCountCheckSample()
        {
            final DatabaseConfig rowCountCheckOn = new DatabaseConfig();
            rowCountCheckOn.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
            databaseTester = new DataSourceDatabaseTester(pool, schema, null, rowCountCheckOn);
        }

        @BeforeEach
        void configureLifecycle() throws Exception
        {
            databaseTester.setDataSet(new FlatXmlDataSetBuilder()
                    .build(getClass().getResource("annotation-it-prep.xml")));
            databaseTester.setSetUpOperation(DatabaseOperation.CLEAN_INSERT);
            databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        }

        @Test
        void runsTheFullLifecycle()
        {
            // The connection accounting is the assertion - see the enclosing test. The
            // @BeforeEach dataset + CLEAN_INSERT setup + DELETE_ALL teardown (+ row count
            // check) is a complete lifecycle without the body needing to touch the database.
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class ClassicNoRowCountCheckSample
    {
        static CountingDataSource pool;
        static String schema;

        final IDatabaseTester databaseTester;

        ClassicNoRowCountCheckSample()
        {
            databaseTester = new DataSourceDatabaseTester(pool, schema);
        }

        @BeforeEach
        void configureLifecycle() throws Exception
        {
            databaseTester.setDataSet(new FlatXmlDataSetBuilder()
                    .build(getClass().getResource("annotation-it-prep.xml")));
            databaseTester.setSetUpOperation(DatabaseOperation.CLEAN_INSERT);
            databaseTester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        }

        @Test
        void runsTheFullLifecycle()
        {
            // The connection accounting is the assertion - see the enclosing test. The
            // @BeforeEach dataset + CLEAN_INSERT setup + DELETE_ALL teardown (+ row count
            // check) is a complete lifecycle without the body needing to touch the database.
        }
    }

}
