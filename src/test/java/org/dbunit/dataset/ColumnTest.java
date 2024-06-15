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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.DatabaseMetaData;

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
class ColumnTest
{

    @Test
    void testGetColumnName() throws Exception
    {
        final String expected = "columnName";
        final Column column = new Column(expected, DataType.REAL);

        assertThat(column.getColumnName()).as("column name")
                .isEqualTo(expected);
    }

    @Test
    void testGetDataType() throws Exception
    {
        final DataType expected = DataType.DATE;
        final Column column = new Column(expected.toString(), expected);

        assertThat(column.getDataType()).as("data type").isEqualTo(expected);
    }

    @Test
    void testNullableValue() throws Exception
    {
        assertThat(Column.nullableValue(DatabaseMetaData.columnNullable))
                .as("nullable").isEqualTo(Column.NULLABLE);

        assertThat(Column.nullableValue(DatabaseMetaData.columnNoNulls))
                .as("not nullable").isEqualTo(Column.NO_NULLS);

        assertThat(Column.nullableValue(DatabaseMetaData.columnNullableUnknown))
                .as("nullable unknown").isEqualTo(Column.NULLABLE_UNKNOWN);

        assertThrows(IllegalArgumentException.class,
                () -> Column.nullableValue(12345),
                "Should throw an IllegalArgumentException");

    }

}
