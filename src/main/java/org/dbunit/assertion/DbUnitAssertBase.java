package org.dbunit.assertion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.assertion.DbUnitAssert.ComparisonColumn;
import org.dbunit.assertion.comparer.value.DefaultValueComparerDefaults;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.ValueComparerDefaults;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for DbUnit assert classes containing common methods.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class DbUnitAssertBase
{
    private final Logger log = LoggerFactory.getLogger(DbUnitAssertBase.class);

    private FailureFactory junitFailureFactory = getJUnitFailureFactory();

    /**
     * The value comparer defaults used when no comparer is explicitly configured for a table/column.
     */
    protected ValueComparerDefaults valueComparerDefaults =
            new DefaultValueComparerDefaults();

    /**
     * Returns the default failure handler, without additional column info.
     *
     * @return The default failure handler
     * @since 2.4
     */
    protected FailureHandler getDefaultFailureHandler()
    {
        return getDefaultFailureHandler(null);
    }

    /**
     * Returns the default failure handler, reporting the given additional columns on failure.
     *
     * @param additionalColumnInfo the additional columns to report on failure, may be <code>null</code>.
     * @return The default failure handler
     * @since 2.4
     */
    protected FailureHandler getDefaultFailureHandler(
            final Column[] additionalColumnInfo)
    {
        final DefaultFailureHandler failureHandler =
                new DefaultFailureHandler(additionalColumnInfo);
        if (junitFailureFactory != null)
        {
            failureHandler.setFailureFactory(junitFailureFactory);
        }
        return failureHandler;
    }

    /**
     * @return the JUnitFailureFactory if JUnit is on the classpath or
     *         <code>null</code> if JUnit is not on the classpath.
     */
    private FailureFactory getJUnitFailureFactory()
    {
        try
        {
            Class.forName("org.junit.jupiter.api.Assertions");
            // JUnit available
            return new JUnitFailureFactory();
        } catch (final ClassNotFoundException e)
        {
            // JUnit not available on the classpath return null
            log.debug("JUnit does not seem to be on the classpath. " + e);
        }
        return null;
    }

    /**
     * Builds the comparison columns to use for the assertion, pairing each expected column with
     * its actual counterpart and resolving the correct datatype.
     *
     * @param expectedTableName
     *            the name of the table being compared, used for failure reporting.
     * @param expectedColumns
     *            the expected columns, providing the comparison order.
     * @param actualColumns
     *            the actual columns, matched positionally to expectedColumns.
     * @param failureHandler
     *            The {@link FailureHandler} to be used when no datatype can be
     *            determined
     * @return The columns to be used for the assertion, including the correct
     *         datatype
     * @since 2.4
     */
    protected ComparisonColumn[] getComparisonColumns(
            final String expectedTableName, final Column[] expectedColumns,
            final Column[] actualColumns, final FailureHandler failureHandler)
    {
        final ComparisonColumn[] result =
                new ComparisonColumn[expectedColumns.length];

        for (int j = 0; j < expectedColumns.length; j++)
        {
            final Column expectedColumn = expectedColumns[j];
            final Column actualColumn = actualColumns[j];
            result[j] = new ComparisonColumn(expectedTableName, expectedColumn,
                    actualColumn, failureHandler);
        }
        return result;
    }

    /**
     * Method to last-minute intercept the comparison of a single expected and
     * actual value. Designed to be overridden in order to skip cell comparison
     * by specific cell values.
     *
     * @param columnName
     *            The column being compared
     * @param expectedValue
     *            The expected value to be compared
     * @param actualValue
     *            The actual value to be compared
     * @return <code>false</code> always so that the comparison is never skipped
     * @since 2.4
     */
    protected boolean skipCompare(final String columnName,
            final Object expectedValue, final Object actualValue)
    {
        return false;
    }

    /**
     * Returns the given failure handler, or the default one if the given handler is <code>null</code>.
     *
     * @param failureHandler the failure handler to validate, may be <code>null</code>.
     * @return the given failure handler, or the default one if <code>null</code>.
     */
    protected FailureHandler determineFailureHandler(
            final FailureHandler failureHandler)
    {
        final FailureHandler validFailureHandler;

        if (failureHandler == null)
        {
            log.debug("FailureHandler is null. Using default implementation");
            validFailureHandler = getDefaultFailureHandler();
        } else
        {
            validFailureHandler = failureHandler;
        }

        return validFailureHandler;
    }

    /**
     * Compares the row counts of the two given tables, failing via the given failure handler on mismatch.
     *
     * @param expectedTable the table containing all expected results.
     * @param actualTable the table containing all actual results.
     * @param failureHandler the failure handler used to report a row count mismatch.
     * @param expectedTableName the table name, used for failure reporting.
     * @return <code>true</code> if both tables are empty, in which case column comparison can be skipped.
     */
    protected boolean compareRowCounts(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler,
            final String expectedTableName) throws Error
    {
        boolean isTablesEmpty;

        final int expectedRowsCount = expectedTable.getRowCount();
        int actualRowsCount = 0;
        boolean skipRowComparison = false;
        try
        {
            actualRowsCount = actualTable.getRowCount();
        } catch (final UnsupportedOperationException exception)
        {
            skipRowComparison = true;
        }

        if (skipRowComparison)
        {
            isTablesEmpty = false;
        } else
        {
            if (expectedRowsCount != actualRowsCount)
            {
                final String msg =
                        "row count (table=" + expectedTableName + ")";
                final Error error = failureHandler.createFailure(msg,
                        String.valueOf(expectedRowsCount),
                        String.valueOf(actualRowsCount));
                log.error(error.toString());
                throw error;
            }

            // if both tables are empty, it is not necessary to compare columns,
            // as such comparison can fail if column metadata is different
            // (which could occurs when comparing empty tables)
            if (expectedRowsCount == 0 && actualRowsCount == 0)
            {
                log.debug("Tables are empty, hence equals.");
                isTablesEmpty = true;
            } else
            {
                isTablesEmpty = false;
            }
        }

        return isTablesEmpty;
    }

    /**
     * Compares the columns of the two given tables, failing via the given failure handler on mismatch.
     *
     * @param expectedColumns the expected columns.
     * @param actualColumns the actual columns.
     * @param expectedMetaData the metadata of the expected table.
     * @param actualMetaData the metadata of the actual table.
     * @param failureHandler the failure handler used to report a column mismatch.
     * @throws DataSetException if the column difference cannot be computed.
     */
    protected void compareColumns(final Column[] expectedColumns,
            final Column[] actualColumns, final ITableMetaData expectedMetaData,
            final ITableMetaData actualMetaData,
            final FailureHandler failureHandler) throws DataSetException, Error
    {
        final Columns.ColumnDiff columnDiff =
                Columns.getColumnDiff(expectedMetaData, actualMetaData);
        if (columnDiff.hasDifference())
        {
            final String message = columnDiff.getMessage();
            final Error error = failureHandler.createFailure(message,
                    Columns.getColumnNamesAsString(expectedColumns),
                    Columns.getColumnNamesAsString(actualColumns));
            log.error(error.toString());
            throw error;
        }
    }

    /**
     * Compares the number of expected and actual table names, failing via the given failure handler on mismatch.
     *
     * @param expectedNames the expected table names.
     * @param actualNames the actual table names.
     * @param failureHandler the failure handler used to report a table count mismatch.
     */
    protected void compareTableCounts(final String[] expectedNames,
            final String[] actualNames, final FailureHandler failureHandler)
            throws Error
    {
        if (expectedNames.length != actualNames.length)
        {
            throw failureHandler.createFailure("table count",
                    String.valueOf(expectedNames.length),
                    String.valueOf(actualNames.length));
        }
    }

    /**
     * Compares the expected and actual table names, failing via the given failure handler on mismatch.
     *
     * @param expectedNames the expected table names, sorted.
     * @param actualNames the actual table names, sorted.
     * @param failureHandler the failure handler used to report a table name mismatch.
     */
    protected void compareTableNames(final String[] expectedNames,
            final String[] actualNames, final FailureHandler failureHandler)
            throws Error
    {
        for (int i = 0; i < expectedNames.length; i++)
        {
            if (!actualNames[i].equals(expectedNames[i]))
            {
                throw failureHandler.createFailure("tables",
                        Arrays.asList(expectedNames).toString(),
                        Arrays.asList(actualNames).toString());
            }
        }
    }

    /**
     * Returns the table names of the given dataset, sorted and upper-cased unless
     * the dataset is case-sensitive.
     *
     * @param dataSet the dataset providing the table names.
     * @return the sorted table names.
     * @throws DataSetException if the table names cannot be retrieved.
     */
    protected String[] getSortedTableNames(final IDataSet dataSet)
            throws DataSetException
    {
        log.debug("getSortedTableNames(dataSet={}) - start", dataSet);

        final String[] names = dataSet.getTableNames().clone();
        if (!dataSet.isCaseSensitiveTableNames())
        {
            for (int i = 0; i < names.length; i++)
            {
                names[i] = names[i].toUpperCase(Locale.ENGLISH);
            }
        }
        Arrays.sort(names);
        return names;
    }

    /**
     * Asserts the two specified {@link IDataSet}s comparing their columns using
     * the specified columnValueComparers or defaultValueComparer and handles
     * failures using the specified failureHandler. This method ignores the
     * table names, the columns order, the columns data type, and which columns
     * are composing the primary keys.
     *
     * @param expectedDataSet
     *            {@link IDataSet} containing all expected results.
     * @param actualDataSet
     *            {@link IDataSet} containing all actual results.
     * @param failureHandler
     *            The failure handler used if the assert fails because of a data
     *            mismatch. Provides some additional information that may be
     *            useful to quickly identify the rows for which the mismatch
     *            occurred (for example by printing an additional primary key
     *            column). Can be <code>null</code>.
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
     * @throws DatabaseUnitException if an expected table is missing from the actual dataset,
     *            or a table comparison fails.
     */
    public void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
            throws DatabaseUnitException
    {
        log.debug(
                "assertWithValueComparer(expectedDataSet={}, actualDataSet={},"
                        + " failureHandler={}, defaultValueComparer={},"
                        + " tableColumnValueComparers={}) - start",
                expectedDataSet, actualDataSet, failureHandler,
                defaultValueComparer, tableColumnValueComparers);

        // do not continue if same instance
        if (expectedDataSet == actualDataSet)
        {
            log.debug("The given datasets reference the same object."
                    + " Skipping comparisons.");
            return;
        }

        final FailureHandler validFailureHandler =
                determineFailureHandler(failureHandler);

        final String[] expectedNames = getSortedTableNames(expectedDataSet);
        final String[] actualNames = getSortedTableNames(actualDataSet);

        compareTableCounts(expectedNames, actualNames, validFailureHandler);

        // table names in no specific order
        compareTableNames(expectedNames, actualNames, validFailureHandler);

        compareTables(expectedDataSet, actualDataSet, expectedNames,
                validFailureHandler, defaultValueComparer,
                tableColumnValueComparers);
    }

    /**
     * Asserts each expected table against its corresponding actual table.
     *
     * @param expectedDataSet the dataset containing all expected results.
     * @param actualDataSet the dataset containing all actual results.
     * @param expectedNames the table names to compare, in comparison order.
     * @param failureHandler the failure handler used if the assert fails because of a data mismatch.
     * @param defaultValueComparer the default value comparer, used when a table/column has none configured.
     * @param tableColumnValueComparers the per-table, per-column value comparers to use.
     * @throws DatabaseUnitException if a table comparison fails.
     */
    protected void compareTables(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final String[] expectedNames,
            final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
            throws DatabaseUnitException
    {
        final Map<String, Map<String, ValueComparer>> validTableColumnValueComparers =
                determineValidTableColumnValueComparers(
                        tableColumnValueComparers);

        for (int i = 0; i < expectedNames.length; i++)
        {
            final String tableName = expectedNames[i];

            final ITable expectedTable = expectedDataSet.getTable(tableName);
            final ITable actualTable = actualDataSet.getTable(tableName);
            final Map<String, ValueComparer> columnValueComparers =
                    validTableColumnValueComparers.get(tableName);

            assertWithValueComparer(expectedTable, actualTable, failureHandler,
                    defaultValueComparer, columnValueComparers);
        }
    }

    /**
     * Asserts the two specified {@link ITable}s comparing their columns using
     * the specified columnValueComparers or defaultValueComparer and handles
     * failures using the specified failureHandler. This method ignores the
     * table names, the columns order, the columns data type, and which columns
     * are composing the primary keys.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @param failureHandler
     *            The failure handler used if the assert fails because of a data
     *            mismatch. Provides some additional information that may be
     *            useful to quickly identify the rows for which the mismatch
     *            occurred (for example by printing an additional primary key
     *            column). Can be <code>null</code>.
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
     * @throws DatabaseUnitException if the tables' row counts, columns, or data do not match.
     */
    public void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        log.trace("assertWithValueComparer(expectedTable, actualTable,"
                + " failureHandler, defaultValueComparer,"
                + " columnValueComparers) - start");
        log.debug("assertWithValueComparer: expectedTable={}", expectedTable);
        log.debug("assertWithValueComparer: actualTable={}", actualTable);
        log.debug("assertWithValueComparer: failureHandler={}", failureHandler);
        log.debug("assertWithValueComparer: defaultValueComparer={}",
                defaultValueComparer);
        log.debug("assertWithValueComparer: columnValueComparers={}",
                columnValueComparers);

        // Do not continue if same instance
        if (expectedTable == actualTable)
        {
            log.debug("The given tables reference the same object."
                    + " Skipping comparisons.");
            return;
        }

        final FailureHandler validFailureHandler =
                determineFailureHandler(failureHandler);

        final ITableMetaData expectedMetaData =
                expectedTable.getTableMetaData();
        final ITableMetaData actualMetaData = actualTable.getTableMetaData();
        final String expectedTableName = expectedMetaData.getTableName();

        final boolean isTablesEmpty = compareRowCounts(expectedTable,
                actualTable, validFailureHandler, expectedTableName);
        if (isTablesEmpty)
        {
            return;
        }

        // Put the columns into the same order
        final Column[] expectedColumns =
                Columns.getSortedColumns(expectedMetaData);
        final Column[] actualColumns = Columns.getSortedColumns(actualMetaData);

        // Verify columns
        compareColumns(expectedColumns, actualColumns, expectedMetaData,
                actualMetaData, validFailureHandler);

        // Get the datatypes to be used for comparing the sorted columns
        final ComparisonColumn[] comparisonCols =
                getComparisonColumns(expectedTableName, expectedColumns,
                        actualColumns, validFailureHandler);

        // Finally compare the data
        compareData(expectedTable, actualTable, comparisonCols,
                validFailureHandler, defaultValueComparer,
                columnValueComparers);
    }

    /**
     * Compares the data of the two given tables using the default value comparers.
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param actualTable
     *            Table containing all actual results.
     * @param comparisonCols
     *            The columns to be compared, also including the correct
     *            {@link DataType}s for comparison
     * @param failureHandler
     *            The failure handler used if the assert fails because of a data
     *            mismatch. Provides some additional information that may be
     *            useful to quickly identify the rows for which the mismatch
     *            occurred (for example by printing an additional primary key
     *            column). Must not be <code>null</code> at this stage
     * @throws DataSetException if a data comparison fails.
     * @since 2.4
     */
    protected void compareData(final ITable expectedTable,
            final ITable actualTable, final ComparisonColumn[] comparisonCols,
            final FailureHandler failureHandler) throws DataSetException
    {
        final ValueComparer defaultValueComparer = null;
        final Map<String, ValueComparer> columnValueComparers = null;
        try
        {
            compareData(expectedTable, actualTable, comparisonCols,
                    failureHandler, defaultValueComparer, columnValueComparers);
        } catch (final DatabaseUnitException e)
        {
            // not-private method, signature change breaks compatability
            throw new DataSetException(e);
        }
    }

    /**
     * Compares the data of the two given tables using the given value comparers.
     *
     * @param expectedTable
     *            {@link ITable} containing all expected results.
     * @param actualTable
     *            {@link ITable} containing all actual results.
     * @param comparisonCols
     *            The columns to be compared, also including the correct
     *            {@link DataType}s for comparison
     * @param failureHandler
     *            The failure handler used if the assert fails because of a data
     *            mismatch. Provides some additional information that may be
     *            useful to quickly identify the rows for which the mismatch
     *            occurred (for example by printing an additional primary key
     *            column). Must not be <code>null</code> at this stage.
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
     * @throws DatabaseUnitException if a data comparison fails.
     * @since 2.4
     * @since 2.6.0
     */
    protected void compareData(final ITable expectedTable,
            final ITable actualTable, final ComparisonColumn[] comparisonCols,
            final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        log.debug(
                "compareData(expectedTable={}, actualTable={}, "
                        + "comparisonCols={}, failureHandler={},"
                        + " defaultValueComparer={}, columnValueComparers={})"
                        + " - start",
                expectedTable, actualTable, comparisonCols, failureHandler,
                defaultValueComparer, columnValueComparers);

        if (expectedTable == null)
        {
            throw new IllegalArgumentException(
                    "The parameter 'expectedTable' is null");
        }
        if (actualTable == null)
        {
            throw new IllegalArgumentException(
                    "The parameter 'actualTable' is null");
        }
        if (comparisonCols == null)
        {
            throw new IllegalArgumentException(
                    "The parameter 'comparisonCols' is null");
        }
        if (failureHandler == null)
        {
            throw new IllegalArgumentException(
                    "The parameter 'failureHandler' is null");
        }

        final ValueComparer validDefaultValueComparer =
                determineValidDefaultValueComparer(defaultValueComparer);
        final String expectedTableName =
                expectedTable.getTableMetaData().getTableName();
        final Map<String, ValueComparer> validColumnValueComparers =
                determineValidColumnValueComparers(columnValueComparers,
                        expectedTableName);

        // iterate over all rows
        final int rowCount = expectedTable.getRowCount();
        for (int rowNum = 0; rowNum < rowCount; rowNum++)
        {
            // iterate over all columns of the current row
            final int columnCount = comparisonCols.length;
            for (int columnNum = 0; columnNum < columnCount; columnNum++)
            {
                compareData(expectedTable, actualTable, comparisonCols,
                        failureHandler, validDefaultValueComparer,
                        validColumnValueComparers, rowNum, columnNum);
            }
        }
    }

    /**
     * Compares a single expected and actual cell value at the given row and column, failing
     * via the given failure handler on mismatch.
     *
     * @param expectedTable {@link ITable} containing all expected results.
     * @param actualTable {@link ITable} containing all actual results.
     * @param comparisonCols the columns to be compared, also including the correct {@link DataType}s.
     * @param failureHandler the failure handler used if the assert fails because of a data mismatch.
     * @param defaultValueComparer the default value comparer, used when the column has none configured.
     * @param columnValueComparers the per-column value comparers to use.
     * @param rowNum the row index of the cell to compare.
     * @param columnNum the index, into comparisonCols, of the column to compare.
     * @throws DatabaseUnitException if the cell comparison fails.
     */
    protected void compareData(final ITable expectedTable,
            final ITable actualTable, final ComparisonColumn[] comparisonCols,
            final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers,
            final int rowNum, final int columnNum) throws DatabaseUnitException
    {
        final ComparisonColumn compareColumn = comparisonCols[columnNum];

        final String columnName = compareColumn.getColumnName();
        final DataType dataType = compareColumn.getDataType();

        final Object expectedValue = expectedTable.getValue(rowNum, columnName);
        final Object actualValue = actualTable.getValue(rowNum, columnName);

        // Compare the values
        if (skipCompare(columnName, expectedValue, actualValue))
        {
            log.trace(
                    "skipCompare: ignoring comparison" + " {}={} on column={}",
                    expectedValue, actualValue, columnName);
        } else
        {
            final ValueComparer valueComparer = determineValueComparer(
                    columnName, defaultValueComparer, columnValueComparers);

            if (log.isDebugEnabled())
            {
                log.debug(
                        "compareData: comparing actualValue={}"
                                + " to expectedValue={} with valueComparer={}",
                        actualValue, expectedValue, valueComparer);
            }
            final String failMessage =
                    valueComparer.compare(expectedTable, actualTable, rowNum,
                            columnName, dataType, expectedValue, actualValue);

            failIfNecessary(expectedTable, actualTable, failureHandler, rowNum,
                    columnName, expectedValue, actualValue, failMessage);
        }
    }

    /**
     * Reports a failure to the given failure handler if the given fail message is not <code>null</code>.
     *
     * @param expectedTable the table containing all expected results.
     * @param actualTable the table containing all actual results.
     * @param failureHandler the failure handler to report the difference to.
     * @param rowNum the row index of the compared cell.
     * @param columnName the name of the compared column.
     * @param expectedValue the expected cell value.
     * @param actualValue the actual cell value.
     * @param failMessage the comparison failure message, or <code>null</code> if the values matched.
     */
    protected void failIfNecessary(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler,
            final int rowNum, final String columnName,
            final Object expectedValue, final Object actualValue,
            final String failMessage)
    {
        if (failMessage != null)
        {
            final Difference diff = new Difference(expectedTable, actualTable,
                    rowNum, columnName, expectedValue, actualValue,
                    failMessage);

            failureHandler.handle(diff);
        }
    }

    /**
     * Returns the value comparer configured for the given column, or the default value comparer
     * if the column has none configured.
     *
     * @param columnName the column name to look up.
     * @param defaultValueComparer the value comparer to fall back to.
     * @param columnValueComparers the per-column value comparers to look up columnName in.
     * @return the value comparer to use for the given column.
     */
    protected ValueComparer determineValueComparer(final String columnName,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
    {
        ValueComparer valueComparer = columnValueComparers.get(columnName);
        if (valueComparer == null)
        {
            if (log.isDebugEnabled())
            {
                log.debug(
                        "determineValueComparer: using defaultValueComparer='{}'"
                                + " as columnName='{}' not found"
                                + " in columnValueComparers='{}'",
                        defaultValueComparer, columnName, columnValueComparers);
            }
            valueComparer = defaultValueComparer;
        }

        return valueComparer;
    }

    /**
     * Returns the given default value comparer, or {@link ValueComparerDefaults#getDefaultValueComparer()}
     * if the given comparer is <code>null</code>.
     *
     * @param defaultValueComparer the value comparer to validate, may be <code>null</code>.
     * @return the given value comparer, or the framework default if <code>null</code>.
     */
    protected ValueComparer determineValidDefaultValueComparer(
            final ValueComparer defaultValueComparer)
    {
        final ValueComparer validValueComparer;

        if (defaultValueComparer == null)
        {
            validValueComparer =
                    valueComparerDefaults.getDefaultValueComparer();
            log.debug(
                    "determineValidDefaultValueComparer:"
                            + " using getDefaultValueComparer()={}"
                            + " as defaultValueComparer={}",
                    validValueComparer, defaultValueComparer);
        } else
        {
            validValueComparer = defaultValueComparer;
        }

        return validValueComparer;
    }

    /**
     * Returns the given per-table, per-column value comparer map, or
     * {@link ValueComparerDefaults#getDefaultTableColumnValueComparerMap()} if the given map is
     * <code>null</code>.
     *
     * @param tableColumnValueComparers the map to validate, may be <code>null</code>.
     * @return the given map, or the framework default if <code>null</code>.
     */
    protected Map<String, Map<String, ValueComparer>> determineValidTableColumnValueComparers(
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
    {
        final Map<String, Map<String, ValueComparer>> validMap;

        if (tableColumnValueComparers == null)
        {
            validMap = valueComparerDefaults
                    .getDefaultTableColumnValueComparerMap();
            log.debug(
                    "determineValidTableColumnValueComparers:"
                            + " using getDefaultTableColumnValueComparerMap()={}"
                            + " as tableColumnValueComparers={}",
                    validMap, tableColumnValueComparers);
        } else
        {
            validMap = tableColumnValueComparers;
        }

        return validMap;
    }

    /**
     * Returns the given per-column value comparer map, or
     * {@link ValueComparerDefaults#getDefaultColumnValueComparerMapForTable(String)} for the given
     * table if the given map is <code>null</code>.
     *
     * @param columnValueComparers the map to validate, may be <code>null</code>.
     * @param tableName the table name to look up the default map for, if needed.
     * @return the given map, or the framework default if <code>null</code>.
     */
    protected Map<String, ValueComparer> determineValidColumnValueComparers(
            final Map<String, ValueComparer> columnValueComparers,
            final String tableName)
    {
        final Map<String, ValueComparer> validMap;

        if (columnValueComparers == null)
        {
            validMap = valueComparerDefaults
                    .getDefaultColumnValueComparerMapForTable(tableName);
            log.debug(
                    "determineValidColumnValueComparers:"
                            + " using getDefaultValueComparerMap()={}"
                            + " as columnValueComparers={} for tableName={}",
                    validMap, columnValueComparers, tableName);
        } else
        {
            validMap = columnValueComparers;
        }

        return validMap;
    }

    /**
     * Sets the value comparer defaults used when no comparer is explicitly configured
     * for a table/column.
     *
     * @param valueComparerDefaults the value comparer defaults to use.
     */
    public void setValueComparerDefaults(
            final ValueComparerDefaults valueComparerDefaults)
    {
        this.valueComparerDefaults = valueComparerDefaults;
    }
}
