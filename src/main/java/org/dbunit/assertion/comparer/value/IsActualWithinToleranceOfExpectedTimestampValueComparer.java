package org.dbunit.assertion.comparer.value;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Timestamp;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ValueComparer} implementation for {@link Timestamp}s that verifies
 * actual value is within a low and high milliseconds tolerance range of
 * expected value.
 *
 * Note: If actual and expected values are both null, the comparison passes.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class IsActualWithinToleranceOfExpectedTimestampValueComparer
        extends ValueComparerTemplateBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** One second, in milliseconds. */
    public static final long ONE_SECOND_IN_MILLIS = 1000;
    /** Two seconds, in milliseconds. */
    public static final long TWO_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 2;
    /** Three seconds, in milliseconds. */
    public static final long THREE_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 3;
    /** Four seconds, in milliseconds. */
    public static final long FOUR_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 4;
    /** Five seconds, in milliseconds. */
    public static final long FIVE_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 5;

    /** One minute, in milliseconds. */
    public static final long ONE_MINUTE_IN_MILLIS = ONE_SECOND_IN_MILLIS * 60;
    /** Two minutes, in milliseconds. */
    public static final long TWO_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 2;
    /** Three minutes, in milliseconds. */
    public static final long THREE_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 3;
    /** Four minutes, in milliseconds. */
    public static final long FOUR_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 4;
    /** Five minutes, in milliseconds. */
    public static final long FIVE_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 5;
    /** Ten minutes, in milliseconds. */
    public static final long TEN_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 10;

    private long lowToleranceValueInMillis;
    private long highToleranceValueInMillis;

    /**
     * Create instance specifying the allowed actual time difference range from
     * expected.
     *
     * @param lowToleranceValueInMillis
     *            The minimum time difference allowed.
     * @param highToleranceValueInMillis
     *            The maximum time difference allowed.
     */
    public IsActualWithinToleranceOfExpectedTimestampValueComparer(
            final long lowToleranceValueInMillis,
            final long highToleranceValueInMillis)
    {
        this.lowToleranceValueInMillis = lowToleranceValueInMillis;
        this.highToleranceValueInMillis = highToleranceValueInMillis;
    }

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final boolean isExpected;

        // handle nulls: prevent NPE and isExpected=true when both null
        if (expectedValue == null || actualValue == null)
        {
            isExpected = isExpectedWithNull(expectedValue, actualValue);
        } else
        {
            isExpected =
                    isExpectedWithoutNull(expectedValue, actualValue, dataType);
        }

        return isExpected;
    }

    /**
     * Since one is a known null, isExpected=true when they equal.
     *
     * @param expectedValue the expected value, possibly null.
     * @param actualValue the actual value, possibly null.
     * @return <code>true</code> if expectedValue and actualValue are the same reference
     *         (both null, or the same non-null instance).
     */
    protected boolean isExpectedWithNull(final Object expectedValue,
            final Object actualValue)
    {
        final boolean isExpected = expectedValue == actualValue;

        log.debug("isExpectedWithNull: {}, actualValue={}, expectedValue={}",
                isExpected, actualValue, expectedValue);

        return isExpected;
    }

    /**
     * Neither is null so compare values with tolerance.
     *
     * @param expectedValue the expected value, not null.
     * @param actualValue the actual value, not null.
     * @param dataType the data type used to cast both values to a {@link Timestamp}.
     * @return <code>true</code> if actualValue is within tolerance of expectedValue.
     * @throws TypeCastException if either value cannot be cast using dataType.
     */
    protected boolean isExpectedWithoutNull(final Object expectedValue, final Object actualValue,
            final DataType dataType) throws TypeCastException {
        assertNotNull(expectedValue, "expectedValue is null.");
        assertNotNull(actualValue, "actualValue is null.");

        final Object actualTimestamp = getCastedValue(actualValue, dataType);
        final long actualTime = convertValueToTimeInMillis(actualTimestamp);

        final Object expectedTimestamp =
                getCastedValue(expectedValue, dataType);
        final long expectedTime = convertValueToTimeInMillis(expectedTimestamp);

        final long diffTime = calcTimeDifference(actualTime, expectedTime);
        return isTolerant(diffTime);
    }

    /**
     * Casts the given value using the given data type, or returns it unchanged if the type
     * is <code>null</code> or {@link DataType#UNKNOWN}.
     *
     * @param value the value to cast.
     * @param type the data type to cast with, may be <code>null</code>.
     * @return the cast value.
     * @throws TypeCastException if the value cannot be cast using the given type.
     */
    protected Object getCastedValue(final Object value, final DataType type)
            throws TypeCastException
    {
        final Object castedValue;

        if (type == null || type == DataType.UNKNOWN)
        {
            castedValue = value;
        } else
        {
            castedValue = type.typeCast(value);
        }

        return castedValue;
    }

    /**
     * Returns whether the given time difference is within the configured tolerance range.
     *
     * @param diffTime the (non-negative) time difference, in milliseconds.
     * @return <code>true</code> if diffTime is within the configured tolerance range.
     */
    protected boolean isTolerant(final long diffTime)
    {
        final boolean isLowTolerant = diffTime >= lowToleranceValueInMillis;
        final boolean isHighTolerant = diffTime <= highToleranceValueInMillis;
        final boolean isTolerant = isLowTolerant && isHighTolerant;

        log.debug(
                "isTolerant: {},"
                        + " diffTime={}, lowToleranceValueInMillis={},"
                        + " highToleranceValueInMillis={}",
                isTolerant, diffTime, lowToleranceValueInMillis,
                highToleranceValueInMillis);

        return isTolerant;
    }

    /**
     * Returns the given {@link Timestamp} value's time in milliseconds.
     *
     * @param timestampValue the value to convert, must be a {@link Timestamp}.
     * @return the timestamp's time in milliseconds.
     */
    protected long convertValueToTimeInMillis(final Object timestampValue)
    {
        final Timestamp timestamp = (Timestamp) timestampValue;
        return timestamp.getTime();
    }

    /**
     * Returns the absolute time difference between the given times.
     *
     * @param actualTimeInMillis the actual time, in milliseconds.
     * @param expectedTimeInMillis the expected time, in milliseconds.
     * @return the absolute difference between the two times, in milliseconds.
     */
    protected long calcTimeDifference(final long actualTimeInMillis,
            final long expectedTimeInMillis)
    {
        final long diffTime = actualTimeInMillis - expectedTimeInMillis;
        final long diffTimeAbs = Math.abs(diffTime);
        log.debug(
                "calcTimeDifference: "
                        + "actualTimeInMillis={}, expectedTimeInMillis={},"
                        + " diffInMillisTime={}, diffTimeInMillisAbs={}",
                actualTimeInMillis, expectedTimeInMillis, diffTime,
                diffTimeAbs);

        return diffTimeAbs;
    }

    @Override
    protected String getFailPhrase()
    {
        return "not within tolerance range of " + lowToleranceValueInMillis
                + " - " + highToleranceValueInMillis + " milliseconds of";
    }
}
