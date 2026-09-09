package org.dbunit.assertion.comparer.value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ValueComparer} implementation that verifies the actual value matches
 * the regular expression supplied as the expected value.
 *
 * <p>
 * The expected value, converted to a {@link String}, is the
 * {@link java.util.regex.Pattern regular expression}; the actual value, also
 * converted to a {@link String}, is the input tested against it. This mirrors
 * how {@link IsActualContainingExpectedStringValueComparer} treats the expected
 * value as the substring to look for.
 *
 * <p>
 * The comparison succeeds only when the pattern matches the <em>entire</em>
 * actual value, using {@link Matcher#matches()}, consistent with
 * {@link String#matches(String)}. To match only part of the actual value, make
 * the pattern permissive at both ends, for example <code>.*[0-9]{4}.*</code>.
 * A <code>.</code> does not match a line terminator unless the
 * {@link Pattern#DOTALL DOTALL} flag is set, so prefix such a pattern with
 * <code>(?s)</code> when the actual value may contain a newline, for example
 * <code>(?s).*[0-9]{4}.*</code>.
 *
 * <p>
 * Useful for columns whose exact content a test does not control but whose
 * format it does, such as database-generated identifiers, UUID columns, or
 * timestamps rendered into a text column.
 *
 * <p>
 * Special case: if both values are null, they match; if exactly one is null,
 * they do not.
 *
 * <p>
 * This comparer adds no dependency beyond {@code java.util.regex}, so it is also
 * available as {@link ValueComparers#regularExpressionValueComparer}.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class RegularExpressionValueComparer extends ValueComparerTemplateBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected boolean isExpected(final ITable expectedTable,
            final ITable actualTable, final int rowNum, final String columnName,
            final DataType dataType, final Object expectedValue,
            final Object actualValue) throws DatabaseUnitException
    {
        final boolean isExpected;

        // handle nulls: prevent NPE and isExpected=true when both null
        if (expectedValue == null && actualValue == null)
        {
            // both are null, so match
            isExpected = true;
        } else if (expectedValue == null || actualValue == null)
        {
            // both aren't null, one is null, so no match
            isExpected = false;
        } else
        {
            // neither are null, so compare
            isExpected = isMatching(rowNum, columnName, expectedValue,
                    actualValue);
        }

        return isExpected;
    }

    /**
     * Returns whether the regular expression held in the expected value matches
     * the whole actual value, both converted to strings.
     *
     * @param rowNum
     *            The current row number comparing, used only to identify an
     *            invalid pattern.
     * @param columnName
     *            The name of the current column comparing, used only to identify
     *            an invalid pattern.
     * @param expectedValue
     *            The expected value, holding the regular expression.
     * @param actualValue
     *            The actual value tested against the regular expression.
     * @return <code>true</code> if the regular expression matches the entire
     *         actual value string.
     * @throws DatabaseUnitException
     *             If either value cannot be converted to a string, or the
     *             expected value is not a valid regular expression.
     */
    protected boolean isMatching(final int rowNum, final String columnName,
            final Object expectedValue, final Object actualValue)
            throws DatabaseUnitException
    {
        final String regex = DataType.asString(expectedValue);
        final String actualValueString = DataType.asString(actualValue);
        final Pattern pattern = compilePattern(rowNum, columnName, regex);
        final Matcher matcher = pattern.matcher(actualValueString);
        final boolean isMatching = matcher.matches();
        log.debug("isMatching: regex={}, actualValueString={}, isMatching={}",
                regex, actualValueString, isMatching);

        return isMatching;
    }

    /**
     * Compiles the expected value into a {@link Pattern}, turning an invalid
     * expression into a {@link DatabaseUnitException} identifying the row and
     * column, consistent with how
     * {@link IsActualEqualToExpectedJsonValueComparer} reports an unparseable
     * expected value.
     */
    private Pattern compilePattern(final int rowNum, final String columnName,
            final String regex) throws DatabaseUnitException
    {
        try
        {
            return Pattern.compile(regex);
        } catch (final PatternSyntaxException e)
        {
            final String message = String.format(
                    "Unable to compile expected value as a regular expression"
                            + " for column '%s', row %d: %s",
                    columnName, rowNum, regex);
            throw new DatabaseUnitException(message, e);
        }
    }

    @Override
    protected String getFailPhrase()
    {
        return "not matching the regular expression";
    }
}
