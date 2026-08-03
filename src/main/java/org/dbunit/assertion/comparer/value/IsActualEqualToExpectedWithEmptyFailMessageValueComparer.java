package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * {@link ValueComparer} implementation that verifies actual value is equal to
 * expected value and sets fail message to empty String.
 *
 * This is primarily for the original assertEquals(*) on
 * {@link org.dbunit.assertion.DbUnitAssert} so the
 * {@link org.dbunit.assertion.Difference} message stays the same.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class IsActualEqualToExpectedWithEmptyFailMessageValueComparer
        extends ValueComparerTemplateBase
{

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        return dataType.compare(actualValue, expectedValue) == 0;
    }

    @Override
    protected String makeFailMessage(final Object expectedValue,
            final Object actualValue)
    {
        return "";
    }

    @Override
    protected String getFailPhrase()
    {
        return null;
    }
}
