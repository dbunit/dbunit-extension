package org.dbunit.assertion.comparer.value.builder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.dbunit.assertion.comparer.value.ValueComparer;

/**
 * Convenience methods to help build the map of column name ->
 * {@link ValueComparer}.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class ColumnValueComparerMapBuilder
{
    private Map<String, ValueComparer> comparers = new HashMap<>();

    /**
     * Add a columnName to {@link ValueComparer} mapping.
     *
     * @param columnName the column name to map.
     * @param valueComparer the value comparer to associate with the column name.
     * @return this for fluent syntax.
     */
    public ColumnValueComparerMapBuilder add(final String columnName,
            final ValueComparer valueComparer)
    {
        comparers.put(columnName, valueComparer);
        return this;
    }

    /**
     * Builds the map of column name to {@link ValueComparer}.
     *
     * @return The assembled map.
     */
    public Map<String, ValueComparer> build()
    {
        return Collections.unmodifiableMap(comparers);
    }
}
