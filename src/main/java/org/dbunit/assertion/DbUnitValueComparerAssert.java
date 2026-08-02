package org.dbunit.assertion;

import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparerDefaults;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;

/**
 * DbUnit assertions using {@link ValueComparer}s for the column comparisons.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class DbUnitValueComparerAssert extends DbUnitAssertBase
{
    /**
     * Asserts the two specified {@link IDataSet}s comparing their columns using
     * the default {@link ValueComparer} and handles failures using the default
     * {@link FailureHandler}. This method ignores the table names, the columns
     * order, the columns data type, and which columns are composing the primary
     * keys.
     *
     * @param expectedDataSet
     *            {@link IDataSet} containing all expected results.
     * @param actualDataSet
     *            {@link IDataSet} containing all actual results.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet) throws DatabaseUnitException
    {
        final ValueComparer defaultValueComparer =
                valueComparerDefaults.getDefaultValueComparer();
        assertWithValueComparer(expectedDataSet, actualDataSet,
                defaultValueComparer);
    }

    /**
     * Asserts the two specified {@link IDataSet}s comparing their columns using
     * the specified defaultValueComparer and handles failures using the default
     * {@link FailureHandler}. This method ignores the table names, the columns
     * order, the columns data type, and which columns are composing the primary
     * keys.
     *
     * @param expectedDataSet
     *            {@link IDataSet} containing all expected results.
     * @param actualDataSet
     *            {@link IDataSet} containing all actual results.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with all column value
     *            comparisons. Can be <code>null</code> and will default to
     *            {@link ValueComparerDefaults#getDefaultValueComparer()}.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet,
            final ValueComparer defaultValueComparer)
            throws DatabaseUnitException
    {
        final Map<String, Map<String, ValueComparer>> tableColumnValueComparers =
                valueComparerDefaults.getDefaultTableColumnValueComparerMap();
        assertWithValueComparer(expectedDataSet, actualDataSet,
                defaultValueComparer, tableColumnValueComparers);
    }

    /**
     * Asserts the two specified {@link IDataSet}s comparing their columns using
     * the specified columnValueComparers or defaultValueComparer and handles
     * failures using the default {@link FailureHandler}. This method ignores
     * the table names, the columns order, the columns data type, and which
     * columns are composing the primary keys.
     *
     * @param expectedDataSet
     *            {@link IDataSet} containing all expected results.
     * @param actualDataSet
     *            {@link IDataSet} containing all actual results.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            tableColumnValueComparers {@link Map}. Can be
     *            <code>null</code> and will default to
     *            {@link ValueComparerDefaults#getDefaultValueComparer()}.
     * @param tableColumnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            tables and columns. Key is table name, value is {@link Map} of
     *            column name in the table to {@link ValueComparer}s. Can be
     *            <code>null</code> and will default to using
     *            {@link ValueComparerDefaults#getDefaultColumnValueComparerMapForTable(String)} or,
     *            if that is empty, defaultValueComparer for all columns in all
     *            tables.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet,
            final ValueComparer defaultValueComparer,
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
            throws DatabaseUnitException
    {
        final FailureHandler failureHandler = getDefaultFailureHandler();
        assertWithValueComparer(expectedDataSet, actualDataSet, failureHandler,
                defaultValueComparer, tableColumnValueComparers);
    }

    /**
     * Asserts the two specified {@link ITable}s comparing their columns using
     * the default {@link ValueComparer} and handles failures using the default
     * {@link FailureHandler}. This method ignores the table names, the columns
     * order, the columns data type, and which columns are composing the primary
     * keys.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable) throws DatabaseUnitException
    {
        final ValueComparer defaultValueComparer =
                valueComparerDefaults.getDefaultValueComparer();

        assertWithValueComparer(expectedTable, actualTable,
                defaultValueComparer);
    }

    /**
     * Asserts the two specified {@link ITable}s comparing their columns using
     * the specified defaultValueComparer and handles failures using the default
     * {@link FailureHandler}. This method ignores the table names, the columns
     * order, the columns data type, and which columns are composing the primary
     * keys.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with all column value
     *            comparisons. Can be <code>null</code> and will default to
     *            {@link ValueComparerDefaults#getDefaultValueComparer()}.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final ValueComparer defaultValueComparer)
            throws DatabaseUnitException
    {
        final String tableName =
                expectedTable.getTableMetaData().getTableName();
        final Map<String, ValueComparer> columnValueComparers =
                valueComparerDefaults
                        .getDefaultColumnValueComparerMapForTable(tableName);

        assertWithValueComparer(expectedTable, actualTable,
                defaultValueComparer, columnValueComparers);
    }

    /**
     * Asserts the two specified {@link ITable}s comparing their columns using
     * the specified columnValueComparers or defaultValueComparer and handles
     * failures using the default {@link FailureHandler}. This method ignores
     * the table names, the columns order, the columns data type, and which
     * columns are composing the primary keys.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default to {@link ValueComparerDefaults#getDefaultValueComparer()}.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name in the table, value is
     *            {@link ValueComparer} to use in comparing expected to actual
     *            column values. Can be <code>null</code> and will default to
     *            using
     *            {@link ValueComparerDefaults#getDefaultColumnValueComparerMapForTable(String)} or,
     *            if that is empty, defaultValueComparer for all columns in the
     *            table.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        final FailureHandler failureHandler = getDefaultFailureHandler();
        assertWithValueComparer(expectedTable, actualTable, failureHandler,
                defaultValueComparer, columnValueComparers);
    }

    /**
     * Asserts the two specified {@link ITable}s comparing their columns using
     * the specified columnValueComparers or defaultValueComparer and handles
     * failures using the default {@link FailureHandler}, using
     * additionalColumnInfo, if specified. This method ignores the table names,
     * the columns order, the columns data type, and which columns are composing
     * the primary keys.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @param additionalColumnInfo
     *            The columns to be printed out if the assert fails because of a
     *            data mismatch. Provides some additional column values that may
     *            be useful to quickly identify the columns for which the
     *            mismatch occurred (for example a primary key column). Can be
     *            <code>null</code>
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default to {@link ValueComparerDefaults#getDefaultValueComparer()}.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name in the table, value is
     *            {@link ValueComparer} to use in comparing expected to actual
     *            column values. Can be <code>null</code> and will default to
     *            using
     *            {@link ValueComparerDefaults#getDefaultColumnValueComparerMapForTable(String)} or,
     *            if that is empty, defaultValueComparer for all columns in the
     *            table.
     * @throws DatabaseUnitException
     */
    public void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final Column[] additionalColumnInfo,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        final FailureHandler failureHandler =
                getDefaultFailureHandler(additionalColumnInfo);

        assertWithValueComparer(expectedTable, actualTable, failureHandler,
                defaultValueComparer, columnValueComparers);
    }
}
