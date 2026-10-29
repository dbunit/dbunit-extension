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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.JndiDatabaseTester;
import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

class FixedConnectionCheckTest
{
    private static final String DRIVER_CLASS = "org.h2.Driver";
    private static final String CONNECTION_URL = "jdbc:h2:mem:fixed_connection_check";
    private static final String LOOKUP_NAME = "java:comp/env/jdbc/dbunit";

    @Test
    void testGetAsBoolean_jdbcTesterWithoutProvider_returnsFalse() throws Exception
    {
        final IDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL);

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A JdbcDatabaseTester without a CachingConnectionProvider opens a new"
                        + " connection on every call.")
                .isFalse();
    }

    @Test
    void testGetAsBoolean_jdbcTesterWithProvider_returnsTrue() throws Exception
    {
        final IDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL, null,
                null, null, new CachingConnectionProvider());

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A JdbcDatabaseTester over a CachingConnectionProvider hands out its cached"
                        + " connection again.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_jdbcTesterSubclassReportingItReusesItsConnection_returnsTrue()
            throws Exception
    {
        final IDatabaseTester tester = new JdbcDatabaseTester(DRIVER_CLASS, CONNECTION_URL)
        {
            @Override
            public boolean isReusingConnection()
            {
                return true;
            }
        };

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A subclass that overrides isReusingConnection() says what its connections"
                        + " are.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_dataSourceTesterWithoutProvider_returnsFalse()
    {
        final IDatabaseTester tester = new DataSourceDatabaseTester(mock(DataSource.class));

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A DataSourceDatabaseTester without a CachingConnectionProvider asks its"
                        + " DataSource for a connection on every call.")
                .isFalse();
    }

    @Test
    void testGetAsBoolean_dataSourceTesterWithProvider_returnsTrue()
    {
        final IDatabaseTester tester = new DataSourceDatabaseTester(mock(DataSource.class), null,
                new CachingConnectionProvider());

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A DataSourceDatabaseTester over a CachingConnectionProvider hands out its"
                        + " cached connection again.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_jndiTesterWithoutProvider_returnsFalse()
    {
        final IDatabaseTester tester = new JndiDatabaseTester(LOOKUP_NAME);

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A JndiDatabaseTester without a CachingConnectionProvider asks its DataSource"
                        + " for a connection on every call.")
                .isFalse();
    }

    @Test
    void testGetAsBoolean_jndiTesterWithProvider_returnsTrue()
    {
        final IDatabaseTester tester =
                new JndiDatabaseTester(null, LOOKUP_NAME, null, new CachingConnectionProvider());

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A JndiDatabaseTester over a CachingConnectionProvider hands out its cached"
                        + " connection again.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_testerOverOneFixedConnection_returnsTrue()
    {
        final IDatabaseTester tester = new DefaultDatabaseTester(mock(IDatabaseConnection.class));

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A DefaultDatabaseTester returns the same connection from every call.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_customTesterReportingItReusesItsConnection_returnsTrue()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);
        when(tester.isReusingConnection()).thenReturn(true);

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A tester of any kind that reports it reuses its connection hands out one"
                        + " fixed connection.")
                .isTrue();
    }

    @Test
    void testGetAsBoolean_testerNotKnownToTheCheck_returnsFalse()
    {
        final IDatabaseTester tester = mock(IDatabaseTester.class);

        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(tester).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("A tester written for another source may open a new connection on every call,"
                        + " so it is not known to hand out one fixed connection.")
                .isFalse();
    }

    @Test
    void testGetAsBoolean_noTester_returnsFalse()
    {
        final boolean handsOutOneFixedConnection = new FixedConnectionCheck(null).getAsBoolean();

        assertThat(handsOutOneFixedConnection)
                .as("Without a tester there is no connection to hand out.")
                .isFalse();
    }
}
