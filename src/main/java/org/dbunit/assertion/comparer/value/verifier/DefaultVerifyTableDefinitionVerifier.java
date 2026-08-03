package org.dbunit.assertion.comparer.value.verifier;

import java.util.Map;

import org.dbunit.VerifyTableDefinition;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for {@link VerifyTableDefinitionVerifier} which throws
 * {@link IllegalStateException} on configuration conflicts.
 *
 * @author Jeff Jensen
 * @since 2.6.0
 */
public class DefaultVerifyTableDefinitionVerifier
        implements VerifyTableDefinitionVerifier
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void verify(final VerifyTableDefinition verifyTableDefinition)
    {
        final String tableName = verifyTableDefinition.getTableName();
        final String[] columnExclusionFilters =
                verifyTableDefinition.getColumnExclusionFilters();
        final Map<String, ValueComparer> columnValueComparers =
                verifyTableDefinition.getColumnValueComparers();

        verify(tableName, columnExclusionFilters, columnValueComparers);
    }

    /**
     * Verify the given columnExclusionFilters and columnValueComparers agree, e.g. a
     * {@link ValueComparer} does not exist for an excluded column.
     *
     * @param tableName the table name, used for failure reporting.
     * @param columnExclusionFilters the columns excluded from comparison.
     * @param columnValueComparers the per-column value comparers configured.
     */
    public void verify(final String tableName,
            final String[] columnExclusionFilters,
            final Map<String, ValueComparer> columnValueComparers)
    {
        final boolean hasColumnExclusionFilters =
                hasColumnExclusionFilters(columnExclusionFilters);
        final boolean hasColumnValueComparers =
                hasColumnValueComparers(columnValueComparers);

        if (hasColumnExclusionFilters && hasColumnValueComparers)
        {
            doVerify(tableName, columnExclusionFilters, columnValueComparers);
        }
    }

    /**
     * Verify the columnExclusionFilters and columnValueComparers agree.
     *
     * @param tableName the table name, used for failure reporting.
     * @param columnExclusionFilters the columns excluded from comparison.
     * @param columnValueComparers the per-column value comparers configured.
     */
    protected void doVerify(final String tableName,
            final String[] columnExclusionFilters,
            final Map<String, ValueComparer> columnValueComparers)
    {
        for (final String columnName : columnExclusionFilters)
        {
            log.trace("doVerify: columnName={}", columnName);
            failIfColumnValueComparersHaveExcludedColumn(tableName, columnName,
                    columnValueComparers);
        }
    }

    /**
     * Fails with an {@link IllegalStateException} if the given columnName has both a column
     * exclusion and a specific {@link ValueComparer} configured.
     *
     * @param tableName the table name, used for failure reporting.
     * @param columnName the excluded column name to check.
     * @param columnValueComparers the per-column value comparers configured.
     */
    protected void failIfColumnValueComparersHaveExcludedColumn(
            final String tableName, final String columnName,
            final Map<String, ValueComparer> columnValueComparers)
    {
        final ValueComparer valueComparer =
                columnValueComparers.get(columnName);
        if (valueComparer == null)
        {
            log.trace("failIfColumnValueComparersHaveExcludedColumn:"
                    + "config ok as no valueComparer found"
                    + " for excluded columnName={}", columnName);
        } else
        {
            final String msg = "Test setup conflict: table=" + tableName
                    + ", columnName=" + columnName
                    + ", has a VerifyTableDefinition column exclusion"
                    + " and a specific column ValueComparer=" + valueComparer
                    + "; to test the column, remove the exclusion;"
                    + " to ignore the column, remove the ValueComparer";
            log.error("failIfColumnValueComparersHaveExcludedColumn: {}", msg);
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Returns whether any column exclusion filters are configured.
     *
     * @param columnExclusionFilters the columns excluded from comparison.
     * @return <code>true</code> if any column exclusion filters are configured.
     */
    protected boolean hasColumnExclusionFilters(
            final String[] columnExclusionFilters)
    {
        final boolean isMissing = columnExclusionFilters == null
                || columnExclusionFilters.length == 0;

        if (isMissing)
        {
            log.debug("hasColumnExclusionFilters:"
                    + " no columnExclusionFilters specified");
        }

        return !isMissing;
    }

    /**
     * Returns whether any column value comparers are configured.
     *
     * @param columnValueComparers the per-column value comparers configured.
     * @return <code>true</code> if any column value comparers are configured.
     */
    protected boolean hasColumnValueComparers(
            final Map<String, ValueComparer> columnValueComparers)
    {
        final boolean isMissing =
                columnValueComparers == null || columnValueComparers.isEmpty();

        if (isMissing)
        {
            log.debug("hasColumnValueComparers:"
                    + " no columnValueComparers specified");
        }

        return !isMissing;
    }
}
