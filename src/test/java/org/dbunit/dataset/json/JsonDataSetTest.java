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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
        try (InputStream in = new FileInputStream(TestUtils.getFile("json/dataSetTest.json")))
        {
            return new JsonDataSet(in);
        }
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        try (InputStream in =
                new FileInputStream(TestUtils.getFile("json/jsonDataSetDuplicateTest.json")))
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
        final File tempFile = File.createTempFile("dataSetTest", ".json");
        try
        {
            final OutputStream out = new FileOutputStream(tempFile);
            try
            {
                JsonDataSet.write(expectedDataSet, out);

                final IDataSet actualDataSet;
                try (InputStream in = new FileInputStream(tempFile))
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
