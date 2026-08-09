package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class IsActualEqualToExpectedJsonValueComparerTest
{
    final IsActualEqualToExpectedJsonValueComparer sut =
            new IsActualEqualToExpectedJsonValueComparer();

    private final ITable expectedTable = null;
    private final ITable actualTable = null;
    private final int rowNum = 3;
    private final String columnName = "MY_JSON_COLUMN";
    private final DataType dataType = DataType.LONGVARCHAR;

    @Test
    void testIsExpected_AllNull_True() throws DatabaseUnitException
    {
        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, null, null);

        assertThat(actual).as("All null should have been equal.").isTrue();
    }

    @Test
    void testIsExpected_ActualNullExpectedNotNull_False()
            throws DatabaseUnitException
    {
        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, "{\"a\":1}", null);

        assertThat(actual).as(
                "Actual null, expected not null should not have been equal.")
                .isFalse();
    }

    @Test
    void testIsExpected_ActualNotNullExpectedNull_False()
            throws DatabaseUnitException
    {
        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, null, "{\"a\":1}");

        assertThat(actual).as(
                "Actual not null, expected null, should not have been equal.")
                .isFalse();
    }

    @Test
    void testIsExpected_IdenticalText_True() throws DatabaseUnitException
    {
        final Object expectedValue = "{\"a\":1,\"b\":2}";
        final Object actualValue = "{\"a\":1,\"b\":2}";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as("Identical JSON text should have been equal.")
                .isTrue();
    }

    @Test
    void testIsExpected_DifferentInsignificantWhitespace_True()
            throws DatabaseUnitException
    {
        final Object expectedValue = "{\"a\":1,\"b\":2}";
        final Object actualValue = "{ \"a\" : 1, \"b\" : 2 }";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as(
                "JSON differing only in insignificant whitespace should have been equal, "
                        + "matching how MySQL/PostgreSQL/H2 reformat stored JSON.")
                .isTrue();
    }

    @Test
    void testIsExpected_DifferentObjectKeyOrder_True()
            throws DatabaseUnitException
    {
        final Object expectedValue = "{\"a\":1,\"b\":2}";
        final Object actualValue = "{\"b\":2,\"a\":1}";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as(
                "JSON objects differing only in member order should have been equal, "
                        + "matching how MySQL sorts object keys on storage.")
                .isTrue();
    }

    @Test
    void testIsExpected_EquivalentNestedObjectsAndArrays_True()
            throws DatabaseUnitException
    {
        final Object expectedValue =
                "{\"name\":\"a\",\"tags\":[\"x\",\"y\"],\"meta\":{\"n\":1,\"m\":2}}";
        final Object actualValue =
                "{\"meta\":{\"m\":2,\"n\":1},\"tags\":[\"x\",\"y\"],\"name\":\"a\"}";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as(
                "Nested JSON objects/arrays equivalent apart from object member order "
                        + "should have been equal.").isTrue();
    }

    @Test
    void testIsExpected_DifferentArrayElementOrder_False()
            throws DatabaseUnitException
    {
        final Object expectedValue = "[1,2,3]";
        final Object actualValue = "[3,2,1]";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual).as(
                "JSON arrays differing in element order should not have been equal: "
                        + "array order is significant.").isFalse();
    }

    @Test
    void testIsExpected_DifferentValues_False() throws DatabaseUnitException
    {
        final Object expectedValue = "{\"a\":1}";
        final Object actualValue = "{\"a\":2}";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("JSON with a different value for the same key should not have been equal.")
                .isFalse();
    }

    @Test
    void testIsExpected_MissingKey_False() throws DatabaseUnitException
    {
        final Object expectedValue = "{\"a\":1,\"b\":2}";
        final Object actualValue = "{\"a\":1}";

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("JSON missing a key present in the other should not have been equal.")
                .isFalse();
    }

    @Test
    void testIsExpected_ExpectedNotValidJson_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "not json";
        final Object actualValue = "{\"a\":1}";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Unparseable expected value should have thrown.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue))
                .withMessageContaining(columnName)
                .withMessageContaining(String.valueOf(rowNum));
    }

    @Test
    void testIsExpected_ActualNotValidJson_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "{\"a\":1}";
        final Object actualValue = "not json";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Unparseable actual value should have thrown.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue))
                .withMessageContaining(columnName)
                .withMessageContaining(String.valueOf(rowNum));
    }

    @Test
    void testIsExpected_ExpectedEmptyString_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "";
        final Object actualValue = "{\"a\":1}";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Empty-string expected value should have thrown "
                        + "DatabaseUnitException instead of NullPointerException, "
                        + "since Jackson returns null (not an exception) for empty input.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue));
    }

    @Test
    void testIsExpected_ActualEmptyString_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "{\"a\":1}";
        final Object actualValue = "";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Empty-string actual value should have thrown "
                        + "DatabaseUnitException instead of NullPointerException, "
                        + "since Jackson returns null (not an exception) for empty input.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue));
    }

    @Test
    void testIsExpected_ExpectedWhitespaceOnly_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "   ";
        final Object actualValue = "{\"a\":1}";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Whitespace-only expected value should have thrown "
                        + "DatabaseUnitException instead of NullPointerException, "
                        + "since Jackson returns null (not an exception) for "
                        + "whitespace-only input.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue));
    }

    @Test
    void testIsExpected_ActualWhitespaceOnly_ThrowsDatabaseUnitException()
    {
        final Object expectedValue = "{\"a\":1}";
        final Object actualValue = "   ";

        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Whitespace-only actual value should have thrown "
                        + "DatabaseUnitException instead of NullPointerException, "
                        + "since Jackson returns null (not an exception) for "
                        + "whitespace-only input.")
                .isThrownBy(() -> sut.isExpected(expectedTable, actualTable,
                        rowNum, columnName, dataType, expectedValue,
                        actualValue));
    }

    @Test
    void testGetFailPhrase_DefaultComparer_ReturnsNonNullPhrase()
            throws Exception
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull();
    }
}
