package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.sql.Clob;
import java.sql.SQLException;

import org.dbunit.Assertion;
import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetBuilder;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.stream.DataSetProducerAdapter;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * Created By: fede Date: 10-mar-2004 Time: 17.21.34
 *
 * Last Checkin: $Author$ Date: $Date$ Revision: $Revision$
 */
class CsvDataSetWriterTest
{
    private static final String DEST = "target/csv/orders-out";
    private static final String SOURCE = TestUtils.getFileName("csv/orders");

    @Test
    void testProduceAndWriteBackToDisk_withCsvSource_writesAndReadBackEquivalent() throws Exception
    {
        produceToFolder(SOURCE, DEST);
        final IDataSet expected = produceToMemory(SOURCE);
        final IDataSet actual = produceToMemory(DEST);
        Assertion.assertEquals(expected, actual);
    }

    private IDataSet produceToMemory(final String source)
            throws DataSetException
    {
        final CsvProducer producer = new CsvProducer(source);
        final CachedDataSet cached = new CachedDataSet();
        producer.setConsumer(cached);
        producer.produce();
        return cached;
    }

    private void produceToFolder(final String source, final String dest)
            throws DataSetException
    {
        final CsvProducer producer = new CsvProducer(source);
        new File(dest).delete();
        final CsvDataSetWriter writer = new CsvDataSetWriter(dest);
        producer.setConsumer(writer);
        producer.produce();
    }

    @Test
    void testWrite_multiRowTable_writesCompleteFile() throws Exception
    {
        final String dest = "target/csv/multi-row-out";
        final int rowCount = 2000;
        final DataSetBuilder.TableBuilder builder = new DataSetBuilder().table("MULTI_ROW")
                .columns("ID", "NAME");
        for (int i = 0; i < rowCount; i++)
        {
            builder.row(i, "name-" + i);
        }
        final IDataSet expected = builder.build();

        new File(dest).delete();
        CsvDataSetWriter.write(expected, new File(dest));
        final IDataSet actual = produceToMemory(dest);

        assertThat(actual.getTable("MULTI_ROW").getRowCount())
                .as("All rows should be present after the buffered writer is closed.")
                .isEqualTo(rowCount);
        Assertion.assertEquals(expected, actual);
    }

    @Test
    void testWrite_laterRowFailsTypeCast_earlierRowsAlreadyFlushedToDisk()
            throws Exception
    {
        final String dest = "target/csv/flush-on-error-out";
        final Clob unreadableClob = mock(Clob.class);
        when(unreadableClob.length())
                .thenThrow(new SQLException("cannot read CLOB length"));

        final IDataSet dataSet = new DataSetBuilder().table("FLUSH_ON_ERROR")
                .columns("ID", "VALUE")
                .row(1, "row one, written before the failure")
                .row(2, unreadableClob)
                .build();

        new File(dest).mkdirs();
        final File tableFile = new File(dest, "FLUSH_ON_ERROR.csv");
        tableFile.delete();

        final CsvDataSetWriter writer = new CsvDataSetWriter(dest);
        final DataSetProducerAdapter producer = new DataSetProducerAdapter(dataSet);
        producer.setConsumer(writer);

        assertThrows(DataSetException.class, producer::produce,
                "Row 2's unreadable CLOB should fail the write.");

        final String writtenContent =
                new String(Files.readAllBytes(tableFile.toPath()));
        assertThat(writtenContent)
                .as("Row 1, written to the buffered writer before row 2 failed, "
                        + "must have been flushed to disk despite the overall "
                        + "write failing without ever reaching endTable().")
                .contains("row one, written before the failure");
    }

    @Test
    void testEscapeQuote_withDoubleQuotedString_returnsBackslashEscapedQuotes()
    {
        assertThat(CsvDataSetWriter.escape("\"foo\"")).isEqualTo("\\\"foo\\\"");
    }

    @Test
    void testEscapeEscape_withBackslashString_returnsDoubleBackslash()
    {
        assertThat(CsvDataSetWriter.escape("\\foo\\")).isEqualTo("\\\\foo\\\\");
    }

}
