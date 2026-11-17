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

    /**
     * Returns whether the expected values of a column that this comparer
     * compares can order the rows the way the actual values do. Before it
     * compares a table, {@link org.dbunit.DefaultPrepAndExpectedTestCase}
     * sorts the expected and the actual rows by the same columns, so that
     * each expected row meets its counterpart. A column whose comparer
     * returns false is moved after every other column of the sort, so it only
     * orders the rows that tie on all of them. A comparer that compares the
     * actual value with the expected one by equality, by order or by a
     * tolerance returns true, the default: its expected values are values of
     * the column, such as the generated ids of a sequence compared with
     * "greater than or equal to", so the column keeps its place in the sort.
     * A comparer whose expected value is a pattern, a part of the value, or
     * ignored returns false.
     *
     * @return True if the expected values can order the rows; false to sort
     *         by the column after the others.
     * @since 3.6.0
     */
    default boolean isSortableByExpectedValue()
    {
        return true;
    }
}
