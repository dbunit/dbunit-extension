package org.dbunit.assertion.comparer.value;

import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * Strategy for selecting a {@link ValueComparer} from a {@link Map} of them.
 *
 * @author Jeff Jensen
 *
 * @since 2.6.0
 */
@FunctionalInterface
public interface ValueComparerSelector
{
    /**
     * Selects a {@link ValueComparer} from the given map for the given row and column.
     *
     * @param expectedTable Table containing all expected results.
     * @param actualTable Table containing all actual results.
     * @param rowNum The current row number comparing.
     * @param columnName The name of the current column comparing.
     * @param dataType The {@link DataType} for the current column comparing.
     * @param expectedValue The current expected value for the column.
     * @param actualValue The current actual value for the column.
     * @param valueComparers The map of value comparers to select from.
     * @return The selected {@link ValueComparer} from the specified
     *         valueComparers map.
     * @throws DatabaseUnitException if a value comparer cannot be selected.
     */
    ValueComparer select(ITable expectedTable, ITable actualTable, int rowNum,
            String columnName, DataType dataType, Object expectedValue,
            Object actualValue, Map<Object, ValueComparer> valueComparers)
            throws DatabaseUnitException;
}
