/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dbunit.database.InMemoryDatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultMetadataHandler}.
 *
 * @since 3.2.0
 */
class DefaultMetadataHandlerTest
{
    private static final String TABLE_NAME = "TEST_TABLE";
    private static final String SCHEMA_NAME = "PUBLIC";

    private IDatabaseConnection connection;
    private DefaultMetadataHandler handler;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = InMemoryDatabaseConnection.create(SCHEMA_NAME);
        handler = new DefaultMetadataHandler();

        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE " + TABLE_NAME + " (ID INTEGER PRIMARY KEY, NAME VARCHAR(50))");
        stmt.close();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (connection != null)
        {
            connection.close();
        }
    }

    @Test
    void testGetColumns_withExistingTable_returnsResultSet() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();

        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);

        assertThat(resultSet).as("result set is not null.").isNotNull();
        assertThat(resultSet.next()).as("result set has at least one row.").isTrue();
        resultSet.close();
    }

    @Test
    void testTableExists_withExistingTable_returnsTrue() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();

        final boolean exists = handler.tableExists(metaData, SCHEMA_NAME, TABLE_NAME);

        assertThat(exists).as("table exists.").isTrue();
    }

    @Test
    void testTableExists_withNonExistentTable_returnsFalse() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();

        final boolean exists = handler.tableExists(metaData, SCHEMA_NAME, "NONEXISTENT_TABLE");

        assertThat(exists).as("non-existent table does not exist.").isFalse();
    }

    @Test
    void testTableExists_withUnderscoreInName_doesNotMatchWildcardTable() throws Exception
    {
        // Create a table whose name would match "FOO_BAR" if '_' were treated as a wildcard.
        // "FOO_BAR" does not exist; "FOOXBAR" does.  Without escaping, getTables() with
        // the pattern "FOO_BAR" would match "FOOXBAR" via the '_' wildcard, returning true.
        // The escapePattern fix must prevent this false positive.
        final Statement stmt = connection.getConnection().createStatement();
        stmt.execute("CREATE TABLE FOOXBAR (ID INTEGER)");
        stmt.close();

        final DatabaseMetaData metaData = connection.getConnection().getMetaData();

        final boolean exists = handler.tableExists(metaData, SCHEMA_NAME, "FOO_BAR");

        assertThat(exists).as("underscore in name is not treated as a wildcard.").isFalse();
    }

    @Test
    void testGetTables_withSchema_returnsResultSetContainingTable() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final String[] tableTypes = {"TABLE"};

        final ResultSet resultSet = handler.getTables(metaData, SCHEMA_NAME, tableTypes);

        assertThat(resultSet).as("result set is not null.").isNotNull();
        boolean foundTable = false;
        while (resultSet.next())
        {
            final String name = resultSet.getString(3);
            if (TABLE_NAME.equalsIgnoreCase(name))
            {
                foundTable = true;
                break;
            }
        }
        resultSet.close();
        assertThat(foundTable).as("table found in getTables result.").isTrue();
    }

    @Test
    void testGetPrimaryKeys_withTableHavingPk_returnsResultSetWithPk() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();

        final ResultSet resultSet = handler.getPrimaryKeys(metaData, SCHEMA_NAME, TABLE_NAME);

        assertThat(resultSet).as("primary keys result set is not null.").isNotNull();
        assertThat(resultSet.next()).as("primary key result has at least one row.").isTrue();
        final String columnName = resultSet.getString(4);
        assertThat(columnName).as("primary key column name.").isEqualToIgnoringCase("ID");
        resultSet.close();
    }

    @Test
    void testGetSchema_withColumnsResultSet_returnsSchemaName() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final String schemaName = handler.getSchema(resultSet);

        assertThat(schemaName).as("schema name.").isEqualToIgnoringCase(SCHEMA_NAME);
        resultSet.close();
    }

    @Test
    void testMatchesSchemaAndTable_withMatchingValues_returnsTrue() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, SCHEMA_NAME, TABLE_NAME, false);

        assertThat(matches).as("matches schema and table.").isTrue();
        resultSet.close();
    }

    @Test
    void testMatchesSchemaAndTable_withNonMatchingTable_returnsFalse() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, SCHEMA_NAME, "WRONG_TABLE", false);

        assertThat(matches).as("does not match wrong table.").isFalse();
        resultSet.close();
    }

    @Test
    void testMatchesFull_withMatchingValues_returnsTrue() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, null, SCHEMA_NAME, TABLE_NAME, "ID", false);

        assertThat(matches).as("matches catalog/schema/table/column.").isTrue();
        resultSet.close();
    }

    @Test
    void testMatchesFull_withNullCatalogIgnored_returnsTrue() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, null, SCHEMA_NAME, TABLE_NAME, null, false);

        assertThat(matches).as("null catalog and column are ignored.").isTrue();
        resultSet.close();
    }

    @Test
    void testMatchesFull_withWrongColumn_returnsFalse() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, null, SCHEMA_NAME, TABLE_NAME, "WRONG_COL", false);

        assertThat(matches).as("does not match wrong column.").isFalse();
        resultSet.close();
    }

    @Test
    void testMatchesCaseSensitive_withUpperCaseValues_returnsTrue() throws Exception
    {
        final DatabaseMetaData metaData = connection.getConnection().getMetaData();
        final ResultSet resultSet = handler.getColumns(metaData, SCHEMA_NAME, TABLE_NAME);
        resultSet.next();

        final boolean matches = handler.matches(resultSet, null, SCHEMA_NAME, TABLE_NAME, null, true);

        assertThat(matches).as("case-sensitive match succeeds with correct case.").isTrue();
        resultSet.close();
    }
}
