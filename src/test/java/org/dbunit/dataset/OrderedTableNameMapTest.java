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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.dbunit.database.AmbiguousTableNameException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderedTableNameMap}.
 */
class OrderedTableNameMapTest
{
    // -------------------------------------------------------------------------
    // getOriginalTableName — added with the case-sensitivity bug fix (issue 734)
    // -------------------------------------------------------------------------

    @Test
    void testGetOriginalTableName_caseInsensitive_withLowercaseInput_returnsUppercaseStoredName()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("TEST_TABLE", null);

        assertThat(map.getOriginalTableName("test_table")).as("original name")
                .isEqualTo("TEST_TABLE");
    }

    @Test
    void testGetOriginalTableName_caseInsensitive_withExactInput_returnsStoredName()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("TEST_TABLE", null);

        assertThat(map.getOriginalTableName("TEST_TABLE")).as("original name")
                .isEqualTo("TEST_TABLE");
    }

    @Test
    void testGetOriginalTableName_caseInsensitive_withMixedCaseStoredName_returnsStoredName()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("MyTable", null);

        assertThat(map.getOriginalTableName("mytable")).as("original name")
                .isEqualTo("MyTable");
    }

    @Test
    void testGetOriginalTableName_caseInsensitive_whenNotFound_returnsInput() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("TEST_TABLE", null);

        assertThat(map.getOriginalTableName("unknown_table")).as("fallback to input")
                .isEqualTo("unknown_table");
    }

    @Test
    void testGetOriginalTableName_caseSensitive_withExactInput_returnsStoredName()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(true);
        map.add("TEST_TABLE", null);

        assertThat(map.getOriginalTableName("TEST_TABLE")).as("original name")
                .isEqualTo("TEST_TABLE");
    }

    @Test
    void testGetOriginalTableName_caseSensitive_withDifferentCaseInput_returnsInput()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(true);
        map.add("TEST_TABLE", null);

        assertThat(map.getOriginalTableName("test_table")).as("fallback to input")
                .isEqualTo("test_table");
    }

    // -------------------------------------------------------------------------
    // add / AmbiguousTableNameException
    // -------------------------------------------------------------------------

    @Test
    void testAdd_caseInsensitive_withNewTable_storesTableName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", "value1");

        assertThat(map.containsTable("ORDERS")).as("contains after add.").isTrue();
    }

    @Test
    void testAdd_caseInsensitive_withDuplicateName_throwsAmbiguousTableNameException()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", null);

        assertThatThrownBy(() -> map.add("ORDERS", null))
                .as("duplicate table throws.")
                .isInstanceOf(AmbiguousTableNameException.class);
    }

    @Test
    void testAdd_caseInsensitive_withDuplicateInDifferentCase_throwsAmbiguousTableNameException()
            throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", null);

        assertThatThrownBy(() -> map.add("orders", null))
                .as("case-variant duplicate throws.")
                .isInstanceOf(AmbiguousTableNameException.class);
    }

    @Test
    void testAdd_caseSensitive_withSameNameDifferentCase_storesBothNames() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(true);
        map.add("ORDERS", "upper");
        map.add("orders", "lower");

        assertThat(map.containsTable("ORDERS")).as("uppercase present.").isTrue();
        assertThat(map.containsTable("orders")).as("lowercase present.").isTrue();
    }

    @Test
    void testAdd_caseInsensitive_multipleTables_resetsLastTableNameOverride() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ALPHA", null);
        map.setLastTable("ALPHA");
        map.add("BETA", null);

        assertThat(map.getLastTableName()).as("override cleared after add.")
                .isEqualTo("BETA");
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    void testGet_caseInsensitive_withExistingTable_returnsAssociatedObject() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        final Object value = "myValue";
        map.add("CUSTOMER", value);

        assertThat(map.get("CUSTOMER")).as("get returns stored object.").isSameAs(value);
    }

    @Test
    void testGet_caseInsensitive_withLowercaseKey_returnsAssociatedObject() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        final Object value = "myValue";
        map.add("CUSTOMER", value);

        assertThat(map.get("customer")).as("case-insensitive get.").isSameAs(value);
    }

    @Test
    void testGet_caseInsensitive_withMissingKey_returnsNull() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("CUSTOMER", "v");

        assertThat(map.get("UNKNOWN")).as("missing key returns null.").isNull();
    }

    // -------------------------------------------------------------------------
    // containsTable
    // -------------------------------------------------------------------------

    @Test
    void testContainsTable_caseInsensitive_withExistingTable_returnsTrue() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("PRODUCT", null);

        assertThat(map.containsTable("PRODUCT")).as("contains existing.").isTrue();
    }

    @Test
    void testContainsTable_caseInsensitive_withLowercaseLookup_returnsTrue() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("PRODUCT", null);

        assertThat(map.containsTable("product")).as("case-insensitive contains.").isTrue();
    }

    @Test
    void testContainsTable_withMissingTable_returnsFalse() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("PRODUCT", null);

        assertThat(map.containsTable("UNKNOWN")).as("missing table returns false.").isFalse();
    }

    @Test
    void testContainsTable_withEmptyMap_returnsFalse()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.containsTable("ANY")).as("empty map returns false.").isFalse();
    }

    // -------------------------------------------------------------------------
    // getTableNames
    // -------------------------------------------------------------------------

    @Test
    void testGetTableNames_withEmptyMap_returnsEmptyArray()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.getTableNames()).as("empty map returns empty array.").isEmpty();
    }

    @Test
    void testGetTableNames_afterAddingTables_returnsNamesInInsertionOrder() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ALPHA", null);
        map.add("BETA", null);
        map.add("GAMMA", null);

        assertThat(map.getTableNames()).as("insertion order preserved.")
                .containsExactly("ALPHA", "BETA", "GAMMA");
    }

    @Test
    void testGetTableNames_preservesOriginalCaseInReturnedNames() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("MyTable", null);

        assertThat(map.getTableNames()).as("original case preserved in names.")
                .containsExactly("MyTable");
    }

    // -------------------------------------------------------------------------
    // isLastTable
    // -------------------------------------------------------------------------

    @Test
    void testIsLastTable_withEmptyMap_returnsFalse()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.isLastTable("ANY")).as("empty map returns false.").isFalse();
    }

    @Test
    void testIsLastTable_withLastAddedTable_returnsTrue() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", null);
        map.add("SECOND", null);

        assertThat(map.isLastTable("SECOND")).as("last added table.").isTrue();
    }

    @Test
    void testIsLastTable_withNonLastTable_returnsFalse() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", null);
        map.add("SECOND", null);

        assertThat(map.isLastTable("FIRST")).as("non-last table returns false.").isFalse();
    }

    @Test
    void testIsLastTable_caseInsensitive_withLowercaseLookup_returnsTrue() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("LAST", null);

        assertThat(map.isLastTable("last")).as("case-insensitive last check.").isTrue();
    }

    @Test
    void testIsLastTable_afterSetLastTable_returnsOverriddenTable() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", null);
        map.add("SECOND", null);
        map.setLastTable("FIRST");

        assertThat(map.isLastTable("FIRST")).as("overridden last table.").isTrue();
        assertThat(map.isLastTable("SECOND")).as("no longer last after override.").isFalse();
    }

    // -------------------------------------------------------------------------
    // getLastTableName
    // -------------------------------------------------------------------------

    @Test
    void testGetLastTableName_withEmptyMap_returnsNull()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.getLastTableName()).as("empty map returns null.").isNull();
    }

    @Test
    void testGetLastTableName_afterAddingTables_returnsLastAddedName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", null);
        map.add("SECOND", null);

        assertThat(map.getLastTableName()).as("last added.").isEqualTo("SECOND");
    }

    @Test
    void testGetLastTableName_afterSetLastTable_returnsOverriddenName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", null);
        map.add("SECOND", null);
        map.setLastTable("FIRST");

        assertThat(map.getLastTableName()).as("override active.").isEqualTo("FIRST");
    }

    // -------------------------------------------------------------------------
    // setLastTable
    // -------------------------------------------------------------------------

    @Test
    void testSetLastTable_withExistingTable_overridesLastTableName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("TABLE_A", null);
        map.add("TABLE_B", null);
        map.setLastTable("TABLE_A");

        assertThat(map.getLastTableName()).as("override set.").isEqualTo("TABLE_A");
    }

    @Test
    void testSetLastTable_withNonExistentTable_throwsNoSuchTableException() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("TABLE_A", null);

        assertThatThrownBy(() -> map.setLastTable("NONEXISTENT"))
                .as("non-existent table throws.")
                .isInstanceOf(NoSuchTableException.class);
    }

    // -------------------------------------------------------------------------
    // orderedValues
    // -------------------------------------------------------------------------

    @Test
    void testOrderedValues_withEmptyMap_returnsEmptyCollection()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.orderedValues()).as("empty map returns empty collection.").isEmpty();
    }

    @Test
    void testOrderedValues_afterAddingTables_returnsValuesInInsertionOrder() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("FIRST", "v1");
        map.add("SECOND", "v2");
        map.add("THIRD", "v3");

        assertThat(map.orderedValues()).as("values in insertion order.")
                .containsExactly("v1", "v2", "v3");
    }

    @Test
    void testOrderedValues_withNullValues_includesNullsInOrder() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("A", "v1");
        map.add("B", null);
        map.add("C", "v3");

        assertThat(map.orderedValues()).as("null values included.")
                .containsExactly("v1", null, "v3");
    }

    @Test
    void testOrderedValues_afterUpdate_reflectsUpdatedValue() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ONLY", "original");
        map.update("ONLY", "updated");

        assertThat(map.orderedValues()).as("updated value returned.")
                .containsExactly("updated");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void testUpdate_withExistingTable_replacesAssociatedObject() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", "old");
        map.update("ORDERS", "new");

        assertThat(map.get("ORDERS")).as("value replaced.").isEqualTo("new");
    }

    @Test
    void testUpdate_caseInsensitive_withLowercaseKey_updatesStoredEntry() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", "old");
        map.update("orders", "new");

        assertThat(map.get("ORDERS")).as("case-insensitive update.").isEqualTo("new");
    }

    @Test
    void testUpdate_withNonExistentTable_throwsIllegalArgumentException() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", null);

        assertThatThrownBy(() -> map.update("UNKNOWN", "v"))
                .as("non-existent table throws.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // getTableName (case normalisation)
    // -------------------------------------------------------------------------

    @Test
    void testGetTableName_caseInsensitive_withLowercaseInput_returnsUppercase()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);

        assertThat(map.getTableName("orders")).as("uppercased.").isEqualTo("ORDERS");
    }

    @Test
    void testGetTableName_caseSensitive_withLowercaseInput_returnsUnchanged()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(true);

        assertThat(map.getTableName("orders")).as("case preserved.").isEqualTo("orders");
    }

    @Test
    void testGetTableName_caseInsensitive_withTurkishLocaleCharacter_upperCasesCorrectly()
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        // 'i' in Turkish locale upper-cases to 'İ', but we require English 'I'
        assertThat(map.getTableName("info")).as("English uppercase.").isEqualTo("INFO");
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void testToString_withPopulatedMap_containsClassName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", null);

        assertThat(map.toString()).as("contains class name.")
                .contains("OrderedTableNameMap");
    }

    @Test
    void testToString_withPopulatedMap_containsTableName() throws Exception
    {
        final OrderedTableNameMap map = new OrderedTableNameMap(false);
        map.add("ORDERS", null);

        assertThat(map.toString()).as("contains table name.").contains("ORDERS");
    }
}
