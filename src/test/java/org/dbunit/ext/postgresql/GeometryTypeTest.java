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
 * Unit tests for {@link GeometryType}.
 *
 * @author DbUnit.org
 */
class GeometryTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Override
    protected AbstractDataType createType()
    {
        return new GeometryType();
    }

    @Test
    void testTypeCast_withWktPointString_returnsStringRepresentation() throws TypeCastException
    {
        final GeometryType type = new GeometryType();
        final String wkt = "POINT(1.0 2.0)";
        final Object result = type.typeCast(wkt);
        assertThat(result)
                .as("typeCast() should return the string representation of the geometry value.")
                .isEqualTo("POINT(1.0 2.0)");
    }

    @Test
    void testTypeCast_withWktPolygonString_returnsStringRepresentation() throws TypeCastException
    {
        final GeometryType type = new GeometryType();
        final String wkt = "POLYGON((0 0,1 0,1 1,0 1,0 0))";
        final Object result = type.typeCast(wkt);
        assertThat(result)
                .as("typeCast() should return the string representation of polygon WKT.")
                .isEqualTo("POLYGON((0 0,1 0,1 1,0 1,0 0))");
    }

    @Test
    void testTypeCast_withObjectValue_returnsToStringResult() throws TypeCastException
    {
        final GeometryType type = new GeometryType();
        final Object value = new Object()
        {
            @Override
            public String toString()
            {
                return "LINESTRING(0 0, 1 1)";
            }
        };
        final Object result = type.typeCast(value);
        assertThat(result)
                .as("typeCast() should call toString() on the provided object.")
                .isEqualTo("LINESTRING(0 0, 1 1)");
    }
}
