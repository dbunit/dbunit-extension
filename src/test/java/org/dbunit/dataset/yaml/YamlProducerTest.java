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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.error.YAMLException;

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

    @ParameterizedTest
    @ValueSource(strings = {"[NULL]", "[now]", "[now-1d]", "[1, 2]", "[]", "{a: 1}", "{}"})
    void testProduce_unquotedListOrMappingCell_throwsDataSetExceptionSayingToQuoteIt(
            final String cell)
    {
        final YamlProducer producer =
                new YamlProducer(stream("TEST_TABLE:\n  - ID: 1\n  - NOTE: " + cell + "\n"));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A cell that YAML reads as a list or a mapping, such as an unquoted [NULL] or"
                        + " [now-1d], must be reported with its table, row and column and with"
                        + " the advice to quote it, not handed on to a column type that cannot"
                        + " use it.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("Column 'NOTE' of row 1 of table 'TEST_TABLE'")
                .hasMessageContaining("Quote the value");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "[NULL]|a list, [null]",
            "{a: 1}|a mapping, {a=1}"})
    void testProduce_unquotedPlaceholderOrMapping_throwsDataSetExceptionShowingWhatYamlBuilt(
            final String cell, final String built)
    {
        final YamlProducer producer =
                new YamlProducer(stream("TEST_TABLE:\n  - NOTE: " + cell + "\n"));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("The message names the cell, shows the list or the mapping that YAML built,"
                        + " says why, and gives the quoted spelling.")
                .isInstanceOf(DataSetException.class)
                .hasMessage("Column 'NOTE' of row 0 of table 'TEST_TABLE' must be a value, but"
                        + " was " + built + ": YAML reads an unquoted value that starts with"
                        + " '[' as a list and one that starts with '{' as a mapping. Quote the"
                        + " value, such as '[NULL]' or '[now-1d]', to give it as text.");
    }

    @Test
    void testProduce_quotedBracketAndBraceValues_produceTheTextOfTheDocument() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("TEST_TABLE:\n"
                + "  - NULL_PLACEHOLDER: '[NULL]'\n"
                + "    RELATIVE_DATE: '[now-1d]'\n"
                + "    BINARY: '[TEXT]hello'\n"
                + "    MAPPING_LIKE: '{a: 1}'\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("A quoted value that starts with a square bracket or a curly brace reaches"
                        + " the column type as the text of the document, for the column type or"
                        + " a ReplacementDataSet to read.")
                .containsExactly("[NULL]", "[now-1d]", "[TEXT]hello", "{a: 1}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEST_TABLE:\n  - COLUMN0: [row0\n",
            "TEST_TABLE:\n  - COLUMN0: [TEXT]hello\n",
            "TEST_TABLE:\n  - COLUMN0: [BASE64]aGVsbG8=\n",
            "TEST_TABLE:\n  - COLUMN0: row0\n---\nSECOND_TABLE:\n  - COLUMN0: row1\n"})
    void testProduce_malformedDocument_throwsDataSetExceptionNamingTheProblem(
            final String document)
    {
        final YamlProducer producer = new YamlProducer(stream(document));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A syntax error, or a second document in the stream, must be reported as a"
                        + " DataSetException, as JsonProducer reports a malformed JSON file,"
                        + " rather than as the unchecked exception SnakeYAML throws.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("Error reading YAML dataset")
                .hasCauseInstanceOf(YAMLException.class);
    }

    @Test
    void testProduce_columnNamedTwiceInARow_throwsDataSetExceptionThatIsNoAmbiguousTableName()
    {
        final YamlProducer producer =
                new YamlProducer(stream("TEST_TABLE:\n  - COLUMN0: row0\n    COLUMN0: row1\n"));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A column named twice in a row is a duplicate column, not a duplicate table,"
                        + " so it must be reported as a DataSetException that names the column,"
                        + " not as an ambiguous table name.")
                .isInstanceOf(DataSetException.class)
                .isNotInstanceOf(AmbiguousTableNameException.class)
                .hasMessageContaining("COLUMN0");
    }

    @Test
    void testProduce_tableNamedTwice_throwsAmbiguousTableNameException()
    {
        final YamlProducer producer = new YamlProducer(stream(
                "TEST_TABLE:\n  - COLUMN0: row0\nTEST_TABLE:\n  - COLUMN0: row1\n"));
        producer.setConsumer(new RecordingConsumer());

        assertThatThrownBy(producer::produce)
                .as("A table named twice at the root of the document is an ambiguous table name.")
                .isInstanceOf(AmbiguousTableNameException.class)
                .hasMessageContaining("TEST_TABLE");
    }

    @Test
    void testProduce_documentPastSnakeYamlsDefaultCodePointLimit_readsEveryRow() throws Exception
    {
        final int rowCount = 200_000;
        final StringBuilder document = new StringBuilder("TEST_TABLE:\n");
        for (int i = 0; i < rowCount; i++)
        {
            document.append("  - COLUMN0: row").append(i).append('\n');
        }
        final int snakeYamlDefaultCodePointLimit = 3 * 1024 * 1024;
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream(document.toString()));
        producer.setConsumer(consumer);

        producer.produce();

        final long producedRows =
                consumer.events.stream().filter(event -> event.startsWith("row:")).count();
        assertThat(document.length())
                .as("The document must be longer than the limit SnakeYAML sets by default.")
                .isGreaterThan(snakeYamlDefaultCodePointLimit);
        assertThat(producedRows)
                .as("A dataset is a fixture of the test's own project, so a document of any size"
                        + " is read, as JsonProducer reads one.")
                .isEqualTo(rowCount);
    }

    @Test
    void testProduce_unquotedDateAndFraction_producesTheTextOfTheDocument() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("EVENT:\n"
                + "  - EVENT_DAY: 2024-03-01\n"
                + "    AMOUNT: 12345678901234567890.123\n"
                + "    QUANTITY: 7\n"
                + "    ACTIVE: true\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("An unquoted date and an unquoted number with a fraction reach the column"
                        + " type as the text of the document, so it parses the date in the JVM's"
                        + " time zone and keeps every digit; integers and booleans keep their"
                        + " types.")
                .containsExactly("2024-03-01", "12345678901234567890.123", 7, true);
    }

    @Test
    void testProduce_explicitTimestampTag_producesTheTaggedType() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer =
                new YamlProducer(stream("EVENT:\n  - EVENT_DAY: !!timestamp 2024-03-01\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0)[0])
                .as("A value with an explicit tag keeps the type the tag names.")
                .isInstanceOf(java.util.Date.class);
    }

    @Test
    void testProduce_unquotedDateAndTimeAsTheColumnTypeParsesIt_producesTheTextOfTheDocument()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("EVENT:\n"
                + "  - STARTED: 2024-03-01 10:15:30\n"
                + "    ENDED: 2024-03-01 10:15:30.5\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("A date and time with one space between them and no time zone, as the"
                        + " column type parses text, reaches it as the text of the document, so"
                        + " it parses the value in the JVM's time zone.")
                .containsExactly("2024-03-01 10:15:30", "2024-03-01 10:15:30.5");
    }

    @Test
    void testProduce_unquotedTimestampTheColumnTypeDoesNotParse_producesTheInstantSnakeYamlReads()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("EVENT:\n"
                + "  - IN_UTC: 2024-03-01T10:15:30Z\n"
                + "    WITH_OFFSET: 2024-03-01t10:15:30.1-05:00\n"
                + "    WITH_SPACED_OFFSET: 2024-03-01 10:15:30.10 -5\n"
                + "    WITHOUT_ZONE: 2024-03-01T10:15:30\n"));
        producer.setConsumer(consumer);

        producer.produce();

        final Object[] expected = {Date.from(Instant.parse("2024-03-01T10:15:30Z")),
                Date.from(Instant.parse("2024-03-01T15:15:30.100Z")),
                Date.from(Instant.parse("2024-03-01T15:15:30.100Z")),
                Date.from(Instant.parse("2024-03-01T10:15:30Z"))};
        assertThat(consumer.rows.get(0))
                .as("A timestamp with a T or a time zone, which the column type does not parse"
                        + " as text, reaches it as the instant SnakeYAML reads, as before 3.6.0;"
                        + " SnakeYAML takes one without a time zone as UTC.")
                .containsExactly(expected);
    }

    @Test
    void testProduce_unquotedDecimalNumbers_produceTheTextOfTheDocument() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("MEASURE:\n"
                + "  - SIGNED: +1.5\n"
                + "    LEADING_POINT: .5\n"
                + "    TRAILING_POINT: 1.\n"
                + "    EXPONENT: 1.5e3\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("A decimal number, as the column type parses text, reaches it as the text"
                        + " of the document.")
                .containsExactly("+1.5", ".5", "1.", "1.5e3");
    }

    @Test
    void testProduce_unquotedYamlNumberTheColumnTypeDoesNotParse_producesTheDoubleSnakeYamlReads()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("MEASURE:\n"
                + "  - GROUPED: 1_000.5\n"
                + "    INFINITE: .inf\n"
                + "    NEGATIVE_INFINITE: -.Inf\n"
                + "    NOT_A_NUMBER: .nan\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("A YAML number with digit groups or not finite, which the column type does"
                        + " not parse as text, reaches it as the double SnakeYAML reads, as"
                        + " before 3.6.0.")
                .containsExactly(1000.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.NaN);
    }

    @Test
    void testProduce_unquotedTimeFromTenOClock_producesTheTextOfTheDocument() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("SHIFT:\n"
                + "  - OPENS: 09:30:00\n"
                + "    CLOSES: 10:30:00\n"
                + "    LAST_ORDER: 23:59:59\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("A time reaches the column type as the text of the document, one from"
                        + " 10:00:00 on included, which YAML 1.1 would read as a base-60"
                        + " integer such as 37800.")
                .containsExactly("09:30:00", "10:30:00", "23:59:59");
        assertThat(DataType.TIME.typeCast(consumer.rows.get(0)[1]))
                .as("The TIME data type parses the text.")
                .isEqualTo(java.sql.Time.valueOf("10:30:00"));
    }

    @Test
    void testProduce_unquotedIntegerWithLeadingZeros_producesTheTextOfTheDocument()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("ADDRESS:\n"
                + "  - ZIP: 01234\n"
                + "    FLOOR: 007\n"
                + "    BALANCE: -007\n"
                + "    COUNT: 0\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("An integer with leading zeros reaches the column type as the text of the"
                        + " document, as from every other format, instead of the octal number"
                        + " YAML 1.1 reads, such as 668 for 01234; a plain zero is still the"
                        + " integer.")
                .containsExactly("01234", "007", "-007", 0);
        assertThat(DataType.INTEGER.typeCast(consumer.rows.get(0)[0]))
                .as("The integer data types parse the text as a decimal number.")
                .isEqualTo(1234);
    }

    @Test
    void testProduce_unquotedIntegerNoColumnTypeParsesAsText_producesTheIntegerSnakeYamlReads()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("NUMBERS:\n"
                + "  - HEXADECIMAL: 0x1F\n"
                + "    BINARY: 0b101\n"
                + "    GROUPED: 1_000\n"
                + "    SIGNED_HEXADECIMAL: +0x1F\n"
                + "    SIGNED_GROUPED: +1_000\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("An integer that no column type parses as text reaches it as the integer"
                        + " SnakeYAML reads, as before, with a leading plus or without: 0x1F is"
                        + " 31, 0b101 is 5 and 1_000 is 1000.")
                .containsExactly(31, 5, 1000, 31, 1000);
    }

    @Test
    void testProduce_unquotedBase60Number_producesTheTextOfTheDocument() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("MEASURE:\n"
                + "  - RATIO: 16:9\n"
                + "    CLOCK_TIME: 12:30\n"
                + "    MINUTES_AND_SECONDS: 1:30\n"
                + "    NINE_THIRTY: 9:30\n"
                + "    SCORE: 3:16\n"
                + "    WITH_FRACTION: 1:30.5\n"
                + "    NEGATIVE: -1:30\n"
                + "    SIGNED: +12:30\n"));
        producer.setConsumer(consumer);

        producer.produce();

        final Object[] row = consumer.rows.get(0);
        assertThat(row)
                .as("A base-60 number reaches the column type as the text of the document, as"
                        + " from every other format, instead of the sum of its parts that YAML"
                        + " 1.1 reads, such as 969 for 16:9 or 750 for 12:30.")
                .containsExactly("16:9", "12:30", "1:30", "9:30", "3:16", "1:30.5", "-1:30",
                        "+12:30");
        assertThatThrownBy(() -> DataType.TIME.typeCast(row[1]))
                .as("A column type that wants no base-60 number names the text that the dataset"
                        + " holds, not a number the user never wrote.")
                .isInstanceOf(TypeCastException.class).hasMessageContaining("<12:30>");
    }

    @Test
    void testProduce_unquotedIntegerWithLeadingPlus_producesTheTextOfTheDocument()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("PHONE:\n"
                + "  - NUMBER: +4930123456\n"
                + "    COUNTRY_CODE: +49\n"
                + "    OFFSET: +0\n"
                + "    DELTA: -49\n"));
        producer.setConsumer(consumer);

        producer.produce();

        final Object[] row = consumer.rows.get(0);
        assertThat(row)
                .as("An integer with a leading plus reaches the column type as the text of the"
                        + " document, which keeps the plus a text column should get; a negative"
                        + " integer is still the integer.")
                .containsExactly("+4930123456", "+49", "+0", -49);
        assertThat(DataType.BIGINT.typeCast(row[0]))
                .as("The integer data types parse the text with its plus.")
                .isEqualTo(BigInteger.valueOf(4930123456L));
        assertThat(DataType.INTEGER.typeCast(row[1]))
                .as("The integer data types parse the text with its plus.")
                .isEqualTo(49);
    }

    @Test
    void testProduce_unquotedYesNoOnOff_producesTheBooleansYaml11Reads() throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("FLAGS:\n"
                + "  - AFFIRMED: yes\n"
                + "    DENIED: NO\n"
                + "    SWITCHED_ON: On\n"
                + "    SWITCHED_OFF: off\n"
                + "    MIXED_CASE: yEs\n"));
        producer.setConsumer(consumer);

        producer.produce();

        assertThat(consumer.rows.get(0))
                .as("YAML 1.1 reads yes, no, on and off, in lower case, capitalized or in upper"
                        + " case, as booleans, so a text column needs them quoted; yEs is text.")
                .containsExactly(true, false, true, false, "yEs");
    }

    @Test
    void testProduce_unquotedTrueAndFalse_produceBooleansThatAnIntegerColumnTypeCasts()
            throws Exception
    {
        final RecordingConsumer consumer = new RecordingConsumer();
        final YamlProducer producer = new YamlProducer(stream("FLAGS:\n"
                + "  - ACTIVE: true\n"
                + "    DELETED: false\n"));
        producer.setConsumer(consumer);

        producer.produce();

        final Object[] row = consumer.rows.get(0);
        assertThat(row)
                .as("An unquoted YAML true or false is produced as a Boolean.")
                .containsExactly(true, false);
        assertThat(new Object[] {DataType.INTEGER.typeCast(row[0]),
                DataType.INTEGER.typeCast(row[1])})
                        .as("An integer column takes such a Boolean as 1 or 0, as it takes"
                                + " the text true or false.")
                        .containsExactly(1, 0);
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

        private final List<Object[]> rows = new ArrayList<>();

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
            rows.add(values);
        }
    }
}
