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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.IOperationListener;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.database.rowcount.RowCountChecker;
import org.dbunit.database.rowcount.UnexpectedRowCountException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the row count check wiring in {@link DbUnitExtension}, mirroring
 * {@link DbUnitExtensionTest}'s Mockito-based style. {@link DbUnitExtensionTest} itself
 * already covers the case where {@link IDatabaseTester#getConnection()} goes unstubbed
 * (returning null, the Mockito default) and confirms that leaves onSetup()/onTearDown()
 * unaffected; this class exercises the check with a real connection stubbed in.
 */
@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class DbUnitExtensionRowCountCheckTest {
    @Mock
    ExtensionContext context;

    @Mock
    ExtensionContext.Store store;

    @Mock
    IDatabaseTester databaseTester;

    @Mock
    IDatabaseConnection databaseConnection;

    final DbUnitExtension extension = new DbUnitExtension();

    @Test
    void testBeforeTestExecution_checkEnabled_capturesBaselineWithoutClosingTheConnection()
            throws Exception {
        givenTesterField();
        stubConnection(enabledDatabaseConfig(), "ACCOUNT");
        when(databaseConnection.getRowCount("ACCOUNT")).thenReturn(5);

        extension.beforeTestExecution(context);

        verify(store).put(eq(DbUnitExtension.ROW_COUNT_CHECKER_KEY),
                any(RowCountChecker.class));
        verify(databaseConnection, never()).close();
        verify(databaseTester).onSetup();
    }

    @Test
    void testBeforeTestExecution_checkDisabled_neverQueriesTheConnection()
            throws Exception {
        givenTesterField();
        stubConnection(new DatabaseConfig()); // FEATURE_ROW_COUNT_CHECK defaults to false

        extension.beforeTestExecution(context);

        verify(databaseConnection, never()).createDataSet();
        verify(databaseConnection, never()).getRowCount(anyString());
    }

    @Test
    void testBeforeTestExecution_fixedConnectionTester_neverClosesTheConnectionOnSetupNeeds()
            throws Exception {
        // DefaultDatabaseTester(connection) returns this same connection from every
        // getConnection() call, including the one onSetup() makes right after the row
        // count check captures its baseline; NONE + a non-closing listener isolates that
        // onSetup() call so any close() interaction can only have come from the check
        // itself (#944)
        when(databaseConnection.getConfig()).thenReturn(enabledDatabaseConfig());
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(databaseConnection.createDataSet()).thenReturn(dataSet);
        when(databaseConnection.getRowCount("ACCOUNT")).thenReturn(5);
        final IDatabaseTester fixedConnectionTester =
                new DefaultDatabaseTester(databaseConnection);
        fixedConnectionTester.setSetUpOperation(DatabaseOperation.NONE);
        fixedConnectionTester.setOperationListener(IOperationListener.NO_OP_OPERATION_LISTENER);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional
                .of(new DbUnitExtensionTest.HasTester(fixedConnectionTester)));

        extension.beforeTestExecution(context);

        verify(databaseConnection, never()).close();
    }

    @Test
    void testBeforeTestExecution_testerConnectionNull_doesNotThrowAndStillRunsOnSetup()
            throws Exception {
        givenTesterField();
        when(databaseTester.getConnection()).thenReturn(null);

        assertThatCode(() -> extension.beforeTestExecution(context))
                .as("A tester with no connection to inspect (e.g. a test double) must be"
                        + " tolerated, not throw a NullPointerException.")
                .doesNotThrowAnyException();
        verify(databaseTester).onSetup();
    }

    @Test
    void testAfterTestExecution_countsUnchangedAndNoExecutionException_doesNotThrow()
            throws Exception {
        givenStoredTester();
        when(context.getExecutionException()).thenReturn(Optional.empty());
        final RowCountChecker rowCountChecker = capturedRealBaseline("ACCOUNT", 5);
        when(store.get(DbUnitExtension.ROW_COUNT_CHECKER_KEY, RowCountChecker.class))
                .thenReturn(rowCountChecker);
        // current count still 5, read back through the same stub the baseline capture
        // above used - unchanged

        assertThatCode(() -> extension.afterTestExecution(context))
                .as("Matching current counts must not be reported as a failure.")
                .doesNotThrowAnyException();
        verify(databaseTester).onTearDown();
        verify(databaseConnection, never()).close();
    }

    @Test
    void testAfterTestExecution_rowCountChanged_throwsUnexpectedRowCountException()
            throws Exception {
        givenStoredTester();
        when(context.getExecutionException()).thenReturn(Optional.empty());
        final RowCountChecker rowCountChecker = capturedRealBaseline("ACCOUNT", 5);
        when(store.get(DbUnitExtension.ROW_COUNT_CHECKER_KEY, RowCountChecker.class))
                .thenReturn(rowCountChecker);
        // the fresh count this verify() call reads back differs from the captured baseline
        when(databaseConnection.getRowCount("ACCOUNT")).thenReturn(8);

        assertThatThrownBy(() -> extension.afterTestExecution(context))
                .as("A table whose count no longer matches the baseline must fail the test.")
                .isInstanceOf(UnexpectedRowCountException.class)
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void testAfterTestExecution_executionExceptionPresent_skipsVerification() throws Exception {
        givenStoredBaseline();
        when(context.getExecutionException())
                .thenReturn(Optional.of(new AssertionError("test method failed")));

        assertThatCode(() -> extension.afterTestExecution(context))
                .as("The database is in an unknown state after a test failure, so a count"
                        + " difference would be noise; verification must be skipped entirely.")
                .doesNotThrowAnyException();
        verify(databaseTester, never()).getConnection();
    }

    @Test
    void testAfterTestExecution_noBaselineStored_skipsVerification() throws Exception {
        givenStoredTester();
        when(store.get(DbUnitExtension.ROW_COUNT_CHECKER_KEY, RowCountChecker.class)).thenReturn(null);

        assertThatCode(() -> extension.afterTestExecution(context))
                .as("No stored checker (check disabled, or the tester had no connection to"
                        + " capture one from) must skip verification silently.")
                .doesNotThrowAnyException();
        verify(databaseTester, never()).getConnection();
    }

    @Test
    void testAfterTestExecution_checkDisabled_neverAcquiresASecondConnection()
            throws Exception {
        givenTesterField();
        stubConnection(new DatabaseConfig()); // FEATURE_ROW_COUNT_CHECK defaults to false
        extension.beforeTestExecution(context);
        // afterTestExecution() looks the tester back up from the store by key, same as
        // beforeTestExecution() stored it under - givenTesterField() only covers resolving
        // it the first time, via the test instance field
        when(store.get(DbUnitExtension.TESTER_KEY, IDatabaseTester.class)).thenReturn(databaseTester);
        final ArgumentCaptor<RowCountChecker> checkerCaptor =
                ArgumentCaptor.forClass(RowCountChecker.class);
        verify(store).put(eq(DbUnitExtension.ROW_COUNT_CHECKER_KEY), checkerCaptor.capture());
        when(store.get(DbUnitExtension.ROW_COUNT_CHECKER_KEY, RowCountChecker.class))
                .thenReturn(checkerCaptor.getValue());

        extension.afterTestExecution(context);

        assertThat(checkerCaptor.getValue().hasBaseline())
                .as("A disabled check must resolve a RowCountChecker holding no baseline.")
                .isFalse();
        verify(databaseTester, times(1)).getConnection();
        verify(databaseConnection, never()).close();
        verify(databaseTester).onTearDown();
    }

    private void givenTesterField() {
        final DbUnitExtensionTest.HasTester testInstance =
                new DbUnitExtensionTest.HasTester(databaseTester);
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(context.getTestInstance()).thenReturn(Optional.of(testInstance));
    }

    private void givenStoredTester() {
        when(context.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(store.get(DbUnitExtension.TESTER_KEY, IDatabaseTester.class)).thenReturn(databaseTester);
    }

    private void givenStoredBaseline() {
        givenStoredTester();
        final RowCountChecker rowCountChecker = mock(RowCountChecker.class);
        when(rowCountChecker.hasBaseline()).thenReturn(true);
        when(store.get(DbUnitExtension.ROW_COUNT_CHECKER_KEY, RowCountChecker.class))
                .thenReturn(rowCountChecker);
    }

    /**
     * Captures a real baseline of one table into a fresh {@link RowCountChecker}, via
     * {@link #stubConnection(DatabaseConfig, String...)} against the shared
     * {@code databaseConnection} mock - so a later stub of {@code getRowCount(tableName)}
     * within the same test transparently changes what a subsequent {@code verify()} reads
     * back as the current count.
     */
    private RowCountChecker capturedRealBaseline(final String tableName, final int rowCount)
            throws Exception {
        stubConnection(enabledDatabaseConfig(), tableName);
        when(databaseConnection.getRowCount(tableName)).thenReturn(rowCount);

        final RowCountChecker rowCountChecker = new RowCountChecker();
        rowCountChecker.capture(databaseConnection);
        return rowCountChecker;
    }

    private void stubConnection(final DatabaseConfig config, final String... tableNames)
            throws Exception {
        when(databaseConnection.getConfig()).thenReturn(config);
        when(databaseTester.getConnection()).thenReturn(databaseConnection);
        // only stub table enumeration when the caller expects it to actually be queried -
        // a disabled check must never reach createDataSet(), and strict stubbing rejects an
        // unused stub
        if (tableNames.length > 0) {
            final IDataSet dataSet = mock(IDataSet.class);
            when(dataSet.getTableNames()).thenReturn(tableNames);
            when(databaseConnection.createDataSet()).thenReturn(dataSet);
        }
    }

    private static DatabaseConfig enabledDatabaseConfig() {
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        return config;
    }
}
