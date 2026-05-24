package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualGreaterThanOrEqualToExpectedWithIgnoreMillisValueComparerTest
{
    private static final LocalDateTime NOW = LocalDateTime.now();

    private static final Timestamp TS_NOW = Timestamp.valueOf(NOW);
    private static final Timestamp TS_TOMORROW =
            Timestamp.valueOf(NOW.plusDays(1L));

    private final IsActualGreaterThanOrEqualToExpectedWithIgnoreMillisValueComparer sut =
            new IsActualGreaterThanOrEqualToExpectedWithIgnoreMillisValueComparer();

    @Test
    void testIsExpected_AllNulls_True() throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.TIMESTAMP;
        final Object expectedValue = null;
        final Object actualValue = null;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as("All null should have been true.").isTrue();
    }

    @Test
    void testIsExpected_ActualEqualToExpected_True()
            throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.TIMESTAMP;
        final Object expectedValue = TS_NOW;
        final Object actualValue = TS_NOW;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual)
                .as("Actual is equal to expected, should have been true.")
                .isTrue();
    }

    @Test
    void testIsExpected_ActualGreaterThanExpected_True()
            throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.TIMESTAMP;
        final Object expectedValue = TS_NOW;
        final Object actualValue = TS_TOMORROW;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual)
                .as("Actual is greater than expected, should have been true.")
                .isTrue();
    }

    @Test
    void testIsExpected_ActualLessThanExpected_False()
            throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.TIMESTAMP;
        final Object expectedValue = TS_TOMORROW;
        final Object actualValue = TS_NOW;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual)
                .as("Actual is less than expected, should not have been true.")
                .isFalse();
    }

    @Test
    void testGetFailPhrase_returnsNonNullPhrase() throws Exception
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull()
                .contains("not greater than or equal to");
    }
}
