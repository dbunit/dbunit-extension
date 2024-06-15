package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualWithinToleranceOfExpectedTimestampValueComparerTest
{
    @Test
    void testIsExpected_AllNull_True() throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 0;
        final long highToleranceValueInMillis = 0;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = null;
        final Object actualValue = null;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as("All null should have been equal.").isTrue();
    }

    @Test
    void testIsExpected_ActualNullExpectedNotNull_False()
            throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 0;
        final long highToleranceValueInMillis = 0;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = "expected string";
        final Object actualValue = null;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as(
                "Actual null, expected not null should not have been equal.")
                .isFalse();
    }

    @Test
    void testIsExpected_WithinToleranceMiddle_True()
            throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 500;
        final long highToleranceValueInMillis = 1500;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);
        final long expectedMillis = 1000;
        final long actualMillis = 2000;

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = new Timestamp(expectedMillis);
        final Object actualValue = new Timestamp(actualMillis);

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as("Within tolerance, should have been equal.")
                .isTrue();
    }

    @Test
    void testIsExpected_WithinToleranceMatchLow_True()
            throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 500;
        final long highToleranceValueInMillis = 1500;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);
        final long expectedMillis = 1000;
        final long actualMillis = expectedMillis + lowToleranceValueInMillis;

        final long diff = Math.abs(expectedMillis - actualMillis);
        assertThat(diff)
                .as("Test setup problem, diff does not match low tolerance")
                .isEqualTo(lowToleranceValueInMillis);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = new Timestamp(expectedMillis);
        final Object actualValue = new Timestamp(actualMillis);

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("Diff matches low tolerance, should have been equal.")
                .isTrue();
    }

    @Test
    void testIsExpected_WithinToleranceMatchHigh_True()
            throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 500;
        final long highToleranceValueInMillis = 1500;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);
        final long expectedMillis = 1000;
        final long actualMillis = expectedMillis + highToleranceValueInMillis;

        final long diff = Math.abs(expectedMillis - actualMillis);
        assertThat(diff)
                .as("Test setup problem, diff does not match high tolerance")
                .isEqualTo(highToleranceValueInMillis);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = new Timestamp(expectedMillis);
        final Object actualValue = new Timestamp(actualMillis);

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("Diff matches high tolerance, should have been equal.")
                .isTrue();
    }

    @Test
    void testIsTolerant_DiffTimeNotInRangeLow_False()
    {
        final long lowToleranceValueInMillis = 200;
        final long highToleranceValueInMillis = 400;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final long diffTime = lowToleranceValueInMillis - 100;
        final boolean actual = sut.isTolerant(diffTime);

        assertThat(actual).as("Diff value is low of tolerant range but passed.")
                .isFalse();
    }

    @Test
    void testIsTolerant_DiffTimeNotInRangeHigh_False()
    {
        final long lowToleranceValueInMillis = 200;
        final long highToleranceValueInMillis = 400;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final long diffTime = highToleranceValueInMillis + 100;
        final boolean actual = sut.isTolerant(diffTime);

        assertThat(actual)
                .as("Diff value is high of tolerant range but passed.")
                .isFalse();
    }

    @Test
    void testIsTolerant_DiffTimeInRange_True()
    {
        final long lowToleranceValueInMillis = 200;
        final long highToleranceValueInMillis = 400;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final long diffTime =
                lowToleranceValueInMillis + highToleranceValueInMillis / 2;
        final boolean actual = sut.isTolerant(diffTime);

        assertThat(actual)
                .as("Diff value is in tolerant range but did not pass.")
                .isTrue();
    }

    @Test
    void testGetFailPhrase() throws Exception
    {
        final long lowToleranceValueInMillis = 500;
        final long highToleranceValueInMillis = 1500;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull();
    }

    @Test
    void testStringExpectedTimestampActual() throws DatabaseUnitException
    {
        final long lowToleranceValueInMillis = 500;
        final long highToleranceValueInMillis = 1500;
        final IsActualWithinToleranceOfExpectedTimestampValueComparer sut =
                new IsActualWithinToleranceOfExpectedTimestampValueComparer(
                        lowToleranceValueInMillis, highToleranceValueInMillis);

        final long expectedMillis = 1000;
        final long actualMillis = expectedMillis + highToleranceValueInMillis;

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.TIMESTAMP;
        final Object expectedValue = new Timestamp(expectedMillis).toString();
        final Object actualValue = new Timestamp(actualMillis);

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as("Should have been equal.").isTrue();
    }
}
