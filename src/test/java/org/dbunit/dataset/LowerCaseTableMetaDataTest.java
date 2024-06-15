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
class LowerCaseTableMetaDataTest
{

    @Test
    void testGetTableName() throws Exception
    {
        final String original = "TABLE_NAME";
        final String expected = original.toLowerCase();

        final ITableMetaData metaData = new LowerCaseTableMetaData(
                new DefaultTableMetaData(original, new Column[0]));

        assertThat(metaData.getTableName()).as("table name")
                .isEqualTo(expected);
    }

    @Test
    void testGetColumns() throws Exception
    {
        final Column[] columns = new Column[] {
                new Column("NUMBER_COLUMN", DataType.NUMERIC, "qwerty",
                        Column.NULLABLE),
                new Column("STRING_COLUMN", DataType.VARCHAR, "toto",
                        Column.NO_NULLS),
                new Column("BOOLEAN_COLUMN", DataType.BOOLEAN),};

        final ITableMetaData metaData =
                new LowerCaseTableMetaData("TABLE_NAME", columns);

        final Column[] lowerColumns = metaData.getColumns();
        assertThat(lowerColumns).as("column count").hasSameSizeAs(columns);
        for (int i = 0; i < columns.length; i++)
        {
            final Column column = columns[i];
            final Column lowerColumn = lowerColumns[i];

            assertThat(lowerColumn.getColumnName()).as("name")
                    .isEqualTo(column.getColumnName().toLowerCase());
            assertThat(
                    column.getColumnName().equals(lowerColumn.getColumnName()))
                            .as("name not equals").isFalse();
            assertThat(lowerColumn.getDataType()).as("type")
                    .isEqualTo(column.getDataType());
            assertThat(lowerColumn.getSqlTypeName()).as("sql type")
                    .isEqualTo(column.getSqlTypeName());
            assertThat(lowerColumn.getNullable()).as("nullable")
                    .isEqualTo(column.getNullable());
        }
        assertThat(metaData.getPrimaryKeys().length).as("key count")
                .isEqualTo(0);
    }

    @Test
    void testGetPrimaryKeys() throws Exception
    {
        final Column[] columns = new Column[] {
                new Column("NUMBER_COLUMN", DataType.NUMERIC, "qwerty",
                        Column.NULLABLE),
                new Column("STRING_COLUMN", DataType.VARCHAR, "toto",
                        Column.NO_NULLS),
                new Column("BOOLEAN_COLUMN", DataType.BOOLEAN),};
        final String[] keyNames =
                new String[] {"Boolean_Column", "Number_Column"};

        final ITableMetaData metaData =
                new LowerCaseTableMetaData("TABLE_NAME", columns, keyNames);

        final Column[] keys = metaData.getPrimaryKeys();
        assertThat(keys).as("key count").hasSameSizeAs(keyNames);
        for (int i = 0; i < keys.length; i++)
        {
            assertThat(keyNames[i]).as("name not equals")
                    .isNotEqualTo(keys[i].getColumnName());
            assertThat(keys[i].getColumnName()).as("key name")
                    .isEqualTo(keyNames[i].toLowerCase());
        }
    }

}
