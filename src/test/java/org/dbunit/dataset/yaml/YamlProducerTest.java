/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests of {@link YamlProducer}, driving it with a consumer that records the events it
 * receives.
 */
class YamlProducerTest
{
    private static final String ONE_TABLE_ONE_ROW = "TEST_TABLE:\n  - COLUMN0: row0\n";

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n"})
    void testProduce_documentWithoutContent_producesAnEmptyDataSet(final String document)
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.events)
                .as("A document with no content has no tables, so the dataset it produces is"
                        + " empty, and complete.")
                .isEqualTo(Arrays.asList("startDataSet", "endDataSet"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"- first\n- second\n", "just text\n"})
    void testProduce_rootIsNotAMapping_throwsDataSetExceptionNamingTheProblem(
            final String document)
    {
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A root that is not a mapping of table names to rows is not a dataset, and"
                        + " must be reported as one rather than as a ClassCastException.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("must be a mapping of table names to rows");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEST_TABLE: {COLUMN0: row0}\n", "TEST_TABLE: row0\n"})
    void testProduce_tableIsNotAList_throwsDataSetExceptionNamingTheTable(final String document)
    {
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A table that is not a list of rows must be reported, naming the table,"
                        + " rather than as a ClassCastException.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("Table 'TEST_TABLE' must be a list of rows");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEST_TABLE:\n  - row0\n", "TEST_TABLE:\n  - [row0, row1]\n"})
    void testProduce_rowIsNotAMapping_throwsDataSetExceptionNamingTheRow(final String document)
    {
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A row that is not a mapping of column names to values must be reported,"
                        + " naming the table and the row, rather than as a ClassCastException.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining(
                        "Row 0 of table 'TEST_TABLE' must be a mapping of column names to values");
    }

    @Test
    void testProduce_rowWithNoValue_producesARowOfNulls() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer =
                new YamlProducer(stream("TEST_TABLE:\n  -\n  - COLUMN0: row1\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.events)
                .as("A list entry with no value is a row of nulls, as JsonProducer reads a null"
                        + " row, not a NullPointerException.")
                .isEqualTo(Arrays.asList("startDataSet", "startTable:TEST_TABLE", "row:[null]",
                        "row:[row1]", "endTable", "endDataSet"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123:\n  - COLUMN0: row0\n", "TEST_TABLE:\n  - on: row0\n"})
    void testProduce_nameIsNotAString_throwsDataSetExceptionSayingToQuoteIt(
            final String document)
    {
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A table or column name YAML reads as a number or a boolean must be"
                        + " reported, rather than as a ClassCastException.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("must be a string")
                .hasMessageContaining("quote it");
    }

    @Test
    void testProduce_tableWithOneRow_endsTheDataSetAfterTheLastTable() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream(ONE_TABLE_ONE_ROW));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.events)
                .as("The consumer must be told the dataset ended, as a consumer that writes in"
                        + " endDataSet() would otherwise leave its output unfinished.")
                .isEqualTo(Arrays.asList("startDataSet", "startTable:TEST_TABLE", "row:[row0]",
                        "endTable", "endDataSet"));
    }

    @Test
    void testProduce_producerCreatedFromAFile_closesTheStreamItOpened() throws Exception
    {
        final File file = Files.createTempFile("yamlProducerTest", ".yml").toFile();
        try
        {
            Files.write(file.toPath(), ONE_TABLE_ONE_ROW.getBytes(StandardCharsets.UTF_8));
            final YamlProducer producer = new YamlProducer(file);
            producer.setConsumer(new RecordingConsumer());
            final InputStream openedStream = streamOf(producer);

            producer.produce();

            assertThatThrownBy(openedStream::read)
                    .as("The stream the producer opened on the file must be closed once the"
                            + " file is read, or each producer leaves a file handle open.")
                    .isInstanceOf(IOException.class);
        } finally
        {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void testProduce_streamTheProducerOwns_closesItAfterProducing() throws Exception
    {
        final CloseTrackingInputStream stream = new CloseTrackingInputStream(ONE_TABLE_ONE_ROW);
        final YamlProducer producer = new YamlProducer(stream, true);
        producer.setConsumer(new RecordingConsumer());

        producer.produce();

        assertThat(stream.closed).as("A stream the producer opened itself must be closed.")
                .isTrue();
    }

    @Test
    void testProduce_streamTheProducerOwnsAndTheRootIsNotAMapping_stillClosesIt()
            throws Exception
    {
        final CloseTrackingInputStream stream = new CloseTrackingInputStream("- first\n");
        final YamlProducer producer = new YamlProducer(stream, true);
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce).isInstanceOf(DataSetException.class);

        assertThat(stream.closed)
                .as("A stream the producer opened itself must be closed when producing fails too.")
                .isTrue();
    }

    @Test
    void testProduce_streamTheCallerGave_leavesItOpen() throws Exception
    {
        final CloseTrackingInputStream stream = new CloseTrackingInputStream(ONE_TABLE_ONE_ROW);
        final YamlProducer producer = new YamlProducer(stream);
        producer.setConsumer(new RecordingConsumer());

        producer.produce();

        assertThat(stream.closed).as("A stream the caller gave belongs to the caller.")
                .isFalse();
    }

    private static InputStream stream(final String yaml)
    {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads the stream a producer created from a file opened, which nothing else exposes: the
     * producer keeps it private, and the file cannot show it is open, as the stream is opened
     * so that it can still be deleted on Windows.
     */
    private static InputStream streamOf(final YamlProducer producer) throws Exception
    {
        final Field field = YamlProducer.class.getDeclaredField("_inputStream");
        field.setAccessible(true);
        return (InputStream) field.get(producer);
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream
    {
        private boolean closed;

        CloseTrackingInputStream(final String yaml)
        {
            super(yaml.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException
        {
            closed = true;
            super.close();
        }
    }

    private static final class RecordingConsumer implements IDataSetConsumer
    {
        private final List<String> events = new ArrayList<>();

        @Override
        public void startDataSet() throws DataSetException
        {
            events.add("startDataSet");
        }

        @Override
        public void endDataSet() throws DataSetException
        {
            events.add("endDataSet");
        }

        @Override
        public void startTable(final ITableMetaData metaData) throws DataSetException
        {
            events.add("startTable:" + metaData.getTableName());
        }

        @Override
        public void endTable() throws DataSetException
        {
            events.add("endTable");
        }

        @Override
        public void row(final Object[] values) throws DataSetException
        {
            events.add("row:" + Arrays.toString(values));
        }
    }
}
