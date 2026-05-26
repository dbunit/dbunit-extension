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

import java.sql.Types;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InetType}.
 *
 * @author DbUnit.org
 */
class InetTypeTest
{
    @Test
    void testGetSqlType_onNewInstance_returnsTypesOther()
    {
        final InetType type = new InetType();
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.OTHER for inet type.")
                .isEqualTo(Types.OTHER);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final InetType type = new InetType();
        assertThat(type.isNumber())
                .as("isNumber() should return false for inet type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final InetType type = new InetType();
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }

    @Test
    void testTypeCast_withIpv4AddressString_returnsStringRepresentation() throws TypeCastException
    {
        final InetType type = new InetType();
        final String address = "192.168.1.1";
        final Object result = type.typeCast(address);
        assertThat(result)
                .as("typeCast() should return the string representation of the IPv4 address.")
                .isEqualTo("192.168.1.1");
    }

    @Test
    void testTypeCast_withCidrNotation_returnsStringRepresentation() throws TypeCastException
    {
        final InetType type = new InetType();
        final String cidr = "10.0.0.0/8";
        final Object result = type.typeCast(cidr);
        assertThat(result)
                .as("typeCast() should return the string representation of CIDR notation.")
                .isEqualTo("10.0.0.0/8");
    }

    @Test
    void testTypeCast_withIpv6AddressString_returnsStringRepresentation() throws TypeCastException
    {
        final InetType type = new InetType();
        final String ipv6 = "::1";
        final Object result = type.typeCast(ipv6);
        assertThat(result)
                .as("typeCast() should return the string representation of the IPv6 address.")
                .isEqualTo("::1");
    }
}
