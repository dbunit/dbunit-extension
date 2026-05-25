/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseProfile;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link DatabaseDataSourceConnection}.
 *
 * <p>These tests verify construction variants, connection caching, schema
 * storage, close behaviour, and the ability to perform real database operations
 * through the DataSource-backed connection.
 *
 * @since 3.2.0
 */
class DatabaseDataSourceConnectionIT extends AbstractDatabaseIT
{
    /**
     * Minimal {@link DataSource} backed by {@link DriverManager}.  Opens a new
     * physical connection per {@link #getConnection()} call so that each test
     * can observe independently-managed connections.
     */
    private static class ProfileDataSource implements DataSource
    {
        private final String url;
        private final String user;
        private final String password;

        ProfileDataSource(final String url, final String user,
                final String password)
        {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException
        {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(final String u, final String p)
                throws SQLException
        {
            return DriverManager.getConnection(url, u, p);
        }

        @Override
        public PrintWriter getLogWriter()
        {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out)
        {
        }

        @Override
        public void setLoginTimeout(final int seconds)
        {
        }

        @Override
        public int getLoginTimeout()
        {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException
        {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException
        {
            throw new SQLException("Not a wrapper.");
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface)
        {
            return false;
        }
    }

    private DataSource buildDataSource() throws Exception
    {
        final DatabaseProfile profile = getEnvironment().getProfile();
        Class.forName(profile.getDriverClass());
        return new ProfileDataSource(profile.getConnectionUrl(),
                profile.getUser(), profile.getPassword());
    }

    private String profileSchema() throws Exception
    {
        return getEnvironment().getProfile().getSchema();
    }

    private String profileUser() throws Exception
    {
        return getEnvironment().getProfile().getUser();
    }

    private String profilePassword() throws Exception
    {
        return getEnvironment().getProfile().getPassword();
    }

    // -------------------------------------------------------------------------
    // getConnection — lazy open and caching
    // -------------------------------------------------------------------------

    @Test
    void testGetConnection_withDataSourceOnly_returnsOpenConnection()
            throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        try
        {
            final Connection jdbc = conn.getConnection();
            assertThat(jdbc).as("connection returned.").isNotNull();
            assertThat(jdbc.isClosed()).as("connection is open.").isFalse();
        } finally
        {
            conn.close();
        }
    }

    @Test
    void testGetConnection_calledTwice_returnsSameInstance() throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        try
        {
            final Connection first = conn.getConnection();
            final Connection second = conn.getConnection();
            assertThat(second).as("same instance returned on second call.")
                    .isSameAs(first);
        } finally
        {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // getSchema
    // -------------------------------------------------------------------------

    @Test
    void testGetSchema_withNoSchemaConstructor_returnsNull() throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        try
        {
            assertThat(conn.getSchema()).as("no-schema constructor gives null.").isNull();
        } finally
        {
            conn.close();
        }
    }

    @Test
    void testGetSchema_withExplicitSchema_returnsSchema() throws Exception
    {
        final String schema = profileSchema();
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource(), schema);
        try
        {
            assertThat(conn.getSchema()).as("schema stored correctly.").isEqualTo(schema);
        } finally
        {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // close
    // -------------------------------------------------------------------------

    @Test
    void testClose_withOpenConnection_closesUnderlyingJdbcConnection()
            throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        final Connection jdbc = conn.getConnection();
        assertThat(jdbc.isClosed()).as("open before close.").isFalse();

        conn.close();

        assertThat(jdbc.isClosed()).as("closed after close().").isTrue();
    }

    @Test
    void testClose_withoutPriorGetConnection_doesNotThrow() throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        // close() before any getConnection() call must be a safe no-op
        conn.close();
    }

    @Test
    void testClose_calledTwice_doesNotThrow() throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        conn.getConnection();
        conn.close();
        conn.close();
    }

    @Test
    void testGetConnection_afterClose_returnsNewOpenConnection() throws Exception
    {
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource());
        try
        {
            final Connection first = conn.getConnection();
            conn.close();
            assertThat(first.isClosed()).as("first connection closed.").isTrue();

            final Connection second = conn.getConnection();
            assertThat(second).as("new connection obtained after close.").isNotNull();
            assertThat(second.isClosed()).as("new connection is open.").isFalse();
            assertThat(second).as("different instance from closed connection.")
                    .isNotSameAs(first);
        } finally
        {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // Constructor with user / password
    // -------------------------------------------------------------------------

    @Test
    void testGetConnection_withUserAndPassword_returnsOpenConnection()
            throws Exception
    {
        final DatabaseDataSourceConnection conn = new DatabaseDataSourceConnection(
                buildDataSource(), profileUser(), profilePassword());
        try
        {
            final Connection jdbc = conn.getConnection();
            assertThat(jdbc).as("connection via user/password.").isNotNull();
            assertThat(jdbc.isClosed()).as("connection is open.").isFalse();
        } finally
        {
            conn.close();
        }
    }

    @Test
    void testGetSchema_withUserPasswordConstructorAndNoSchema_returnsNull()
            throws Exception
    {
        final DatabaseDataSourceConnection conn = new DatabaseDataSourceConnection(
                buildDataSource(), profileUser(), profilePassword());
        try
        {
            assertThat(conn.getSchema())
                    .as("user/password constructor has no schema.").isNull();
        } finally
        {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // Full constructor (schema + user + password)
    // -------------------------------------------------------------------------

    @Test
    void testGetSchema_withSchemaAndUserPasswordConstructor_returnsSchema()
            throws Exception
    {
        final String schema = profileSchema();
        final DatabaseDataSourceConnection conn = new DatabaseDataSourceConnection(
                buildDataSource(), schema, profileUser(), profilePassword());
        try
        {
            assertThat(conn.getSchema()).as("schema from full constructor.").isEqualTo(schema);
        } finally
        {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // Integration: real database operations
    // -------------------------------------------------------------------------

    @Test
    void testCreateDataSet_withDataSourceConnection_returnsDataSetContainingTestTable()
            throws Exception
    {
        final String schema = profileSchema();
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource(), schema);
        try
        {
            final IDataSet dataSet = conn.createDataSet();
            assertThat(dataSet).as("dataset created.").isNotNull();
            assertThat(dataSet.getTableNames())
                    .as("dataset contains at least one table.").isNotEmpty();
        } finally
        {
            conn.close();
        }
    }

    @Test
    void testGetRowCount_withDataSourceConnection_returnsCorrectRowCount()
            throws Exception
    {
        final String schema = profileSchema();
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource(), schema);
        try
        {
            assertThat(conn.getRowCount("TEST_TABLE"))
                    .as("TEST_TABLE row count from DataSource connection.")
                    .isEqualTo(6);
        } finally
        {
            conn.close();
        }
    }

    @Test
    void testCreateQueryTable_withDataSourceConnection_returnsTableWithRows()
            throws Exception
    {
        final String schema = profileSchema();
        final DatabaseDataSourceConnection conn =
                new DatabaseDataSourceConnection(buildDataSource(), schema);
        try
        {
            final String sql = "SELECT * FROM TEST_TABLE";
            final org.dbunit.dataset.ITable table =
                    conn.createQueryTable("TEST_TABLE", sql);
            assertThat(table).as("query table returned.").isNotNull();
            assertThat(table.getRowCount()).as("rows returned by query.").isEqualTo(6);
        } finally
        {
            conn.close();
        }
    }
}
