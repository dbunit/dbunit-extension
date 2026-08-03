package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ValueComparer} implementation that verifies actual value contains
 * expected value by converting to {@link String}s and using
 * {@link String#contains(CharSequence)}. Special case: if both are null, they
 * match.
 *
 * @author Jeff Jensen
 * @since 2.7.0
 */
public class IsActualContainingExpectedStringValueComparer
        extends ValueComparerTemplateBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final boolean isExpected;

        // handle nulls: prevent NPE and isExpected=true when both null
        if (expectedValue == null && actualValue == null)
        {
            // both are null, so match
            isExpected = true;
        } else if (expectedValue == null || actualValue == null)
        {
            // both aren't null, one is null, so no match
            isExpected = false;
        } else
        {
            // neither are null, so compare
            isExpected = isContaining(expectedValue, actualValue);
        }

        return isExpected;
    }

    /**
     * Returns whether the actual value, converted to a string, contains the expected value,
     * also converted to a string.
     *
     * @param expectedValue the expected value.
     * @param actualValue the actual value.
     * @return <code>true</code> if the actual value's string form contains the expected value's string form.
     * @throws TypeCastException if either value cannot be converted to a string.
     */
    protected boolean isContaining(final Object expectedValue,
            final Object actualValue) throws TypeCastException
    {
        final String expectedValueString = DataType.asString(expectedValue);
        final String actualValueString = DataType.asString(actualValue);
        log.debug("isContaining: expectedValueString={}, actualValueString={}",
                expectedValueString, actualValueString);

        return actualValueString.contains(expectedValueString);
    }

    @Override
    protected String getFailPhrase()
    {
        return "not containing";
    }
}
