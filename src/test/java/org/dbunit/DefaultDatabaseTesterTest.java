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
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultDatabaseTester}, run against real H2 in-memory connections (via
 * {@link InMemoryDatabaseConnection}).
 *
 * @since 3.4.0
 */
class DefaultDatabaseTesterTest
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
            } catch (final Exception e)
            {
                // Already closed by the test itself - nothing left to clean up.
            }
        }
    }

    @Test
    void testGetConnection_withRawConnectionConstructor_alwaysReturnsSameInstance()
            throws Exception
    {
        final IDatabaseConnection connection = newRealConnection();
        final DefaultDatabaseTester tester = new DefaultDatabaseTester(connection);

        assertThat(tester.getConnection())
                .as("DefaultDatabaseTester's original constructor is a dumb holder: it must "
                        + "always return the exact connection instance it was given, with no "
                        + "liveness check.")
                .isSameAs(connection);
        assertThat(tester.getConnection()).isSameAs(connection);
    }

    @Test
    void testGetConnection_withProviderConstructor_reusesConnectionWhileAlive() throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final DefaultDatabaseTester tester =
                new DefaultDatabaseTester(provider, this::newRealConnection);

        final IDatabaseConnection first = tester.getConnection();
        final IDatabaseConnection second = tester.getConnection();

        assertThat(second)
                .as("With a CachingConnectionProvider, calls must reuse the cached connection.")
                .isSameAs(first);
    }

    @Test
    void testGetConnection_withProviderConstructor_afterConnectionDies_returnsNewConnection()
            throws Exception
    {
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final DefaultDatabaseTester tester =
                new DefaultDatabaseTester(provider, this::newRealConnection);

        final IDatabaseConnection first = tester.getConnection();
        first.getConnection().close();

        final IDatabaseConnection second = tester.getConnection();

        assertThat(second).as("A dead cached connection must be transparently replaced.")
                .isNotSameAs(first);
        assertThat(second.getConnection().isClosed())
                .as("The replacement connection must be open.").isFalse();
    }

    @Test
    void testConstructor_withNullConnectionProvider_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new DefaultDatabaseTester(null, this::newRealConnection))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testConstructor_withNullConnectionFactory_throwsNullPointerException()
    {
        assertThatThrownBy(() -> new DefaultDatabaseTester(new CachingConnectionProvider(), null))
                .isInstanceOf(NullPointerException.class);
    }

    private IDatabaseConnection newRealConnection() throws Exception
    {
        final IDatabaseConnection connection = InMemoryDatabaseConnection.create();
        realConnections.add(connection);
        return connection;
    }
}
