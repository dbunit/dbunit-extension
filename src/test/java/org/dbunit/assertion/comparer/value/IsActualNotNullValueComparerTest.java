package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualNotNullValueComparerTest
{
    private IsActualNotNullValueComparer sut =
            new IsActualNotNullValueComparer();

    @Test
    void testIsExpected_ActualNull_False() throws DatabaseUnitException
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
        assertThat(actual).as("Actual null should have been false.").isFalse();
    }

    @Test
    void testIsExpected_ActualNotNull_True() throws DatabaseUnitException
    {
        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = null;
        final Object actualValue = "not null string";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as("Actual not null should have been true.")
                .isTrue();
    }

    @Test
    void testMakeFailMessage() throws Exception
    {
        final String actual = sut.makeFailMessage();

        assertThat(actual).as("Should have fail phrase.").isNotNull();
    }
}
