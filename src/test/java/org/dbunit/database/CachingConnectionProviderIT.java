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

import java.sql.Connection;
import java.sql.DriverManager;

import org.dbunit.DatabaseEnvironment;
import org.dbunit.DatabaseProfile;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link CachingConnectionProvider} against whichever real database is
 * configured by the active Maven profile (see {@link DatabaseEnvironment}), proving the liveness
 * detection and reconnection logic against a real JDBC driver, not just the H2 in-memory driver
 * used by {@link CachingConnectionProviderTest}.
 *
 * <p>
 * Builds its own independent JDBC connections directly from the {@link DatabaseProfile} rather
 * than going through {@link DatabaseEnvironment#getConnection()}'s shared singleton, so it cannot
 * interfere with the connection lifecycle other IT classes depend on.
 *
 * @since 3.4.0
 */
class CachingConnectionProviderIT
{
    private DatabaseProfile profile;

    private CachingConnectionProvider provider;

    @BeforeEach
    void setUp() throws Exception
    {
        // assign provider first so tearDown()'s provider.close() is always
        // safe to call, even if the profile lookup or driver loading below
        // fails partway through setUp()
        provider = new CachingConnectionProvider();
        profile = DatabaseEnvironment.getInstance().getProfile();
        Class.forName(profile.getDriverClass());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        provider.close();
    }

    @Test
    void testGetConnection_calledTwice_returnsSameConnectionAndPreservesMetadataCache()
            throws Exception
    {
        final IDatabaseConnection first = provider.getConnection(this::createConnection);
        final IDataSet firstDataSet = first.createDataSet();

        final IDatabaseConnection second = provider.getConnection(this::createConnection);
        final IDataSet secondDataSet = second.createDataSet();

        assertThat(second).as("A second call while the cached connection is alive must reuse it.")
                .isSameAs(first);
        assertThat(secondDataSet)
                .as("Reusing the connection must also reuse its accumulated table-metadata "
                        + "cache (DatabaseDataSet), not re-fetch it - that avoided re-fetch is the "
                        + "entire point of this feature.")
                .isSameAs(firstDataSet);
    }

    @Test
    void testGetConnection_afterUnderlyingConnectionDies_transparentlyReconnectsAndStaysUsable()
            throws Exception
    {
        final IDatabaseConnection first = provider.getConnection(this::createConnection);
        // Simulate a DB blip / idle-timeout kill from the client's point of view.
        first.getConnection().close();

        final IDatabaseConnection second = provider.getConnection(this::createConnection);

        assertThat(second).as("A dead cached connection must be replaced, not returned as-is.")
                .isNotSameAs(first);
        assertThat(second.getRowCount("TEST_TABLE"))
                .as("The replacement connection must be a real, working connection against the "
                        + "configured database.")
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void testClose_thenGetConnection_reconnectsSuccessfully() throws Exception
    {
        final IDatabaseConnection first = provider.getConnection(this::createConnection);

        provider.close();

        assertThat(first.getConnection().isClosed())
                .as("close() must close the connection it hands back control of.").isTrue();

        final IDatabaseConnection second = provider.getConnection(this::createConnection);

        assertThat(second)
                .as("Closing the provider must create a new connection.")
                .isNotSameAs(first);
        assertThat(second.getRowCount("TEST_TABLE"))
                .as("The connection created after close() must be usable.")
                .isGreaterThanOrEqualTo(0);
    }

    private IDatabaseConnection createConnection() throws Exception
    {
        final Connection jdbcConnection = DriverManager.getConnection(profile.getConnectionUrl(),
                profile.getUser(), profile.getPassword());
        return new DatabaseConnection(jdbcConnection, profile.getSchema());
    }
}
