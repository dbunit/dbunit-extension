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

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryJndiContextFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link JndiDatabaseTester}, run against a real
 * {@link javax.naming.InitialContext} (backed by the in-memory
 * {@link InMemoryJndiContextFactory}) resolving a real H2 in-memory
 * {@link DataSource}, focused on its opt-in
 * {@link CachingConnectionProvider} integration.
 *
 * @since 3.4.0
 */
class JndiDatabaseTesterIT
{
    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private IDatabaseConnection openedConnection;
    private String boundName;

    @AfterEach
    void closeOpenedConnection() throws Exception
    {
        if (openedConnection != null)
        {
            openedConnection.close();
        }
        if (boundName != null)
        {
            InMemoryJndiContextFactory.unbind(boundName);
        }
    }

    @Test
    void testGetConnection_withoutProvider_returnsDifferentInstanceOnEachCall() throws Exception
    {
        final JndiDatabaseTester tester = tester(null);

        final IDatabaseConnection first = tester.getConnection();
        final IDatabaseConnection second = tester.getConnection();

        assertThat(second)
                .as("Without a CachingConnectionProvider, every call must create a fresh "
                        + "connection, matching pre-existing behavior.")
                .isNotSameAs(first);
        first.close();
        second.close();
    }

    @Test
    void testGetConnection_withProvider_returnsSameInstanceOnEachCall() throws Exception
    {
        final JndiDatabaseTester tester = tester(new CachingConnectionProvider());

        final IDatabaseConnection first = tester.getConnection();
        final IDatabaseConnection second = tester.getConnection();

        openedConnection = second;
        assertThat(second)
                .as("With a CachingConnectionProvider, calls must reuse the cached connection.")
                .isSameAs(first);
    }

    @Test
    void testGetConnection_withProvider_afterUnderlyingConnectionDies_returnsNewWorkingConnection()
            throws Exception
    {
        final JndiDatabaseTester tester = tester(new CachingConnectionProvider());

        final IDatabaseConnection first = tester.getConnection();
        first.getConnection().close();

        final IDatabaseConnection second = tester.getConnection();

        openedConnection = second;
        assertThat(second).as("A dead cached connection must be transparently replaced.")
                .isNotSameAs(first);
        assertThat(second.getConnection().createStatement().execute("SELECT 1"))
                .as("The replacement connection must be a real, working JDBC connection.")
                .isTrue();
    }

    @Test
    void testGetConnection_withProvider_looksUpJndiOnlyOnceEvenAcrossConnectionReplacement()
            throws Exception
    {
        final JdbcDataSource dataSource = newDataSource();
        final String lookupName = InMemoryJndiContextFactory.bind(dataSource);
        boundName = lookupName;
        final Properties environment = InMemoryJndiContextFactory.environment();
        final JndiDatabaseTester tester =
                new JndiDatabaseTester(environment, lookupName, null, new CachingConnectionProvider());

        tester.getConnection();
        tester.getConnection().getConnection().close();
        final IDatabaseConnection third = tester.getConnection();

        openedConnection = third;
        assertThat(InMemoryJndiContextFactory.lookupCount(lookupName))
                .as("JndiDatabaseTester's pre-existing initialize()/initialized-flag memoization "
                        + "must still resolve the DataSource via JNDI only once, even across "
                        + "multiple getConnection() calls and a CachingConnectionProvider "
                        + "replacing a dead connection in between.")
                .isEqualTo(1);
    }

    private JndiDatabaseTester tester(final CachingConnectionProvider provider)
    {
        final String lookupName = InMemoryJndiContextFactory.bind(newDataSource());
        boundName = lookupName;
        final Properties environment = InMemoryJndiContextFactory.environment();
        return new JndiDatabaseTester(environment, lookupName, null, provider);
    }

    private static JdbcDataSource newDataSource()
    {
        final JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl(
                "jdbc:h2:mem:jnditester_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
