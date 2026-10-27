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
package org.dbunit.ext.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link MySqlMetadataHandler}, focused on the catalog/schema swap in
 * {@link MySqlMetadataHandler#matches(ResultSet, String, String, String, String, boolean)} and
 * its value-based {@link MySqlMetadataHandler#matchesColumn} counterpart: MySQL's
 * {@code getColumns} rows report only a catalog (MySQL has no schema distinct from its
 * catalog/database), so a search that expects a schema must be compared against the row's
 * catalog instead.
 *
 * @since 3.2.1
 */
class MySqlMetadataHandlerTest
{
    private final MySqlMetadataHandler handler = new MySqlMetadataHandler();

    @ParameterizedTest
    @MethodSource("provideCatalogSchemaTableColumnCombinations")
    void testMatchesColumn_variousCatalogSchemaTableColumnCombinations_agreesWithResultSetBasedMatches(
            String searchCatalog, String actualCatalog, String searchSchema, String actualSchema,
            String searchTable, String actualTable, String searchColumn, String actualColumn,
            boolean caseSensitive) throws SQLException
    {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString(1)).thenReturn(actualCatalog);
        when(resultSet.getString(2)).thenReturn(actualSchema);
        when(resultSet.getString(3)).thenReturn(actualTable);
        when(resultSet.getString(4)).thenReturn(actualColumn);

        final boolean viaResultSet = handler.matches(resultSet, searchCatalog, searchSchema,
                searchTable, searchColumn, caseSensitive);
        final boolean viaValues = handler.matchesColumn(searchCatalog, actualCatalog, searchSchema,
                actualSchema, searchTable, actualTable, searchColumn, actualColumn, caseSensitive);

        assertThat(viaValues)
                .as("matchesColumn(...) must agree with matches(ResultSet, ...) for this combination.")
                .isEqualTo(viaResultSet);
    }

    private static Stream<Arguments> provideCatalogSchemaTableColumnCombinations()
    {
        return Stream.of(
                // Typical MySQL row shape: only a catalog is reported, no schema.
                Arguments.of(null, "mydb", "mydb", null, "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of(null, "otherdb", "mydb", null, "TABLE", "TABLE", "COL", "COL", false),
                // Both catalog and schema supplied on both sides: no swap, plain comparison.
                Arguments.of("mydb", "mydb", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of("mydb", "otherdb", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                // Neither side supplies a schema: swap condition not met (searchSchema is null).
                Arguments.of(null, "mydb", null, null, "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of(null, "mydb", "mydb", null, "TABLE", "WRONG_TABLE", "COL", "COL", false),
                Arguments.of(null, "mydb", "mydb", null, "TABLE", "TABLE", "COL", "WRONG_COL", false),
                Arguments.of(null, "mydb", "mydb", null, "table", "TABLE", "col", "COL", false),
                Arguments.of(null, "mydb", "mydb", null, "table", "TABLE", "col", "COL", true));
    }

    @ParameterizedTest
    @MethodSource("provideSwapScenarios")
    void testMatchesColumn_withOnlyCatalogReportedByRow_comparesRowCatalogAgainstSearchedSchema(
            String searchSchema, String actualCatalog, boolean expectedMatch)
    {
        final boolean matches = handler.matchesColumn(null, actualCatalog, searchSchema, null,
                "TABLE", "TABLE", "COL", "COL", false);

        assertThat(matches).as("row catalog vs. searched schema comparison after the swap.")
                .isEqualTo(expectedMatch);
    }

    private static Stream<Arguments> provideSwapScenarios()
    {
        return Stream.of(Arguments.of("mydb", "mydb", true), Arguments.of("mydb", "otherdb", false));
    }

    @Test
    void testGetCurrentSchema_connectionWorkingInADatabase_returnsThatDatabase() throws Exception
    {
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.getCatalog()).thenReturn("mydb");

        final String currentSchema = handler.getCurrentSchema(jdbcConnection);

        assertThat(currentSchema)
                .as("MySQL Connector/J and MariaDB Connector/J report the database a connection"
                        + " works in as its catalog and no current schema, and the handler passes"
                        + " a schema to the driver as the catalog, so it must report that"
                        + " database.")
                .isEqualTo("mydb");
    }

    @Test
    void testGetTables_emptySchema_passesTheCurrentDatabaseAsTheCatalog() throws Exception
    {
        final DatabaseMetaData metaData = metaDataOfAConnectionWorkingIn("mydb");
        final String[] tableTypes = {"TABLE"};

        handler.getTables(metaData, "", tableTypes);

        verify(metaData).getTables("mydb", null, "%", tableTypes);
    }

    @Test
    void testGetTables_noSchema_passesNoCatalog() throws Exception
    {
        final DatabaseMetaData metaData = metaDataOfAConnectionWorkingIn("mydb");
        final String[] tableTypes = {"TABLE"};

        handler.getTables(metaData, null, tableTypes);

        verify(metaData).getTables(null, null, "%", tableTypes);
    }

    @Test
    void testGetColumns_emptySchema_passesTheCurrentDatabaseAsTheCatalog() throws Exception
    {
        final DatabaseMetaData metaData = metaDataOfAConnectionWorkingIn("mydb");

        handler.getColumns(metaData, "", "TEST_TABLE");

        verify(metaData).getColumns("mydb", null, "TEST_TABLE", "%");
    }

    @Test
    void testGetPrimaryKeys_emptySchema_passesTheCurrentDatabaseAsTheCatalog() throws Exception
    {
        final DatabaseMetaData metaData = metaDataOfAConnectionWorkingIn("mydb");

        handler.getPrimaryKeys(metaData, "", "TEST_TABLE");

        verify(metaData).getPrimaryKeys("mydb", null, "TEST_TABLE");
    }

    @Test
    void testTableExists_emptySchema_looksInTheCurrentDatabase() throws Exception
    {
        final DatabaseMetaData metaData = metaDataOfAConnectionWorkingIn("mydb");
        final ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables("mydb", null, "TEST_TABLE", null)).thenReturn(tables);
        when(tables.next()).thenReturn(true);

        final boolean exists = handler.tableExists(metaData, "", "TEST_TABLE");

        assertThat(exists)
                .as("An empty schema must mean the database the connection works in, as MySQL"
                        + " Connector/J lists nothing for an empty catalog.")
                .isTrue();
    }

    /**
     * Returns metadata of a connection that works in the given database, which MySQL Connector/J
     * and MariaDB Connector/J report as the catalog of the connection.
     */
    private static DatabaseMetaData metaDataOfAConnectionWorkingIn(final String database)
            throws SQLException
    {
        final Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn(database);
        final DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getConnection()).thenReturn(connection);
        return metaData;
    }
}
