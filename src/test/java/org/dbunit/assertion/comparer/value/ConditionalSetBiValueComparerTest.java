package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class ConditionalSetBiValueComparerTest
{
    private ValueFactory<Long> valueFactory1 = new ValueFactory<Long>()
    {
        @Override
        public Long make(final ITable table, final int rowNum)
                throws DataSetException
        {
            return 1L;
        }
    };

    private Set<Long> values1 = new HashSet<>(Arrays.asList(1L));
    private Set<Long> values2 = new HashSet<>(Arrays.asList(2L));

    @Test
    void testDoCompare_InValuesAllNulls_NullFailMessage()
            throws DatabaseUnitException
    {
        final ValueFactory<Long> actualValueFactory = valueFactory1;
        final Set<Long> values = values1;
        final ValueComparer inValuesValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer notInValuesValueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final ConditionalSetBiValueComparer<Long> sut =
                new ConditionalSetBiValueComparer<Long>(actualValueFactory,
                        values, inValuesValueComparer,
                        notInValuesValueComparer);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = null;
        final Object actualValue = null;

        final String actual = sut.doCompare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as("All null should have been no fail message.")
                .isNull();
    }

    @Test
    void testDoCompare_InValuesActualEqualToExpected_NullFailMessage()
            throws DatabaseUnitException
    {
        final ValueFactory<Long> actualValueFactory = valueFactory1;
        final Set<Long> values = values1;
        final ValueComparer inValuesValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer notInValuesValueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final ConditionalSetBiValueComparer<Long> sut =
                new ConditionalSetBiValueComparer<Long>(actualValueFactory,
                        values, inValuesValueComparer,
                        notInValuesValueComparer);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 4;

        final String actual = sut.doCompare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as(
                "Actual is equal to expected, should have been no fail message.")
                .isNull();
    }

    @Test
    void testDoCompare_InValuesActualGreaterThanExpected_FailMessage()
            throws DatabaseUnitException
    {
        final ValueFactory<Long> actualValueFactory = valueFactory1;
        final Set<Long> values = values1;
        final ValueComparer inValuesValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer notInValuesValueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final ConditionalSetBiValueComparer<Long> sut =
                new ConditionalSetBiValueComparer<Long>(actualValueFactory,
                        values, inValuesValueComparer,
                        notInValuesValueComparer);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 8;

        final String actual = sut.doCompare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as(
                "Actual is greater than expected, should have been a fail message.")
                .isNotNull();
    }

    @Test
    void testDoCompare_InValuesActualLessThanExpected_FailMessage()
            throws DatabaseUnitException
    {
        final ValueFactory<Long> actualValueFactory = valueFactory1;
        final Set<Long> values = values1;
        final ValueComparer inValuesValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer notInValuesValueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final ConditionalSetBiValueComparer<Long> sut =
                new ConditionalSetBiValueComparer<Long>(actualValueFactory,
                        values, inValuesValueComparer,
                        notInValuesValueComparer);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 2;

        final String actual = sut.doCompare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as(
                "Actual is greater than expected, should have been a fail message.")
                .isNotNull();
    }

    @Test
    void testDoCompare_NotInValuesActualEqualToExpected_FailMessage()
            throws DatabaseUnitException
    {
        final ValueFactory<Long> actualValueFactory = valueFactory1;
        final Set<Long> values = values2;
        final ValueComparer inValuesValueComparer =
                ValueComparers.isActualEqualToExpected;
        final ValueComparer notInValuesValueComparer =
                ValueComparers.isActualNotEqualToExpected;
        final ConditionalSetBiValueComparer<Long> sut =
                new ConditionalSetBiValueComparer<Long>(actualValueFactory,
                        values, inValuesValueComparer,
                        notInValuesValueComparer);

        final ITable expectedTable = null;
        final ITable actualTable = null;
        final int rowNum = 0;
        final String columnName = null;
        final DataType dataType = DataType.BIGINT;
        final Object expectedValue = 4;
        final Object actualValue = 4;

        final String actual = sut.doCompare(expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue);
        assertThat(actual).as(
                "Actual is equal to expected, should have been fail message.")
                .isNotNull();
    }
}
