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

import org.dbunit.dataset.datatype.AbstractDataType;
import org.junit.jupiter.api.Test;

/**
 * Abstract base for unit tests of PostgreSQL data types that map to SQL type
 * {@code OTHER} and represent values as {@code String}.
 *
 * <p>Subclasses supply the instance under test via {@link #createType()} and
 * inherit the three common property checks; they add type-specific
 * {@code typeCast} tests of their own.
 *
 * @author DbUnit.org
 */
abstract class AbstractPostgresqlStringDataTypeTest
{
    /**
     * Creates the data type instance under test.
     *
     * @return A new instance of the PostgreSQL data type being tested.
     */
    protected abstract AbstractDataType createType();

    @Test
    void testGetSqlType_onNewInstance_returnsTypesOther()
    {
        final AbstractDataType type = createType();
        assertThat(type.getSqlType())
                .as("getSqlType() should return Types.OTHER.")
                .isEqualTo(Types.OTHER);
    }

    @Test
    void testIsNumber_onNewInstance_returnsFalse()
    {
        final AbstractDataType type = createType();
        assertThat(type.isNumber())
                .as("isNumber() should return false.")
                .isFalse();
    }

    @Test
    void testGetTypeClass_onNewInstance_returnsStringClass()
    {
        final AbstractDataType type = createType();
        assertThat(type.getTypeClass())
                .as("getTypeClass() should return String.class.")
                .isEqualTo(String.class);
    }
}
