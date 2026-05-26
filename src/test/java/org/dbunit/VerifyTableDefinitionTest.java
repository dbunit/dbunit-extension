/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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

import java.util.Collections;
import java.util.Map;

import org.dbunit.assertion.comparer.value.IsActualEqualToExpectedValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VerifyTableDefinition}.
 *
 * @author DbUnit.org
 * @since 2.4.8
 */
class VerifyTableDefinitionTest
{
    @Test
    void testConstructor_withTableAndNullExcludeColumns_storesTableName()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("MY_TABLE", (String[]) null);
        assertThat(def.getTableName())
                .as("getTableName() should return the table name passed to the constructor.")
                .isEqualTo("MY_TABLE");
    }

    @Test
    void testConstructor_withTableAndExcludeColumns_storesExcludeColumns()
    {
        final String[] excluded = {"COL_A", "COL_B"};
        final VerifyTableDefinition def =
                new VerifyTableDefinition("MY_TABLE", excluded);
        assertThat(def.getColumnExclusionFilters())
                .as("getColumnExclusionFilters() should return the exclusion columns array.")
                .isEqualTo(excluded);
    }

    @Test
    void testConstructor_withNullTable_throwsIllegalArgumentException()
    {
        assertThatThrownBy(() -> new VerifyTableDefinition(null, (String[]) null))
                .as("Constructor should throw IllegalArgumentException when table is null.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructor_withTableAndEmptyExcludeColumns_storesEmptyArray()
    {
        final String[] excluded = {};
        final VerifyTableDefinition def =
                new VerifyTableDefinition("ORDERS", excluded);
        assertThat(def.getColumnExclusionFilters())
                .as("getColumnExclusionFilters() should return the empty exclusion array.")
                .isEmpty();
    }

    @Test
    void testConstructor_withExcludeAndIncludeColumns_storesBothFilters()
    {
        final String[] excluded = {"CREATED_AT"};
        final String[] included = {"ID", "NAME"};
        final VerifyTableDefinition def =
                new VerifyTableDefinition("PRODUCTS", excluded, included);
        assertThat(def.getColumnExclusionFilters())
                .as("getColumnExclusionFilters() should return the provided exclusion columns.")
                .isEqualTo(excluded);
        assertThat(def.getColumnInclusionFilters())
                .as("getColumnInclusionFilters() should return the provided inclusion columns.")
                .isEqualTo(included);
    }

    @Test
    void testConstructor_withNullIncludeColumns_storesNullInclusionFilter()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("ORDERS", new String[]{"UPDATED_AT"}, null);
        assertThat(def.getColumnInclusionFilters())
                .as("getColumnInclusionFilters() should return null when no inclusion filter is set.")
                .isNull();
    }

    @Test
    void testConstructor_withDefaultValueComparer_storesDefaultValueComparer()
    {
        final ValueComparer comparer = new IsActualEqualToExpectedValueComparer();
        final VerifyTableDefinition def =
                new VerifyTableDefinition("ITEMS", comparer, null);
        assertThat(def.getDefaultValueComparer())
                .as("getDefaultValueComparer() should return the comparer passed to the constructor.")
                .isEqualTo(comparer);
    }

    @Test
    void testConstructor_withColumnValueComparers_storesColumnValueComparers()
    {
        final ValueComparer comparer = new IsActualEqualToExpectedValueComparer();
        final Map<String, ValueComparer> columnComparers =
                Collections.singletonMap("PRICE", comparer);
        final VerifyTableDefinition def =
                new VerifyTableDefinition("ITEMS", comparer, columnComparers);
        assertThat(def.getColumnValueComparers())
                .as("getColumnValueComparers() should return the map passed to the constructor.")
                .isEqualTo(columnComparers);
    }

    @Test
    void testGetColumnInclusionFilters_whenNotSet_returnsNull()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("MY_TABLE", (String[]) null);
        assertThat(def.getColumnInclusionFilters())
                .as("getColumnInclusionFilters() should return null when not set.")
                .isNull();
    }

    @Test
    void testGetDefaultValueComparer_whenNotSet_returnsNull()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("MY_TABLE", (String[]) null);
        assertThat(def.getDefaultValueComparer())
                .as("getDefaultValueComparer() should return null when not set.")
                .isNull();
    }

    @Test
    void testGetColumnValueComparers_whenNotSet_returnsNull()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("MY_TABLE", (String[]) null);
        assertThat(def.getColumnValueComparers())
                .as("getColumnValueComparers() should return null when not set.")
                .isNull();
    }

    @Test
    void testToString_withTableName_containsTableName()
    {
        final VerifyTableDefinition def =
                new VerifyTableDefinition("CUSTOMER", (String[]) null);
        assertThat(def.toString())
                .as("toString() should contain the table name.")
                .contains("CUSTOMER");
    }
}
