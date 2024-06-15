package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.dbunit.Assertion;
import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
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
    void testProduceAndWriteBackToDisk() throws Exception
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
    void testEscapeQuote()
    {
        assertThat(CsvDataSetWriter.escape("\"foo\"")).isEqualTo("\\\"foo\\\"");
    }

    @Test
    void testEscapeEscape()
    {
        assertThat(CsvDataSetWriter.escape("\\foo\\")).isEqualTo("\\\\foo\\\\");
    }

}
