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
package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OperationData}.
 */
class OperationDataTest
{
    private static final String SQL = "INSERT INTO ORDERS (ID, NAME) VALUES (?, ?)";

    private Column[] buildColumns()
    {
        return new Column[] {
                new Column("ID", DataType.INTEGER),
                new Column("NAME", DataType.VARCHAR)};
    }

    // -------------------------------------------------------------------------
    // getSql
    // -------------------------------------------------------------------------

    @Test
    void testGetSql_afterConstruction_returnsConstructorSql()
    {
        final OperationData data = new OperationData(SQL, buildColumns());

        assertThat(data.getSql()).as("SQL returned correctly.").isEqualTo(SQL);
    }

    @Test
    void testGetSql_withNullSql_returnsNull()
    {
        final OperationData data = new OperationData(null, buildColumns());

        assertThat(data.getSql()).as("null SQL returned as null.").isNull();
    }

    // -------------------------------------------------------------------------
    // getColumns
    // -------------------------------------------------------------------------

    @Test
    void testGetColumns_afterConstruction_returnsSameConstructorColumns()
    {
        final Column[] columns = buildColumns();
        final OperationData data = new OperationData(SQL, columns);

        assertThat(data.getColumns()).as("columns returned correctly.")
                .isSameAs(columns);
    }

    @Test
    void testGetColumns_withEmptyArray_returnsEmptyArray()
    {
        final Column[] columns = new Column[0];
        final OperationData data = new OperationData(SQL, columns);

        assertThat(data.getColumns()).as("empty column array returned.").isEmpty();
    }

    @Test
    void testGetColumns_withNullColumns_returnsNull()
    {
        final OperationData data = new OperationData(SQL, null);

        assertThat(data.getColumns()).as("null columns returned as null.").isNull();
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void testToString_afterConstruction_containsClassName()
    {
        final OperationData data = new OperationData(SQL, buildColumns());

        assertThat(data.toString()).as("toString contains class name.")
                .contains("OperationData");
    }

    @Test
    void testToString_afterConstruction_containsSql()
    {
        final OperationData data = new OperationData(SQL, buildColumns());

        assertThat(data.toString()).as("toString contains SQL.").contains(SQL);
    }

    @Test
    void testToString_withNullColumns_containsNullLiteral()
    {
        final OperationData data = new OperationData(SQL, null);

        assertThat(data.toString()).as("toString handles null columns.").contains("null");
    }
}
