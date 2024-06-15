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
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
class DefaultTableMetaDataTest
{

    protected ITableMetaData createMetaData(final String tableName,
            final Column[] columns, final String[] keyNames) throws Exception
    {
        return new DefaultTableMetaData(tableName, columns, keyNames);
    }

    @Test
    void testGetTableName() throws Exception
    {
        final String expected = "tableName";
        final ITableMetaData metaData = createMetaData(expected, null, null);

        assertThat(metaData.getTableName()).as("table name")
                .isEqualTo(expected);
    }

    @Test
    void testGetColumns() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("numberColumn", DataType.NUMERIC),
                        new Column("stringColumn", DataType.VARCHAR),
                        new Column("booleanColumn", DataType.BOOLEAN),};

        final ITableMetaData metaData = createMetaData("toto", columns, null);

        assertThat(metaData.getColumns()).as("column count")
                .hasSameSizeAs(columns);
        for (int i = 0; i < columns.length; i++)
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
                        new Column("booleanColumn", DataType.BOOLEAN),};
        final String[] keyNames =
                new String[] {"booleanColumn", "numberColumn"};

        final ITableMetaData metaData =
                createMetaData("toto", columns, keyNames);

        final Column[] keys = metaData.getPrimaryKeys();
        assertThat(keys).as("key count").hasSameSizeAs(keyNames);
        for (int i = 0; i < keys.length; i++)
        {
            assertThat(keys[i].getColumnName()).as("key name")
                    .isEqualTo(keyNames[i]);
        }
    }

    @Test
    void testGetPrimaryKeysColumnDontMatch() throws Exception
    {
        final Column[] columns =
                new Column[] {new Column("numberColumn", DataType.NUMERIC),
                        new Column("stringColumn", DataType.VARCHAR),
                        new Column("booleanColumn", DataType.BOOLEAN),};
        final String[] keyNames = new String[] {"invalidColumn"};

        final ITableMetaData metaData =
                createMetaData("toto", columns, keyNames);

        final Column[] keys = metaData.getPrimaryKeys();
        assertThat(keys.length).as("key count").isEqualTo(0);
    }
}