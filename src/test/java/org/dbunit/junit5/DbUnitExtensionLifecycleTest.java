/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2025, DbUnit.org
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
package org.dbunit.junit5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbunit.IOperationListener;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Lifecycle integration tests for {@link DbUnitExtension} that verify execution
 * ordering and teardown behavior under failure using {@link EngineTestKit}.
 */
class DbUnitExtensionLifecycleTest {

    @Test
    void testLifecycle_onSetupCalledAfterBeforeEach_onTearDownCalledBeforeAfterEach() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(LifecycleOrderSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void testLifecycle_onTearDownCalledEvenWhenTestFails() {
        FailingTestSample.CALL_LOG.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FailingTestSample.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(FailingTestSample.CALL_LOG)
                .as("onTearDown() must be called even when the test method fails.")
                .contains("onTearDown");
    }

    /**
     * Sample test class with a known-good lifecycle that verifies call ordering.
     * Assertions inside verify that onSetup runs after @BeforeEach and onTearDown
     * runs before @AfterEach; a failure here surfaces as a test failure in
     * the outer EngineTestKit assertion.
     */
    @ExtendWith(DbUnitExtension.class)
    static class LifecycleOrderSample {
        private final List<String> callLog = new ArrayList<>();
        IDatabaseTester databaseTester = new CallLoggingTester(callLog);

        @BeforeEach
        void beforeEach() {
            callLog.add("beforeEach");
        }

        @AfterEach
        void afterEach() {
            assertThat(callLog)
                    .as("Execution order must be: beforeEach, onSetup, test, onTearDown.")
                    .containsExactly("beforeEach", "onSetup", "test", "onTearDown");
        }

        @Test
        void testOrderVerification() {
            assertThat(callLog)
                    .as("onSetup() must run after @BeforeEach and before the test method.")
                    .containsExactly("beforeEach", "onSetup");
            callLog.add("test");
        }
    }

    /**
     * Sample test class whose single test intentionally fails, used to verify
     * that onTearDown is still called when the test method throws.
     */
    @ExtendWith(DbUnitExtension.class)
    static class FailingTestSample {
        static final List<String> CALL_LOG = Collections.synchronizedList(new ArrayList<>());
        IDatabaseTester databaseTester = new CallLoggingTester(CALL_LOG);

        @Test
        void testThatFails() {
            CALL_LOG.add("test");
            fail("intentional failure to verify onTearDown still runs");
        }
    }

    private static class CallLoggingTester implements IDatabaseTester {
        private final List<String> callLog;

        CallLoggingTester(final List<String> callLog) {
            this.callLog = callLog;
        }

        @Override
        public void onSetup() throws Exception {
            callLog.add("onSetup");
        }

        @Override
        public void onTearDown() throws Exception {
            callLog.add("onTearDown");
        }

        @Override
        public IDatabaseConnection getConnection() throws Exception {
            return null;
        }

        @Override
        public IDataSet getDataSet() {
            return null;
        }

        @Override
        public void setDataSet(final IDataSet dataSet) {
        }

        @Override
        public DatabaseOperation getSetUpOperation() {
            return null;
        }

        @Override
        public DatabaseOperation getTearDownOperation() {
            return null;
        }

        @Override
        public void setSetUpOperation(final DatabaseOperation setUpOperation) {
        }

        @Override
        public void setTearDownOperation(final DatabaseOperation tearDownOperation) {
        }

        @Override
        public void setOperationListener(final IOperationListener operationListener) {
        }

        @Override
        @Deprecated
        public void closeConnection(final IDatabaseConnection connection) throws Exception {
        }

        @Override
        @Deprecated
        public void setSchema(final String schema) {
        }
    }
}
