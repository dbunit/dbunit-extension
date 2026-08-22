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

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.annotation.DbUnitConfig;
import org.dbunit.annotation.DbUnitExpected;
import org.dbunit.annotation.DbUnitPrep;
import org.dbunit.annotation.DbUnitSetup;
import org.dbunit.annotation.DbUnitTearDown;
import org.dbunit.annotation.DbUnitTester;
import org.dbunit.annotation.DbUnitVerifyTable;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.operation.DbUnitOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Real-database (hsqldb) integration test of the annotation-driven path through
 * {@link DbUnitExtension}: {@code @DbUnitPrep} seeds, the test mutates, {@code @DbUnitExpected}
 * verifies, and {@code @DbUnitTearDown} cleans up - plus a class-level {@code @DbUnitSetup}
 * operation staying in force across a method that declares its own {@code @DbUnitPrep}, the
 * override trap the split between the two annotations exists to prevent.
 */
class DbUnitExtensionAnnotationIT {
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final String PK_TABLE = "PK_TABLE";

    @Test
    void testAfterTestExecution_prepMutateExpected_verifiesAndCleansUp() throws Exception {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try {
            final IDatabaseConnection connection = environment.getConnection();
            PrepExpectedSample.databaseTester = new DefaultDatabaseTester(connection);
            PrepExpectedSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(PrepExpectedSample.class)).execute().testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));

            // the prep/expected path closes its own connection when the test finishes
            // (closeConnectionAfterTest defaults to true), so verify with a fresh one.
            final IDatabaseConnection verifyConnection = environment.getConnection();
            assertThat(rowCount(verifyConnection, TEST_TABLE))
                    .as("@DbUnitTearDown(operation = DELETE_ALL) must have cleaned up.")
                    .isZero();
        } finally {
            deleteAllRowsQuietly(environment, TEST_TABLE);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_classLevelSetupOperation_survivesMethodLevelPrep()
            throws Exception {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try {
            final IDatabaseConnection connection = environment.getConnection();
            ClassLevelOperationSample.databaseTester = new DefaultDatabaseTester(connection);
            ClassLevelOperationSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
            try (Statement statement = connection.getConnection().createStatement()) {
                statement.execute("INSERT INTO " + PK_TABLE
                        + " (PK0, PK1, PK2, NORMAL0) VALUES (997, 997, 997, 'preexisting')");
            }

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ClassLevelOperationSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(rowCountForPks(connection, 997, 998))
                    .as("REFRESH (an upsert) must not have wiped the pre-existing row (PK 997)"
                            + " the way CLEAN_INSERT would have; the class-level @DbUnitSetup"
                            + " operation must have survived the method's own @DbUnitPrep"
                            + " (which seeds PK 998).")
                    .isEqualTo(2);
        } finally {
            deletePksQuietly(environment, 997, 998);
            environment.closeConnection();
        }
    }

    @Test
    void testBeforeTestExecution_expectedPathClassLevelSetupOperation_survivesMethodLevelPrep()
            throws Exception {
        final DatabaseEnvironment environment = DatabaseEnvironment.getInstance();
        try {
            final IDatabaseConnection connection = environment.getConnection();
            ExpectedPathClassLevelOperationSample.databaseTester =
                    new DefaultDatabaseTester(connection);
            ExpectedPathClassLevelOperationSample.databaseTester
                    .setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
            try (Statement statement = connection.getConnection().createStatement()) {
                statement.execute("INSERT INTO " + PK_TABLE
                        + " (PK0, PK1, PK2, NORMAL0) VALUES (895, 895, 895, 'preexisting')");
            }

            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExpectedPathClassLevelOperationSample.class)).execute()
                    .testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(rowCountForPks(connection, 895, 998))
                    .as("REFRESH (an upsert) must not have wiped the pre-existing row (PK 895)"
                            + " the way CLEAN_INSERT would have; the class-level @DbUnitSetup"
                            + " operation must apply on the @DbUnitExpected prep/expected path"
                            + " too, the same as it already does on the simple path.")
                    .isEqualTo(2);
        } finally {
            deletePksQuietly(environment, 895, 998);
            environment.closeConnection();
        }
    }

    private static int rowCount(final IDatabaseConnection connection, final String tableName)
            throws Exception {
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet =
                        statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static int rowCountForPks(final IDatabaseConnection connection, final int... pk0s)
            throws Exception {
        final StringBuilder inList = new StringBuilder();
        for (int i = 0; i < pk0s.length; i++) {
            if (i > 0) {
                inList.append(',');
            }
            inList.append(pk0s[i]);
        }
        try (Statement statement = connection.getConnection().createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + PK_TABLE + " WHERE PK0 IN (" + inList + ")")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void deleteAllRowsQuietly(final DatabaseEnvironment environment,
            final String tableName) {
        try (Statement statement =
                environment.getConnection().getConnection().createStatement()) {
            statement.execute("DELETE FROM " + tableName);
        } catch (final Exception e) {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    private static void deletePksQuietly(final DatabaseEnvironment environment,
            final int... pk0s) {
        final StringBuilder inList = new StringBuilder();
        for (int i = 0; i < pk0s.length; i++) {
            if (i > 0) {
                inList.append(',');
            }
            inList.append(pk0s[i]);
        }
        try (Statement statement =
                environment.getConnection().getConnection().createStatement()) {
            statement.execute("DELETE FROM " + PK_TABLE + " WHERE PK0 IN (" + inList + ")");
        } catch (final Exception e) {
            // best-effort cleanup only; a failure here must not fail the test that already ran
        }
    }

    @ExtendWith(DbUnitExtension.class)
    static class PrepExpectedSample {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-prep.xml")
        @DbUnitExpected(value = "annotation-it-expected.xml",
                verify = @DbUnitVerifyTable(value = TEST_TABLE,
                        include = {"COLUMN0", "COLUMN1"}))
        @DbUnitTearDown(operation = DbUnitOperation.DELETE_ALL)
        void testWithdraw_sufficientBalance_decrementsBalance() throws Exception {
            try (Statement statement =
                    databaseTester.getConnection().getConnection().createStatement()) {
                statement.execute(
                        "UPDATE " + TEST_TABLE + " SET COLUMN1 = 'after' WHERE COLUMN0 = 'row0'");
            }
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the outer test's own connection,
    // reused below (rowCountForPks) after this sample class finishes running.
    @DbUnitConfig(closeConnectionAfterTest = false)
    @DbUnitSetup(operation = DbUnitOperation.REFRESH)
    static class ClassLevelOperationSample {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        @Test
        @DbUnitPrep("annotation-it-pk-prep.xml")
        void testPrepDeclaredOnMethod_classLevelSetupOperationAlsoDeclared_bothApply() {
        }
    }

    @ExtendWith(DbUnitExtension.class)
    // closeConnectionAfterTest = false: databaseTester wraps the outer test's own connection,
    // reused below (rowCountForPks) after this sample class finishes running.
    @DbUnitConfig(closeConnectionAfterTest = false)
    @DbUnitSetup(operation = DbUnitOperation.REFRESH)
    static class ExpectedPathClassLevelOperationSample {
        @DbUnitTester
        static IDatabaseTester databaseTester;

        // No verifyTables/verify/verifyDefinitions: @DbUnitExpected here only needs to switch
        // the test onto the prep/expected path, not compare any table - the row counts the
        // calling test reads afterward are what prove the class-level @DbUnitSetup operation
        // was applied there too, not just on the simple path.
        @Test
        @DbUnitPrep("annotation-it-pk-prep.xml")
        @DbUnitExpected
        void testPrepDeclaredOnMethod_expectedPathWithClassLevelSetupOperation_bothApply() {
        }
    }
}
