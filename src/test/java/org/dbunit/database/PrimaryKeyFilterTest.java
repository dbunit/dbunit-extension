/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2005, DbUnit.org
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dbunit.database.PrimaryKeyFilter.PkTableMap;
import org.dbunit.database.search.ForeignKeyRelationshipEdge;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PrimaryKeyFilter} and its inner class {@link PkTableMap}.
 *
 * @since 3.2.0
 */
class PrimaryKeyFilterTest
{
    // -------------------------------------------------------------------------
    // PkTableMap tests
    // -------------------------------------------------------------------------

    @Test
    void testPkTableMapDefaultConstructor_withNoArgs_createsEmptyMap()
    {
        final PkTableMap map = new PkTableMap();

        assertThat(map.isEmpty()).as("map is empty.").isTrue();
        assertThat(map.size()).as("map size.").isZero();
    }

    @Test
    void testPkTableMapAdd_withSingleEntry_storesEntry()
    {
        final PkTableMap map = new PkTableMap();
        map.add("MY_TABLE", 42);

        assertThat(map.isEmpty()).as("map is not empty after add.").isFalse();
        assertThat(map.size()).as("map size.").isEqualTo(1);
        assertThat(map.contains("MY_TABLE", 42)).as("contains added pk.").isTrue();
    }

    @Test
    void testPkTableMapContains_withMatchingTableAndPk_returnsTrue()
    {
        final PkTableMap map = new PkTableMap();
        map.add("ORDERS", "PK-001");

        assertThat(map.contains("ORDERS", "PK-001")).as("contains matching pk.").isTrue();
    }

    @Test
    void testPkTableMapContains_withNonMatchingPk_returnsFalse()
    {
        final PkTableMap map = new PkTableMap();
        map.add("ORDERS", "PK-001");

        assertThat(map.contains("ORDERS", "PK-999")).as("does not contain non-matching pk.").isFalse();
    }

    @Test
    void testPkTableMapContains_withNonMatchingTable_returnsFalse()
    {
        final PkTableMap map = new PkTableMap();
        map.add("ORDERS", "PK-001");

        assertThat(map.contains("UNKNOWN_TABLE", "PK-001")).as("does not contain non-matching table.").isFalse();
    }

    @Test
    void testPkTableMapContains_withAbsentTable_returnsFalse()
    {
        final PkTableMap map = new PkTableMap();

        assertThat(map.contains("ABSENT", 1)).as("absent table returns false.").isFalse();
    }

