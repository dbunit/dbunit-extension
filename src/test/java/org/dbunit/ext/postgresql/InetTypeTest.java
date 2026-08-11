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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;

import org.dbunit.dataset.datatype.AbstractDataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link InetType}.
 *
 * @author DbUnit.org
 */
@ExtendWith(MockitoExtension.class)
class InetTypeTest extends AbstractPostgresqlStringDataTypeTest
{
    @Mock
    private PreparedStatement statement;

    @Override
    protected AbstractDataType createType()
    {
        return new InetType();
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

    @Test
    void testTypeCast_withNullValue_returnsNull() throws TypeCastException
    {
        final InetType type = new InetType();
        final Object result = type.typeCast(null);
        assertThat(result)
                .as("typeCast() should return null when given null.")
                .isNull();
    }

    /**
     * Issue 930: AbstractDataType.compare() calls typeCast() directly, even
     * when comparing a null value against a non-null one.
     */
    @Test
    void testCompare_withNullAndNonNullValue_doesNotThrow() throws TypeCastException
    {
        final InetType type = new InetType();

        assertThatCode(() -> type.compare(null, "192.168.1.1"))
                .as("compare() should not throw NullPointerException when one value is null.")
                .doesNotThrowAnyException();

        assertThat(type.compare(null, "192.168.1.1"))
                .as("compare() should treat null as less than a non-null value.")
                .isEqualTo(-1);
    }

    /**
     * Issue 930: setSqlValue() bypasses typeCast() and dereferences the raw
     * value directly, so it needs its own null guard.
     */
    @Test
    void testSetSqlValue_withNullValue_doesNotThrowAndSetsSqlNull() throws Exception
    {
        final InetType type = new InetType();

        assertThatCode(() -> type.setSqlValue(null, 1, statement))
                .as("setSqlValue() should not throw NullPointerException when value is null.")
                .doesNotThrowAnyException();

        verify(statement).setNull(1, Types.OTHER);
    }
}
