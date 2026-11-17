package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ValueComparerTest
{
    static Stream<Arguments> comparersAndWhetherTheirExpectedValuesOrderTheRows()
    {
        return Stream.of(
                Arguments.of("isActualEqualToExpected",
                        ValueComparers.isActualEqualToExpected, true),
                Arguments.of("isActualEqualToExpectedWithEmptyFailMessage",
                        ValueComparers.isActualEqualToExpectedWithEmptyFailMessage,
                        true),
                Arguments.of("isActualEqualToExpectedTimestampWithIgnoreMillis",
                        ValueComparers.isActualEqualToExpectedTimestampWithIgnoreMillis,
                        true),
                Arguments.of("isActualGreaterThanExpected",
                        ValueComparers.isActualGreaterThanExpected, true),
                Arguments.of("isActualGreaterThanOrEqualToExpected",
                        ValueComparers.isActualGreaterThanOrEqualToExpected, true),
                Arguments.of("isActualGreaterThanOrEqualToExpectedWithIgnoreMillis",
                        new IsActualGreaterThanOrEqualToExpectedWithIgnoreMillisValueComparer(),
                        true),
                Arguments.of("isActualLessOrEqualToThanExpected",
                        ValueComparers.isActualLessOrEqualToThanExpected, true),
                Arguments.of("isActualLessThanExpected",
                        ValueComparers.isActualLessThanExpected, true),
                Arguments.of("isActualWithinOneMinuteNewerOfExpectedTimestamp",
                        ValueComparers.isActualWithinOneMinuteNewerOfExpectedTimestamp,
                        true),
                Arguments.of("isActualNotEqualToExpected",
                        ValueComparers.isActualNotEqualToExpected, false),
                Arguments.of("isActualNotNullValueComparer",
                        ValueComparers.isActualNotNullValueComparer, false),
                Arguments.of("isActualNullValueComparer",
                        ValueComparers.isActualNullValueComparer, false),
                Arguments.of("neverFails", ValueComparers.neverFails, false),
                Arguments.of("regularExpressionValueComparer",
                        ValueComparers.regularExpressionValueComparer, false),
                Arguments.of("isActualContainingExpectedStringValueComparer",
                        ValueComparers.isActualContainingExpectedStringValueComparer,
                        false),
                Arguments.of("IsActualEqualToExpectedJsonValueComparer",
                        new IsActualEqualToExpectedJsonValueComparer(), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comparersAndWhetherTheirExpectedValuesOrderTheRows")
    void testIsSortableByExpectedValue_dbUnitComparer_saysWhetherItsExpectedValuesOrderTheRows(
            final String comparerName, final ValueComparer valueComparer,
            final boolean expected)
    {
        final boolean actual = valueComparer.isSortableByExpectedValue();

        assertThat(actual).as("A comparer that compares by equality, by order or by"
                + " a tolerance can order the rows by its expected values; one whose"
                + " expected value is a pattern, a part of the value, or ignored cannot.")
                .isEqualTo(expected);
    }

    @Test
    void testIsSortableByExpectedValue_comparerOfItsOwn_returnsTrue()
    {
        final ValueComparer valueComparer = (expectedTable, actualTable, rowNum,
                columnName, dataType, expectedValue, actualValue) -> null;

        final boolean actual = valueComparer.isSortableByExpectedValue();

        assertThat(actual).as("A comparer that does not say otherwise keeps its column"
                + " in its place in the sort.").isTrue();
    }
}
