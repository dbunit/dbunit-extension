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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void testSetEnabledOverride_beforeCapture_doesNotEagerlyResolveARowCountCheck()
    {
        final RowCountChecker checker = new RowCountChecker();

        checker.setEnabledOverride(true, new String[] {"IGNORED_TABLE"});

        assertThat(checker.getRowCountCheck())
                .as("setEnabledOverride() must only store the override values; resolving a"
                        + " RowCountCheck from them happens lazily, the same as the plain"
                        + " default-from-config path.")
                .isNull();
    }

    @Test
    void testCapture_enabledOverrideTrue_enablesCheckRegardlessOfConnectionConfig()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(true, new String[0]);
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, false);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);

        checker.capture(connection);

        assertThat(checker.hasBaseline())
                .as("setEnabledOverride(true, ...) must enable the check even though the"
                        + " connection's own FEATURE_ROW_COUNT_CHECK is false.")
                .isTrue();
    }

    @Test
    void testCapture_enabledOverrideFalse_disablesCheckRegardlessOfConnectionConfig()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(false, new String[0]);
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);

        checker.capture(connection);

        assertThat(checker.hasBaseline())
                .as("setEnabledOverride(false, ...) must disable the check even though the"
                        + " connection's own FEATURE_ROW_COUNT_CHECK is true.")
                .isFalse();
        verify(connection, never()).createDataSet();
    }

    @Test
    void testCapture_enabledOverrideWithExcludePatterns_excludesMatchingTables()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(true, new String[] {"IGNORED_TABLE"});
        final DatabaseConfig config = new DatabaseConfig();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT", "IGNORED_TABLE"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);

        checker.capture(connection);

        verify(connection).getRowCount("ACCOUNT");
        verify(connection, never()).getRowCount("IGNORED_TABLE");
    }

    @Test
    void testSetEnabledOverride_callerMutatesArrayAfterward_doesNotAffectStoredExclude()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        final String[] exclude = {"IGNORED_TABLE"};
        checker.setEnabledOverride(true, exclude);
        exclude[0] = "ACCOUNT";
        final DatabaseConfig config = new DatabaseConfig();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT", "IGNORED_TABLE"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);
        when(connection.getRowCount("IGNORED_TABLE")).thenReturn(5);

        checker.capture(connection);

        // setEnabledOverride() must clone the caller's array, so mutating it afterward - here,
        // turning "IGNORED_TABLE" into "ACCOUNT" - must not retroactively change which table
        // this checker excludes: IGNORED_TABLE, frozen at call time, not ACCOUNT.
        verify(connection).getRowCount("ACCOUNT");
        verify(connection, never()).getRowCount("IGNORED_TABLE");
    }

    @Test
    void testSetEnabledOverride_nullExclude_treatedAsEmpty() throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(true, null);
        final DatabaseConfig config = new DatabaseConfig();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);
        when(connection.getRowCount("ACCOUNT")).thenReturn(5);

        assertThatCode(() -> checker.capture(connection))
                .as("A null exclude array must be treated as empty (excludes nothing), not"
                        + " throw a NullPointerException.")
                .doesNotThrowAnyException();
        verify(connection).getRowCount("ACCOUNT");
    }

    @Test
    void testCapture_enabledOverrideSet_carriesOverConnectionsConfiguredRowCounter()
            throws Exception
    {
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(true, new String[0]);
        final RowCounter customRowCounter = mock(RowCounter.class);
        when(customRowCounter.countRows(any(), any())).thenReturn(Collections.emptyMap());
        final DatabaseConfig config = new DatabaseConfig();
        config.setProperty(DatabaseConfig.PROPERTY_ROW_COUNTER, customRowCounter);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection.createDataSet()).thenReturn(dataSet);

        checker.capture(connection);

        verify(customRowCounter).countRows(eq(connection), any());
        assertThat(checker.hasBaseline())
                .as("setEnabledOverride() must still carry over the connection's own"
                        + " PROPERTY_ROW_COUNTER, the same as resolving directly from its"
                        + " DatabaseConfig would.")
                .isTrue();
    }

    @Test
    void testCapture_enabledOverrideSet_overlayHandlesEveryPropertyRowCountCheckConfigurationReads()
            throws Exception
    {
        // RowCountCheckConfiguration's constructor reads exactly three DatabaseConfig entries:
        // FEATURE_ROW_COUNT_CHECK, PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES, PROPERTY_ROW_COUNTER.
        // RowCountChecker.overlayEnabledOverride() must account for all three - overriding the
        // first two, carrying the third over from the real connection config. This asserts the
        // whole set at once, with a distinctive non-default value for each; a fourth property
        // added to RowCountCheckConfiguration needs matching handling in overlayEnabledOverride().
        final RowCountChecker checker = new RowCountChecker();
        checker.setEnabledOverride(true, new String[] {"OVERRIDE_EXCLUDED"});
        final RowCounter realConfigRowCounter = mock(RowCounter.class);
        when(realConfigRowCounter.countRows(any(), any())).thenReturn(Collections.emptyMap());
        final DatabaseConfig realConfig = new DatabaseConfig();
        realConfig.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, false);
        realConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES,
                new String[] {"REAL_CONFIG_EXCLUDED"});
        realConfig.setProperty(DatabaseConfig.PROPERTY_ROW_COUNTER, realConfigRowCounter);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConfig()).thenReturn(realConfig);
        final IDataSet dataSet = mock(IDataSet.class);
        when(dataSet.getTableNames())
                .thenReturn(new String[] {"KEPT", "OVERRIDE_EXCLUDED", "REAL_CONFIG_EXCLUDED"});
        when(connection.createDataSet()).thenReturn(dataSet);

        checker.capture(connection);

        assertThat(checker.hasBaseline())
                .as("FEATURE_ROW_COUNT_CHECK: the override's true must win over the real"
                        + " config's false.")
                .isTrue();
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<String>> tables = ArgumentCaptor.forClass(List.class);
        verify(realConfigRowCounter).countRows(eq(connection), tables.capture());
        assertThat(tables.getValue())
                .as("PROPERTY_ROW_COUNTER: the real config's counter is used (so the overlay"
                        + " carried it). PROPERTY_ROW_COUNT_CHECK_EXCLUDE_TABLES: the override's"
                        + " exclude list applies, not the real config's - OVERRIDE_EXCLUDED is"
                        + " filtered out, REAL_CONFIG_EXCLUDED is not.")
                .containsExactly("KEPT", "REAL_CONFIG_EXCLUDED");
    }

    @Test
    void testCapture_reusedAcrossTwoTestsWithDifferentOverride_secondOverrideWins()
            throws Exception
    {
        // Simulates one RowCountChecker instance reused across two tests - e.g. a
        // DefaultPrepAndExpectedTestCase shared through a @DbUnitTestCase static field -
        // where the first test enables the check and the second explicitly disables it.
        final RowCountChecker checker = new RowCountChecker();

        checker.setEnabledOverride(true, new String[0]);
        final DatabaseConfig config1 = new DatabaseConfig();
        final IDatabaseConnection connection1 = mock(IDatabaseConnection.class);
        when(connection1.getConfig()).thenReturn(config1);
        final IDataSet dataSet1 = mock(IDataSet.class);
        when(dataSet1.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection1.createDataSet()).thenReturn(dataSet1);
        when(connection1.getRowCount("ACCOUNT")).thenReturn(5);
        checker.capture(connection1);
        assertThat(checker.hasBaseline()).as("First test's check was enabled.").isTrue();
        checker.verify(connection1);
        checker.discardBaseline();

        checker.setEnabledOverride(false, new String[0]);
        final IDatabaseConnection connection2 = mock(IDatabaseConnection.class);
        when(connection2.getConfig()).thenReturn(new DatabaseConfig());
        checker.capture(connection2);

        assertThat(checker.hasBaseline())
                .as("The second test's setEnabledOverride(false, ...) must take effect - the"
                        + " RowCountCheck resolved (and cached) for the first test must not be"
                        + " reused as-is for the second.")
                .isFalse();
        verify(connection2, never()).createDataSet();
    }

    @Test
    void testCapture_reusedAcrossTwoTestsSecondClearsOverride_fallsBackToConnectionConfig()
            throws Exception
    {
        // Simulates a testCase reused across two tests where only the first declares
        // @DbUnitRowCountCheck; the second must not silently inherit that override.
        final RowCountChecker checker = new RowCountChecker();

        checker.setEnabledOverride(false, new String[0]);
        final IDatabaseConnection connection1 = mock(IDatabaseConnection.class);
        when(connection1.getConfig()).thenReturn(new DatabaseConfig());
        checker.capture(connection1);
        assertThat(checker.hasBaseline())
                .as("The first test's setEnabledOverride(false, ...) must capture no baseline.")
                .isFalse();

        checker.clearEnabledOverride();
        final DatabaseConfig config2 = new DatabaseConfig();
        config2.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        final IDatabaseConnection connection2 = mock(IDatabaseConnection.class);
        when(connection2.getConfig()).thenReturn(config2);
        final IDataSet dataSet2 = mock(IDataSet.class);
        when(dataSet2.getTableNames()).thenReturn(new String[] {"ACCOUNT"});
        when(connection2.createDataSet()).thenReturn(dataSet2);
        when(connection2.getRowCount("ACCOUNT")).thenReturn(5);

        checker.capture(connection2);

        assertThat(checker.hasBaseline())
                .as("clearEnabledOverride() must return to resolving from the connection's own"
                        + " DatabaseConfig (enabled here), not keep the first test's disabled"
                        + " override.")
                .isTrue();
    }

    @Test
    void testCapture_rowCountCheckSetExplicitly_reusedAcrossTwoCapturesWithoutRebuilding()
            throws Exception
    {
        // setRowCountCheck() is a manual pin, unlike setEnabledOverride() - it must survive
        // reuse across tests exactly like databaseTester/dataFileLoader fields do, until
        // changed by another setRowCountCheck() call.
        final RowCountChecker checker = new RowCountChecker();
        final RowCountCheck mockRowCountCheck = mock(RowCountCheck.class);
        checker.setRowCountCheck(mockRowCountCheck);
        final IDatabaseConnection connection1 = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot1 = new RowCountSnapshot(Collections.emptyMap());
        when(mockRowCountCheck.capture(connection1)).thenReturn(snapshot1);

        checker.capture(connection1);
        final IDatabaseConnection connection2 = mock(IDatabaseConnection.class);
        final RowCountSnapshot snapshot2 = new RowCountSnapshot(Collections.emptyMap());
        when(mockRowCountCheck.capture(connection2)).thenReturn(snapshot2);

        checker.capture(connection2);

        assertThat(checker.getRowCountCheck())
                .as("A RowCountCheck supplied via setRowCountCheck() must still be used as-is"
                        + " on a second capture(), never rebuilt.")
                .isSameAs(mockRowCountCheck);
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
