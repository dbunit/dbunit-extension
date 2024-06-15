package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualNotEqualToExpectedValueComparerTest
{
    private final IsActualNotEqualToExpectedValueComparer sut =
            new IsActualNotEqualToExpectedValueComparer();

    @Test
    void testIsExpected_AllNulls_False() throws DatabaseUnitException
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
        assertThat(actual).as("All null should not have been equal.").isFalse();
    }

    @Test
    void testIsExpected_NotEqualNumbers_True() throws DatabaseUnitException
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
        assertThat(actual).as("Unequal numbers should not have been equal.")
                .isTrue();
    }

    @Test
    void testGetFailPhrase() throws Exception
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull();
    }
}
