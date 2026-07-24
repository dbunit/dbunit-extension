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

import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JdbcDatabaseTester}, run against a real H2 in-memory database, focused on
 * its opt-in {@link CachingConnectionProvider} integration.
 *
 * @since 3.4.0
 */
class JdbcDatabaseTesterTest
{
    private static final String DRIVER_CLASS = "org.h2.Driver";

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private IDatabaseConnection openedConnection;

    @AfterEach
    void closeOpenedConnection() throws Exception
    {
        if (openedConnection != null)
        {
            openedConnection.close();
        }
    }

    @Test
    void testGetConnection_withoutProvider_returnsDifferentInstanceOnEachCall() throws Exception
    {
        final JdbcDatabaseTester tester =
                new JdbcDatabaseTester(DRIVER_CLASS, nextConnectionUrl());

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
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final JdbcDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS,
                nextConnectionUrl(), null, null, null, provider);

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
        final CachingConnectionProvider provider = new CachingConnectionProvider();
        final JdbcDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS,
                nextConnectionUrl(), null, null, null, provider);

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
    void testGetConnection_withProviderSharedAcrossTesterInstances_reusesConnection() throws Exception
    {
        final CachingConnectionProvider sharedProvider = new CachingConnectionProvider();
        final String url = nextConnectionUrl();

        final JdbcDatabaseTester firstTester =
                new JdbcDatabaseTester(DRIVER_CLASS, url, null, null, null, sharedProvider);
        final IDatabaseConnection fromFirstTester = firstTester.getConnection();

        final JdbcDatabaseTester secondTester =
                new JdbcDatabaseTester(DRIVER_CLASS, url, null, null, null, sharedProvider);
        final IDatabaseConnection fromSecondTester = secondTester.getConnection();

        openedConnection = fromSecondTester;
        assertThat(fromSecondTester)
                .as("A CachingConnectionProvider shared across separate tester instances - as "
                        + "happens when each test method builds a fresh tester - must still hand "
                        + "back the one cached connection.")
                .isSameAs(fromFirstTester);
    }

    private static String nextConnectionUrl()
    {
        return "jdbc:h2:mem:jdbctester_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
    }
}
