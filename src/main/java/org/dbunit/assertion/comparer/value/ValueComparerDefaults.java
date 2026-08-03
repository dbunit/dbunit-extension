package org.dbunit.assertion.comparer.value;

import java.util.Map;

/**
 * Default {@link ValueComparer}s, used when one is not specified by a test.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public interface ValueComparerDefaults
{
    /**
     * Returns the default value comparer, used when a table/column has none configured.
     *
     * @return the default value comparer.
     */
    ValueComparer getDefaultValueComparer();

    /**
     * Returns the default per-table, per-column value comparer map.
     *
     * @return the default per-table, per-column value comparer map.
     */
    Map<String, Map<String, ValueComparer>> getDefaultTableColumnValueComparerMap();

    /**
     * Returns the default per-column value comparer map for the given table.
     *
     * @param tableName the table name to get the default column value comparer map for.
     * @return the default per-column value comparer map for the given table.
     */
    Map<String, ValueComparer> getDefaultColumnValueComparerMapForTable(
            String tableName);
}
