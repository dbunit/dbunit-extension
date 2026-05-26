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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachedTable}.
 */
class CachedTableTest
{
    private ITable buildSource() throws DataSetException
    {
        final Column[] columns = new Column[] {
                new Column("ID", DataType.INTEGER),
                new Column("NAME", DataType.VARCHAR)};
        final DefaultTable source = new DefaultTable("SOURCE_TABLE", columns);
        source.addRow(new Object[] {1, "Alice"});
        source.addRow(new Object[] {2, "Bob"});
        return source;
    }

    // -------------------------------------------------------------------------
    // Constructor: copies rows from source table
    // -------------------------------------------------------------------------

    @Test
    void testConstructor_withPopulatedTable_copiesAllRows() throws DataSetException
    {
        final ITable source = buildSource();
        final CachedTable cached = new CachedTable(source);

        assertThat(cached.getRowCount()).as("row count copied.").isEqualTo(2);
    }

    @Test
    void testConstructor_withEmptyTable_producesEmptyCachedTable() throws DataSetException
    {
        final Column[] columns = new Column[] {new Column("ID", DataType.INTEGER)};
        final DefaultTable empty = new DefaultTable("EMPTY", columns);
        final CachedTable cached = new CachedTable(empty);

        assertThat(cached.getRowCount()).as("empty source gives empty cache.").isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // getTableMetaData
    // -------------------------------------------------------------------------

    @Test
    void testGetTableMetaData_afterConstruction_preservesSourceTableName() throws DataSetException
    {
        final ITable source = buildSource();
        final CachedTable cached = new CachedTable(source);

        assertThat(cached.getTableMetaData().getTableName())
                .as("table name preserved.").isEqualTo("SOURCE_TABLE");
    }

    @Test
    void testGetTableMetaData_afterConstruction_preservesSourceColumnCount() throws DataSetException
    {
        final ITable source = buildSource();
        final CachedTable cached = new CachedTable(source);

        assertThat(cached.getTableMetaData().getColumns()).as("columns preserved.").hasSize(2);
    }

    // -------------------------------------------------------------------------
    // getValue
    // -------------------------------------------------------------------------

    @Test
    void testGetValue_afterConstruction_returnsCorrectValues() throws DataSetException
    {
        final ITable source = buildSource();
        final CachedTable cached = new CachedTable(source);

        assertThat(cached.getValue(0, "ID")).as("row 0 ID.").isEqualTo(1);
        assertThat(cached.getValue(0, "NAME")).as("row 0 NAME.").isEqualTo("Alice");
        assertThat(cached.getValue(1, "ID")).as("row 1 ID.").isEqualTo(2);
        assertThat(cached.getValue(1, "NAME")).as("row 1 NAME.").isEqualTo("Bob");
    }

    @Test
    void testGetValue_withNullValue_returnsNull() throws DataSetException
    {
        final Column[] columns = new Column[] {new Column("VAL", DataType.VARCHAR)};
        final DefaultTable source = new DefaultTable("T", columns);
        source.addRow(new Object[] {null});
        final CachedTable cached = new CachedTable(source);

        assertThat(cached.getValue(0, "VAL")).as("null value preserved.").isNull();
    }

    // -------------------------------------------------------------------------
    // Independence from source after construction
    // -------------------------------------------------------------------------

    @Test
    void testGetValue_afterSourceModified_returnsOriginalValues() throws DataSetException
    {
        final Column[] columns = new Column[] {new Column("ID", DataType.INTEGER)};
        final DefaultTable source = new DefaultTable("T", columns);
        source.addRow(new Object[] {10});
        final CachedTable cached = new CachedTable(source);

        // Mutating source after caching must not affect the cache
        source.addRow(new Object[] {20});

        assertThat(cached.getRowCount()).as("cache is independent of source after construction.")
                .isEqualTo(1);
    }
}
