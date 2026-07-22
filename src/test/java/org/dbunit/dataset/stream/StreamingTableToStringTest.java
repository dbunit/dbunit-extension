package org.dbunit.dataset.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;

/**
 * Asserts the toString() representation of a real StreamingTable.
 * StreamingTable is a private inner class of StreamingIterator, so this lives
 * in the same package to obtain an instance via StreamingTableTest's existing
 * fixture.
 *
 * @see org.dbunit.dataset.ToStringViewTest
 */
class StreamingTableToStringTest
{
    @Test
    void testStreamingTable_withStreamingTable_hasExpectedToString() throws Exception
    {
        final StreamingTableTest test = new StreamingTableTest();
        final ITable table = test.createTable();

        final String expected =
                "org.dbunit.dataset.stream.StreamingIterator$StreamingTable["
                        + "_metaData=tableName=TEST_TABLE, columns=["
                        + "(COLUMN0, UNKNOWN, nullableUnknown), "
                        + "(COLUMN1, UNKNOWN, nullableUnknown), "
                        + "(COLUMN2, UNKNOWN, nullableUnknown), "
                        + "(COLUMN3, UNKNOWN, nullableUnknown)], keys=[], "
                        + "_eot=false, _lastRow=-1, _rowValues=null]";

        assertThat(table.toString())
                .as("StreamingTable.toString() should have the expected, stable representation.")
                .isEqualTo(expected);
    }
}
