package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DataSetBuilder}.
 */
class DataSetBuilderTest
{
    @Test
    void testBuild_withSingleTableAndTwoRows_returnsCorrectDataSet() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("FOO")
                .columns("ID", "NAME")
                .row(1, "Alice")
                .row(2, "Bob")
                .build();

        assertThat(dataSet.getTableNames()).as("table names").containsExactly("FOO");
        final ITable table = dataSet.getTable("FOO");
        assertThat(table.getRowCount()).as("row count").isEqualTo(2);
        assertThat(table.getValue(0, "ID")).as("row 0 ID").isEqualTo(1);
        assertThat(table.getValue(0, "NAME")).as("row 0 NAME").isEqualTo("Alice");
        assertThat(table.getValue(1, "ID")).as("row 1 ID").isEqualTo(2);
        assertThat(table.getValue(1, "NAME")).as("row 1 NAME").isEqualTo("Bob");
    }

    @Test
    void testBuild_withMultipleTables_returnsDataSetWithAllTables() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("FOO")
                .columns("ID")
                .row(1)
                .table("BAR")
                .columns("NAME")
                .row("test")
                .build();

        assertThat(dataSet.getTableNames()).as("table names").containsExactly("FOO", "BAR");
        assertThat(dataSet.getTable("FOO").getRowCount()).as("FOO rows").isEqualTo(1);
        assertThat(dataSet.getTable("BAR").getRowCount()).as("BAR rows").isEqualTo(1);
        assertThat(dataSet.getTable("BAR").getValue(0, "NAME")).as("BAR.NAME").isEqualTo("test");
    }

    @Test
    void testBuild_withNoRows_returnsEmptyTableWithColumns() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("EMPTY")
                .columns("ID", "NAME")
                .build();

        final ITable table = dataSet.getTable("EMPTY");
        assertThat(table.getRowCount()).as("row count").isZero();
        assertThat(table.getTableMetaData().getColumns()).as("columns").hasSize(2);
    }

    @Test
    void testBuild_withNoColumns_returnsTableWithNoColumns() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("NO_COLS")
                .build();

        final ITable table = dataSet.getTable("NO_COLS");
        assertThat(table.getTableMetaData().getColumns()).as("columns").isEmpty();
        assertThat(table.getRowCount()).as("row count").isZero();
    }

    @Test
    void testBuild_withNullValue_storesNullInRow() throws Exception
    {
        final IDataSet dataSet = new DataSetBuilder()
                .table("T")
                .columns("A", "B")
                .row(1, null)
                .build();

        assertThat(dataSet.getTable("T").getValue(0, "B")).as("null value").isNull();
    }
}
