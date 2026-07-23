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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.CachingConnectionProvider;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PropertiesBasedJdbcDatabaseTester}, run against a real H2 in-memory
 * database, focused on its opt-in {@link CachingConnectionProvider} integration.
 *
 * @since 3.4.0
 */
class PropertiesBasedJdbcDatabaseTesterTest
{
    private static final String[] PROPERTY_KEYS = {
            PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS,
            PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL,
            PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME,
            PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD,
            PropertiesBasedJdbcDatabaseTester.DBUNIT_SCHEMA};

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private final Map<String, String> savedProperties = new HashMap<>();

    private IDatabaseConnection openedConnection;

    @BeforeEach
    void saveAndSetProperties()
    {
        for (final String key : PROPERTY_KEYS)
        {
            savedProperties.put(key, System.getProperty(key));
        }

        System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.h2.Driver");
        System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL,
                "jdbc:h2:mem:propsjdbctester_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        System.clearProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME);
        System.clearProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD);
        System.clearProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_SCHEMA);
    }

    @AfterEach
    void closeConnectionAndRestoreProperties() throws Exception
    {
        if (openedConnection != null)
        {
            openedConnection.close();
        }

        for (final String key : PROPERTY_KEYS)
        {
            final String savedValue = savedProperties.get(key);
            if (savedValue == null)
            {
                System.clearProperty(key);
            } else
            {
                System.setProperty(key, savedValue);
            }
        }
    }

    @Test
    void testGetConnection_withoutProvider_returnsDifferentInstanceOnEachCall() throws Exception
    {
        final PropertiesBasedJdbcDatabaseTester tester = new PropertiesBasedJdbcDatabaseTester();

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
        final PropertiesBasedJdbcDatabaseTester tester =
                new PropertiesBasedJdbcDatabaseTester(provider);

        final IDatabaseConnection first = tester.getConnection();
        final IDatabaseConnection second = tester.getConnection();

        openedConnection = second;
        assertThat(second)
                .as("With a CachingConnectionProvider, calls must reuse the cached connection.")
                .isSameAs(first);
    }
}
