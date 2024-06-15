/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
import static org.junit.jupiter.api.Assertions.fail;

import org.dbunit.dataset.Columns.ColumnDiff;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @version $Revision$
 * @since 2.3.0
 */
class ColumnsTest
{

    @Test
    void testGetColumn() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("c1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c3", DataType.UNKNOWN),
                        new Column("c4", DataType.UNKNOWN),};

        for (int i = 0; i < columns.length; i++)
        {
            assertThat(Columns.getColumn("c" + i, columns))
                    .as("find column same").isEqualTo(columns[i]);
        }
    }

    @Test
    void testGetColumnCaseInsensitive() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("C1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("C3", DataType.UNKNOWN),
                        new Column("c4", DataType.UNKNOWN),};

        for (int i = 0; i < columns.length; i++)
        {
            assertThat(Columns.getColumn("c" + i, columns))
                    .as("find column same").isEqualTo(columns[i]);
        }
    }

    @Test
    void testGetColumnValidated() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("C1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),};
        for (int i = 0; i < columns.length; i++)
        {
            assertThat(Columns.getColumnValidated("c" + i, columns, "TableABC"))
                    .as("find column same").isEqualTo(columns[i]);
        }
    }

    @Test
    void testGetColumnValidatedColumnNotFound() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("C1", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),};
        try
        {
            Columns.getColumnValidated("A1", columns, "TableABC");
            fail("Should not be able to get a validated column that does not exist");
        } catch (final NoSuchColumnException expected)
        {
            assertThat(expected.getMessage()).isEqualTo("TableABC.A1");
        }
    }

    @Test
    void testGetColumnDiff_NoDifference() throws Exception
    {
        final Column[] expectedColumns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("c1", DataType.UNKNOWN),};
        final Column[] actualColumns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("c1", DataType.UNKNOWN),};
        final ITableMetaData metaDataExpected = createMetaData(expectedColumns);
        final ITableMetaData metaDataActual = createMetaData(actualColumns);

        // Create the difference
        final ColumnDiff diff =
                new ColumnDiff(metaDataExpected, metaDataActual);
        assertThat(diff.hasDifference()).isEqualTo(false);
        assertThat(diff.getExpectedAsString()).isEqualTo("[]");
        assertThat(diff.getActualAsString()).isEqualTo("[]");
        assertThat(diff.getMessage()).isEqualTo("no difference found");
    }

    @Test
    void testGetColumnDiffDifferentOrder_NoDifference() throws Exception
    {
        // order [c0, c1]
        final Column[] expectedColumns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("c1", DataType.UNKNOWN),};
        // order [c1, c0]
        final Column[] actualColumnsDifferentOrder =
                new Column[] {new Column("c1", DataType.UNKNOWN),
                        new Column("c0", DataType.UNKNOWN),};
        final ITableMetaData metaDataExpected = createMetaData(expectedColumns);
        final ITableMetaData metaDataActual =
                createMetaData(actualColumnsDifferentOrder);

        // Create the difference
        final ColumnDiff diff =
                new ColumnDiff(metaDataExpected, metaDataActual);
        assertThat(diff.hasDifference()).isEqualTo(false);
        assertThat(diff.getExpectedAsString()).isEqualTo("[]");
        assertThat(diff.getActualAsString()).isEqualTo("[]");
        assertThat(diff.getMessage()).isEqualTo("no difference found");
    }

    @Test
    void testGetColumnDiff_Difference() throws Exception
    {
        final Column[] expectedColumns =
                new Column[] {new Column("c0", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),
                        new Column("c1", DataType.UNKNOWN),};
        final Column[] actualColumns =
                new Column[] {new Column("d0", DataType.UNKNOWN),
                        new Column("c2", DataType.UNKNOWN),};
        final ITableMetaData metaDataExpected = createMetaData(expectedColumns);
        final ITableMetaData metaDataActual = createMetaData(actualColumns);

        // Create the difference
        final ColumnDiff diff =
                new ColumnDiff(metaDataExpected, metaDataActual);
        assertThat(diff.hasDifference()).isTrue();
        assertThat(diff.getExpected()).hasSize(2);
        assertThat(diff.getActual()).hasSize(1);
        assertThat(diff.getExpected()[0]).isEqualTo(expectedColumns[0]);
        assertThat(diff.getExpected()[1]).isEqualTo(expectedColumns[2]);
        assertThat(diff.getActual()[0]).isEqualTo(actualColumns[0]);
        assertThat(diff.getExpectedAsString()).isEqualTo("[c0, c1]");
        assertThat(diff.getActualAsString()).isEqualTo("[d0]");
        assertThat(diff.getMessage()).isEqualTo(
                "column count (table=MY_TABLE, expectedColCount=3, actualColCount=2)");
    }

    private ITableMetaData createMetaData(final Column[] columns)
    {
        final DefaultTableMetaData tableMetaData =
                new DefaultTableMetaData("MY_TABLE", columns);
        return tableMetaData;
    }

}
