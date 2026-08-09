package org.dbunit.assertion.comparer.value;

import java.io.IOException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>Requires the optional {@code jackson-databind} dependency (the same one
 * used by {@link org.dbunit.dataset.json.JsonDataSet}) on the classpath.
 * Deliberately not exposed as a constant on {@link ValueComparers}, because
 * that class eagerly instantiates every constant it declares; doing so here
 * would force the optional dependency onto every consumer of
 * {@link ValueComparers}, not only those comparing JSON columns. Construct
 * this comparer directly instead.
 *
 * @author Jeff Jensen
 * @since 3.4.1
 */
public class IsActualEqualToExpectedJsonValueComparer
        extends ValueComparerTemplateBase
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    private JsonNode parseJson(final int rowNum, final String columnName,
            final String label, final Object value) throws DatabaseUnitException
    {
        final String json = DataType.asString(value);

        final JsonNode node;
        try
        {
            node = OBJECT_MAPPER.readTree(json);
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
