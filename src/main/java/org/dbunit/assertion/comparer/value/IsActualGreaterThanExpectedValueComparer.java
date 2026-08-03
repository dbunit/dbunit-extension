package org.dbunit.assertion.comparer.value;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;

/**
 * {@link ValueComparer} implementation that verifies actual value is greater
 * than expected value.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class IsActualGreaterThanExpectedValueComparer extends ValueComparerTemplateBase
{

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        return dataType.compare(actualValue, expectedValue) > 0;
    }

    @Override
    protected String getFailPhrase()
    {
        return "not greater than";
    }
}
