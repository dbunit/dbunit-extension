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
package org.dbunit.database.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TestScopedConnectionTest
{
    private final ConnectionOwnership alwaysMayClose =
            new ConnectionOwnership(() -> true, () -> null, () -> true);
    private final ConnectionOwnership neverMayClose =
            new ConnectionOwnership(() -> false, () -> null, () -> true);

    private IDatabaseConnection openConnection() throws SQLException
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.isClosed()).thenReturn(false);
        when(jdbc.getAutoCommit()).thenReturn(true);
        return connection;
    }

    @Test
    void testGetConnection_calledTwice_acquiresOnceAndMemoizes() throws Exception
    {
        final IDatabaseConnection connection = openConnection();
        final AtomicInteger calls = new AtomicInteger();
        final TestScopedConnection holder = new TestScopedConnection(() ->
        {
            calls.incrementAndGet();
            return connection;
        }, alwaysMayClose, null);

        assertThat(holder.getConnection()).isSameAs(connection);
        assertThat(holder.getConnection()).isSameAs(connection);
        assertThat(calls).as("The supplier is called once, then the result is memoized.")
                .hasValue(1);
    }

    @Test
    void testGetConnection_memoizedConnectionSinceClosed_dropsItAndReacquires() throws Exception
    {
        final IDatabaseConnection dead = mock(IDatabaseConnection.class);
        final Connection deadJdbc = mock(Connection.class);
        when(dead.getConnection()).thenReturn(deadJdbc);
        when(deadJdbc.isClosed()).thenReturn(true);
        final IDatabaseConnection fresh = openConnection();
        final List<IDatabaseConnection> toReturn =
                new ArrayList<>(Arrays.asList(dead, fresh));
        final TestScopedConnection holder =
                new TestScopedConnection(() -> toReturn.remove(0), alwaysMayClose, null);

        assertThat(holder.getConnection()).as("First acquisition.").isSameAs(dead);
        assertThat(holder.getConnection())
                .as("The memoized connection now reports closed, so a fresh one is acquired.")
                .isSameAs(fresh);
    }

    @Test
    void testGetConnection_onAcquiredHook_runsOncePerAcquiredConnectionNotForNull()
            throws Exception
    {
        final List<IDatabaseConnection> seen = new ArrayList<>();
        final IDatabaseConnection connection = openConnection();
        final TestScopedConnection holder = new TestScopedConnection(() -> connection,
                alwaysMayClose, seen::add);

        holder.getConnection();
        holder.getConnection();

        assertThat(seen).as("onAcquired runs once, on the connection actually acquired.")
                .containsExactly(connection);
    }

    @Test
    void testRelease_ownershipAllows_closesAndForgets() throws Exception
    {
        final IDatabaseConnection connection = openConnection();
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        holder.release();

        verify(connection).close();
        assertThat(holder.isResolved()).as("A closed connection is forgotten.").isFalse();
    }

    @Test
    void testRelease_ownershipDeclines_leavesConnectionOpenAndMemoized() throws Exception
    {
        final IDatabaseConnection connection = openConnection();
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, neverMayClose, null);
        holder.getConnection();

        holder.release();

        verify(connection, never()).close();
        assertThat(holder.peekConnection()).as("The memo is kept for the connection's real owner.")
                .isSameAs(connection);
    }

    @Test
    void testRelease_connectionAlreadyClosed_doesNotCloseItAgain() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.isClosed()).thenReturn(true);
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        holder.release();

        verify(connection, never()).close();
    }

    @Test
    void testRelease_connectionAutoCommitOff_rollsBackBeforeClosing() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.isClosed()).thenReturn(false);
        when(jdbc.getAutoCommit()).thenReturn(false);
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        holder.release();

        final InOrder inOrder = inOrder(jdbc, connection);
        inOrder.verify(jdbc).rollback();
        inOrder.verify(connection).close();
    }

    @Test
    void testRelease_connectionAutoCommitOn_closesWithoutRollingBack() throws Exception
    {
        final IDatabaseConnection connection = openConnection();
        final Connection jdbc = connection.getConnection();
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        holder.release();

        verify(jdbc, never()).rollback();
        verify(connection).close();
    }

    @Test
    void testRelease_autoCommitOffAndRollbackFails_stillClosesTheConnection() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        when(jdbc.isClosed()).thenReturn(false);
        when(jdbc.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("rollback failed")).when(jdbc).rollback();
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        holder.release();

        verify(connection).close();
        assertThat(holder.isResolved())
                .as("A best-effort rollback failure does not stop the close or the forget.")
                .isFalse();
    }

    @Test
    void testRelease_isClosedCheckThrows_propagatesTheException() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final Connection jdbc = mock(Connection.class);
        when(connection.getConnection()).thenReturn(jdbc);
        final SQLException failure = new SQLException("connection lost");
        when(jdbc.isClosed()).thenThrow(failure);
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();

        assertThatThrownBy(holder::release)
                .as("A connection whose isClosed() throws has failed in a way worth"
                        + " surfacing, not swallowing.")
                .isSameAs(failure);
    }

    @Test
    void testReleaseSuppressing_closeFails_attachesItToPrimaryAndForgetsTheConnection()
            throws Exception
    {
        final IDatabaseConnection connection = openConnection();
        final SQLException closeFailure = new SQLException("close failed");
        doThrow(closeFailure).when(connection).close();
        final TestScopedConnection holder =
                new TestScopedConnection(() -> connection, alwaysMayClose, null);
        holder.getConnection();
        final RuntimeException primary = new RuntimeException("the real failure");

        holder.releaseSuppressing(primary);

        assertThat(primary.getSuppressed()).containsExactly(closeFailure);
        assertThat(holder.isResolved()).as("releaseSuppressing always forgets the connection.")
                .isFalse();
    }

    @Test
    void testDiscardOnFailure_thenGetConnection_reacquires() throws Exception
    {
        final AtomicInteger calls = new AtomicInteger();
        final Callable<IDatabaseConnection> source = () ->
        {
            calls.incrementAndGet();
            return mockOpen();
        };
        final TestScopedConnection holder =
                new TestScopedConnection(source, alwaysMayClose, null);

        holder.getConnection();
        holder.discardOnFailure();
        holder.getConnection();

        assertThat(calls).as("discardOnFailure() makes the next getConnection() re-acquire.")
                .hasValue(2);
    }

    @Test
    void testAdopt_takesOverAConnectionWithoutRunningOnAcquired()
    {
        final List<IDatabaseConnection> seen = new ArrayList<>();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        final TestScopedConnection holder = new TestScopedConnection(() ->
        {
            throw new AssertionError("source must not be called after adopt()");
        }, alwaysMayClose, seen::add);

        holder.adopt(connection);

        assertThat(holder.peekConnection()).isSameAs(connection);
        assertThat(holder.isResolved()).isTrue();
        assertThat(seen).as("adopt() does not run the onAcquired hook.").isEmpty();
    }

    @Test
    void testPeekConnection_beforeGetConnection_returnsNullWithoutAcquiring()
    {
        final TestScopedConnection holder = new TestScopedConnection(() ->
        {
            throw new AssertionError("peekConnection() must not acquire");
        }, alwaysMayClose, null);

        assertThat(holder.peekConnection()).isNull();
        assertThat(holder.isResolved()).isFalse();
    }

    @Test
    void testGetConnection_supplierReturnsNull_isResolvedButPeekConnectionIsNull()
            throws Exception
    {
        final TestScopedConnection holder =
                new TestScopedConnection(() -> null, alwaysMayClose, null);

        assertThat(holder.getConnection()).isNull();
        assertThat(holder.isResolved()).isTrue();
        assertThat(holder.peekConnection()).isNull();
    }

    private static IDatabaseConnection mockOpen()
    {
        try
        {
            final IDatabaseConnection connection = mock(IDatabaseConnection.class);
            final Connection jdbc = mock(Connection.class);
            when(connection.getConnection()).thenReturn(jdbc);
            when(jdbc.isClosed()).thenReturn(false);
            when(jdbc.getAutoCommit()).thenReturn(true);
            return connection;
        } catch (final SQLException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
