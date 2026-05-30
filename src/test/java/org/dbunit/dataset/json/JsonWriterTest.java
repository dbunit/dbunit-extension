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

package org.dbunit.dataset.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.dbunit.Assertion;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for {@link JsonWriter}.
 *
 * @author Jeff Jensen
 */
public class JsonWriterTest
{
    private static IDataSet buildTwoTableDataSet() throws Exception
    {
        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN), new Column(col1, DataType.UNKNOWN)};

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

    private static IDataSet buildEmptyTableDataSet() throws DataSetException
    {
        final Column[] columns =
                new Column[] {new Column("COL0", DataType.UNKNOWN)};
        final DefaultTable table1 = new DefaultTable("TEST_TABLE", columns);
        table1.addRow();
        table1.setValue(0, "COL0", "value");
        final DefaultTable table2 = new DefaultTable("EMPTY_TABLE", columns);
        return new DefaultDataSet(table1, table2);
    }

    @Test
    void testWrite_withTwoTables_producesValidJsonWithExpectedStructure() throws Exception
    {
        final IDataSet dataSet = buildTwoTableDataSet();
        final StringWriter stringWriter = new StringWriter();
        new JsonWriter(stringWriter).write(dataSet);

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root =
                mapper.readTree(new ByteArrayInputStream(
                        stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(root.isObject()).as("root is object.").isTrue();
        assertThat(root.size()).as("table count.").isEqualTo(2);

        final JsonNode table1 = root.get("TABLE1");
        assertThat(table1.isArray()).as("TABLE1 is array.").isTrue();
        assertThat(table1.size()).as("TABLE1 row count.").isEqualTo(2);
        assertThat(table1.get(0).get("COL0").asText()).as("TABLE1 row 0 COL0.").isEqualTo("t1c0r0");
        assertThat(table1.get(0).get("COL1").asText()).as("TABLE1 row 0 COL1.").isEqualTo("t1c1r0");
        assertThat(table1.get(1).get("COL0").asText()).as("TABLE1 row 1 COL0.").isEqualTo("t1c0r1");
        assertThat(table1.get(1).get("COL1").asText()).as("TABLE1 row 1 COL1.").isEqualTo("t1c1r1");

        final JsonNode table2 = root.get("TABLE2");
        assertThat(table2.isArray()).as("TABLE2 is array.").isTrue();
        assertThat(table2.size()).as("TABLE2 row count.").isEqualTo(1);
        assertThat(table2.get(0).get("COL0").asText()).as("TABLE2 row 0 COL0.").isEqualTo("t2c0r0");
    }

    @Test
    void testWrite_withEmptyTable_producesEmptyArrayForEmptyTable() throws Exception
    {
        final IDataSet dataSet = buildEmptyTableDataSet();
        final StringWriter stringWriter = new StringWriter();
        new JsonWriter(stringWriter).write(dataSet);

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root =
                mapper.readTree(new ByteArrayInputStream(
                        stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        final JsonNode emptyTable = root.get("EMPTY_TABLE");
        assertThat(emptyTable.isArray()).as("EMPTY_TABLE is array.").isTrue();
        assertThat(emptyTable.size()).as("EMPTY_TABLE row count.").isZero();
    }

    @Test
    void testWrite_withNullCellValue_omitsNullKeyFromJsonRow() throws Exception
    {
        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN), new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, "c0r0");
        table.setValue(0, col1, "c1r0");
        table.addRow();
        table.setValue(1, col0, "c0r1");
        table.setValue(1, col1, null);

        final StringWriter stringWriter = new StringWriter();
        new JsonWriter(stringWriter).write(new DefaultDataSet(table));

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root =
                mapper.readTree(new ByteArrayInputStream(
                        stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        final JsonNode rows = root.get("TEST_TABLE");
        assertThat(rows.get(0).has("COL1")).as("row 0 has COL1.").isTrue();
        assertThat(rows.get(1).has("COL1")).as("row 1 omits null COL1.").isFalse();
    }

    @Test
    void testWrite_withValidDataSet_writesAndReadsBackEquivalentData() throws Exception
    {
        final IDataSet expectedDataSet = buildTwoTableDataSet();
        final StringWriter stringWriter = new StringWriter();
        new JsonWriter(stringWriter).write(expectedDataSet);

        final IDataSet actualDataSet = new JsonDataSet(
                new ByteArrayInputStream(stringWriter.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(actualDataSet.getTableNames()).as("table names.")
                .containsExactly(expectedDataSet.getTableNames());
        Assertion.assertEquals(expectedDataSet, actualDataSet);
    }

    @Test
    void testWrite_withColumnNullInEveryRow_preservesColumnOnRoundTrip() throws Exception
    {
        final String col0 = "COL0";
        final String col1 = "COL1";
        final Column[] columns =
                new Column[] {new Column(col0, DataType.UNKNOWN), new Column(col1, DataType.UNKNOWN)};

        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, "c0r0");
        table.setValue(0, col1, null);
        table.addRow();
        table.setValue(1, col0, "c0r1");
        table.setValue(1, col1, null);

        final StringWriter stringWriter = new StringWriter();
        new JsonWriter(stringWriter).write(new DefaultDataSet(table));

        final IDataSet actualDataSet = new JsonDataSet(
                new ByteArrayInputStream(stringWriter.toString().getBytes(StandardCharsets.UTF_8)));
        final ITable actualTable = actualDataSet.getTable("TEST_TABLE");

        assertThat(actualTable.getTableMetaData().getColumns())
                .as("column that is null in every row still exists after round-trip.").hasSize(2);
        assertThat(actualTable.getValue(0, col1)).as("row 0 COL1.").isNull();
        assertThat(actualTable.getValue(1, col1)).as("row 1 COL1.").isNull();
    }

    @Test
    void testWrite_withValidDataSet_doesNotCloseSuppliedWriter() throws Exception
    {
        final IDataSet dataSet = buildTwoTableDataSet();
        final StringWriter stringWriter = new StringWriter();
        final TrackedCloseWriter trackedWriter = new TrackedCloseWriter(stringWriter);

        new JsonWriter(trackedWriter).write(dataSet);

        assertThat(trackedWriter.closed).as("JsonWriter must not close the caller-supplied writer.").isFalse();
    }

    private static final class TrackedCloseWriter extends FilterWriter
    {
        private boolean closed;

        TrackedCloseWriter(final Writer out)
        {
            super(out);
        }

        @Override
        public void close() throws IOException
        {
            closed = true;
        }
    }
}
