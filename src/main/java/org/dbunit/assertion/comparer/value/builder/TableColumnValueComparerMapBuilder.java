package org.dbunit.assertion.comparer.value.builder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.dbunit.assertion.comparer.value.ValueComparer;

/**
 * Convenience methods to help build the map of table name -> map of column name
 * -> {@link ValueComparer}.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class TableColumnValueComparerMapBuilder
{
    private Map<String, Map<String, ValueComparer>> comparers = new HashMap<>();

    /**
     * Add all mappings from the specified table map to this builder.
     *
     * @param tableColumnValueComparers the table map to add.
     * @return this for fluent syntax.
     */
    public TableColumnValueComparerMapBuilder add(
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
    {
        comparers.putAll(tableColumnValueComparers);

        return this;
    }

    /**
     * Add all mappings from the specified
     * {@link TableColumnValueComparerMapBuilder} builder to this builder.
     *
     * @param tableColumnValueComparerMapBuilder the builder whose mappings to add.
     * @return this for fluent syntax.
     */
    public TableColumnValueComparerMapBuilder add(
            final TableColumnValueComparerMapBuilder tableColumnValueComparerMapBuilder)
    {
        final Map<String, Map<String, ValueComparer>> map =
                tableColumnValueComparerMapBuilder.build();
        comparers.putAll(map);

        return this;
    }

    /**
     * Add all mappings from the specified column map to a column map for the
     * specified table in this builder.
     *
     * @param tableName the table to add the column map for.
     * @param columnValueComparers the column map to add.
     * @return this for fluent syntax.
     */
    public TableColumnValueComparerMapBuilder add(final String tableName,
            final Map<String, ValueComparer> columnValueComparers)
    {
        final Map<String, ValueComparer> map = findOrMakeColumnMap(tableName);

        map.putAll(columnValueComparers);

        return this;
    }

    /**
     * Add all mappings from the specified {@link ColumnValueComparerMapBuilder}
     * builder to a column map for the specified table in this builder.
     *
     * @param tableName the table to add the column map for.
     * @param columnValueComparerMapBuilder the builder whose column mappings to add.
     * @return this for fluent syntax.
     */
    public TableColumnValueComparerMapBuilder add(final String tableName,
            final ColumnValueComparerMapBuilder columnValueComparerMapBuilder)
    {
        final Map<String, ValueComparer> map = findOrMakeColumnMap(tableName);
        final Map<String, ValueComparer> columnMap =
                columnValueComparerMapBuilder.build();

        map.putAll(columnMap);

        return this;
    }

    /**
     * Add a table to column to {@link ValueComparer} mapping.
     *
     * @param tableName the table the mapping applies to.
     * @param columnName the column the mapping applies to.
     * @param valueComparer the value comparer to map to the given table and column.
     * @return this for fluent syntax.
     */
    public TableColumnValueComparerMapBuilder add(final String tableName,
            final String columnName, final ValueComparer valueComparer)
    {
        final Map<String, ValueComparer> map = findOrMakeColumnMap(tableName);
        map.put(columnName, valueComparer);

        return this;
    }

    /**
     * Assembles and returns the built map.
     *
     * @return The unmodifiable assembled map.
     */
    public Map<String, Map<String, ValueComparer>> build()
    {
        return Collections.unmodifiableMap(comparers);
    }

    /**
     * Returns the column map for the given table, creating and registering one if absent.
     *
     * @param tableName the table to find or create the column map for.
     * @return the column map for the given table.
     */
    protected Map<String, ValueComparer> findOrMakeColumnMap(
            final String tableName)
    {
        Map<String, ValueComparer> map = comparers.get(tableName);
        if (map == null)
        {
            map = makeColumnToValueComparerMap();
            comparers.put(tableName, map);
        }

        return map;
    }

    /**
     * Creates a new, empty column-to-{@link ValueComparer} map.
     *
     * @return a new, empty column-to-{@link ValueComparer} map.
     */
    protected Map<String, ValueComparer> makeColumnToValueComparerMap()
    {
        return new HashMap<>();
    }
}
