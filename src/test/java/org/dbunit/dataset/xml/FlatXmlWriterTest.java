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

package org.dbunit.dataset.xml;

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
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Sep 8, 2003$
 */
public class FlatXmlWriterTest
{

    @Test
    void testWrite() throws Exception
    {
        final String expectedOutput = "<dataset>\n"
                + "  <TABLE1 COL0=\"t1v1\" COL1=\"t1v2\"/>\n"
                + "  <TABLE2 COL0=\"t2v1\" COL1=\"t2v2\"/>\n" + "</dataset>\n";

        final IDataSet dataSet = XmlDataSetWriterTest.getDefaultDataSet();

        final StringWriter stringWriter = new StringWriter();
        final FlatXmlWriter xmlWriter = new FlatXmlWriter(stringWriter);
        xmlWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteWithDocType() throws Exception
    {
        final String expectedOutput =
                "<!DOCTYPE dataset SYSTEM \"dataset.dtd\">\n" + "<dataset>\n"
                        + "  <TABLE1 COL0=\"t1v1\" COL1=\"t1v2\"/>\n"
                        + "</dataset>\n";

        final IDataSet dataSet = XmlDataSetWriterTest.getMinimalDataSet();

        final StringWriter stringWriter = new StringWriter();
        final FlatXmlWriter xmlWriter = new FlatXmlWriter(stringWriter);
        xmlWriter.setDocType("dataset.dtd");
        xmlWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteExcludeEmptyTable() throws Exception
    {
        final String expectedOutput = "<dataset>\n"
                + "  <TEST_TABLE COL0=\"value\"/>\n" + "</dataset>\n";

        final IDataSet dataSet = getEmptyTableDataSet();

        final StringWriter stringWriter = new StringWriter();
        final FlatXmlWriter datasetWriter = new FlatXmlWriter(stringWriter);
        datasetWriter.setIncludeEmptyTable(false);
        datasetWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteIncludeEmptyTable() throws Exception
    {
        final String expectedOutput =
                "<dataset>\n" + "  <TEST_TABLE COL0=\"value\"/>\n"
                        + "  <EMPTY_TABLE/>\n" + "</dataset>\n";

        final IDataSet dataSet = getEmptyTableDataSet();

        final StringWriter stringWriter = new StringWriter();
        final FlatXmlWriter datasetWriter = new FlatXmlWriter(stringWriter);
        datasetWriter.setIncludeEmptyTable(true);
        datasetWriter.write(dataSet);

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteNullValue() throws Exception
    {
        final String expectedOutput =
                "<dataset>\n" + "  <TEST_TABLE COL0=\"c0r0\" COL1=\"c1r0\"/>\n"
                        + "  <TEST_TABLE COL0=\"c0r1\"/>\n" + "</dataset>\n";

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
        final FlatXmlWriter xmlWriter = new FlatXmlWriter(stringWriter);
        xmlWriter.write(new DefaultDataSet(table));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWritePrettyPrintDisabled() throws Exception
    {
        final String expectedOutput = "<dataset>"
                + "<TABLE1 COL0=\"t1v1\" COL1=\"t1v2\"/>" + "</dataset>";

        final IDataSet dataSet = XmlDataSetWriterTest.getMinimalDataSet();

        final StringWriter stringWriter = new StringWriter();
        final FlatXmlWriter xmlWriter = new FlatXmlWriter(stringWriter);
        xmlWriter.setPrettyPrint(false);
        xmlWriter.write(dataSet);

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
        final IDataSet dataSet = new DefaultDataSet(table1, table2);
        return dataSet;
    }

}
