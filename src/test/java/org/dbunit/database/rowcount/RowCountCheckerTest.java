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
package org.dbunit.database.rowcount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

@ClearRowCountCheckSystemProperties
class RowCountCheckerTest
{
    @Test
    void testGetRowCountCheck_beforeAnyUse_returnsNull()
    {
        final RowCountChecker checker = new RowCountChecker();

        assertThat(checker.getRowCountCheck())
                .as("No RowCountCheck must be resolved before capture()/verify() ever run.")
                .isNull();
    }

    @Test
    void testCapture_noRowCountCheckSet_lazilyResolvesOneFromConnectionConfig()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());

        checker.capture(connection);

        assertThat(checker.getRowCountCheck())
                .as("capture() must lazily build a RowCountCheck from the connection's"
                        + " DatabaseConfig when none was set.")
                .isNotNull();
    }

    @Test
    void testCapture_rowCountCheckAlreadySet_reusesItWithoutQueryingConnectionConfig()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot = new RowCountSnapshot(Collections.emptyMap());
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);

        checker.capture(connection);

        verify(connection, never()).getConfig();
        assertThat(checker.getRowCountCheck())
                .as("A RowCountCheck supplied via setRowCountCheck() must be used as-is,"
                        + " never rebuilt.")
                .isSameAs(mockRowCountCheck);
    }

    @Test
    void testVerify_noBaselineCaptured_doesNotQueryTheConnection() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);

        assertThatCode(() -> checker.verify(connection))
                .as("No captured baseline means nothing to compare; verify() must skip"
                        + " silently rather than fail.")
                .doesNotThrowAnyException();
        verifyNoInteractions(connection);
    }

    @Test
    void testVerify_baselineCaptured_delegatesToTheResolvedRowCountCheck() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);
        checker.capture(connection);

        checker.verify(connection);

        verify(mockRowCountCheck).verify(snapshot, connection);
    }

    @Test
    void testVerify_rowCountCheckThrows_propagates() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);
        checker.capture(connection);
        final UnexpectedRowCountException failure = new UnexpectedRowCountException(
                Collections.singletonList(new RowCountDifference("ACCOUNT", 5, 8)));
        doThrow(failure).when(mockRowCountCheck).verify(snapshot, connection);

        assertThatThrownBy(() -> checker.verify(connection))
                .as("A row count difference detected during verify() must propagate as-is.")
                .isSameAs(failure);
    }

    @Test
    void testDiscardBaseline_afterCapture_verifyBecomesNoOp() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);
        checker.capture(connection);

        checker.discardBaseline();
        checker.verify(connection);

        verify(mockRowCountCheck, never()).verify(any(), any());
    }

    @Test
    void testCaptureThenVerify_bothReuseTheSameResolvedRowCountCheck() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[0]);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        when(connection.createDataSet()).thenReturn(dataSet);

        checker.capture(connection);
        final RowCountCheck resolvedDuringCapture = checker.getRowCountCheck();
        checker.verify(connection);

        verify(connection, times(1)).getConfig();
        assertThat(checker.getRowCountCheck())
                .as("verify() must reuse the same RowCountCheck capture() resolved, not"
                        + " build a second one.")
                .isSameAs(resolvedDuringCapture);
    }

    @Test
    void testHasBaseline_beforeAnyUse_returnsFalse()
    {
        final RowCountChecker checker = new RowCountChecker();

        assertThat(checker.hasBaseline())
                .as("No baseline has been captured yet.").isFalse();
    }

    @Test
    void testHasBaseline_afterCapture_returnsTrue() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);

        checker.capture(connection);

        assertThat(checker.hasBaseline())
                .as("A caller must be able to tell a baseline was captured without needing a"
                        + " connection just to ask.")
                .isTrue();
    }

    @Test
    void testHasBaseline_captureResolvesDisabled_returnsFalse() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());

        checker.capture(connection);

        assertThat(checker.hasBaseline())
                .as("A disabled check captures no baseline, so a caller can skip acquiring a"
                        + " connection for verify() entirely.")
                .isFalse();
    }

    @Test
    void testHasBaseline_afterDiscardBaseline_returnsFalse() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot =
                new RowCountSnapshot(Collections.singletonMap("ACCOUNT", 5));
        when(mockRowCountCheck.capture(connection)).thenReturn(snapshot);
        checker.capture(connection);

        checker.discardBaseline();

        assertThat(checker.hasBaseline())
                .as("A discarded baseline must no longer be reported as held.").isFalse();
    }
}
