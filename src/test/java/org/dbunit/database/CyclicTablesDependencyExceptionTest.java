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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CyclicTablesDependencyException}.
 *
 * @since 3.2.0
 */
class CyclicTablesDependencyExceptionTest
{
    @Test
    void testMessageConstructor_withMessage_storesMessage()
    {
        final String message = "Cyclic dependency detected";
        final CyclicTablesDependencyException actual = new CyclicTablesDependencyException(message);

        assertThat(actual.getMessage()).as("message.").isEqualTo(message);
        assertThat(actual.getCause()).as("cause.").isNull();
    }

    @Test
    void testTableNameAndSetConstructor_withTableNameAndCyclicSet_buildsFormattedMessage()
    {
        final String tableName = "ORDER_ITEM";
        final Set<String> cyclicTableNames = new LinkedHashSet<>();
        cyclicTableNames.add("ORDER");
        cyclicTableNames.add("PRODUCT");

        final CyclicTablesDependencyException actual =
                new CyclicTablesDependencyException(tableName, cyclicTableNames);

        final String message = actual.getMessage();
        assertThat(message).as("message contains table name.").contains(tableName);
        assertThat(message).as("message contains cyclic tables.").contains("ORDER");
        assertThat(message).as("message contains cyclic tables.").contains("PRODUCT");
    }

    @Test
    void testTableNameAndSetConstructor_withSingleCyclicTable_buildsFormattedMessage()
    {
        final String tableName = "A";
        final Set<String> cyclicTableNames = new LinkedHashSet<>();
        cyclicTableNames.add("B");

        final CyclicTablesDependencyException actual =
                new CyclicTablesDependencyException(tableName, cyclicTableNames);

        assertThat(actual.getMessage()).as("message.").isEqualTo("Table: A ([B])");
    }

    @Test
    void testIsDataSetException_whenCreated_extendsDataSetException()
    {
        final CyclicTablesDependencyException actual =
                new CyclicTablesDependencyException("msg");

        assertThat(actual).as("is DataSetException.").isInstanceOf(DataSetException.class);
    }

    @Test
    void testTableNameAndSetConstructor_withEmptySet_includesEmptyBrackets()
    {
        final String tableName = "FOO";
        final Set<String> cyclicTableNames = new LinkedHashSet<>();

        final CyclicTablesDependencyException actual =
                new CyclicTablesDependencyException(tableName, cyclicTableNames);

        assertThat(actual.getMessage()).as("message.").isEqualTo("Table: FOO ([])");
    }
}
