package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    private boolean compare(final Object expectedValue, final Object actualValue)
            throws DatabaseUnitException
    {
        return sut.isExpected(expectedTable, actualTable, rowNum, columnName, dataType,
                expectedValue, actualValue);
    }

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
    void testIsExpected_ActualIsUtf8BytesOfTheSameDocument_True() throws DatabaseUnitException
    {
        final byte[] actualValue = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);

        final boolean actual = compare("{\"a\":1,\"b\":2}", actualValue);

        assertThat(actual).as("A byte[] value is the UTF-8 text of the document.").isTrue();
    }

    @Test
    void testIsExpected_ActualIsUtf8BytesOfAReorderedDocument_True() throws DatabaseUnitException
    {
        final byte[] actualValue = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);

        final boolean actual = compare("{\"b\": 2, \"a\": 1}", actualValue);

        assertThat(actual).as("A byte[] value compares as the document, whatever its member order.")
                .isTrue();
    }

    @Test
    void testIsExpected_ActualIsUtf8BytesOfADifferentDocument_False() throws DatabaseUnitException
    {
        final byte[] actualValue = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        final boolean actual = compare("{\"a\":2}", actualValue);

        assertThat(actual).as("A byte[] value of another document differs.").isFalse();
    }

    @Test
    void testIsExpected_ActualIsUtf8BytesWithNonAsciiText_True() throws DatabaseUnitException
    {
        final byte[] actualValue = "{\"a\":\"\u00e4\u20ac\"}".getBytes(StandardCharsets.UTF_8);

        final boolean actual = compare("{\"a\":\"\u00e4\u20ac\"}", actualValue);

        assertThat(actual).as("The bytes are decoded as UTF-8.").isTrue();
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
    void testIsExpected_StringValuePastJacksonsDefaultLimit_True()
            throws DatabaseUnitException
    {
        final Object expectedValue = jsonWithStringValueOfLength(20_000_001, 'x');
        final Object actualValue = jsonWithStringValueOfLength(20_000_001, 'x');

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("JSON with a string value of 20,000,001 characters, one more than the limit"
                        + " that Jackson 2.15 and later set by default, should have been equal"
                        + " to the same JSON.")
                .isTrue();
    }

    @Test
    void testIsExpected_DifferentStringValuesPastJacksonsDefaultLimit_False()
            throws DatabaseUnitException
    {
        final Object expectedValue = jsonWithStringValueOfLength(20_000_001, 'x');
        final Object actualValue = jsonWithStringValueOfLength(20_000_001, 'y');

        final boolean actual = sut.isExpected(expectedTable, actualTable,
                rowNum, columnName, dataType, expectedValue, actualValue);

        assertThat(actual)
                .as("JSON with a different string value of more than 20,000,000 characters should"
                        + " not have been equal.")
                .isFalse();
    }

    private static String jsonWithStringValueOfLength(final int length, final char character)
    {
        final char[] characters = new char[length];
        Arrays.fill(characters, character);
        return "{\"a\":\"" + new String(characters) + "\"}";
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
    void testIsExpected_actualHasTextAfterTheValue_throwsNamingTheColumnAndRow()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("Text after the first JSON value is not a valid value, and the comparer must"
                        + " not stop reading at the first value and call it equal.")
                .isThrownBy(() -> compare("{\"a\":1}", "{\"a\":1} xyz"))
                .withMessageContaining("actual")
                .withMessageContaining(columnName)
                .withMessageContaining(String.valueOf(rowNum))
                .withMessageContaining("{\"a\":1} xyz");
    }

    @Test
    void testIsExpected_actualHasTwoDocuments_throwsNamingTheColumnAndRow()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("A second document after the first is content after the value.")
                .isThrownBy(() -> compare("{\"a\":1}", "{\"a\":1}{\"a\":2}"))
                .withMessageContaining("actual")
                .withMessageContaining(columnName);
    }

    @Test
    void testIsExpected_actualArrayHasAnotherArrayAfterIt_throwsNamingTheColumnAndRow()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("A second array after the first is content after the value.")
                .isThrownBy(() -> compare("[1]", "[1] [2]"))
                .withMessageContaining("actual")
                .withMessageContaining(columnName);
    }

    @Test
    void testIsExpected_expectedHasTextAfterTheValue_throwsNamingTheColumnAndRow()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("The expected value goes through the same reader as the actual one.")
                .isThrownBy(() -> compare("{\"a\":1} xyz", "{\"a\":1}"))
                .withMessageContaining("expected")
                .withMessageContaining(columnName);
    }

    @Test
    void testIsExpected_actualHasWhitespaceAfterTheValue_true() throws DatabaseUnitException
    {
        final boolean actual = compare("{\"a\":1}", "{\"a\":1}  \r\n");

        assertThat(actual).as("Whitespace after the value is no content.").isTrue();
    }

    @Test
    void testIsExpected_actualValueIsCutOff_throwsNamingTheColumnAndRow()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("A value that ends inside a document is not valid JSON.")
                .isThrownBy(() -> compare("{\"a\":[1,2]}", "{\"a\":[1,2]"))
                .withMessageContaining("actual")
                .withMessageContaining(columnName);
    }

    @Test
    void testIsExpected_numbersDifferPastWhatADoubleHolds_false() throws DatabaseUnitException
    {
        final boolean actual = compare("{\"n\":0.1}", "{\"n\":0.10000000000000000001}");

        assertThat(actual)
                .as("A number is compared exactly, and not as a double that drops the digits"
                        + " past about 17 significant ones.")
                .isFalse();
    }

    @Test
    void testIsExpected_fractionsDifferInTheLastDigitOfMany_false() throws DatabaseUnitException
    {
        final boolean actual =
                compare("{\"n\":1.00000000000000001}", "{\"n\":1.00000000000000002}");

        assertThat(actual).as("Every digit of a number counts.").isFalse();
    }

    @Test
    void testIsExpected_sameFractionWithTrailingZero_true() throws DatabaseUnitException
    {
        final boolean actual = compare("{\"n\":1.5}", "{\"n\":1.50}");

        assertThat(actual)
                .as("A database may write 1.5 as 1.50, and the two are the same number.")
                .isTrue();
    }

    @Test
    void testIsExpected_zeroWrittenWithDifferentScale_true() throws DatabaseUnitException
    {
        final boolean actual = compare("[0.0]", "[0.00]");

        assertThat(actual).as("0.0 and 0.00 are the same number.").isTrue();
    }

    @Test
    void testIsExpected_wholeNumberAgainstNumberWithFraction_false() throws DatabaseUnitException
    {
        final boolean actual = compare("{\"n\":1}", "{\"n\":1.0}");

        assertThat(actual)
                .as("A whole number is not the same as a number with a fraction, as the text"
                        + " of the two differs, and the documentation says so.")
                .isFalse();
    }

    @Test
    void testIsExpected_nullMemberAgainstMissingMember_false() throws DatabaseUnitException
    {
        final boolean actual = compare("{\"a\":null}", "{}");

        assertThat(actual)
                .as("A member with the value null is not the same as a missing member, and the"
                        + " documentation says so.")
                .isFalse();
    }

    @Test
    void testGetFailPhrase_DefaultComparer_ReturnsNonNullPhrase()
            throws Exception
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have fail phrase.").isNotNull();
    }
}
