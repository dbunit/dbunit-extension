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
    /**
     * Default constructor.
     */
    protected ValueComparers()
    {
    }

    /**
     * Compares actual and expected values for equality.
     *
     * @see IsActualEqualToExpectedValueComparer
     */
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

    /**
     * Compares actual and expected values for inequality.
     *
     * @see IsActualNotEqualToExpectedValueComparer
     */
    public static final ValueComparer isActualNotEqualToExpected =
            new IsActualNotEqualToExpectedValueComparer();

    /**
     * Checks whether the actual value is greater than the expected value.
     *
     * @see IsActualGreaterThanExpectedValueComparer
     */
    public static final ValueComparer isActualGreaterThanExpected =
            new IsActualGreaterThanExpectedValueComparer();

    /**
     * Checks whether the actual value is greater than or equal to the expected value.
     *
     * @see IsActualGreaterThanOrEqualToExpectedValueComparer
     */
    public static final ValueComparer isActualGreaterThanOrEqualToExpected =
            new IsActualGreaterThanOrEqualToExpectedValueComparer();

    /**
     * Checks whether the actual value is less than or equal to the expected value.
     *
     * @see IsActualLessThanOrEqualToExpectedValueComparer
     */
    public static final ValueComparer isActualLessOrEqualToThanExpected =
            new IsActualLessThanOrEqualToExpectedValueComparer();

    /**
     * Checks whether the actual value is less than the expected value.
     *
     * @see IsActualLessThanExpectedValueComparer
     */
    public static final ValueComparer isActualLessThanExpected =
            new IsActualLessThanExpectedValueComparer();

    /**
     * Checks whether the actual value is not null.
     *
     * @see IsActualNotNullValueComparer
     */
    public static final ValueComparer isActualNotNullValueComparer =
            new IsActualNotNullValueComparer();

    /**
     * Checks whether the actual value is null.
     *
     * @see IsActualNullValueComparer
     */
    public static final ValueComparer isActualNullValueComparer =
            new IsActualNullValueComparer();

    /**
     * Checks whether the actual timestamp is up to one second newer than the expected timestamp.
     *
     * @see IsActualWithinToleranceOfExpectedTimestampValueComparer
     */
    public static final ValueComparer isActualWithinOneSecondNewerOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(0,
                    ONE_SECOND_IN_MILLIS);

    /**
     * Checks whether the actual timestamp is up to one second older than the expected timestamp.
     *
     * @see IsActualWithinToleranceOfExpectedTimestampValueComparer
     */
    public static final ValueComparer isActualWithinOneSecondOlderOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                    ONE_SECOND_IN_MILLIS, 0);

    /**
     * Checks whether the actual timestamp is up to one minute newer than the expected timestamp.
     *
     * @see IsActualWithinToleranceOfExpectedTimestampValueComparer
     */
    public static final ValueComparer isActualWithinOneMinuteNewerOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(0,
                    ONE_MINUTE_IN_MILLIS);

    /**
     * Checks whether the actual timestamp is up to one minute older than the expected timestamp.
     *
     * @see IsActualWithinToleranceOfExpectedTimestampValueComparer
     */
    public static final ValueComparer isActualWithinOneMinuteOlderOfExpectedTimestamp =
            new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                    ONE_MINUTE_IN_MILLIS, 0);

    /**
     * Checks whether the actual value contains the expected string.
     *
     * @see IsActualContainingExpectedStringValueComparer
     * @since 2.7.0
     */
    public static final ValueComparer isActualContainingExpectedStringValueComparer =
            new IsActualContainingExpectedStringValueComparer();

    /**
     * Verifies nothing and never fails.
     *
     * @see NeverFailsValueComparer
     */
    public static final ValueComparer neverFails =
            new NeverFailsValueComparer();
}
