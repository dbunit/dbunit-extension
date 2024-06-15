package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class NeverFailsValueComparerTest
{
    private final NeverFailsValueComparer sut = new NeverFailsValueComparer();

    @Test
    void testIsExpected_AllNulls_True() throws DatabaseUnitException
    {
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
    void testIsExpected_DifferenceValues_True() throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = null;
        final Object expectedValue = "expected value";
        final Object actualValue = "actual value";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as("Unequal values should have been equal.")
                .isTrue();
    }

    @Test
    void testGetFailPhrase()
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Fail phrase is not empty String"
                + " and must be empty for never fails.").isEmpty();
    }
}
