package org.dbunit.assertion.comparer.value;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;

/**
 * Create instances of a single or multi-column object, typically from the row
 * in the table. This includes compound column primary keys.
 *
 * @author Jeff Jensen
 *
 * @param <T>
 *            The resulting type, possibly the primary key type.
 * @since 2.6.0
 */
@FunctionalInterface
public interface ValueFactory<T>
{
    /**
     * Make the instance from the row in the table.
     *
     * @param table
     *            The table containing the data.
     * @param rowNum
     *            The row number to make the value for.
     * @return The type.
     * @throws DataSetException if the value cannot be made from the row.
     */
    T make(ITable table, int rowNum) throws DataSetException;
}
