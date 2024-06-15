package org.dbunit.dataset.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

/**
 * <p>
 * Copyright (c) 2003 OZ.COM. All Rights Reserved.
 * </p>
 *
 * @author manuel.laflamme
 * @since Jan 26, 2004
 */
class FlatDtdWriterTest
{
    @Test
    void testWriteSequenceModel() throws Exception
    {
        final String expectedOutput = "<!ELEMENT dataset (\n" + "    TABLE1*,\n"
                + "    TABLE2*)>\n" + "\n" + "<!ELEMENT TABLE1 EMPTY>\n"
                + "<!ATTLIST TABLE1\n" + "    COL0 CDATA #IMPLIED\n"
                + "    COL1 CDATA #IMPLIED\n" + "    COL2 CDATA #REQUIRED\n"
                + "    COL3 CDATA #IMPLIED\n" + // Has default value
                ">\n" + "\n" + "<!ELEMENT TABLE2 EMPTY>\n"
                + "<!ATTLIST TABLE2\n" + "    COL0 CDATA #IMPLIED\n"
                + "    COL1 CDATA #IMPLIED\n" + "    COL2 CDATA #REQUIRED\n"
                + "    COL3 CDATA #IMPLIED\n" + // Has default value
                ">\n" + "\n";

        final Column[] columns = new Column[] {
                new Column("COL0", DataType.UNKNOWN, Column.NULLABLE),
                new Column("COL1", DataType.UNKNOWN, Column.NULLABLE_UNKNOWN),
                new Column("COL2", DataType.UNKNOWN, Column.NO_NULLS),
                new Column("COL3", DataType.UNKNOWN,
                        DataType.UNKNOWN.toString(), Column.NO_NULLS,
                        "default"),};

        final DefaultTable table1 = new DefaultTable("TABLE1", columns);
        final DefaultTable table2 = new DefaultTable("TABLE2", columns);

        final StringWriter stringWriter = new StringWriter();
        final FlatDtdWriter dtdWriter = new FlatDtdWriter(stringWriter);
        dtdWriter.write(new DefaultDataSet(table1, table2));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteChoiceModel() throws Exception
    {
        final String expectedOutput = "<!ELEMENT dataset (\n" + "   (TABLE1|\n"
                + "    TABLE2)*)>\n" + "\n" + "<!ELEMENT TABLE1 EMPTY>\n"
                + "<!ATTLIST TABLE1\n" + "    COL0 CDATA #IMPLIED\n"
                + "    COL1 CDATA #IMPLIED\n" + "    COL2 CDATA #REQUIRED\n"
                + ">\n" + "\n" + "<!ELEMENT TABLE2 EMPTY>\n"
                + "<!ATTLIST TABLE2\n" + "    COL0 CDATA #IMPLIED\n"
                + "    COL1 CDATA #IMPLIED\n" + "    COL2 CDATA #REQUIRED\n"
                + ">\n" + "\n";

        final Column[] columns = new Column[] {
                new Column("COL0", DataType.UNKNOWN, Column.NULLABLE),
                new Column("COL1", DataType.UNKNOWN, Column.NULLABLE_UNKNOWN),
                new Column("COL2", DataType.UNKNOWN, Column.NO_NULLS),};

        final DefaultTable table1 = new DefaultTable("TABLE1", columns);
        final DefaultTable table2 = new DefaultTable("TABLE2", columns);

        final StringWriter stringWriter = new StringWriter();
        final FlatDtdWriter dtdWriter = new FlatDtdWriter(stringWriter);
        dtdWriter.setContentModel(FlatDtdWriter.CHOICE);
        dtdWriter.write(new DefaultDataSet(table1, table2));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

    @Test
    void testWriteChoiceModel_NoInputColumns() throws Exception
    {
        final String expectedOutput = "<!ELEMENT dataset (\n" + "   (TABLE1|\n"
                + "    TABLE2)*)>\n" + "\n" + "<!ELEMENT TABLE1 EMPTY>\n"
                + "<!ATTLIST TABLE1\n" + ">\n" + "\n"
                + "<!ELEMENT TABLE2 EMPTY>\n" + "<!ATTLIST TABLE2\n" + ">\n"
                + "\n";

        final Column[] columns = new Column[0];

        final DefaultTable table1 = new DefaultTable("TABLE1", columns);
        final DefaultTable table2 = new DefaultTable("TABLE2", columns);

        final StringWriter stringWriter = new StringWriter();
        final FlatDtdWriter dtdWriter = new FlatDtdWriter(stringWriter);
        dtdWriter.setContentModel(FlatDtdWriter.CHOICE);
        dtdWriter.write(new DefaultDataSet(table1, table2));

        final String actualOutput = stringWriter.toString();
        assertThat(actualOutput).as("output").isEqualTo(expectedOutput);
    }

}
