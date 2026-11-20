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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dbunit.AbstractDatabaseTester;
import org.dbunit.DatabaseUnitRuntimeException;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.connection.ConnectionOwnership;
import org.dbunit.database.connection.TestScopedConnection;
import org.dbunit.database.rowcount.ClearRowCountCheckSystemProperties;
import org.dbunit.dataset.IDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ClearRowCountCheckSystemProperties
class ClassicRowCountCheckTest
{
    private final ConnectionOwnership alwaysMayClose =
            new ConnectionOwnership(() -> true, () -> null);

    // ---- canPiggybackBaselineOn(): the decision matrix ----

    @Test
    void testCanPiggybackBaselineOn_listenerInstalledRealTesterNonNoneOpNothingResolved_true()
    {
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new InheritingTester(), holderThatMustNotAcquire(), () -> true);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("A piggyback is safe: a listener is installed, an AbstractDatabaseTester"
                        + " that does not override onSetup(), a non-NONE operation, and no"
                        + " connection resolved yet.")
                .isTrue();
    }

    @Test
    void testCanPiggybackBaselineOn_listenerReplacedAfterConstruction_false()
    {
        final AtomicBoolean listenerInPlace = new AtomicBoolean(true);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new InheritingTester(), holderThatMustNotAcquire(), listenerInPlace::get);
        listenerInPlace.set(false);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("Whether the installed listener is still there is read when the setup is"
                        + " about to run: one replaced since, as a @BeforeEach method can, would"
                        + " never pass onSetup()'s connection on.")
                .isFalse();
    }

    @Test
    void testCanPiggybackBaselineOn_noListenerInstalled_false()
    {
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new InheritingTester(), holderThatMustNotAcquire(), () -> false);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("A tester that cannot report its listener gets none installed, so there is"
                        + " nothing to piggyback on.")
                .isFalse();
    }

    @Test
    void testCanPiggybackBaselineOn_connectionAlreadyResolved_false() throws Exception
    {
        final TestScopedConnection holder = new TestScopedConnection(
                () -> mock(IDatabaseConnection.class), alwaysMayClose, null);
        holder.getConnection();
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new InheritingTester(), holder, () -> true);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("A connection is already memoized (e.g. from a @BeforeEach parameter);"
                        + " piggybacking on a second, different one would orphan it.")
                .isFalse();
    }

    @Test
    void testCanPiggybackBaselineOn_customTester_false()
    {
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holderThatMustNotAcquire(), () -> true);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("A custom IDatabaseTester is not assumed to route through executeOperation()"
                        + " and notify the listener.")
                .isFalse();
    }

    @Test
    void testCanPiggybackBaselineOn_testerOverridesOnSetup_false()
    {
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new OverridingOnSetupTester(), holderThatMustNotAcquire(), () -> true);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.CLEAN_INSERT))
                .as("A tester that overrides onSetup() might apply the operation some other"
                        + " way, so the listener is not guaranteed to fire before it.")
                .isFalse();
    }

    @Test
    void testCanPiggybackBaselineOn_noneOperation_false()
    {
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                new InheritingTester(), holderThatMustNotAcquire(), () -> true);

        assertThat(check.canPiggybackBaselineOn(DatabaseOperation.NONE))
                .as("A NONE operation makes executeOperation() retrieve no connection.")
                .isFalse();
    }

    // ---- capture / verify ----

    @Test
    void testCaptureBaselineEagerly_nullConnection_marksAttemptedAndCapturesNothing()
            throws Exception
    {
        final TestScopedConnection holder =
                new TestScopedConnection(() -> null, alwaysMayClose, null);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holder, () -> true);

        check.captureBaselineEagerly();

        assertThat(check.isBaselineAttempted())
                .as("A null connection (e.g. a test double) still counts as an attempt, so the"
                        + " prediction is never retried later.")
                .isTrue();
    }

    @Test
    void testVerify_noBaselineCaptured_isANoOp() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holder, () -> true);

        check.verify();

        assertThat(check.isBaselineAttempted())
                .as("verify() with no baseline never touches the connection or the checker.")
                .isFalse();
    }

    @Test
    void testVerify_baselineCapturedButConnectionGoneAtVerifyTime_isASilentNoOp()
            throws Exception
    {
        final IDatabaseConnection captured = mock(IDatabaseConnection.class);
        stubRowCountCheckConnection(captured, "ACCOUNT", 5);
        final Connection jdbc = mock(Connection.class);
        when(captured.getConnection()).thenReturn(jdbc);
        when(jdbc.isClosed()).thenReturn(true);
        final TestScopedConnection holder =
                new TestScopedConnection(() -> null, alwaysMayClose, null);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holder, () -> true);
        check.adoptAndCaptureBaselineOnFirstConnection(captured);

        assertThatCode(check::verify)
                .as("The pool killed the connection between onSetup() and onTearDown() and the"
                        + " re-acquire yields nothing; verify() skips silently rather than"
                        + " throwing.")
                .doesNotThrowAnyException();
    }

    @Test
    void testAdoptAndCaptureBaselineOnFirstConnection_calledTwice_secondCallIsANoOp()
            throws Exception
    {
        final IDatabaseConnection first = mock(IDatabaseConnection.class);
        stubRowCountCheckConnection(first, "ACCOUNT", 5);
        final IDatabaseConnection second = mock(IDatabaseConnection.class);
        final TestScopedConnection holder = new TestScopedConnection(
                () -> mock(IDatabaseConnection.class), alwaysMayClose, null);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holder, () -> true);

        check.adoptAndCaptureBaselineOnFirstConnection(first);
        check.adoptAndCaptureBaselineOnFirstConnection(second);

        assertThat(holder.peekConnection())
                .as("The first connection is adopted into the shared holder; a later call"
                        + " (e.g. onTearDown() notifying the same listener) does not replace it.")
                .isSameAs(first);
    }

    @Test
    void testAdoptAndCaptureBaselineOnFirstConnection_captureFails_wrapsInRuntimeException()
            throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        when(connection.getConfig()).thenReturn(config);
        when(connection.createDataSet()).thenThrow(new SQLException("connection lost"));
        final TestScopedConnection holder = new TestScopedConnection(
                () -> mock(IDatabaseConnection.class), alwaysMayClose, null);
        final ClassicRowCountCheck check = new ClassicRowCountCheck(
                mock(IDatabaseTester.class), holder, () -> true);

        assertThatThrownBy(() -> check.adoptAndCaptureBaselineOnFirstConnection(connection))
                .as("This route is reached from a listener callback that declares no checked"
                        + " exception, so a capture failure surfaces unchecked.")
                .isInstanceOf(DatabaseUnitRuntimeException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    // ---- helpers ----

    private static void stubRowCountCheckConnection(final IDatabaseConnection connection,
            final String tableName, final int rowCount) throws Exception
    {
        final DatabaseConfig config = new DatabaseConfig();
        config.setFeature(DatabaseConfig.FEATURE_ROW_COUNT_CHECK, true);
        lenient().when(connection.getConfig()).thenReturn(config);
        final IDataSet dataSet = mock(IDataSet.class);
        lenient().when(dataSet.getTableNames()).thenReturn(new String[] {tableName});
        lenient().when(connection.createDataSet()).thenReturn(dataSet);
        lenient().when(connection.getRowCount(tableName)).thenReturn(rowCount);
    }

    private TestScopedConnection holderThatMustNotAcquire()
    {
        return new TestScopedConnection(() ->
        {
            throw new AssertionError("canPiggybackBaselineOn() must not acquire a connection");
        }, alwaysMayClose, null);
    }

    /** An {@link AbstractDatabaseTester} that inherits {@code onSetup()}. */
    private static final class InheritingTester extends AbstractDatabaseTester
    {
        @Override
        public IDatabaseConnection getConnection()
        {
            return mock(IDatabaseConnection.class);
        }
    }

    /** An {@link AbstractDatabaseTester} that overrides {@code onSetup()}. */
    private static final class OverridingOnSetupTester extends AbstractDatabaseTester
    {
        @Override
        public void onSetup()
        {
        }

        @Override
        public IDatabaseConnection getConnection()
        {
            return mock(IDatabaseConnection.class);
        }
    }
}
