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
 * connections at once than it needs: the executor's own memoized connection (for the row count
 * check, or - on the classic path - the eagerly-resolved baseline probe) plus at most one the
 * tester opens for an operation. A pool of two always suffices; the executor never reaches for a
 * third.
 *
 * <p>The classic path holds its baseline connection until {@code afterTest()} the same way the
 * annotation paths do - it must not close a connection a fixed-connection tester would hand
 * straight to {@code onSetup()} - so a bounded pool needs capacity for two even with the row
 * count check disabled.
 *
 * <p>Uses {@link CountingDataSource} with a hard cap so a runtime that tries to hold too many
 * fails fast rather than deadlocking. Matrix rows 1 & 2 under pool pressure (G-c3).
 */
@ClearRowCountCheckSystemProperties
class DbUnitExtensionBoundedPoolIT
{
    private static final String TEST_TABLE = "TEST_TABLE";

    @Test
    void testAfterTestExecution_classicPathRowCountCheckOnPoolOfTwo_succeedsHoldingAtMostTwo()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource pool = new CountingDataSource(environment.getProfile(), 2);
        deleteAllRowsQuietly(environment);
        try
        {
            ClassicRowCountCheckSample.pool = pool;
            ClassicRowCountCheckSample.schema = environment.getProfile().getSchema();

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassicRowCountCheckSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(pool.peakConcurrent())
                    .as("The classic path with the row count check enabled holds its baseline"
                            + " connection through onSetup() (which opens a second) and the"
                            + " later verify - a pool of two must be enough, and must actually"
                            + " be used.")
                    .isEqualTo(2);
        } finally
        {
            deleteAllRowsQuietly(environment);
            environment.closeConnection();
            assertThat(pool.leaked())
                    .as("Every connection the run opened must have been closed.").isZero();
        }
    }

    @Test
    void testAfterTestExecution_classicPathRowCountCheckOffPoolOfTwo_succeedsHoldingAtMostTwo()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        final CountingDataSource pool = new CountingDataSource(environment.getProfile(), 2);
        deleteAllRowsQuietly(environment);
        try
        {
            ClassicNoRowCountCheckSample.pool = pool;
            ClassicNoRowCountCheckSample.schema = environment.getProfile().getSchema();

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassicNoRowCountCheckSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(pool.peakConcurrent())
                    .as("Even with the row count check disabled, the classic path holds its"
                            + " eagerly-resolved baseline connection through onSetup() (which"
                            + " opens a second) rather than closing a connection a"
                            + " fixed-connection tester would hand straight to onSetup() - a pool"
                            + " of two must be enough, and is actually used.")
                    .isEqualTo(2);
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
