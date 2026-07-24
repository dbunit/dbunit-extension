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

import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DataSourceDatabaseTester}, run against a real
 * H2 in-memory {@link DataSource}, focused on its opt-in
 * {@link CachingConnectionProvider} integration.
 *
 * @since 3.4.0
 */
class DataSourceDatabaseTesterIT
{
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
        final DataSourceDatabaseTester tester = new DataSourceDatabaseTester(newDataSource());

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
        final DataSourceDatabaseTester tester =
                new DataSourceDatabaseTester(newDataSource(), null, provider);

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
        final DataSourceDatabaseTester tester =
                new DataSourceDatabaseTester(newDataSource(), null, provider);

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
    void testConstructor_withNullDataSourceAndProvider_throwsNullPointerException()
    {
        assertThatThrownBy(
                () -> new DataSourceDatabaseTester(null, null, new CachingConnectionProvider()))
                        .as("The 3-arg constructor must reject a null DataSource just like the "
                                + "pre-existing constructors do.")
                        .isInstanceOf(NullPointerException.class);
    }

    private static DataSource newDataSource()
    {
        final JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl(
                "jdbc:h2:mem:datasourcetester_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
