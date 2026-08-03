package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * {@link ValueComparer} implementation that verifies actual value is greater
 * than or equal to expected value.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class IsActualGreaterThanOrEqualToExpectedValueComparer
        extends ValueComparerTemplateBase
{

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        return dataType.compare(actualValue, expectedValue) > -1;
    }

    @Override
    protected String getFailPhrase()
    {
        return "not greater than or equal to";
    }
}
