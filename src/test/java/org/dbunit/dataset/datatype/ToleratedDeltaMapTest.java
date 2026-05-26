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
package org.dbunit.dataset.datatype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.dbunit.dataset.datatype.ToleratedDeltaMap.Precision;
import org.dbunit.dataset.datatype.ToleratedDeltaMap.ToleratedDelta;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ToleratedDeltaMap} and its nested classes
 * {@link ToleratedDelta} and {@link Precision}.
 */
class ToleratedDeltaMapTest
{
    // -------------------------------------------------------------------------
    // getToleratedDeltas — lazy initialisation
    // -------------------------------------------------------------------------

    @Test
    void testGetToleratedDeltas_beforeAnyAdd_returnsNull()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();

        assertThat(map.getToleratedDeltas()).as("map null before first add.").isNull();
    }

    @Test
    void testGetToleratedDeltas_afterAdd_returnsNonNullMap()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("ORDERS", "AMOUNT", 0.01));

        assertThat(map.getToleratedDeltas()).as("map initialised after first add.").isNotNull();
    }

    // -------------------------------------------------------------------------
    // addToleratedDelta
    // -------------------------------------------------------------------------

    @Test
    void testAddToleratedDelta_withNullDelta_throwsNullPointerException()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();

        assertThatThrownBy(() -> map.addToleratedDelta(null))
                .as("null delta throws NullPointerException.")
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAddToleratedDelta_withDoubleDelta_storesDelta()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("ORDERS", "PRICE", 0.001));

        final ToleratedDelta found = map.findToleratedDelta("ORDERS", "PRICE");
        assertThat(found).as("delta stored.").isNotNull();
    }

    @Test
    void testAddToleratedDelta_withBigDecimalDelta_storesDelta()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(
                new ToleratedDelta("INVOICE", "TAX", new BigDecimal("0.0001")));

        assertThat(map.findToleratedDelta("INVOICE", "TAX")).as("BigDecimal delta stored.")
                .isNotNull();
    }

    @Test
    void testAddToleratedDelta_withDuplicateKey_replacesExistingEntry()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("T", "COL", 1.0));
        map.addToleratedDelta(new ToleratedDelta("T", "COL", 0.5));

        final ToleratedDelta found = map.findToleratedDelta("T", "COL");
        assertThat(found.getToleratedDelta().getDelta())
                .as("second add replaces first.")
                .isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void testAddToleratedDelta_withMultipleDistinctEntries_storesAll()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("T", "COL_A", 0.1));
        map.addToleratedDelta(new ToleratedDelta("T", "COL_B", 0.2));

        assertThat(map.findToleratedDelta("T", "COL_A")).as("COL_A stored.").isNotNull();
        assertThat(map.findToleratedDelta("T", "COL_B")).as("COL_B stored.").isNotNull();
        assertThat(map.getToleratedDeltas()).as("two entries.").hasSize(2);
    }

    // -------------------------------------------------------------------------
    // findToleratedDelta
    // -------------------------------------------------------------------------

    @Test
    void testFindToleratedDelta_withExistingKey_returnsDelta()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("ACCOUNTS", "BALANCE", 0.01));

        final ToleratedDelta found = map.findToleratedDelta("ACCOUNTS", "BALANCE");
        assertThat(found).as("delta found.").isNotNull();
        assertThat(found.getTableName()).as("table name correct.").isEqualTo("ACCOUNTS");
        assertThat(found.getColumnName()).as("column name correct.").isEqualTo("BALANCE");
    }

    @Test
    void testFindToleratedDelta_withMissingKey_returnsNull()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();
        map.addToleratedDelta(new ToleratedDelta("T", "COL", 0.01));

        assertThat(map.findToleratedDelta("T", "OTHER")).as("missing key returns null.").isNull();
    }

    @Test
    void testFindToleratedDelta_onEmptyMap_returnsNull()
    {
        final ToleratedDeltaMap map = new ToleratedDeltaMap();

        assertThat(map.findToleratedDelta("T", "COL")).as("empty map returns null.").isNull();
    }

    // -------------------------------------------------------------------------
    // ToleratedDelta.matches()
    // -------------------------------------------------------------------------

    @Test
    void testToleratedDeltaMatches_withMatchingTableAndColumn_returnsTrue()
    {
        final ToleratedDelta delta = new ToleratedDelta("ORDERS", "AMOUNT", 0.01);

        assertThat(delta.matches("ORDERS", "AMOUNT")).as("exact match returns true.").isTrue();
    }

    @Test
    void testToleratedDeltaMatches_withDifferentColumn_returnsFalse()
    {
        final ToleratedDelta delta = new ToleratedDelta("ORDERS", "AMOUNT", 0.01);

        assertThat(delta.matches("ORDERS", "PRICE")).as("different column returns false.")
                .isFalse();
    }

    @Test
    void testToleratedDeltaMatches_withDifferentTable_returnsFalse()
    {
        final ToleratedDelta delta = new ToleratedDelta("ORDERS", "AMOUNT", 0.01);

        assertThat(delta.matches("INVOICES", "AMOUNT")).as("different table returns false.")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // ToleratedDelta.toString()
    // -------------------------------------------------------------------------

    @Test
    void testToleratedDeltaToString_afterConstruction_containsTableAndColumnName()
    {
        final ToleratedDelta delta = new ToleratedDelta("MY_TABLE", "MY_COL", 0.05);
        final String str = delta.toString();

        assertThat(str).as("toString contains table name.").contains("MY_TABLE");
        assertThat(str).as("toString contains column name.").contains("MY_COL");
    }

    // -------------------------------------------------------------------------
    // Precision
    // -------------------------------------------------------------------------

    @Test
    void testPrecision_withZeroDelta_isAllowed()
    {
        final Precision p = new Precision(BigDecimal.ZERO);

        assertThat(p.getDelta()).as("zero delta allowed.").isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.isPercentage()).as("not percentage by default.").isFalse();
    }

    @Test
    void testPrecision_withNegativeDelta_throwsIllegalArgumentException()
    {
        assertThatThrownBy(() -> new Precision(new BigDecimal("-0.001")))
                .as("negative delta throws.")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPrecision_withPercentageFlag_storesFlag()
    {
        final Precision p = new Precision(new BigDecimal("5.0"), true);

        assertThat(p.isPercentage()).as("percentage flag stored.").isTrue();
        assertThat(p.getDelta()).as("delta stored.").isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    void testToleratedDelta_withPercentageConstructor_storesPrecision()
    {
        final ToleratedDelta delta =
                new ToleratedDelta("T", "COL", new BigDecimal("2.5"), true);

        assertThat(delta.getToleratedDelta().isPercentage()).as("percentage precision stored.")
                .isTrue();
    }
}
