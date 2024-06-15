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
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.dbunit.dataset.filter.IColumnFilter;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since May 11, 2004
 */
class FilteredTableMetaDataTest
{

    protected IColumnFilter createColumnFilter() throws Exception
    {
        final DefaultColumnFilter filter = new DefaultColumnFilter();
        filter.excludeColumn("excluded*");
        return filter;
    }

    @Test
    void testGetTableName() throws Exception
    {
        final String expected = "tableName";
        ITableMetaData metaData =
                new DefaultTableMetaData(expected, null, (Column[]) null);
        metaData = new FilteredTableMetaData(metaData, createColumnFilter());

        assertThat(metaData.getTableName()).as("table name")
                .isEqualTo(expected);
    }

    @Test
    void testGetColumns() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("numberColumn", DataType.NUMERIC),
                        new Column("stringColumn", DataType.VARCHAR),
                        new Column("booleanColumn", DataType.BOOLEAN),
                        new Column("excludedColumn", DataType.BOOLEAN),};

        ITableMetaData metaData =
                new DefaultTableMetaData("toto", columns, (Column[]) null);
        metaData = new FilteredTableMetaData(metaData, createColumnFilter());

        assertThat(metaData.getColumns().length).as("column count")
                .isEqualTo(3);
        for (int i = 0; i < 3; i++)
        {
            final Column column = columns[i];
            assertThat(metaData.getColumns()[i]).as("columns" + i)
                    .isEqualTo(column);
        }
        assertThat(metaData.getPrimaryKeys().length).as("key count")
                .isEqualTo(0);
    }

    @Test
    void testGetPrimaryKeys() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("numberColumn", DataType.NUMERIC),
                        new Column("stringColumn", DataType.VARCHAR),
                        new Column("booleanColumn", DataType.BOOLEAN),
                        new Column("excludedColumn", DataType.BOOLEAN),};
        final String[] keyNames = new String[] {"booleanColumn", "numberColumn",
                "excludedColumn"};

        ITableMetaData metaData =
                new DefaultTableMetaData("toto", columns, keyNames);
        metaData = new FilteredTableMetaData(metaData, createColumnFilter());

        final Column[] keys = metaData.getPrimaryKeys();
        assertThat(keys.length).as("key count").isEqualTo(2);
        for (int i = 0; i < 2; i++)
        {
            assertThat(keys[i].getColumnName()).as("key name")
                    .isEqualTo(keyNames[i]);
        }
    }
}