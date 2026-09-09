package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.regex.PatternSyntaxException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;

class RegularExpressionValueComparerTest
{
    private final RegularExpressionValueComparer sut =
            new RegularExpressionValueComparer();

    private final ITable expectedTable = null;
    private final ITable actualTable = null;
    private final int rowNum = 5;
    private final String columnName = "MY_COLUMN";
    private final DataType dataType = DataType.VARCHAR;

    private boolean isExpected(final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        return sut.isExpected(expectedTable, actualTable, rowNum, columnName,
                dataType, expectedValue, actualValue);
    }

    @Test
    void testIsExpected_AllNull_True() throws DatabaseUnitException
    {
        final boolean actual = isExpected(null, null);

        assertThat(actual).as("Both values null should have matched.").isTrue();
    }

    @Test
    void testIsExpected_ActualNullExpectedNotNull_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", null);

        assertThat(actual)
                .as("Null actual value should not match a non-null pattern.")
                .isFalse();
    }

    @Test
    void testIsExpected_ActualNotNullExpectedNull_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected(null, "123");

        assertThat(actual)
                .as("Non-null actual value should not match a null pattern.")
                .isFalse();
    }

    @Test
    void testIsExpected_LiteralPatternEqualsWholeActualValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("abc", "abc");

        assertThat(actual)
                .as("Literal pattern equal to the whole actual value should have matched.")
                .isTrue();
    }

    @Test
    void testIsExpected_DigitPatternMatchesAllDigitActualValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", "12345");

        assertThat(actual)
                .as("Digit pattern should have matched an all-digit actual value.")
                .isTrue();
    }

    @Test
    void testIsExpected_DigitPatternWithNonDigitInActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", "12a45");

        assertThat(actual).as(
                "Digit pattern should not have matched an actual value containing a letter.")
                .isFalse();
    }

    @Test
    void testIsExpected_DigitPatternMatchesOnlyLeadingDigitsOfActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", "123abc");

        assertThat(actual).as(
                "Match is anchored to the whole value, so leading-only digits should not have matched.")
                .isFalse();
    }

    @Test
    void testIsExpected_DigitPatternMatchesOnlyMiddleOfActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", "abc123def");

        assertThat(actual).as(
                "Match is anchored to the whole value, so a matching substring should not have matched.")
                .isFalse();
    }

    @Test
    void testIsExpected_WildcardWrappedPatternMatchesSubstringWithinWholeValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected(".*\\d{4}.*", "order-2026-xyz");

        assertThat(actual).as(
                "Wrapping the pattern in .* should let it match a substring within the whole value.")
                .isTrue();
    }

    @Test
    void testIsExpected_CharacterClassAndQuantifierFormatPattern_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("[A-Z]{3}-\\d{4}", "ABC-1234");

        assertThat(actual)
                .as("Format pattern should have matched a correctly shaped actual value.")
                .isTrue();
    }

    @Test
    void testIsExpected_FormatPatternWithWrongShapeActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("[A-Z]{3}-\\d{4}", "AB-1234");

        assertThat(actual)
                .as("Format pattern should not have matched a wrongly shaped actual value.")
                .isFalse();
    }

    @Test
    void testIsExpected_UuidPatternMatchesUuidActualValue_True()
            throws DatabaseUnitException
    {
        final String uuidPattern =
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

        final boolean actual =
                isExpected(uuidPattern, "3f2504e0-4f89-41d3-9a0c-0305e82c3301");

        assertThat(actual)
                .as("UUID pattern should have matched a UUID-shaped actual value.")
                .isTrue();
    }

    @Test
    void testIsExpected_AlternationPatternMatchesOneAlternative_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("cat|dog", "dog");

        assertThat(actual)
                .as("Alternation pattern should have matched one of its alternatives.")
                .isTrue();
    }

    @Test
    void testIsExpected_AlternationPatternMatchesNoAlternative_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("cat|dog", "bird");

        assertThat(actual).as(
                "Alternation pattern should not have matched a value that is none of its alternatives.")
                .isFalse();
    }

    @Test
    void testIsExpected_PatternIsCaseSensitiveByDefault_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("abc", "ABC");

        assertThat(actual)
                .as("Matching should be case-sensitive unless the pattern opts out.")
                .isFalse();
    }

    @Test
    void testIsExpected_PatternWithInlineCaseInsensitiveFlag_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("(?i)abc", "ABC");

        assertThat(actual)
                .as("The inline (?i) flag should make matching case-insensitive.")
                .isTrue();
    }

    @Test
    void testIsExpected_PatternWithInlineDotallFlagMatchesNewline_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("(?s)a.b", "a\nb");

        assertThat(actual)
                .as("The inline (?s) flag should let a dot match a newline.")
                .isTrue();
    }

    @Test
    void testIsExpected_DotWithoutDotallFlagDoesNotMatchNewline_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("a.b", "a\nb");

        assertThat(actual)
                .as("Without the (?s) flag a dot should not match a newline.")
                .isFalse();
    }

    @Test
    void testIsExpected_WildcardWrappedPatternDoesNotCrossNewlineInActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected(".*4071.*", "top-line\n4071");

        assertThat(actual).as(
                "The documented .* wrapping should not reach across a newline without the (?s) flag.")
                .isFalse();
    }

    @Test
    void testIsExpected_DotallWildcardWrappedPatternCrossesNewlineInActualValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("(?s).*4071.*", "top-line\n4071");

        assertThat(actual).as(
                "Prefixing the .* wrapping with (?s) should reach across a newline, as documented.")
                .isTrue();
    }

    @Test
    void testIsExpected_EscapedDotMatchesLiteralDot_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("a\\.b", "a.b");

        assertThat(actual)
                .as("An escaped dot in the pattern should match a literal dot.")
                .isTrue();
    }

    @Test
    void testIsExpected_EscapedDotDoesNotMatchOtherCharacter_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("a\\.b", "axb");

        assertThat(actual).as(
                "An escaped dot in the pattern should not match a non-dot character.")
                .isFalse();
    }

    @Test
    void testIsExpected_ExplicitlyAnchoredPattern_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("^\\d+$", "123");

        assertThat(actual).as(
                "Redundant explicit anchors should not prevent a whole-value match.")
                .isTrue();
    }

    @Test
    void testIsExpected_EmptyPatternMatchesEmptyActualValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("", "");

        assertThat(actual)
                .as("An empty pattern should match an empty actual value.")
                .isTrue();
    }

    @Test
    void testIsExpected_EmptyPatternWithNonEmptyActualValue_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("", "x");

        assertThat(actual)
                .as("An empty pattern should not match a non-empty actual value.")
                .isFalse();
    }

    @Test
    void testIsExpected_StarQuantifierPatternMatchesEmptyActualValue_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("a*", "");

        assertThat(actual).as(
                "A pattern that can match zero characters should match an empty actual value.")
                .isTrue();
    }

    @Test
    void testIsExpected_NumericActualValueMatchesDigitPattern_True()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("\\d+", Integer.valueOf(123));

        assertThat(actual).as(
                "A non-string actual value should be converted to a string before matching.")
                .isTrue();
    }

    @Test
    void testIsExpected_NumericActualValueDoesNotMatchLetterPattern_False()
            throws DatabaseUnitException
    {
        final boolean actual = isExpected("[A-Za-z]+", Integer.valueOf(123));

        assertThat(actual).as(
                "A numeric actual value converted to a string should not match a letters-only pattern.")
                .isFalse();
    }

    @Test
    void testIsExpected_ExpectedRegexInvalid_ThrowsDatabaseUnitExceptionWithRowAndColumn()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("An invalid regular expression should have thrown DatabaseUnitException.")
                .isThrownBy(() -> isExpected("[", "abc"))
                .withMessageContaining(columnName)
                .withMessageContaining(String.valueOf(rowNum));
    }

    @Test
    void testIsExpected_ExpectedRegexInvalid_ExceptionCauseIsPatternSyntaxException()
    {
        assertThatExceptionOfType(DatabaseUnitException.class)
                .as("The thrown exception should wrap the underlying PatternSyntaxException.")
                .isThrownBy(() -> isExpected("a(", "abc"))
                .withCauseInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void testCompare_ActualValueMatchesPattern_ReturnsNull()
            throws DatabaseUnitException
    {
        final String actual = sut.compare(expectedTable, actualTable, rowNum,
                columnName, dataType, "\\d+", "42");

        assertThat(actual).as("A matching value should produce no fail message.")
                .isNull();
    }

    @Test
    void testCompare_ActualValueDoesNotMatchPattern_ReturnsFailMessageWithValuesAndPhrase()
            throws DatabaseUnitException
    {
        final String actual = sut.compare(expectedTable, actualTable, rowNum,
                columnName, dataType, "\\d+", "x");

        assertThat(actual)
                .as("A non-matching value should produce a fail message naming both values and the fail phrase.")
                .contains("x").contains("\\d+")
                .contains("not matching the regular expression");
    }

    @Test
    void testCompare_BothNull_ReturnsNull() throws DatabaseUnitException
    {
        final String actual = sut.compare(expectedTable, actualTable, rowNum,
                columnName, dataType, null, null);

        assertThat(actual).as("Both values null should produce no fail message.")
                .isNull();
    }

    @Test
    void testGetFailPhrase_ReturnsNonNullPhrase()
    {
        final String actual = sut.getFailPhrase();

        assertThat(actual).as("Should have a fail phrase.").isNotNull();
    }
}
