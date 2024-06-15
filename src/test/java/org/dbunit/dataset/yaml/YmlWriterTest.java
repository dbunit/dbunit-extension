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

package org.dbunit.dataset.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */
public class YmlWriterTest
{

    public static DefaultDataSet getDefaultDataSet() throws Exception
    {
        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN),
                        new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table1 = new DefaultTable("TABLE1", columns);
        table1.addRow();
        table1.setValue(0, col0, "t1c0r0");
        table1.setValue(0, col1, "t1c1r0");
        table1.addRow();
        table1.setValue(1, col0, "t1c0r1");
        table1.setValue(1, col1, "t1c1r1");

        final DefaultTable table2 = new DefaultTable("TABLE2", columns);
        table2.addRow();
        table2.setValue(0, col0, "t2c0r0");
        table2.setValue(0, col1, "t2c1r0");

        return new DefaultDataSet(table1, table2);
    }

    @Test
    void testWrite() throws Exception
    {
        final String expectedOutput = "TABLE1:\n" + "  - COL0: t1c0r0\n"
                + "    COL1: t1c1r0\n" + "  - COL0: t1c0r1\n"
                + "    COL1: t1c1r1\n" + "TABLE2:\n" + "  - COL0: t2c0r0\n"
                + "    COL1: t2c1r0\n";

        final IDataSet dataSet = getDefaultDataSet();

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteEmptyTable() throws Exception
    {
        final String expectedOutput =
                "TEST_TABLE:\n" + "  - COL0: value\n" + "EMPTY_TABLE: []\n";

        final IDataSet dataSet = getEmptyTableDataSet();

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteNullValue() throws Exception
    {
        final String expectedOutput = "TEST_TABLE:\n" + "  - COL0: c0r0\n"
                + "    COL1: c1r0\n" + "  - COL0: c0r1\n";

        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN),
                        new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, "c0r0");
        table.setValue(0, col1, "c1r0");
        table.addRow();
        table.setValue(1, col0, "c0r1");
        table.setValue(1, col1, null);

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(new DefaultDataSet(table));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteFlow() throws Exception
    {
        final String expectedOutput = "{\n" + "  TABLE1: [\n" + "    {\n"
                + "      COL0: t1c0r0,\n" + "      COL1: t1c1r0\n" + "    },\n"
                + "    {\n" + "      COL0: t1c0r1,\n" + "      COL1: t1c1r1\n"
                + "    }\n" + "  ]\n" + "  ,\n" + "  TABLE2: [\n" + "    {\n"
                + "      COL0: t2c0r0,\n" + "      COL1: t2c1r0\n" + "    }\n"
                + "  ]\n" + "  \n" + "}\n";

        final IDataSet dataSet = getDefaultDataSet();

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.setUseFlowStyle(true);
        yamlWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    public static IDataSet getEmptyTableDataSet() throws DataSetException
    {
        final String col0 = "COL0";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN),};

        final DefaultTable table1 = new DefaultTable("TEST_TABLE", columns);
        table1.addRow();
        table1.setValue(0, col0, "value");
        final DefaultTable table2 = new DefaultTable("EMPTY_TABLE", columns);
        return new DefaultDataSet(table1, table2);
    }

}
