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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dbunit.dataset.Column;
import org.dbunit.ext.db2.Db2MetadataHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResultSetTableMetaData}, run against a real H2 in-memory database so
 * that the {@link java.sql.ResultSetMetaData} and {@link DatabaseMetaData} behavior driving
 * column resolution is genuine. Call counts are verified via {@link org.mockito.Mockito#spy}
 * wrappers rather than fully mocked JDBC objects.
 *
 * @since 3.2.1
 */
class ResultSetTableMetaDataTest
{
    private static final String CREATE_TABLE_SQL = "CREATE TABLE MULTI_COL_TABLE "
            + "(ID INT PRIMARY KEY, NAME VARCHAR(50), AMOUNT DECIMAL(10,2))";

    private IDatabaseConnection connection;

    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            connection.close();
        }
    }

    @Test
    void testCreateMetaData_multiColumnSingleTable_singleGetColumnsQuery() throws Exception
    {
        final Connection realConnection = InMemoryDatabaseConnection.create().getConnection();
        final Statement ddlStmt = realConnection.createStatement();
        ddlStmt.execute(CREATE_TABLE_SQL);
        ddlStmt.close();

        final DatabaseMetaData spyMetaData = spy(realConnection.getMetaData());
        final Connection spyConnection = spy(realConnection);
        when(spyConnection.getMetaData()).thenReturn(spyMetaData);
        connection = new DatabaseConnection(spyConnection);

        final Statement queryStmt = spyConnection.createStatement();
        final ResultSet resultSet =
                queryStmt.executeQuery("SELECT * FROM MULTI_COL_TABLE");

        final ResultSetTableMetaData metaData =
                new ResultSetTableMetaData("MULTI_COL_TABLE", resultSet, connection, false);
        final Column[] columns = metaData.getColumns();

        assertThat(columns).as("column count.").hasSize(3);
        assertThat(columns[0].getColumnName()).as("column 0 name.")
                .isEqualToIgnoringCase("ID");
        assertThat(columns[1].getColumnName()).as("column 1 name.")
                .isEqualToIgnoringCase("NAME");
        assertThat(columns[2].getColumnName()).as("column 2 name.")
                .isEqualToIgnoringCase("AMOUNT");

        verify(spyMetaData, times(1)).getColumns(any(), any(), eq("MULTI_COL_TABLE"), eq("%"));
    }

    @Test
    void testCreateMetaData_db2MetadataHandler_singleGetColumnsQuery() throws Exception
    {
        final Connection realConnection = InMemoryDatabaseConnection.create().getConnection();
        final Statement ddlStmt = realConnection.createStatement();
        ddlStmt.execute(CREATE_TABLE_SQL);
        ddlStmt.close();

        final DatabaseMetaData spyMetaData = spy(realConnection.getMetaData());
        final Connection spyConnection = spy(realConnection);
        when(spyConnection.getMetaData()).thenReturn(spyMetaData);
        connection = new DatabaseConnection(spyConnection);
        connection.getConfig().setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
                new Db2MetadataHandler());

        final Statement queryStmt = spyConnection.createStatement();
        final ResultSet resultSet =
                queryStmt.executeQuery("SELECT * FROM MULTI_COL_TABLE");

        final ResultSetTableMetaData metaData =
                new ResultSetTableMetaData("MULTI_COL_TABLE", resultSet, connection, false);
        final Column[] columns = metaData.getColumns();

        assertThat(columns).as("column count.").hasSize(3);

        verify(spyMetaData, times(1)).getColumns(any(), any(), eq("MULTI_COL_TABLE"), eq("%"));
    }

    @Test
    void testCreateMetaData_customMetadataHandler_fetchesColumnsPerColumn() throws Exception
    {
        final Connection realConnection = InMemoryDatabaseConnection.create().getConnection();
        final Statement ddlStmt = realConnection.createStatement();
        ddlStmt.execute(CREATE_TABLE_SQL);
        ddlStmt.close();

        connection = new DatabaseConnection(realConnection);
        final IMetadataHandler customHandler =
                spy(new DelegatingMetadataHandler(new DefaultMetadataHandler()));
        connection.getConfig().setProperty(DatabaseConfig.PROPERTY_METADATA_HANDLER,
                customHandler);

        final Statement queryStmt = realConnection.createStatement();
        final ResultSet resultSet =
                queryStmt.executeQuery("SELECT * FROM MULTI_COL_TABLE");

        final ResultSetTableMetaData metaData =
                new ResultSetTableMetaData("MULTI_COL_TABLE", resultSet, connection, false);
        final Column[] columns = metaData.getColumns();

        assertThat(columns).as("column count.").hasSize(3);

        verify(customHandler, times(3)).getColumns(any(), any(), eq("MULTI_COL_TABLE"));
    }

    @Test
    void testCreateMetaData_caseSensitiveMetadataEnabled_resolvesExactCaseColumnsViaCache()
            throws Exception
    {
        final Connection realConnection = InMemoryDatabaseConnection.create().getConnection();
        final Statement ddlStmt = realConnection.createStatement();
        ddlStmt.execute(CREATE_TABLE_SQL);
        ddlStmt.close();

        connection = new DatabaseConnection(realConnection);

        final Statement queryStmt = realConnection.createStatement();
        final ResultSet resultSet =
                queryStmt.executeQuery("SELECT * FROM MULTI_COL_TABLE");

        final ResultSetTableMetaData metaData =
                new ResultSetTableMetaData("MULTI_COL_TABLE", resultSet, connection, true);
        final Column[] columns = metaData.getColumns();

        assertThat(columns).as("column count.").hasSize(3);
        assertThat(columns[0].getColumnName()).as("column 0 name.").isEqualTo("ID");
        assertThat(columns[1].getColumnName()).as("column 1 name.").isEqualTo("NAME");
        assertThat(columns[2].getColumnName()).as("column 2 name.").isEqualTo("AMOUNT");
    }

    /**
     * A non-{@link DefaultMetadataHandler} {@link IMetadataHandler}, so the fast path's
     * exact-class safety valve in {@link ResultSetTableMetaData} does not apply and the legacy
     * per-column path is exercised instead. Delegates to a real handler so results stay correct.
     */
    private static final class DelegatingMetadataHandler implements IMetadataHandler
    {
        private final IMetadataHandler delegate;

        DelegatingMetadataHandler(IMetadataHandler delegate)
        {
            this.delegate = delegate;
        }

        public ResultSet getColumns(DatabaseMetaData databaseMetaData, String schemaName,
                String tableName) throws SQLException
        {
            return delegate.getColumns(databaseMetaData, schemaName, tableName);
        }

        public boolean matches(ResultSet resultSet, String schema, String table,
                boolean caseSensitive) throws SQLException
        {
            return delegate.matches(resultSet, schema, table, caseSensitive);
        }

        public boolean matches(ResultSet resultSet, String catalog, String schema, String table,
                String column, boolean caseSensitive) throws SQLException
        {
            return delegate.matches(resultSet, catalog, schema, table, column, caseSensitive);
        }

        public String getSchema(ResultSet resultSet) throws SQLException
        {
            return delegate.getSchema(resultSet);
        }

        public boolean tableExists(DatabaseMetaData databaseMetaData, String schemaName,
                String tableName) throws SQLException
        {
            return delegate.tableExists(databaseMetaData, schemaName, tableName);
        }

        public ResultSet getTables(DatabaseMetaData databaseMetaData, String schemaName,
                String[] tableTypes) throws SQLException
        {
            return delegate.getTables(databaseMetaData, schemaName, tableTypes);
        }

        public ResultSet getPrimaryKeys(DatabaseMetaData databaseMetaData, String schemaName,
                String tableName) throws SQLException
        {
            return delegate.getPrimaryKeys(databaseMetaData, schemaName, tableName);
        }
    }

}
