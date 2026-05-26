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
package org.dbunit.ext.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntervalType}.
 *
 * @author DbUnit.org
 * @since 2.4.6
 */
class IntervalTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Override
    protected AbstractDataType createType()
    {
        return new IntervalType();
    }

    @Test
    void testTypeCast_withIntervalString_returnsStringRepresentation() throws TypeCastException
    {
        final IntervalType type = new IntervalType();
        final String interval = "1 year 2 months 3 days";
        final Object result = type.typeCast(interval);
        assertThat(result)
                .as("typeCast() should return the string representation of the interval.")
                .isEqualTo("1 year 2 months 3 days");
    }

    @Test
    void testTypeCast_withIso8601IntervalString_returnsStringRepresentation() throws TypeCastException
    {
        final IntervalType type = new IntervalType();
        final String interval = "P1Y2M3DT4H5M6S";
        final Object result = type.typeCast(interval);
        assertThat(result)
                .as("typeCast() should return the ISO 8601 interval string unchanged.")
                .isEqualTo("P1Y2M3DT4H5M6S");
    }

    @Test
    void testTypeCast_withObjectValue_returnsToStringResult() throws TypeCastException
    {
        final IntervalType type = new IntervalType();
        final Object value = new Object()
        {
            @Override
            public String toString()
            {
                return "00:30:00";
            }
        };
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should call toString() on the provided object.")
                .isEqualTo("00:30:00");
    }
}
