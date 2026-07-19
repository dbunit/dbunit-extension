package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.dbunit.dataset.filter.IRowFilter;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @since 2.3.0
 */
class RowFilterTableTest
{

    private IDataSet getDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder()
                .build(TestUtils.getFileReader("xml/rowFilterTableTest.xml"));
    }

    @Test
    void testRowFilter_HappyPath() throws Exception
    {
        final ITable testTable = getDataSet().getTable("TEST_TABLE");
        final IRowFilter rowFilter = new IRowFilter()
        {
            @Override
            public boolean accept(final IRowValueProvider rowValueProvider)
            {
                try
                {
                    final String value =
                            (String) rowValueProvider.getColumnValue("COLUMN0");
                    // filter out first row
                    if (value.equals("row 0 col 0"))
                    {
                        return false;
                    }
                    return true;
                } catch (final DataSetException e)
                {
                    throw new RuntimeException(
                            "Should not happen in this unit test", e);
                }
            }

        };
        final ITable rowFilterTable = new RowFilterTable(testTable, rowFilter);
        // The first row should be filtered
        assertThat(rowFilterTable.getRowCount()).isEqualTo(3);
        assertThat(rowFilterTable.getValue(0, "COLUMN0"))
                .isEqualTo("row 1 col 0");
        assertThat(rowFilterTable.getValue(1, "COLUMN0"))
                .isEqualTo("row 2 col 0");
        assertThat(rowFilterTable.getValue(2, "COLUMN0"))
                .isEqualTo("row 3 col 0");
    }

    @Test
    void testGetValue_withRowIndexAtOrAboveRowCount_throwsRowOutOfBoundsException()
            throws Exception
    {
        final ITable testTable = getDataSet().getTable("TEST_TABLE");
        final IRowFilter acceptNone = rowValueProvider -> false;
        final ITable rowFilterTable = new RowFilterTable(testTable, acceptNone);

        assertThat(rowFilterTable.getRowCount()).as("row count.").isZero();
        assertThatExceptionOfType(RowOutOfBoundsException.class)
                .as("accessing row 0 of an empty filtered table.")
                .isThrownBy(() -> rowFilterTable.getValue(0, "COLUMN0"));
    }

    @Test
    void testRowFilter_withNoRowsFilteredOut_returnsAllRowsInOriginalOrder()
            throws Exception
    {
        final ITable testTable = getDataSet().getTable("TEST_TABLE");
        final IRowFilter acceptAll = rowValueProvider -> true;
        final ITable rowFilterTable = new RowFilterTable(testTable, acceptAll);

        assertThat(rowFilterTable.getRowCount()).as("row count.")
                .isEqualTo(testTable.getRowCount());
        for (int row = 0; row < testTable.getRowCount(); row++)
        {
            assertThat(rowFilterTable.getValue(row, "COLUMN0"))
                    .as("value at row " + row + ".")
                    .isEqualTo(testTable.getValue(row, "COLUMN0"));
        }
    }
}
