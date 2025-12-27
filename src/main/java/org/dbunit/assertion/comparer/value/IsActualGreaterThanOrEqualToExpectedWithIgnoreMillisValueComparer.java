package org.dbunit.assertion.comparer.value;

import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;

/**
 * {@link ValueComparer} implementation that verifies actual Timestamp value is
 * greater than or equal to expected value, ignoring the milliseconds.
 *
 * @author Jeff Jensen
 * @since 3.1.0
 */
public class IsActualGreaterThanOrEqualToExpectedWithIgnoreMillisValueComparer
        extends TimestampIgnoreMillisValueComparerBase
{
    @Override
    protected boolean compareTimestamps(final DataType dataType,
            final long actualTime, final long expectedTime)
            throws TypeCastException
    {
        return dataType.compare(actualTime, expectedTime) > -1;
    }

    @Override
    protected String getFailPhrase()
    {
        return "not greater than or equal to";
    }
}
