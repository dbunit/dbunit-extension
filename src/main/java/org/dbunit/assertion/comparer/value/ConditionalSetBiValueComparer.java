package org.dbunit.assertion.comparer.value;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use one of two {@link ValueComparer}s based on a value present or not in a
 * set of values.
 *
 * When the value returned by the
 * {@link ConditionalSetBiValueComparer#actualValueFactory} is in
 * {@link ConditionalSetBiValueComparer#values}, use the
 * {@link ConditionalSetBiValueComparer#inValuesValueComparer} on the row;
 * otherwise, use the
 * {@link ConditionalSetBiValueComparer#notInValuesValueComparer} on the row.
 *
 * @param <T>
 *            The type of the value used to determine which
 *            {@link ValueComparer} to use.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class ConditionalSetBiValueComparer<T> extends ValueComparerBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ValueFactory<T> actualValueFactory;
    private final Set<T> values;
    private final ValueComparer inValuesValueComparer;
    private final ValueComparer notInValuesValueComparer;

    /**
     * @param actualValueFactory
     *            Factory to make the value to lookup in the values list.
     * @param values
     *            List of values that mean to use the inValuesValueComparer.
     * @param inValuesValueComparer
     *            The {@link ValueComparer} used when the value from the
     *            actualValueFactory is in the values map.
     * @param notInValuesValueComparer
     *            The {@link ValueComparer} used when the value from the
     *            actualValueFactory is not in the values map.
     */
    public ConditionalSetBiValueComparer(final ValueFactory<T> actualValueFactory, final Set<T> values,
            final ValueComparer inValuesValueComparer, final ValueComparer notInValuesValueComparer) {
        assertNotNull(actualValueFactory, "actualValueFactory is null.");
        assertNotNull(values, "values is null.");
        assertNotNull(inValuesValueComparer, "inValuesValueComparer is null.");
        assertNotNull(notInValuesValueComparer, "notInValuesValueComparer is null.");

        this.actualValueFactory = actualValueFactory;
        this.values = values;
        this.inValuesValueComparer = inValuesValueComparer;
        this.notInValuesValueComparer = notInValuesValueComparer;
    }

    @Override
    public String doCompare(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final String failMessage;

        final boolean isInValues = isActualValueInValues(actualTable, rowNum);

        if (isInValues)
        {
            failMessage = inValuesValueComparer.compare(expectedTable,
                    actualTable, rowNum, columnName, dataType, expectedValue,
                    actualValue);
        } else
        {
            failMessage = notInValuesValueComparer.compare(expectedTable,
                    actualTable, rowNum, columnName, dataType, expectedValue,
                    actualValue);
        }

        return failMessage;
    }

    protected boolean isActualValueInValues(final ITable actualTable,
            final int rowNum) throws DataSetException
    {
        final T actualValue = actualValueFactory.make(actualTable, rowNum);
        final boolean isListContains = values.contains(actualValue);

        log.debug("isActualValueInValues: actualValue={}, isListContains={}",
                actualValue, isListContains);

        return isListContains;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(400);
        sb.append(super.toString());
        sb.append(": [values=");
        sb.append(values);
        sb.append(", inValuesValueComparer=");
        sb.append(inValuesValueComparer);
        sb.append(", notInValuesValueComparer=");
        sb.append(notInValuesValueComparer);
        sb.append("]");

        return sb.toString();
    }
}
