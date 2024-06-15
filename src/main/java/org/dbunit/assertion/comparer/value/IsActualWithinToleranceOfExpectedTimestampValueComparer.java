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

    public static final long ONE_SECOND_IN_MILLIS = 1000;
    public static final long TWO_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 2;
    public static final long THREE_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 3;
    public static final long FOUR_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 4;
    public static final long FIVE_SECONDS_IN_MILLIS = ONE_SECOND_IN_MILLIS * 5;

    public static final long ONE_MINUTE_IN_MILLIS = ONE_SECOND_IN_MILLIS * 60;
    public static final long TWO_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 2;
    public static final long THREE_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 3;
    public static final long FOUR_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 4;
    public static final long FIVE_MINUTES_IN_MILLIS = ONE_MINUTE_IN_MILLIS * 5;
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

    /** Since one is a known null, isExpected=true when they equal. */
    protected boolean isExpectedWithNull(final Object expectedValue,
            final Object actualValue)
    {
        final boolean isExpected = expectedValue == actualValue;

        log.debug("isExpectedWithNull: {}, actualValue={}, expectedValue={}",
                isExpected, actualValue, expectedValue);

        return isExpected;
    }

    /** Neither is null so compare values with tolerance. */
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

    protected long convertValueToTimeInMillis(final Object timestampValue)
    {
        final Timestamp timestamp = (Timestamp) timestampValue;
        return timestamp.getTime();
    }

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
