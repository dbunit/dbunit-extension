package org.dbunit.dataset;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.dataset.datatype.DataType;

/**
 * Fluent builder for creating {@link IDataSet} instances programmatically,
 * reducing XML file dependencies in unit tests.
 *
 * <p>Single-table example:
 * <pre>
 * IDataSet ds = new DataSetBuilder()
 *     .table("FOO")
 *         .columns("ID", "NAME")
 *         .row(1, "Alice")
 *         .row(2, "Bob")
 *     .build();
 * </pre>
 *
 * <p>Multi-table example:
 * <pre>
 * IDataSet ds = new DataSetBuilder()
 *     .table("FOO")
 *         .columns("ID", "NAME")
 *         .row(1, "Alice")
 *     .table("BAR")
 *         .columns("X")
 *         .row(42)
 *     .build();
 * </pre>
 *
 * @since 3.2.0
 */
public class DataSetBuilder
{
    private final List<TableConfig> tableConfigs = new ArrayList<>();

    /**
     * Starts configuring a new table with the given name.
     *
     * @param name the table name
     * @return a {@link TableBuilder} for configuring columns and rows
     */
    public TableBuilder table(final String name)
    {
        final TableConfig config = new TableConfig(name);
        tableConfigs.add(config);
        return new TableBuilder(config);
    }

    /**
     * Builds and returns an {@link IDataSet} containing all configured tables.
     *
     * @return the dataset
     * @throws DataSetException if any table configuration is invalid
     */
    public IDataSet build() throws DataSetException
    {
        final List<DefaultTable> tables = new ArrayList<>();
        for (final TableConfig config : tableConfigs)
        {
            tables.add(config.buildTable());
        }
        return new DefaultDataSet(tables.toArray(new DefaultTable[0]));
    }

    /**
     * Fluent builder for a single table within a {@link DataSetBuilder}.
     */
    public class TableBuilder
    {
        private final TableConfig config;

        private TableBuilder(final TableConfig config)
        {
            this.config = config;
        }

        /**
         * Sets the column names for this table. Column data types are set to
         * {@link DataType#UNKNOWN} and resolved at runtime by DbUnit.
         *
         * @param names the column names in order
         * @return this builder
         */
        public TableBuilder columns(final String... names)
        {
            config.setColumnNames(names);
            return this;
        }

        /**
         * Adds a row of values to this table. Values must be in the same order
         * as the columns declared via {@link #columns(String...)}.
         *
         * @param values the row values
         * @return this builder
         */
        public TableBuilder row(final Object... values)
        {
            config.addRow(values);
            return this;
        }

        /**
         * Starts configuring another table, finalizing this one.
         *
         * @param name the next table name
         * @return a new {@link TableBuilder} for the next table
         */
        public TableBuilder table(final String name)
        {
            return DataSetBuilder.this.table(name);
        }

        /**
         * Builds and returns an {@link IDataSet} containing all configured tables.
         *
         * @return the dataset
         * @throws DataSetException if any table configuration is invalid
         */
        public IDataSet build() throws DataSetException
        {
            return DataSetBuilder.this.build();
        }
    }

    private static class TableConfig
    {
        private final String name;
        private String[] columnNames = new String[0];
        private final List<Object[]> rows = new ArrayList<>();

        TableConfig(final String name)
        {
            this.name = name;
        }

        void setColumnNames(final String[] names)
        {
            columnNames = names;
        }

        void addRow(final Object[] values)
        {
            rows.add(values);
        }

        DefaultTable buildTable() throws DataSetException
        {
            final Column[] columns = new Column[columnNames.length];
            for (int i = 0; i < columnNames.length; i++)
            {
                columns[i] = new Column(columnNames[i], DataType.UNKNOWN);
            }
            final DefaultTable table = new DefaultTable(name, columns);
            for (final Object[] row : rows)
            {
                table.addRow(row);
            }
            return table;
        }
    }
}
