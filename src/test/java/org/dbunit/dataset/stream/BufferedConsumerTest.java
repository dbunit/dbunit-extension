package org.dbunit.dataset.stream;

import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.MockTableMetaData;
import org.junit.jupiter.api.Test;

class BufferedConsumerTest
{

    @Test
    void testBufferedConsumer() throws Exception
    {
        final MockDataSetConsumer wrappedConsumer = new MockDataSetConsumer();

        final ITableMetaData table1MetaData = new MockTableMetaData(
                "TESTTABLE1", new String[] {"COLUMN1", "COLUMN2", "COLUMN3"});
        final ITableMetaData table2MetaData = new MockTableMetaData(
                "TESTTABLE2", new String[] {"COLUMN1", "COLUMN2", "COLUMN3"});
        final IDataSetConsumer dataSetConsumer =
                new BufferedConsumer(wrappedConsumer);

        final Object[] testRow = new Object[] {"v1", "v2", "v3"};

        // Expected result
        wrappedConsumer.addExpectedStartDataSet();

        wrappedConsumer.addExpectedStartTable(table1MetaData);

        wrappedConsumer.addExpectedRow(table1MetaData.getTableName(), testRow);
        wrappedConsumer.addExpectedRow(table1MetaData.getTableName(), testRow);

        wrappedConsumer.addExpectedEndTable(table1MetaData.getTableName());

        wrappedConsumer.addExpectedStartTable(table2MetaData);

        wrappedConsumer.addExpectedRow(table2MetaData.getTableName(), testRow);

        wrappedConsumer.addExpectedEndTable(table2MetaData.getTableName());

        wrappedConsumer.addExpectedEndDataSet();

        // Actual data
        dataSetConsumer.startDataSet();

        dataSetConsumer.startTable(table1MetaData);

        dataSetConsumer.row(testRow);
        dataSetConsumer.row(testRow);

        dataSetConsumer.endTable();

        dataSetConsumer.startTable(table2MetaData);

        dataSetConsumer.row(testRow);

        dataSetConsumer.endTable();

        dataSetConsumer.endDataSet();

        // Verification
        wrappedConsumer.verify();
    }
}
