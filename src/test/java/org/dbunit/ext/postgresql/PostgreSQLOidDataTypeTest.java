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

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostgreSQLOidDataType}.
 *
 * @author DbUnit.org
 */
class PostgreSQLOidDataTypeTest
{
    @Test
    void testGetSqlType_onNewInstance_returnsTypesBigint()
    {
        final PostgreSQLOidDataType type = new PostgreSQLOidDataType();
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.BIGINT for OID type.")
                .isEqualTo(Types.BIGINT);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final PostgreSQLOidDataType type = new PostgreSQLOidDataType();
        assertThat(type.isNumber())
                .as("isNumber() should return false for OID type.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsByteArrayClass()
    {
        final PostgreSQLOidDataType type = new PostgreSQLOidDataType();
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return byte[].class for OID type.")
                .isEqualTo(byte[].class);
    }

    @Test
    void testInstantiation_withNoArgs_createsNonNullInstance()
    {
        final PostgreSQLOidDataType type = new PostgreSQLOidDataType();
        assertThat(type)
                .as("PostgreSQLOidDataType should be instantiable with no arguments.")
                .isNotNull();
    }
}
