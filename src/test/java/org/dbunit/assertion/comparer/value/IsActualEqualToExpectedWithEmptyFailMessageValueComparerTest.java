package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualEqualToExpectedWithEmptyFailMessageValueComparerTest
{
    private final IsActualEqualToExpectedWithEmptyFailMessageValueComparer sut =
            new IsActualEqualToExpectedWithEmptyFailMessageValueComparer();

    @Test
    void testIsExpected_AllNulls_True() throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
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
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 4;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual)
                .as("Actual is equal to expected, should have been true.")
                .isTrue();
    }

    @Test
    void testIsExpected_ActualGreaterThanExpected_False()
            throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 8;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as(
                "Actual is greater than expected, should not have been true.")
                .isFalse();
    }

    @Test
    void testIsExpected_ActualLessThanExpected_False()
            throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 2;

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual)
                .as("Actual is less than expected, should not have been true.")
                .isFalse();
    }

    @Test
    void testGetFailPhrase()
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual)
                .as("Fail phrase is not null"
                        + " and must be null for backwards compatability.")
                .isNull();
    }

    @Test
    void testMakeFailMessage() throws Exception
    {
        final Object expectedValue = null;
        final Object actualValue = null;
        final String actual = sut.makeFailMessage(expectedValue, actualValue);

        assertThat(actual)
                .as("Fail phrase is not empty String"
                        + " and must be empty for backwards compatability.")
                .isEmpty();
    }
}
