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
package org.dbunit.ext.db2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link Db2MetadataHandler}, focused on the catalog-mismatch tolerance in
 * {@link Db2MetadataHandler#matches(ResultSet, String, String, String, String, boolean)} and its
 * value-based {@link Db2MetadataHandler#matchesColumn} counterpart.
 *
 * @since 3.2.1
 */
class Db2MetadataHandlerTest
{
    private final Db2MetadataHandler handler = new Db2MetadataHandler();

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
                Arguments.of("CAT", "CAT", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of("CAT", "OTHER_CAT", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of(null, "CAT", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of("", "", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of("CAT", "CAT", "SCHEMA", "WRONG_SCHEMA", "TABLE", "TABLE", "COL", "COL", false),
                Arguments.of("CAT", "CAT", "SCHEMA", "SCHEMA", "TABLE", "WRONG_TABLE", "COL", "COL", false),
                Arguments.of("CAT", "CAT", "SCHEMA", "SCHEMA", "TABLE", "TABLE", "COL", "WRONG_COL", false),
                Arguments.of("CAT", "CAT", "SCHEMA", "schema", "TABLE", "table", "COL", "col", false),
                Arguments.of("CAT", "CAT", "SCHEMA", "schema", "TABLE", "table", "COL", "col", true));
    }

    /**
     * DB2's catalog comparison is a no-op except when both the searched and actual catalog are
     * literally the empty string (see {@link Db2MetadataHandler#matches}'s javadoc for the
     * driver quirk this works around), so a mismatched, non-empty catalog on either side must
     * never fail the match on its own.
     */
    @ParameterizedTest
    @MethodSource("provideMismatchedCatalogs")
    void testMatchesColumn_withMismatchedNonEmptyCatalogsButOtherwiseMatching_returnsTrue(
            String searchCatalog, String actualCatalog)
    {
        final boolean matches = handler.matchesColumn(searchCatalog, actualCatalog, "SCHEMA",
                "SCHEMA", "TABLE", "TABLE", "COL", "COL", false);

        assertThat(matches).as("catalog mismatch alone must not fail the match.").isTrue();
    }

    private static Stream<Arguments> provideMismatchedCatalogs()
    {
        return Stream.of(Arguments.of("CAT_A", "CAT_B"), Arguments.of("CAT_A", null),
                Arguments.of(null, "CAT_B"), Arguments.of("CAT_A", ""));
    }
}
