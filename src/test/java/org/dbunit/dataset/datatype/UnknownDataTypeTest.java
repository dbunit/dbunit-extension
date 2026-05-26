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

import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnknownDataType}.
 */
class UnknownDataTypeTest
{
    private final UnknownDataType type = (UnknownDataType) DataType.UNKNOWN;

    // -------------------------------------------------------------------------
    // Basic type properties
    // -------------------------------------------------------------------------

    @Test
    void testGetTypeName_onUnknownInstance_returnsUnknown()
    {
        assertThat(type.toString()).as("type name is UNKNOWN.").isEqualTo("UNKNOWN");
    }

    @Test
    void testGetSqlType_onUnknownInstance_returnsTypesOther()
    {
        assertThat(type.getSqlType()).as("SQL type is Types.OTHER.").isEqualTo(Types.OTHER);
    }

    // -------------------------------------------------------------------------
    // typeCast
    // -------------------------------------------------------------------------

    @Test
    void testTypeCast_withNoValue_returnsNull() throws TypeCastException
    {
        assertThat(type.typeCast(ITable.NO_VALUE)).as("NO_VALUE casts to null.").isNull();
    }

    @Test
    void testTypeCast_withString_returnsSameString() throws TypeCastException
    {
        assertThat(type.typeCast("hello")).as("String passes through unchanged.")
                .isEqualTo("hello");
    }

    @Test
    void testTypeCast_withInteger_returnsSameInteger() throws TypeCastException
    {
        final Integer value = Integer.valueOf(42);
        assertThat(type.typeCast(value)).as("Integer passes through unchanged.")
                .isSameAs(value);
    }

    @Test
    void testTypeCast_withNull_returnsNull() throws TypeCastException
    {
        assertThat(type.typeCast(null)).as("null passes through as null.").isNull();
    }

    // -------------------------------------------------------------------------
    // compare
    // -------------------------------------------------------------------------

    @Test
    void testCompare_withEqualObjects_returnsZero() throws TypeCastException
    {
        assertThat(type.compare("abc", "abc")).as("equal strings compare as 0.").isEqualTo(0);
    }

    @Test
    void testCompare_withSameInstance_returnsZero() throws TypeCastException
    {
        final Object obj = new Object()
        {
            @Override
            public String toString()
            {
                return "same";
            }
        };
        assertThat(type.compare(obj, obj)).as("same instance compares as 0.").isEqualTo(0);
    }

    @Test
    void testCompare_withDifferentStrings_returnsNonZero() throws TypeCastException
    {
        assertThat(type.compare("abc", "xyz")).as("different strings compare as non-zero.")
                .isNotEqualTo(0);
    }

    @Test
    void testCompare_withNullBothValues_returnsZero() throws TypeCastException
    {
        assertThat(type.compare(null, null)).as("null vs null compares as 0.").isEqualTo(0);
    }

    @Test
    void testCompare_withIntegersAsStrings_comparesByStringRepresentation()
            throws TypeCastException
    {
        // compare delegates to asString() for non-equal objects, so "10" < "9" lexicographically
        assertThat(type.compare(Integer.valueOf(10), Integer.valueOf(9)))
                .as("compare uses string representation for non-equal objects.")
                .isLessThan(0);
    }
}
