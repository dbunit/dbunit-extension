package org.dbunit.assertion.comparer.value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.RequiredLibrary;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ValueComparer} implementation that verifies the actual value is
 * semantically equal to the expected value by parsing both as JSON and
 * comparing the resulting document trees, instead of comparing their raw
 * text.
 *
 * <p>Databases that store native JSON (for example MySQL {@code JSON},
 * PostgreSQL {@code json}/{@code jsonb}, or H2 {@code JSON}) commonly
 * reformat the text on storage: insignificant whitespace is stripped and
 * object keys may be reordered. A plain string or
 * {@link DataType#compare(Object, Object)} comparison then fails even when
 * the expected and actual documents are equivalent. This comparer instead
 * treats JSON object member order as insignificant while still treating JSON
 * array element order as significant, matching JSON's own equality
 * semantics. Special case: if both values are null, they match.
 *
 * <p>Two values are equal when their documents are the same, as JSON defines it:
 * <ul>
 * <li>Whitespace between the tokens and the order of the members of an object do not matter.
 * <li>The order of the elements of an array does matter.
 * <li>A member with the value {@code null} differs from a missing member.
 * <li>The comparer compares a number exactly, with every digit it has, and a whole number differs
 * from a number with a fraction: {@code 1} and {@code 1.0} differ, while {@code 1.5} and
 * {@code 1.50} do not.
 * <li>A value must hold one JSON document. Anything after the first value, such as
 * {@code {"a":1} xyz} or a second document, makes the value invalid, and the comparison fails
 * with a {@link DatabaseUnitException} that names the column and the row. Jackson 2.9 and later
 * can check for it; an older Jackson cannot, and ignores the content.
 * </ul>
 *
 * <p>A value that a driver hands over as bytes, such as an H2 {@code JSON} column read without
 * {@link org.dbunit.ext.h2.H2DataTypeFactory}, is read as UTF-8 text, the encoding that RFC 8259
 * gives JSON exchanged between systems.
 *
 * <p>The comparer parses a JSON value whatever the length of its strings, as
 * {@link org.dbunit.dataset.json.JsonDataSet} does: it lifts the limit of
 * 20,000,000 characters that Jackson 2.15 and later set on a string value.
 *
 * <p>Requires the optional {@code jackson-databind} dependency (the same one
 * used by {@link org.dbunit.dataset.json.JsonDataSet}) on the classpath.
 * Deliberately not exposed as a constant on {@link ValueComparers}, because
 * that class eagerly instantiates every constant it declares; doing so here
 * would force the optional dependency onto every consumer of
 * {@link ValueComparers}, not only those comparing JSON columns. Construct
 * this comparer directly instead.
 *
 * @author Jeff Jensen
 * @since 3.5.0
 */
public class IsActualEqualToExpectedJsonValueComparer
        extends ValueComparerTemplateBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates the comparer.
     *
     * @throws org.dbunit.DatabaseUnitRuntimeException If Jackson is not on the classpath.
     */
    public IsActualEqualToExpectedJsonValueComparer()
    {
        RequiredLibrary.JACKSON.requireUnchecked(
                IsActualEqualToExpectedJsonValueComparer.class);
    }

    /**
     * Holds the shared mapper apart from the comparer, so that loading and initializing the
     * comparer does not itself need Jackson and its constructor can say what is missing.
     */
    private static final class SharedMapper
    {
        private static final ObjectMapper INSTANCE = newMapper();

        private SharedMapper()
        {
        }

        /**
         * Creates the mapper that reads both values. It reads a string value of any length, as
         * a number with a fraction as an exact decimal, so that digits past what a
         * {@code double} holds still count, and it fails on content after the value.
         */
        private static ObjectMapper newMapper()
        {
            final ObjectMapper mapper = newMapperWithoutStringLengthLimit();
            mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            rejectContentAfterTheValue(mapper);
            return mapper;
        }

        /**
         * Creates the mapper without the limit of 20,000,000 characters that Jackson 2.15 and
         * later set on a string value to protect a service from hostile input, so that a large
         * JSON column value compares as the dataset reader reads it. A Jackson older than 2.15
         * has no such limit and no class to set it.
         */
        private static ObjectMapper newMapperWithoutStringLengthLimit()
        {
            try
            {
                return StringLengthLimit.newMapperWithoutIt();
            }
            catch (final NoClassDefFoundError | NoSuchMethodError e)
            {
                // Jackson before 2.15: there is no limit to lift.
                return new ObjectMapper();
            }
        }

        /**
         * Makes the mapper fail on content after the first value, which Jackson ignores by
         * default. A Jackson older than 2.9 has no such feature, and ignores it.
         */
        private static void rejectContentAfterTheValue(final ObjectMapper mapper)
        {
            try
            {
                mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            }
            catch (final NoSuchFieldError e)
            {
                // Jackson before 2.9: the feature does not exist.
            }
        }
    }

    /**
     * The only code in this comparer that uses the classes of Jackson 2.15 and later that limit
     * a string value, kept in a class of its own so that an older Jackson, which has none of
     * them, fails here, where {@link SharedMapper} falls back to the default mapper.
     */
    private static final class StringLengthLimit
    {
        private StringLengthLimit()
        {
        }

        static ObjectMapper newMapperWithoutIt()
        {
            final StreamReadConstraints.Builder constraintsBuilder = StreamReadConstraints.builder();
            constraintsBuilder.maxStringLength(Integer.MAX_VALUE);
            final StreamReadConstraints constraints = constraintsBuilder.build();
            final JsonFactory factory = JsonFactory.builder().streamReadConstraints(constraints).build();
            return new ObjectMapper(factory);
        }
    }

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
            isExpected = isJsonEqual(rowNum, columnName, expectedValue,
                    actualValue);
        }

        return isExpected;
    }

    /**
     * Returns whether the expected and actual values parse as structurally
     * equal JSON documents.
     *
     * @param rowNum the current row number comparing, used only to identify a parse failure.
     * @param columnName the name of the current column comparing, used only to identify a parse failure.
     * @param expectedValue the expected value.
     * @param actualValue the actual value.
     * @return <code>true</code> if both values parse as JSON and their document trees are equal.
     * @throws DatabaseUnitException if either value cannot be converted to a string or parsed as JSON.
     */
    protected boolean isJsonEqual(final int rowNum, final String columnName,
            final Object expectedValue, final Object actualValue)
            throws DatabaseUnitException
    {
        final JsonNode expectedNode =
                parseJson(rowNum, columnName, "expected", expectedValue);
        final JsonNode actualNode =
                parseJson(rowNum, columnName, "actual", actualValue);
        log.debug("isJsonEqual: expectedNode={}, actualNode={}", expectedNode,
                actualNode);

        return actualNode.equals(expectedNode);
    }

    private static String textOf(final Object value) throws DatabaseUnitException
    {
        if (value instanceof byte[])
        {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return DataType.asString(value);
    }

    private JsonNode parseJson(final int rowNum, final String columnName,
            final String label, final Object value) throws DatabaseUnitException
    {
        final String json = textOf(value);

        final JsonNode node;
        try
        {
            node = SharedMapper.INSTANCE.readTree(json);
        } catch (final IOException e)
        {
            throw new DatabaseUnitException(
                    parseFailureMessage(rowNum, columnName, label, json), e);
        }

        if (node == null || node.isMissingNode())
        {
            // Jackson returns a MissingNode (not an exception, and not a
            // Java null either) for empty or whitespace-only input
            throw new DatabaseUnitException(
                    parseFailureMessage(rowNum, columnName, label, json));
        }

        return node;
    }

    private String parseFailureMessage(final int rowNum,
            final String columnName, final String label, final String json)
    {
        return String.format(
                "Unable to parse %s value as JSON for column '%s', row %d: %s",
                label, columnName, rowNum, json);
    }

    @Override
    protected String getFailPhrase()
    {
        return "not JSON-equal to";
    }
}
