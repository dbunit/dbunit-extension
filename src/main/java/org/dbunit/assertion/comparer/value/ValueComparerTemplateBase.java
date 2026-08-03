package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * Base class for {@link ValueComparer}s, providing template methods and common
 * elements.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public abstract class ValueComparerTemplateBase extends ValueComparerBase
{

    /**
     * {@inheritDoc}
     *
     * This implementation calls
     * {@link #isExpected(ITable, ITable, int, String, DataType, Object, Object)}.
     *
     * @see ValueComparer#compare(ITable, ITable, int, String, DataType, Object,
     *      Object)
     */
    @Override
    protected String doCompare(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final String failMessage;

        final boolean isExpected = isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        if (isExpected)
        {
            failMessage = null;
        } else
        {
            failMessage = makeFailMessage(expectedValue, actualValue);
        }

        return failMessage;
    }

    /**
     * Makes the fail message using {@link #getFailPhrase()}.
     *
     * @param expectedValue the current expected value for the column.
     * @param actualValue the current actual value for the column.
     * @return the formatted fail message with the fail phrase.
     */
    protected String makeFailMessage(final Object expectedValue,
            final Object actualValue)
    {
        final String failPhrase = getFailPhrase();
        return String.format(BASE_FAIL_MSG, actualValue, failPhrase,
                expectedValue);
    }

    /**
     * Determines whether the actual value compares as expected against the expected value.
     *
     * @param expectedTable Table containing all expected results.
     * @param actualTable Table containing all actual results.
     * @param rowNum The current row number comparing.
     * @param columnName The name of the current column comparing.
     * @param dataType The {@link DataType} for the current column comparing.
     * @param expectedValue The current expected value for the column.
     * @param actualValue The current actual value for the column.
     * @return true if comparing actual to expected is as expected.
     * @throws DatabaseUnitException if the comparison cannot be performed.
     */
    protected abstract boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException;

    /**
     * Returns the text snippet for substitution in {@link #BASE_FAIL_MSG}.
     *
     * @return The text snippet for substitution in {@link #BASE_FAIL_MSG}.
     */
    protected abstract String getFailPhrase();
}
