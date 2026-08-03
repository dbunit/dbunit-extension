package org.dbunit.assertion.comparer.value;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;

/**
 * Base class for {@link ValueComparer} implementations that verify Timestamps,
 * ignoring the milliseconds.
 *
 * @author Jeff Jensen
 * @since 3.1.0
 */
public abstract class TimestampIgnoreMillisValueComparerBase
        extends ValueComparerTemplateBase
{
    private static final int ONE_SECOND_IN_MILLIS = 1000;

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final boolean isExpected;

        if (expectedValue == null || actualValue == null)
        {
            isExpected = isExpectedWithNull(expectedValue, actualValue);
        } else
        {
            isExpected =
                    isExpectedWithoutNull(dataType, expectedValue, actualValue);
        }

        return isExpected;
    }

    /**
     * Determines whether the actual value matches the expected value when at least one of them
     * is <code>null</code>.
     *
     * @param expectedValue the expected value, possibly <code>null</code>.
     * @param actualValue the actual value, possibly <code>null</code>.
     * @return <code>true</code> if the values are considered equal.
     */
    protected boolean isExpectedWithNull(final Object expectedValue,
            final Object actualValue)
    {
        final boolean isExpected;

        if (expectedValue == null)
        {
            isExpected = actualValue == null;
        } else
        {
            isExpected = expectedValue.equals(actualValue);
        }

        return isExpected;
    }

    /**
     * Determines whether the actual value matches the expected value, ignoring milliseconds,
     * given that neither is <code>null</code>.
     *
     * @param dataType the column's data type.
     * @param expectedValue the expected value, never <code>null</code>.
     * @param actualValue the actual value, never <code>null</code>.
     * @return <code>true</code> if the values are considered equal, ignoring milliseconds.
     * @throws TypeCastException if a value cannot be converted for comparison.
     */
    protected boolean isExpectedWithoutNull(final DataType dataType,
            final Object expectedValue, final Object actualValue)
            throws TypeCastException
    {
        assertNotNull(expectedValue, "expectedValue is null.");
        assertNotNull(actualValue, "actualValue is null.");

        long actualTime = convertValueToTimeInMillis(actualValue);
        long expectedTime = convertValueToTimeInMillis(expectedValue);

        actualTime = truncateToSecond(actualTime);
        expectedTime = truncateToSecond(expectedTime);

        return compareTimestamps(dataType, actualTime, expectedTime);
    }

    private long truncateToSecond(final long timeInMilliseconds)
    {
        return ONE_SECOND_IN_MILLIS
                * (timeInMilliseconds / ONE_SECOND_IN_MILLIS);
    }

    /**
     * Converts the given value to milliseconds since the epoch.
     *
     * @param timestampValue the value to convert, a {@link Timestamp} or a {@link String}
     *            formatted as <code>yyyy-MM-dd HH:mm:ss</code>.
     * @return the value converted to milliseconds since the epoch, or 0 if the value is neither.
     */
    protected long convertValueToTimeInMillis(final Object timestampValue)
    {
        final Timestamp timestamp;
        if (timestampValue instanceof Timestamp)
        {
            timestamp = (Timestamp) timestampValue;
        } else if (timestampValue instanceof String)
        {
            final DateTimeFormatter dateFormat =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            final LocalDateTime parsedDate =
                    LocalDateTime.parse((String) timestampValue, dateFormat);
            timestamp = Timestamp.valueOf(parsedDate);
        } else
        {
            timestamp = null;
        }

        final long time;
        if (timestamp == null)
        {
            time = 0L;
        } else
        {
            time = timestamp.getTime();
        }
        return time;
    }

    abstract boolean compareTimestamps(final DataType dataType,
            final long actualTime, final long expectedTime)
            throws TypeCastException;
}
