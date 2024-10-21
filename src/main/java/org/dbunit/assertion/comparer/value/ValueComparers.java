package org.dbunit.assertion.comparer.value;

import static org.dbunit.assertion.comparer.value.IsActualWithinToleranceOfExpectedTimestampValueComparer.ONE_MINUTE_IN_MILLIS;
import static org.dbunit.assertion.comparer.value.IsActualWithinToleranceOfExpectedTimestampValueComparer.ONE_SECOND_IN_MILLIS;

/**
 * Convenience set of common {@link ValueComparer} instances.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public abstract class ValueComparers
{
    protected ValueComparers()
    {
    }

    /** @see IsActualEqualToExpectedValueComparer */
    public static final ValueComparer isActualEqualToExpected =
            new IsActualEqualToExpectedValueComparer();

    /**
     * Same as {@link #isActualEqualToExpected} but its {@link ValueComparer}
     * fail message is an empty String (the
     * {@link org.dbunit.assertion.Difference} fail message still exists). Use
     * this one mainly for backwards compatibility so the
     * {@link org.dbunit.assertion.Difference} message stays the same.
     *
     * @see IsActualEqualToExpectedWithEmptyFailMessageValueComparer
     */
    public static final ValueComparer isActualEqualToExpectedWithEmptyFailMessage =
            new IsActualEqualToExpectedWithEmptyFailMessageValueComparer();

    /**
     * Ignores milliseconds as not all databases store it in Timestamp.
     *
     * @see IsActualWithinToleranceOfExpectedTimestampValueComparer
     */
    public static final ValueComparer isActualEqualToExpectedTimestampWithIgnoreMillis =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(0,
                    ONE_SECOND_IN_MILLIS);

    /** @see IsActualNotEqualToExpectedValueComparer */
    public static final ValueComparer isActualNotEqualToExpected =
            new IsActualNotEqualToExpectedValueComparer();

    /** @see IsActualGreaterThanExpectedValueComparer */
    public static final ValueComparer isActualGreaterThanExpected =
            new IsActualGreaterThanExpectedValueComparer();

    /** @see IsActualGreaterThanOrEqualToExpectedValueComparer */
    public static final ValueComparer isActualGreaterThanOrEqualToExpected =
            new IsActualGreaterThanOrEqualToExpectedValueComparer();

    /** @see IsActualLessThanOrEqualToExpectedValueComparer */
    public static final ValueComparer isActualLessOrEqualToThanExpected =
            new IsActualLessThanOrEqualToExpectedValueComparer();

    /** @see IsActualLessThanExpectedValueComparer */
    public static final ValueComparer isActualLessThanExpected =
            new IsActualLessThanExpectedValueComparer();

    /** @see IsActualNotNullValueComparer */
    public static final ValueComparer isActualNotNullValueComparer =
            new IsActualNotNullValueComparer();

    /** @see IsActualNullValueComparer */
    public static final ValueComparer isActualNullValueComparer =
            new IsActualNullValueComparer();

    /** @see IsActualWithinToleranceOfExpectedTimestampValueComparer */
    public static final ValueComparer isActualWithinOneSecondNewerOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(0,
                    ONE_SECOND_IN_MILLIS);

    /** @see IsActualWithinToleranceOfExpectedTimestampValueComparer */
    public static final ValueComparer isActualWithinOneSecondOlderOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                    ONE_SECOND_IN_MILLIS, 0);

    /** @see IsActualWithinToleranceOfExpectedTimestampValueComparer */
    public static final ValueComparer isActualWithinOneMinuteNewerOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(0,
                    ONE_MINUTE_IN_MILLIS);

    /** @see IsActualWithinToleranceOfExpectedTimestampValueComparer */
    public static final ValueComparer isActualWithinOneMinuteOlderOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                    ONE_MINUTE_IN_MILLIS, 0);

    /**
     * @see IsActualContainingExpectedStringValueComparer
     * @since 2.7.0
     */
    public static final ValueComparer isActualContainingExpectedStringValueComparer =
            new IsActualContainingExpectedStringValueComparer();

    /** @see NeverFailsValueComparer */
    public static final ValueComparer neverFails =
            new NeverFailsValueComparer();
}
