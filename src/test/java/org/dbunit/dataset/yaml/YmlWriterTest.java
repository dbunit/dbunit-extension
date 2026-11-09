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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetBuilder;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void testWrite_withTwoTables_writesExpectedYamlOutput() throws Exception
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
    void testWriteEmptyTable_withOneEmptyTable_writesEmptyArraySyntax() throws Exception
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
    void testWriteNullValue_withNullCellValue_omitsNullKeyFromYamlRow() throws Exception
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
    void testWriteNoValueCell_withNoValueCellValue_omitsKeyFromYamlRow() throws Exception
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
        table.setValue(1, col1, ITable.NO_VALUE);

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(new DefaultDataSet(table));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteNullValue_withNullCellInEveryRow_writesNullKeyInFirstRowOnly() throws Exception
    {
        final String expectedOutput = "TEST_TABLE:\n" + "  - COL0: c0r0\n"
                + "    COL1: null\n" + "  - COL0: c0r1\n";

        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN),
                        new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, "c0r0");
        table.setValue(0, col1, null);
        table.addRow();
        table.setValue(1, col0, "c0r1");
        table.setValue(1, col1, null);

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(new DefaultDataSet(table));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput)
                .as("The first row carries every column, with null for a null cell, so that a"
                        + " column that is null in every row survives a read-back; a later row"
                        + " leaves the null cell out.")
                .isEqualTo(expectedOutput);
    }

    @Test
    void testWriteNoValueCell_withNoValueCellInFirstRow_writesNullKeyInFirstRow() throws Exception
    {
        final String expectedOutput = "TEST_TABLE:\n" + "  - COL0: c0r0\n"
                + "    COL1: null\n" + "  - COL0: c0r1\n"
                + "    COL1: c1r1\n";

        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN),
                        new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, "c0r0");
        table.setValue(0, col1, ITable.NO_VALUE);
        table.addRow();
        table.setValue(1, col0, "c0r1");
        table.setValue(1, col1, "c1r1");

        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);
        yamlWriter.write(new DefaultDataSet(table));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput)
                .as("A cell with no value in the first row is written as null, as the first row"
                        + " carries every column.")
                .isEqualTo(expectedOutput);
    }

    @Test
    void testWriteFlow_withFlowStyleEnabled_writesFlowStyleOutput() throws Exception
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testWrite_valuesStartingWithABracketOrBrace_readBackAsTheSameText(final boolean flowStyle)
            throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder().table("PLACEHOLDER")
                .columns("NULL_PLACEHOLDER", "RELATIVE_DATE", "BINARY", "LIST_LIKE", "MAPPING_LIKE")
                .row("[NULL]", "[now-1d]", "[TEXT]hello", "[1, 2]", "{a: 1}").build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter, flowStyle);

        yamlWriter.write(dataSet);
        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        final ITable table = readBack.getTable("PLACEHOLDER");
        final Object[] row = {table.getValue(0, "NULL_PLACEHOLDER"),
                table.getValue(0, "RELATIVE_DATE"), table.getValue(0, "BINARY"),
                table.getValue(0, "LIST_LIKE"), table.getValue(0, "MAPPING_LIKE")};
        assertThat(row)
                .as("The writer quotes a value that starts with a square bracket or a curly"
                        + " brace, in the block and in the flow style, so that the reader, which"
                        + " rejects the list or the mapping that YAML builds from an unquoted one,"
                        + " reads back the same text.")
                .containsExactly("[NULL]", "[now-1d]", "[TEXT]hello", "[1, 2]", "{a: 1}");
    }

    @Test
    void testWrite_valuesOfOtherTypes_writeTheirTextAndReadBackAsTheText() throws Exception
    {
        final UUID uuid = UUID.fromString("55a54008-ad1b-4c7a-8d4c-0a3c8f2b1e11");
        final IDataSet dataSet = new DataSetBuilder().table("OTHER_TYPES")
                .columns("UUID_VALUE", "URI_VALUE", "ENUM_VALUE", "BUILDER_VALUE")
                .row(uuid, URI.create("http://x"), Thread.State.NEW, new StringBuilder("sb"))
                .build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);

        yamlWriter.write(dataSet);
        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        final ITable table = readBack.getTable("OTHER_TYPES");
        final Object[] row = {table.getValue(0, "UUID_VALUE"), table.getValue(0, "URI_VALUE"),
                table.getValue(0, "ENUM_VALUE"), table.getValue(0, "BUILDER_VALUE")};
        assertThat(stringWriter.toString())
                .as("The writer writes no global tag, which the reader refuses.")
                .doesNotContain("!!");
        assertThat(row)
                .as("The writer writes a value of another type as its text, instead of a global"
                        + " tag, or an empty mapping that loses the value.")
                .containsExactly(uuid.toString(), "http://x", "NEW", "sb");
    }

    @Test
    void testWrite_arrayAndListValues_writeASequenceTheReaderRejects() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder().table("SEQUENCES")
                .columns("ARRAY_VALUE", "LIST_VALUE")
                .row(new Object[] {1, 2}, Arrays.asList("a", "b")).build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);

        yamlWriter.write(dataSet);

        assertThat(stringWriter.toString())
                .as("An array and a list are written as YAML sequences, and not as the text of"
                        + " the array object.")
                .doesNotContain("[Ljava").doesNotContain("@");
        assertThatThrownBy(() -> new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8))))
                        .as("The reader rejects a sequence in a cell, as for a document that"
                                + " holds one.")
                        .isInstanceOf(DataSetException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testWrite_textWithAControlCharacter_readsBackAsTheSameText(final boolean flowStyle)
            throws Exception
    {
        final String formFeed = "form\fFeed";
        final String nul = "nul\u0000x";
        final String escape = "esc\u001B[0m";
        final String delete = "del\u007Fx";
        final String c1Control = "c1\u0090dcs";
        final IDataSet dataSet = new DataSetBuilder().table("CONTROL_CHARACTERS")
                .columns("FORM_FEED", "NUL", "ESCAPE", "DELETE", "C1_CONTROL")
                .row(formFeed, nul, escape, delete, c1Control).build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter, flowStyle);

        yamlWriter.write(dataSet);
        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        final ITable table = readBack.getTable("CONTROL_CHARACTERS");
        final Object[] row = {table.getValue(0, "FORM_FEED"), table.getValue(0, "NUL"),
                table.getValue(0, "ESCAPE"), table.getValue(0, "DELETE"),
                table.getValue(0, "C1_CONTROL")};
        assertThat(stringWriter.toString())
                .as("The writer writes text with a control character as a quoted value with YAML"
                        + " escapes, not as a !!binary value.")
                .doesNotContain("!!binary");
        assertThat(row)
                .as("The text with a control character reads back as the same text, in the block"
                        + " and in the flow style, where a !!binary value would read back as bytes.")
                .containsExactly(formFeed, nul, escape, delete, c1Control);
    }

    @Test
    void testWrite_textWithTabLineFeedAndCarriageReturn_readsBackAsTheSameText() throws Exception
    {
        final String text = "tab\tnl\ncr\r";
        final IDataSet dataSet = new DataSetBuilder().table("WHITESPACE").columns("TEXT")
                .row(text).build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);

        yamlWriter.write(dataSet);
        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(readBack.getTable("WHITESPACE").getValue(0, "TEXT"))
                .as("The tab, the line feed and the carriage return are printable to YAML, and"
                        + " the text with them reads back as the same text.")
                .isEqualTo(text);
    }

    @Test
    void testWrite_byteArrayValue_stillWritesBinaryThatReadsBackAsTheBytes() throws Exception
    {
        final byte[] bytes = {0, 1, 2, 12, 27, 127};
        final IDataSet dataSet = new DataSetBuilder().table("BLOBS").columns("BLOB_VALUE")
                .row((Object) bytes).build();
        final StringWriter stringWriter = new StringWriter();
        final YamlWriter yamlWriter = new YamlWriter(stringWriter);

        yamlWriter.write(dataSet);
        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(stringWriter.toString())
                .as("A byte array is not text, so the writer writes it as a !!binary value.")
                .contains("!!binary");
        assertThat(readBack.getTable("BLOBS").getValue(0, "BLOB_VALUE"))
                .as("The !!binary value reads back as the same bytes.").isEqualTo(bytes);
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


    @Test
    void testWrite_withRowsInTableWithNoColumns_preservesRowCountOnRoundTrip() throws Exception
    {
        final DefaultTable table = new DefaultTable("NO_COLUMNS");
        table.addRow();
        table.addRow();

        final StringWriter stringWriter = new StringWriter();
        new YamlWriter(stringWriter).write(new DefaultDataSet(table));

        final IDataSet readBack = new YamlDataSet(new ByteArrayInputStream(
                stringWriter.toString().getBytes(StandardCharsets.UTF_8)));
        final ITable actualTable = readBack.getTable("NO_COLUMNS");

        assertThat(actualTable.getRowCount()).as("Rows of a table with no columns survive the round-trip.")
                .isEqualTo(2);
    }
}
