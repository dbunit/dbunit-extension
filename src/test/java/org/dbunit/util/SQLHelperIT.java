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
package org.dbunit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link SQLHelper}.
 *
 * <p>These tests verify close semantics, schema/table existence checks,
 * metadata printing, column creation, and string comparison utilities
 * against a live database connection.
 *
 * @since 3.1.1
 */
class SQLHelperIT extends AbstractDatabaseIT
{
    // -------------------------------------------------------------------------
    // getPrimaryKeyColumn
    // -------------------------------------------------------------------------

    @Test
    void testGetPrimaryKeyColumn_withPkTable_returnsPrimaryKeyColumnName()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final String pk =
                SQLHelper.getPrimaryKeyColumn(conn, convertString("PK_TABLE"));
        assertThat(pk).as("primary key column name.")
                .isEqualToIgnoringCase("PK0");
    }

    // -------------------------------------------------------------------------
    // close(ResultSet, Statement)
    // -------------------------------------------------------------------------

    @Test
    void testClose_withBothNull_doesNotThrow() throws Exception
    {
        SQLHelper.close((ResultSet) null, (Statement) null);
    }

    @Test
    void testClose_withBothNonNull_closesResultSetAndStatement() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final Statement stmt = conn.createStatement();
        final ResultSet rs =
                stmt.executeQuery("SELECT COUNT(*) FROM TEST_TABLE");
        assertThat(rs.isClosed()).as("rs open before close.").isFalse();
        assertThat(stmt.isClosed()).as("stmt open before close.").isFalse();

        SQLHelper.close(rs, stmt);

        assertThat(rs.isClosed()).as("rs closed after close.").isTrue();
        assertThat(stmt.isClosed()).as("stmt closed after close.").isTrue();
    }

    @Test
    void testClose_withNullResultSetAndNonNullStatement_closesStatement()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final Statement stmt = conn.createStatement();
        assertThat(stmt.isClosed()).as("stmt open before close.").isFalse();

        SQLHelper.close((ResultSet) null, stmt);

        assertThat(stmt.isClosed()).as("stmt closed after close.").isTrue();
    }

    @Test
    void testClose_withNonNullResultSetAndNullStatement_closesResultSet()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final Statement stmt = conn.createStatement();
        final ResultSet rs =
                stmt.executeQuery("SELECT COUNT(*) FROM TEST_TABLE");
        assertThat(rs.isClosed()).as("rs open before close.").isFalse();

        SQLHelper.close(rs, (Statement) null);
        stmt.close();

        assertThat(rs.isClosed()).as("rs closed after close.").isTrue();
    }

    // -------------------------------------------------------------------------
    // close(Statement)
    // -------------------------------------------------------------------------

    @Test
    void testCloseStatement_withNullStatement_doesNotThrow() throws Exception
    {
        SQLHelper.close((Statement) null);
    }

    @Test
    void testCloseStatement_withOpenStatement_closesStatement() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final Statement stmt = conn.createStatement();
        assertThat(stmt.isClosed()).as("stmt open before close.").isFalse();

        SQLHelper.close(stmt);

        assertThat(stmt.isClosed()).as("stmt closed after close.").isTrue();
    }

    // -------------------------------------------------------------------------
    // close(ResultSet)
    // -------------------------------------------------------------------------

    @Test
    void testCloseResultSet_withNullResultSet_doesNotThrow() throws Exception
    {
        SQLHelper.close((ResultSet) null);
    }

    @Test
    void testCloseResultSet_withOpenResultSet_closesResultSet() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final Statement stmt = conn.createStatement();
        final ResultSet rs =
                stmt.executeQuery("SELECT COUNT(*) FROM TEST_TABLE");
        assertThat(rs.isClosed()).as("rs open before close.").isFalse();

        SQLHelper.close(rs);
        stmt.close();

        assertThat(rs.isClosed()).as("rs closed after close.").isTrue();
    }

    // -------------------------------------------------------------------------
    // schemaExists
    // -------------------------------------------------------------------------

    @Test
    void testSchemaExists_withExistingSchema_returnsTrue() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final String schema = getEnvironment().getProfile().getSchema();
        Assumptions.assumeTrue(schema != null && !schema.isEmpty(),
                "Skip for databases that use no schema (e.g., MySQL).");
        assertThat(SQLHelper.schemaExists(conn, schema))
                .as("existing schema returns true.").isTrue();
    }

    @Test
    void testSchemaExists_withNonExistentSchema_returnsFalse() throws Exception
    {
        final Connection conn = _connection.getConnection();
        assertThat(SQLHelper.schemaExists(conn, "NONEXISTENT_SCHEMA_XYZ_99"))
                .as("non-existent schema returns false.").isFalse();
    }

    @Test
    void testSchemaExists_withNullSchema_throwsNullPointerException()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        assertThatNullPointerException()
                .isThrownBy(() -> SQLHelper.schemaExists(conn, null))
                .withMessageContaining("schema");
    }

    // -------------------------------------------------------------------------
    // tableExists
    // -------------------------------------------------------------------------

    @Test
    void testTableExists_withExistingTable_returnsTrue() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        assertThat(SQLHelper.tableExists(metaData, schema,
                convertString("TEST_TABLE")))
                        .as("existing table returns true.").isTrue();
    }

    @Test
    void testTableExists_withNonExistentTable_returnsFalse() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        assertThat(SQLHelper.tableExists(metaData, schema,
                "NONEXISTENT_TABLE_XYZ_99"))
                        .as("non-existent table returns false.").isFalse();
    }

    // -------------------------------------------------------------------------
    // printAllTables
    // -------------------------------------------------------------------------

    @Test
    void testPrintAllTables_withLiveDatabase_outputContainsTestTable()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos);

        SQLHelper.printAllTables(metaData, ps);

        final String output = baos.toString();
        assertThat(output).as("output contains TEST_TABLE.")
                .containsIgnoringCase("TEST_TABLE");
    }

    // -------------------------------------------------------------------------
    // getDatabaseInfo
    // -------------------------------------------------------------------------

    @Test
    void testGetDatabaseInfo_withLiveDatabase_returnsNonNullInfoWithProductName()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String productName = metaData.getDatabaseProductName();

        final String info = SQLHelper.getDatabaseInfo(metaData);

        assertThat(info).as("info is not null.").isNotNull();
        assertThat(info).as("info contains product name.").contains(productName);
        assertThat(info).as("info contains driver name label.")
                .contains("jdbc driver name");
    }

    // -------------------------------------------------------------------------
    // printDatabaseInfo
    // -------------------------------------------------------------------------

    @Test
    void testPrintDatabaseInfo_withLiveDatabase_writesNonEmptyOutput()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(baos);

        SQLHelper.printDatabaseInfo(metaData, ps);

        assertThat(baos.toString()).as("output is not empty.").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // isSybaseDb
    // -------------------------------------------------------------------------

    @Test
    void testIsSybaseDb_withTestDatabase_returnsFalse() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        assertThat(SQLHelper.isSybaseDb(metaData))
                .as("test database is not Sybase.").isFalse();
    }

    // -------------------------------------------------------------------------
    // createColumn
    // -------------------------------------------------------------------------

    @Test
    void testCreateColumn_withValidColumnResultSet_returnsNonNullColumn()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        final IDataTypeFactory dataTypeFactory =
                (IDataTypeFactory) _connection.getConfig()
                        .getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);

        final ResultSet rs = metaData.getColumns(null, schema,
                convertString("TEST_TABLE"), null);
        try
        {
            assertThat(rs.next()).as("at least one column in TEST_TABLE.")
                    .isTrue();
            final Column column =
                    SQLHelper.createColumn(rs, dataTypeFactory, true);
            assertThat(column).as("column created.").isNotNull();
            assertThat(column.getColumnName())
                    .as("column name not empty.").isNotEmpty();
        } finally
        {
            rs.close();
        }
    }

    // -------------------------------------------------------------------------
    // matches (3-arg overload: schema, table, caseSensitive)
    // -------------------------------------------------------------------------

    @Test
    void testMatches_withMatchingSchemaAndTable_returnsTrue() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        final String tableName = convertString("TEST_TABLE");

        final ResultSet rs =
                metaData.getColumns(null, schema, tableName, null);
        try
        {
            assertThat(rs.next())
                    .as("at least one row in columns ResultSet.").isTrue();
            assertThat(SQLHelper.matches(rs, schema, tableName, false))
                    .as("matches with correct schema and table.").isTrue();
        } finally
        {
            rs.close();
        }
    }

    @Test
    void testMatches_withNullSchemaAndNullTable_returnsTrue() throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        final String tableName = convertString("TEST_TABLE");

        final ResultSet rs =
                metaData.getColumns(null, schema, tableName, null);
        try
        {
            assertThat(rs.next())
                    .as("at least one row in columns ResultSet.").isTrue();
            assertThat(SQLHelper.matches(rs, null, null, true))
                    .as("null schema and table act as wildcards.").isTrue();
        } finally
        {
            rs.close();
        }
    }

    // -------------------------------------------------------------------------
    // matches (5-arg overload: catalog, schema, table, column, caseSensitive)
    // -------------------------------------------------------------------------

    @Test
    void testMatchesFull_withNullCatalogSchemaTableAndMatchingColumn_returnsTrue()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String schema = getEnvironment().getProfile().getSchema();
        final String tableName = convertString("TEST_TABLE");

        final ResultSet rs =
                metaData.getColumns(null, schema, tableName, null);
        try
        {
            assertThat(rs.next())
                    .as("at least one row in columns ResultSet.").isTrue();
            final String actualColumnName = rs.getString(4);
            assertThat(
                    SQLHelper.matches(rs, null, null, null, actualColumnName,
                            false))
                    .as("full match with concrete column name.").isTrue();
        } finally
        {
            rs.close();
        }
    }

    // -------------------------------------------------------------------------
    // areEqualIgnoreNull
    // -------------------------------------------------------------------------

    @Test
    void testAreEqualIgnoreNull_withNullValue1_returnsTrue()
    {
        assertThat(SQLHelper.areEqualIgnoreNull(null, "anything", true))
                .as("null v1 always matches.").isTrue();
    }

    @Test
    void testAreEqualIgnoreNull_withEmptyValue1_returnsTrue()
    {
        assertThat(SQLHelper.areEqualIgnoreNull("", "anything", true))
                .as("empty v1 always matches.").isTrue();
    }

    @Test
    void testAreEqualIgnoreNull_withEqualValuesCaseSensitive_returnsTrue()
    {
        assertThat(SQLHelper.areEqualIgnoreNull("ABC", "ABC", true))
                .as("equal values case-sensitive.").isTrue();
    }

    @Test
    void testAreEqualIgnoreNull_withDifferentCaseCaseSensitive_returnsFalse()
    {
        assertThat(SQLHelper.areEqualIgnoreNull("ABC", "abc", true))
                .as("different case with case-sensitive comparison.").isFalse();
    }

    @Test
    void testAreEqualIgnoreNull_withDifferentCaseCaseInsensitive_returnsTrue()
    {
        assertThat(SQLHelper.areEqualIgnoreNull("ABC", "abc", false))
                .as("different case with case-insensitive comparison.").isTrue();
    }

    @Test
    void testAreEqualIgnoreNull_withDifferentValues_returnsFalse()
    {
        assertThat(SQLHelper.areEqualIgnoreNull("ABC", "XYZ", true))
                .as("different values return false.").isFalse();
    }

    // -------------------------------------------------------------------------
    // correctCase(String, Connection)
    // -------------------------------------------------------------------------

    @Test
    void testCorrectCase_withConnection_returnsIdentifierInDatabaseCase()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final String corrected = SQLHelper.correctCase("test_table", conn);
        assertThat(corrected).as("corrected identifier matches DB-stored case.")
                .isEqualTo(convertString("test_table"));
    }

    // -------------------------------------------------------------------------
    // correctCase(String, DatabaseMetaData)
    // -------------------------------------------------------------------------

    @Test
    void testCorrectCase_withDatabaseMetaData_returnsIdentifierInDatabaseCase()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        final String corrected = SQLHelper.correctCase("test_table", metaData);
        assertThat(corrected).as("corrected identifier matches DB-stored case.")
                .isEqualTo(convertString("test_table"));
    }

    @Test
    void testCorrectCase_withNullIdentifier_throwsNullPointerException()
            throws Exception
    {
        final Connection conn = _connection.getConnection();
        final DatabaseMetaData metaData = conn.getMetaData();
        assertThatNullPointerException()
                .isThrownBy(() -> SQLHelper.correctCase(null, metaData))
                .withMessageContaining("databaseIdentifier");
    }
}
