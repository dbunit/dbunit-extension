package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * Strategy for comparing values.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
@FunctionalInterface
public interface ValueComparer
{
    /**
     * Compare expected and actual values.
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param actualTable
     *            Table containing all actual results.
     * @param rowNum
     *            The current row number comparing.
     * @param columnName
     *            The name of the current column comparing.
     * @param dataType
     *            The {@link DataType} for the current column comparing. Use
     *            {@link DataType#compare(Object, Object)} for equal, not equal,
     *            less than, and greater than comparisons.
     * @param expectedValue
     *            The current expected value for the column.
     * @param actualValue
     *            The current actual value for the column.
     * @return compare failure message or null if successful compare.
     * @throws DatabaseUnitException if the comparison cannot be performed.
     */
    String compare(ITable expectedTable, ITable actualTable, int rowNum,
            String columnName, DataType dataType, Object expectedValue,
            Object actualValue) throws DatabaseUnitException;
}
