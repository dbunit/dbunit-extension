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
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitProperty;
import org.dbunit.annotation.DbUnitTearDownOperation;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database integration test proving an annotated test tells the tester's own
 * {@link IOperationListener} about the connection each of its set up and tear down operations
 * retrieves, as the tester's {@code onSetup()}/{@code onTearDown()} do, and that a
 * {@code @DbUnitProperty} value still wins over what that listener sets, because the
 * annotation values are applied after it.
 */
class DbUnitExtensionTesterListenerIT
{
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final int LISTENER_BATCH_SIZE = 99;
    private static final String ANNOTATION_BATCH_SIZE = "7";

    @Test
    void testBeforeTestExecution_annotationDrivenWithTesterListener_tellsItPerOperationAndAnnotationValuesWin()
            throws Exception
    {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try
        {
            final RecordingListener listener = new RecordingListener();
            final IDatabaseTester tester =
                    new DefaultDatabaseTester(environment.getConnection());
            tester.setOperationListener(listener);
            TesterListenerSample.databaseTester = tester;
            TesterListenerSample.batchSizeSeenByTest = null;

            EngineTestKit.engine("junit-jupiter").selectors(selectClass(TesterListenerSample.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(listener.getRetrievedCount())
                    .as("The tester's listener must hear of the connection once for the set up"
                            + " operation and once for the tear down operation.")
                    .isEqualTo(2);
            assertThat(TesterListenerSample.batchSizeSeenByTest)
                    .as("The @DbUnitProperty value must be in force during the test, because"
                            + " it is applied after the tester's listener has set its own.")
                    .isEqualTo(Integer.valueOf(ANNOTATION_BATCH_SIZE));
        } finally
        {
            environment.closeConnection();
        }
    }

    private static final class RecordingListener implements IOperationListener
    {
        private final AtomicInteger retrievedCount = new AtomicInteger();

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            retrievedCount.incrementAndGet();
            connection.getConfig().setProperty(DatabaseConfig.PROPERTY_BATCH_SIZE,
                    LISTENER_BATCH_SIZE);
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
            // the environment owns the connection, so it is left open
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
            // the environment owns the connection, so it is left open
        }

        private int getRetrievedCount()
        {
            return retrievedCount.get();
        }
    }

    @ExtendWith(DbUnitExtension.class)
    @DbUnitConfig(properties = @DbUnitProperty(name = "batchSize", value = ANNOTATION_BATCH_SIZE))
    @DbUnitPrep("annotation-it-prep.xml")
    @DbUnitExpected(value = "annotation-it-expected.xml",
            verify = @DbUnitVerifyTable(value = TEST_TABLE, include = {"COLUMN0", "COLUMN1"}))
    @DbUnitTearDownOperation(DbUnitOperation.DELETE_ALL)
    static class TesterListenerSample
    {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        static volatile Object batchSizeSeenByTest;

        @Test
        void mutateTheSeededRowAndNoteTheBatchSize(final IDatabaseConnection connection)
                throws Exception
        {
            batchSizeSeenByTest =
                    connection.getConfig().getProperty(DatabaseConfig.PROPERTY_BATCH_SIZE);
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("UPDATE " + TEST_TABLE
                        + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }
}
