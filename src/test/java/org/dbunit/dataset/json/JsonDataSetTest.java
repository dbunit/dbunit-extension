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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link JsonDataSet}.
 *
 * @author Jeff Jensen
 */
class JsonDataSetTest extends AbstractDataSetTest
{
    @Override
    protected IDataSet createDataSet() throws Exception
    {
        try (InputStream in = Files.newInputStream(TestUtils.getFile("json/dataSetTest.json").toPath()))
        {
            return new JsonDataSet(in);
        }
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        try (InputStream in = Files.newInputStream(
                TestUtils.getFile("json/jsonDataSetDuplicateTest.json").toPath()))
        {
            return new JsonDataSet(in);
        }
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException("JSON is always case-sensitive");
    }

    @Override
    @Disabled("Not applicable, JSON is always case-sensitive")
    @Test
    public void testCreateMultipleCaseDuplicateDataSet_withDuplicateCaseVariantNames_throwsAmbiguousTableNameException()
            throws Exception
    {
        // Not applicable, JSON is always case-sensitive
    }

    @Override
    @Disabled("Not applicable, JSON is always case-sensitive")
    @Test
    public void testGetCaseInsensitiveTable_withLowercaseTableName_returnsTable() throws Exception
    {
        // Not applicable, JSON is always case-sensitive
    }

    @Override
    @Disabled("Not applicable, JSON is always case-sensitive")
    @Test
    public void testGetCaseInsensitiveTableMetaData_withLowercaseTableName_returnsMetaData()
            throws Exception
    {
        // Not applicable, JSON is always case-sensitive
    }

    @Test
    void testWrite_withValidDataSet_writesAndReadsBackEquivalentData() throws Exception
    {
        final IDataSet expectedDataSet = createDataSet();
        final File tempFile = Files.createTempFile("dataSetTest", ".json").toFile();
        try
        {
            final OutputStream out = Files.newOutputStream(tempFile.toPath());
            try
            {
                JsonDataSet.write(expectedDataSet, out);

                final IDataSet actualDataSet;
                try (InputStream in = Files.newInputStream(tempFile.toPath()))
                {
                    actualDataSet = new JsonDataSet(in);
                }

                assertThat(actualDataSet.getTableNames()).as("table names.")
                        .containsExactly(expectedDataSet.getTableNames());
                Assertion.assertEquals(expectedDataSet, actualDataSet);
            }
            finally
            {
                out.close();
            }
        }
        finally
        {
            tempFile.delete();
        }
    }

    @Test
    void testWrite_withNonAsciiValue_roundTripsThroughUtf8OutputStream() throws Exception
    {
        final String col0 = "COL0";
        final String value = "café 日本語";
        final Column[] columns = new Column[] {new Column(col0, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable("TEST_TABLE", columns);
        table.addRow();
        table.setValue(0, col0, value);
        final IDataSet expectedDataSet = new DefaultDataSet(table);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonDataSet.write(expectedDataSet, out);

        final IDataSet actualDataSet = new JsonDataSet(new ByteArrayInputStream(out.toByteArray()));

        assertThat(actualDataSet.getTable("TEST_TABLE").getValue(0, col0))
                .as("non-ASCII value round-trips through the UTF-8-encoded output stream.")
                .isEqualTo(value);
    }

    @Test
    void testConstructor_numberWithAFraction_keepsEveryDigit() throws Exception
    {
        final InputStream in = new ByteArrayInputStream(
                "{\"EVENT\": [{\"AMOUNT\": 12345678901234567890.123, \"QUANTITY\": 7}]}"
                        .getBytes(StandardCharsets.UTF_8));

        final IDataSet dataSet = new JsonDataSet(in);

        final Object[] row = {dataSet.getTable("EVENT").getValue(0, "AMOUNT"),
                dataSet.getTable("EVENT").getValue(0, "QUANTITY")};
        assertThat(row)
                .as("A number with a fraction is read as an exact decimal, not rounded to a"
                        + " double; an integer stays an integer.")
                .containsExactly(new BigDecimal("12345678901234567890.123"), 7);
    }

    @Test
    void testConstructor_withNonObjectJsonRoot_throwsDataSetException()
    {
        final InputStream in = new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("a JSON array at the root must be rejected, since the dataset root must be an object.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("JSON dataset must be a JSON object at the root level");
    }

    @Test
    void testConstructor_withNonArrayTableValue_throwsDataSetException()
    {
        final InputStream in =
                new ByteArrayInputStream("{\"TEST_TABLE\": {}}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("a table value that is not a JSON array must be rejected.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Table 'TEST_TABLE' must be a JSON array, but was START_OBJECT");
    }

    @Test
    void testConstructor_withMalformedJson_throwsDataSetException()
    {
        final InputStream in = new ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("malformed JSON input must be wrapped as a DataSetException.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Error reading JSON dataset");
    }

    @Test
    void testConstructor_withContentAfterRootObject_throwsDataSetException()
    {
        final InputStream in = new ByteArrayInputStream("{}{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("trailing content after the root JSON object must be rejected.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("JSON dataset must not contain content after the root object");
    }

    @Test
    void testConstructor_withEmptyObject_returnsDataSetWithNoTables() throws Exception
    {
        final InputStream in = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));

        final IDataSet dataSet = new JsonDataSet(in);

        assertThat(dataSet.getTableNames())
                .as("an empty JSON object is the empty dataset, which has no tables.").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \n\t "})
    void testConstructor_withoutContent_throwsDataSetException(final String json)
    {
        final InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("a document with no content must be rejected, so that a truncated file is"
                        + " not read as an empty dataset.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("JSON dataset must be a JSON object at the root level");
    }

    @ParameterizedTest
    @ValueSource(strings = {"// comment\n{}", "/* comment */ {}", "# comment\n{}",
            "{\"TEST_TABLE\": [] // comment\n}"})
    void testConstructor_withComment_throwsDataSetException(final String json)
    {
        final InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("a comment must be rejected, even beside valid content, as JSON has none.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Error reading JSON dataset")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void testConstructor_withNonObjectRowValue_throwsDataSetException()
    {
        final InputStream in =
                new ByteArrayInputStream("{\"TEST_TABLE\": [42]}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JsonDataSet(in))
                .as("a row value that is not a JSON object or null must be rejected.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Row 0 of table 'TEST_TABLE' must be a JSON object or null, but was VALUE_NUMBER_INT");
    }

    @Test
    void testConstructor_withInputStream_doesNotCloseSuppliedStream() throws Exception
    {
        final InputStream in = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        final TrackedCloseInputStream trackedIn = new TrackedCloseInputStream(in);

        new JsonDataSet(trackedIn);

        assertThat(trackedIn.closed)
                .as("JsonDataSet(InputStream) must not close the caller-supplied stream.").isFalse();
    }

    private static final class TrackedCloseInputStream extends FilterInputStream
    {
        private boolean closed;

        TrackedCloseInputStream(final InputStream in)
        {
            super(in);
        }

        @Override
        public void close() throws IOException
        {
            closed = true;
        }
    }
}
