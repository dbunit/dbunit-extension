package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link ValueComparer}s providing a template method and common
 * elements, mainly consistent log message and toString.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public abstract class ValueComparerBase implements ValueComparer
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Format String for consistent fail message; substitution strings are:
     * actual, fail phrase, expected.
     */
    public static final String BASE_FAIL_MSG =
            "Actual value='%s' is %s expected value='%s'";

    /**
     * {@inheritDoc}
     *
     * This implementation calls
     * {@link #doCompare(ITable, ITable, int, String, DataType, Object, Object)}.
     */
    public String compare(final ITable expectedTable, final ITable actualTable,
            final int rowNum, final String columnName, final DataType dataType,
            final Object expectedValue, final Object actualValue)
            throws DatabaseUnitException
    {
        final String failMessage;

        failMessage = doCompare(expectedTable, actualTable, rowNum, columnName,
                dataType, expectedValue, actualValue);

        if (log.isDebugEnabled())
        {
            log.debug(
                    "compare: rowNum={}, columnName={}, expectedValue={},"
                            + " actualValue={}, failMessage={}",
                    rowNum, columnName, expectedValue, actualValue,
                    failMessage);
        }

        return failMessage;
    }

    /**
     * Do the comparison and return a fail message or null if comparison passes.
     *
     * @see ValueComparer#compare(ITable, ITable, int, String, DataType, Object,
     *      Object)
     */
    protected abstract String doCompare(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException;

    @Override
    public String toString()
    {
        return getClass().getName();
    }
}
