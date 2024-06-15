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

import java.sql.Time;
import java.sql.Timestamp;

import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
class DataSetUtilsTest
{

    @Test
    void testGetQualifiedName() throws Exception
    {
        assertThat(DataSetUtils.getQualifiedName("prefix", "name"))
                .as("prefix + name").isEqualTo("prefix.name");

        assertThat(DataSetUtils.getQualifiedName(null, "name"))
                .as("null prefix").isEqualTo("name");

        assertThat(DataSetUtils.getQualifiedName("", "name")).as("empty prefix")
                .isEqualTo("name");

        assertThat(DataSetUtils.getQualifiedName("wrongPrefix", "prefix.name"))
                .as("existing prefix").isEqualTo("prefix.name");

        assertThat(DataSetUtils.getQualifiedName("prefix", "name"))
                .as("escaped prefix + name").isEqualTo("prefix.name");

        assertThat(DataSetUtils.getQualifiedName("prefix", "name", "[?]"))
                .as("escaped prefix + name").isEqualTo("[prefix].[name]");

        assertThat(DataSetUtils.getQualifiedName("prefix", "name", "\""))
                .as("escaped prefix + name").isEqualTo("\"prefix\".\"name\"");
    }

    @Test
    void testGetEscapedName() throws Exception
    {
        assertThat(DataSetUtils.getEscapedName("name", "'?'"))
                .isEqualTo("'name'");

        assertThat(DataSetUtils.getEscapedName("name", "[?]"))
                .isEqualTo("[name]");

        // assertThat(DataSetUtils.getEscapedName(null, "[?]")).isEqualTo(null);

        assertThat(DataSetUtils.getEscapedName("name", null)).isEqualTo("name");

        assertThat(DataSetUtils.getEscapedName("name", "invalid pattern!"))
                .isEqualTo("name");

        assertThat(DataSetUtils.getEscapedName("name", "\""))
                .isEqualTo("\"name\"");
    }

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
            assertThat(DataSetUtils.getColumn("c" + i, columns))
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
            assertThat(DataSetUtils.getColumn("c" + i, columns))
                    .as("find column same").isEqualTo(columns[i]);
        }
    }

    @Test
    void testGetTables() throws Exception
    {
        final String[] expected = {"t0", "t1", "t2", "t3"};
        final ITable[] testTables =
                new ITable[] {new DefaultTable("t0"), new DefaultTable("t1"),
                        new DefaultTable("t2"), new DefaultTable("t3"),};

        final ITable[] tables =
                DataSetUtils.getTables(new DefaultDataSet(testTables));
        assertThat(tables).as("table count").hasSameSizeAs(expected);
        for (int i = 0; i < tables.length; i++)
        {
            final String name = tables[i].getTableMetaData().getTableName();
            assertThat(name).as("table name").isEqualTo(expected[i]);
        }
    }

    @Test
    void testGetTablesByNames() throws Exception
    {
        final String[] expected = {"t0", "t2"};
        final ITable[] testTables =
                new ITable[] {new DefaultTable("t0"), new DefaultTable("t1"),
                        new DefaultTable("t2"), new DefaultTable("t3"),};

        final ITable[] tables = DataSetUtils.getTables(expected,
                new DefaultDataSet(testTables));
        assertThat(tables).as("table count").hasSameSizeAs(expected);
        for (int i = 0; i < tables.length; i++)
        {
            final String name = tables[i].getTableMetaData().getTableName();
            assertThat(name).as("table name").isEqualTo(expected[i]);
        }
    }

    @Test
    void testGetReserseNames() throws Exception
    {
        final String[] expected = {"t3", "t2", "t1", "t0"};
        final ITable[] testTables =
                new ITable[] {new DefaultTable("t0"), new DefaultTable("t1"),
                        new DefaultTable("t2"), new DefaultTable("t3"),};

        final String[] names = DataSetUtils
                .getReverseTableNames(new DefaultDataSet(testTables));
        assertThat(names).as("table count").hasSameSizeAs(expected);
        for (int i = 0; i < names.length; i++)
        {
            assertThat(names[i]).as("table name").isEqualTo(expected[i]);
        }
    }

    @Test
    void testGetSqlValueString() throws Exception
    {
        final ValueStringData[] values = new ValueStringData[] {
                new ValueStringData(null, DataType.REAL, "NULL"),
                new ValueStringData("1234", DataType.NUMERIC, "1234"),
                new ValueStringData("1234", DataType.VARCHAR, "'1234'"),
                new ValueStringData(Float.valueOf("1234.45"), DataType.REAL,
                        "1234.45"),
                new ValueStringData(new java.sql.Date(0L), DataType.DATE,
                        "{d '" + new java.sql.Date(0L).toString() + "'}"),
                new ValueStringData(new Time(0L), DataType.TIME,
                        "{t '" + new Time(0L).toString() + "'}"),
                new ValueStringData(new Timestamp(0L), DataType.TIMESTAMP,
                        "{ts '" + new Timestamp(0L).toString() + "'}"),
                new ValueStringData("12'34", DataType.VARCHAR, "'12''34'"),
                new ValueStringData("'1234", DataType.VARCHAR, "'''1234'"),
                new ValueStringData("1234'", DataType.VARCHAR, "'1234'''"),
                new ValueStringData("'12'34'", DataType.VARCHAR,
                        "'''12''34'''"),};

        for (int i = 0; i < values.length; i++)
        {
            final ValueStringData data = values[i];
            final String valueString = DataSetUtils
                    .getSqlValueString(data.getValue(), data.getDataType());
            assertThat(valueString).as("data " + i)
                    .isEqualTo(data.getExpected());
        }
    }

    private class ValueStringData
    {
        private final Object _value;
        private final DataType _dataType;
        private final String _expected;

        public ValueStringData(final Object value, final DataType dataType,
                final String expected)
        {
            _value = value;
            _dataType = dataType;
            _expected = expected;
        }

        public Object getValue()
        {
            return _value;
        }

        public DataType getDataType()
        {
            return _dataType;
        }

        public String getExpected()
        {
            return _expected;
        }
    }

}
