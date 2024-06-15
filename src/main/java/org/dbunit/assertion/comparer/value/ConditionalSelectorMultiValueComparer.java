package org.dbunit.assertion.comparer.value;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * Use a {@link ValueComparerSelector} to select a {@link ValueComparer} for the
 * column from a {@link Map} of them.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class ConditionalSelectorMultiValueComparer extends ValueComparerBase
{
    private final ValueComparerSelector valueComparerSelector;
    private final Map<Object, ValueComparer> valueComparers;

    public ConditionalSelectorMultiValueComparer(final Map<Object, ValueComparer> valueComparers,
            final ValueComparerSelector valueComparerSelector) {
        assertNotNull(valueComparerSelector, "valueComparerSelector is null.");
        assertNotNull(valueComparers, "valueComparers is null.");

        this.valueComparerSelector = valueComparerSelector;
        this.valueComparers = valueComparers;
    }

    @Override
    public String doCompare(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final ValueComparer valueComparer = valueComparerSelector.select(
                expectedTable, actualTable, rowNum, columnName, dataType,
                expectedValue, actualValue, valueComparers);
        if (valueComparer == null)
        {
            final String msg =
                    "No ValueComparer found by valueComparerSelector="
                            + valueComparerSelector + " in map="
                            + valueComparers;
            throw new IllegalStateException(msg);
        }

        return valueComparer.compare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder(400);
        sb.append(super.toString());
        sb.append(": [valueComparerSelector=");
        sb.append(valueComparerSelector.getClass().getName());
        sb.append(", inValuesValueComparer=");
        sb.append(valueComparers);
        sb.append("]");

        return sb.toString();
    }
}