    @Test
    void testPkTableMapGet_withExistingTable_returnsSortedSet()
    {
        final PkTableMap map = new PkTableMap();
        map.add("FOO", "b");
        map.add("FOO", "a");

        final SortedSet<Object> result = map.get("FOO");
        assertThat(result).as("set is not null.").isNotNull();
        assertThat(result).as("set contains both elements.").containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testPkTableMapGet_withAbsentTable_returnsNull()
    {
        final PkTableMap map = new PkTableMap();

        assertThat(map.get("ABSENT")).as("absent table returns null.").isNull();
    }

    @Test
    void testPkTableMapAddAll_withMultiplePks_addsAllEntries()
    {
        final PkTableMap map = new PkTableMap();
        final SortedSet<Object> pks = new TreeSet<>();
        pks.add(1);
        pks.add(2);
        pks.add(3);
        map.addAll("ITEMS", pks);

        assertThat(map.contains("ITEMS", 1)).as("contains pk 1.").isTrue();
        assertThat(map.contains("ITEMS", 2)).as("contains pk 2.").isTrue();
        assertThat(map.contains("ITEMS", 3)).as("contains pk 3.").isTrue();
    }

    @Test
    void testPkTableMapPut_withSortedSet_replacesEntries()
    {
        final PkTableMap map = new PkTableMap();
        map.add("TABLE_A", 10);

        final SortedSet<Object> newPks = new TreeSet<>();
        newPks.add(20);
        newPks.add(30);
        map.put("TABLE_A", newPks);

        assertThat(map.contains("TABLE_A", 20)).as("contains new pk 20.").isTrue();
        assertThat(map.contains("TABLE_A", 30)).as("contains new pk 30.").isTrue();
    }

    @Test
    void testPkTableMapRemove_withExistingTable_removesEntry()
    {
        final PkTableMap map = new PkTableMap();
        map.add("TO_REMOVE", 1);
        map.remove("TO_REMOVE");

        assertThat(map.contains("TO_REMOVE", 1)).as("removed entry not present.").isFalse();
        assertThat(map.isEmpty()).as("map is empty after remove.").isTrue();
    }

    @Test
    void testPkTableMapGetTableNames_withTwoTables_returnsBothNames()
    {
        final PkTableMap map = new PkTableMap();
        map.add("TABLE_ONE", 1);
        map.add("TABLE_TWO", 2);

        final String[] tableNames = map.getTableNames();
        assertThat(tableNames).as("table names.").containsExactlyInAnyOrder("TABLE_ONE", "TABLE_TWO");
    }

    @Test
    void testPkTableMapCopyConstructor_withPopulatedMap_copiesAllEntries()
    {
        final PkTableMap original = new PkTableMap();
        original.add("T1", "pk-a");
        original.add("T1", "pk-b");
        original.add("T2", "pk-c");

        final PkTableMap copy = new PkTableMap(original);

        assertThat(copy.contains("T1", "pk-a")).as("copy contains T1/pk-a.").isTrue();
        assertThat(copy.contains("T1", "pk-b")).as("copy contains T1/pk-b.").isTrue();
        assertThat(copy.contains("T2", "pk-c")).as("copy contains T2/pk-c.").isTrue();
        assertThat(copy.size()).as("copy has same size.").isEqualTo(original.size());
    }

    @Test
    void testPkTableMapCopyConstructor_modifyingCopy_doesNotAffectOriginal()
    {
        final PkTableMap original = new PkTableMap();
        original.add("T1", "pk-a");

        final PkTableMap copy = new PkTableMap(original);
        copy.add("T1", "pk-z");

        assertThat(original.contains("T1", "pk-z")).as("original unaffected by copy modification.").isFalse();
    }

    @Test
    void testPkTableMapRetainOnly_withSubsetOfTables_removesUnlistedTables()
    {
        final PkTableMap map = new PkTableMap();
        map.add("KEEP", 1);
        map.add("DISCARD", 2);

        final java.util.List<String> tableNames = new java.util.ArrayList<>();
        tableNames.add("KEEP");

        map.retainOnly(tableNames);

        assertThat(map.contains("KEEP", 1)).as("retained table still present.").isTrue();
        assertThat(map.contains("DISCARD", 2)).as("discarded table removed.").isFalse();
    }

    @Test
    void testPkTableMapToString_withEntries_returnsNonEmptyString()
    {
        final PkTableMap map = new PkTableMap();
        map.add("T", 1);

        final String result = map.toString();
        assertThat(result).as("toString is not empty.").isNotBlank();
    }

    // -------------------------------------------------------------------------
    // PrimaryKeyFilter isValidName tests
    // -------------------------------------------------------------------------

    @Test
    void testIsValidName_withAnyTableName_returnsTrue() throws Exception
    {
        final PkTableMap allowedPKs = new PkTableMap();
        allowedPKs.add("MY_TABLE", 1);

        final PrimaryKeyFilter filter =
                new PrimaryKeyFilter(null, allowedPKs, false);

        assertThat(filter.isValidName("MY_TABLE")).as("MY_TABLE is valid.").isTrue();
        assertThat(filter.isValidName("OTHER_TABLE")).as("OTHER_TABLE is valid.").isTrue();
        assertThat(filter.isValidName("NONEXISTENT")).as("NONEXISTENT is valid.").isTrue();
    }

    // -------------------------------------------------------------------------
    // PrimaryKeyFilter toString tests
    // -------------------------------------------------------------------------

    @Test
    void testToString_withPopulatedFilter_returnsNonEmptyString()
    {
        final PkTableMap allowedPKs = new PkTableMap();
        allowedPKs.add("ORDER", 10);

        final PrimaryKeyFilter filter =
                new PrimaryKeyFilter(null, allowedPKs, true);

        final String result = filter.toString();
        assertThat(result).as("toString is not empty.").isNotBlank();
        assertThat(result).as("toString includes reverseScan flag.").contains("reverseScan=true");
    }

    // -------------------------------------------------------------------------
    // PrimaryKeyFilter scanPKs result set closing tests
    // -------------------------------------------------------------------------

    @Test
    void testIterator_multiplePksToScan_closesResultSetPerIteration() throws Exception
    {
        // CHILD has an FK column (PARENT_ID) referencing PARENT.ID.
        final Column[] childColumns = new Column[] {
                new Column("ID", DataType.NUMERIC),
                new Column("PARENT_ID", DataType.NUMERIC)};
        final DefaultTable childTable = new DefaultTable(
                new DefaultTableMetaData("CHILD", childColumns, new String[] {"ID"}));
        final Column[] parentColumns =
                new Column[] {new Column("ID", DataType.NUMERIC)};
        final DefaultTable parentTable = new DefaultTable(
                new DefaultTableMetaData("PARENT", parentColumns, new String[] {"ID"}));
        final IDataSet dataSet = new DefaultDataSet(childTable, parentTable);

        final PreparedStatement pstmt = mock(PreparedStatement.class);
        final ResultSet rs1 = mock(ResultSet.class);
        final ResultSet rs2 = mock(ResultSet.class);
        when(pstmt.executeQuery()).thenReturn(rs1, rs2);
        final Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(pstmt);
        final IDatabaseConnection databaseConnection = mock(IDatabaseConnection.class);
        when(databaseConnection.getConnection()).thenReturn(connection);

        final PkTableMap allowedPKs = new PkTableMap();
        allowedPKs.add("CHILD", Integer.valueOf(1));
        allowedPKs.add("CHILD", Integer.valueOf(2));

        final PrimaryKeyFilter filter =
                new PrimaryKeyFilter(databaseConnection, allowedPKs, false);
        filter.nodeAdded("CHILD");
        filter.nodeAdded("PARENT");
        filter.edgeAdded(new ForeignKeyRelationshipEdge("CHILD", "PARENT",
                "PARENT_ID", "ID"));

        // searchPKs() -- and therefore scanPKs() -- runs synchronously inside iterator().
        filter.iterator(dataSet, false);

        verify(pstmt, times(2)).executeQuery();
        // Each iteration's own ResultSet must be closed, not just the last one.
        verify(rs1, times(1)).close();
        verify(rs2, times(1)).close();
    }
}
