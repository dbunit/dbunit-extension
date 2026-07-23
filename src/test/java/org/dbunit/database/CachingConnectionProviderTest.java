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

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachingConnectionProvider}, run against a real H2 in-memory
 * {@link IDatabaseConnection} (via {@link InMemoryDatabaseConnection}) for the caching and
 * liveness-detection scenarios, and against Mockito-mocked collaborators for the scenarios that
 * need to force a specific {@link Connection#isValid(int)} outcome.
 *
 * @since 3.4.0
 */
class CachingConnectionProviderTest
{
    private final List<IDatabaseConnection> realConnections = new ArrayList<>();

    @AfterEach
    void closeRealConnections()
    {
        for (final IDatabaseConnection connection : realConnections)
        {
            try
            {
                connection.close();
            } catch (final SQLException e)
            {
                // Already closed by the test itself - nothing left to clean up.
            }
        }
    }

    @Test
    void testGetConnection_firstCall_invokesFactoryOnce() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(newRealConnection());

        provider.getConnection(factory);

        verify(factory, times(1)).call();
    }

    @Test
    void testGetConnection_firstCall_returnsFactoryResult() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection created = newRealConnection();

        final IDatabaseConnection returned = provider.getConnection(() -> created);

        assertThat(returned).as("The first call must return the factory's connection.")
                .isSameAs(created);
    }

    @Test
    void testGetConnection_subsequentCallsWhileAlive_returnsSameCachedInstance() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final Callable<IDatabaseConnection> factory = this::newRealConnection;

        final IDatabaseConnection first = provider.getConnection(factory);
        final IDatabaseConnection second = provider.getConnection(factory);

        assertThat(second).as("A live cached connection must be reused, not recreated.")
                .isSameAs(first);
    }

    @Test
    void testGetConnection_subsequentCallsWhileAlive_doesNotInvokeFactoryAgain() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(newRealConnection());

        provider.getConnection(factory);
        provider.getConnection(factory);
        provider.getConnection(factory);

        verify(factory, times(1)).call();
    }

    @Test
    void testGetConnection_afterCachedConnectionClosed_returnsNewConnectionInstance() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final Callable<IDatabaseConnection> factory = this::newRealConnection;

        final IDatabaseConnection first = provider.getConnection(factory);
        first.getConnection().close();

        final IDatabaseConnection second = provider.getConnection(factory);

        assertThat(second).as("A closed cached connection must be replaced by a new one.")
                .isNotSameAs(first);
        assertThat(second.getConnection().isClosed())
                .as("The replacement connection must be open.").isFalse();
    }

    @Test
    void testGetConnection_afterCachedConnectionClosed_closesTheStaleConnection() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection stale = spy(newRealConnection());
        final IDatabaseConnection replacement = newRealConnection();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(stale, replacement);

        provider.getConnection(factory);
        stale.getConnection().close();
        provider.getConnection(factory);

        verify(stale, times(1)).close();
    }

    @Test
    void testGetConnection_whenIsValidReturnsFalse_replacesConnection() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection notValid = mockConnection(false, false);
        final IDatabaseConnection replacement = newRealConnection();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(notValid, replacement);

        provider.getConnection(factory);
        final IDatabaseConnection second = provider.getConnection(factory);

        assertThat(second)
                .as("A connection that reports itself as no longer valid must be replaced, "
                        + "even though it was never explicitly closed.")
                .isSameAs(replacement);
    }

    @Test
    void testGetConnection_whenIsValidThrows_treatsConnectionAsDeadAndReplacesIt() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.isClosed()).thenReturn(false);
        when(jdbcConnection.isValid(anyInt()))
                .thenThrow(new SQLException("driver blew up validating the connection"));
        final IDatabaseConnection broken = mock(IDatabaseConnection.class);
        when(broken.getConnection()).thenReturn(jdbcConnection);
        final IDatabaseConnection replacement = newRealConnection();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(broken, replacement);

        provider.getConnection(factory);
        final IDatabaseConnection second = provider.getConnection(factory);

        assertThat(second)
                .as("A connection whose liveness check throws must be treated as dead, not "
                        + "propagate the SQLException out of getConnection().")
                .isSameAs(replacement);
    }

    @Test
    void testGetConnection_withDefaultConstructor_passesDefaultTimeoutToIsValid() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection first = mockConnection(false, true);

        provider.getConnection(() -> first);
        provider.getConnection(() -> first);

        verify(first.getConnection())
                .isValid(eq(CachingConnectionProvider.DEFAULT_VALIDATION_TIMEOUT_SECONDS));
    }

    @Test
    void testGetConnection_withCustomValidationTimeout_passesItToIsValid() throws Exception
    {
        final int customTimeoutSeconds = 42;
        final CachingConnectionProvider provider =
                new CachingConnectionProvider(customTimeoutSeconds);
        final IDatabaseConnection first = mockConnection(false, true);

        provider.getConnection(() -> first);
        provider.getConnection(() -> first);

        verify(first.getConnection()).isValid(eq(customTimeoutSeconds));
    }

    @Test
    void testConstructor_withNegativeValidationTimeout_throwsIllegalArgumentException()
    {
        assertThatThrownBy(() -> new CachingConnectionProvider(-1))
                .as("A negative validation timeout is nonsensical and must be rejected eagerly, "
                        + "rather than failing confusingly inside a later liveness check.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetConnection_whenFactoryThrowsOnFirstCall_propagatesException() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final SQLException factoryFailure = new SQLException("cannot connect");
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenThrow(factoryFailure);

        assertThatThrownBy(() -> provider.getConnection(factory))
                .as("A factory failure on the very first call must propagate, not be swallowed.")
                .isSameAs(factoryFailure);
    }

    @Test
    void testGetConnection_afterFactoryThrowsOnFirstCall_retriesOnNextCall() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection created = newRealConnection();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenThrow(new SQLException("cannot connect")).thenReturn(created);

        assertThatThrownBy(() -> provider.getConnection(factory))
                .as("The first attempt's factory failure must surface as-is.")
                .isInstanceOf(SQLException.class);
        final IDatabaseConnection second = provider.getConnection(factory);

        assertThat(second)
                .as("A failed first attempt must not permanently poison the cache; the next call "
                        + "must retry via the factory.")
                .isSameAs(created);
    }

    @Test
    void testGetConnection_whenFactoryThrowsWhileReplacingDeadConnection_propagatesException()
            throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection dead = mockConnection(true, false);
        final SQLException replacementFailure = new SQLException("still cannot connect");
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(dead).thenThrow(replacementFailure);

        provider.getConnection(factory);

        assertThatThrownBy(() -> provider.getConnection(factory))
                .as("A factory failure while replacing a dead connection must propagate.")
                .isSameAs(replacementFailure);
    }

    @Test
    void testGetConnection_whenClosingDeadConnectionThrows_stillReturnsReplacement() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection dead = mockConnection(true, false);
        doThrowOnClose(dead, new SQLException("close failed"));
        final IDatabaseConnection replacement = newRealConnection();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(dead, replacement);

        provider.getConnection(factory);

        assertThatCode(() -> {
            final IDatabaseConnection second = provider.getConnection(factory);
            assertThat(second)
                    .as("Failing to close the stale connection must not prevent the provider "
                            + "from handing back a working replacement.")
                    .isSameAs(replacement);
        }).as("close() failing on the stale connection must be swallowed, not propagated.")
                .doesNotThrowAnyException();
    }

    @Test
    void testGetConnection_manyConcurrentCallers_invokesFactoryExactlyOnce() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final int callerCount = 16;
        final AtomicInteger invocationCount = new AtomicInteger();
        final Callable<IDatabaseConnection> factory = () -> {
            invocationCount.incrementAndGet();
            return newRealConnection();
        };
        final CountDownLatch startingGate = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        try
        {
            final List<Future<IDatabaseConnection>> futures = new ArrayList<>();
            for (int i = 0; i < callerCount; i++)
            {
                futures.add(executor.submit(() -> {
                    startingGate.await();
                    return provider.getConnection(factory);
                }));
            }
            startingGate.countDown();

            final Set<IDatabaseConnection> distinctConnections = new HashSet<>();
            for (final Future<IDatabaseConnection> future : futures)
            {
                distinctConnections.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(invocationCount.get())
                    .as("Concurrent first-time callers must not race each other into creating "
                            + "more than one connection.")
                    .isEqualTo(1);
            assertThat(distinctConnections)
                    .as("Every concurrent caller must receive the exact same cached connection.")
                    .hasSize(1);
        } finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    void testClose_withCachedConnection_closesIt() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection cached = newRealConnection();
        provider.getConnection(() -> cached);

        provider.close();

        assertThat(cached.getConnection().isClosed())
                .as("close() must close the cached connection.").isTrue();
    }

    @Test
    void testClose_whenCloseThrows_stillClearsCacheButPropagatesException() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection cached = mockConnection(false, true);
        final SQLException closeFailure = new SQLException("close failed");
        doThrowOnClose(cached, closeFailure);
        provider.getConnection(() -> cached);

        assertThatThrownBy(provider::close)
                .as("Unlike the internal replacement path, the public close() method must "
                        + "propagate a close failure to the caller rather than swallow it.")
                .isSameAs(closeFailure);

        final IDatabaseConnection replacement = newRealConnection();
        final IDatabaseConnection afterFailedClose = provider.getConnection(() -> replacement);
        assertThat(afterFailedClose)
                .as("Even though close() propagated the failure, the cache must have already "
                        + "been cleared, so the next call creates a fresh connection rather than "
                        + "reusing (or re-attempting to close) the one that failed to close.")
                .isSameAs(replacement);
    }

    @Test
    void testClose_withCachedConnection_clearsCacheSoNextCallInvokesFactoryAgain() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        @SuppressWarnings("unchecked")
        final Callable<IDatabaseConnection> factory = mock(Callable.class);
        when(factory.call()).thenReturn(newRealConnection(), newRealConnection());

        provider.getConnection(factory);
        provider.close();
        provider.getConnection(factory);

        verify(factory, times(2)).call();
    }

    @Test
    void testClose_withNoCachedConnection_doesNotThrow()
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();

        assertThatCode(provider::close)
                .as("close() with nothing cached yet must be a harmless no-op.")
                .doesNotThrowAnyException();
    }

    @Test
    void testGetConnection_afterClose_doesNotReuseThePreviouslyCachedConnection() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final IDatabaseConnection first = newRealConnection();
        provider.getConnection(() -> first);
        provider.close();

        final IDatabaseConnection second = provider.getConnection(this::newRealConnection);

        assertThat(second).as("After close(), the next call must create a fresh connection.")
                .isNotSameAs(first);
    }

    /**
     * Creates a real H2 in-memory connection and registers it for automatic cleanup in
     * {@link #closeRealConnections()}.
     */
    private IDatabaseConnection newRealConnection() throws Exception
    {
        final IDatabaseConnection connection = InMemoryDatabaseConnection.create();
        realConnections.add(connection);
        return connection;
    }

    /**
     * Creates a mocked {@link IDatabaseConnection} whose underlying {@link Connection} reports the
     * given closed/valid state.
     */
    private static IDatabaseConnection mockConnection(final boolean closed, final boolean valid)
            throws SQLException
    {
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.isClosed()).thenReturn(closed);
        when(jdbcConnection.isValid(anyInt())).thenReturn(valid);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConnection()).thenReturn(jdbcConnection);
        return connection;
    }

    private static void doThrowOnClose(final IDatabaseConnection connection, final SQLException failure)
            throws SQLException
    {
        doThrow(failure).when(connection).close();
    }
}
