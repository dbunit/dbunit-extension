package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualGreaterThanOrEqualToExpectedValueComparerTest
{
    private final IsActualGreaterThanOrEqualToExpectedValueComparer sut =
            new IsActualGreaterThanOrEqualToExpectedValueComparer();

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
    void testIsExpected_ActualGreaterThanExpected_True()
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
    void testGetFailPhrase() throws Exception
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull()
                .contains("not greater than or equal to");
    }
}
