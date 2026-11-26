package org.dbunit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks that each column name a table's verification gives matches a column of
 * the table, so that a misspelled name fails the verification instead of
 * silently leaving a column out of the comparison, and that no column comparer
 * names a column that the include or exclude column names leave out. When
 * {@link DatabaseConfig#FEATURE_FAIL_ON_UNKNOWN_VERIFY_COLUMN_NAME} is false,
 * it logs a warning for such a name or comparer instead of failing.
 *
 * <p>
 * This class is internal to dbUnit's test cases; do not use it directly.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class VerifyColumnNamesCheck
{
    private static final Logger log =
            LoggerFactory.getLogger(VerifyColumnNamesCheck.class);

    private final String tableName;
    private final Column[] tableColumns;
    private final boolean failOnUnknownColumnName;

    /**
     * Creates a check against the columns of one table.
     *
     * @param tableName
     *            The name of the table, used in the message.
     * @param tableColumns
     *            The columns of the table as the comparison reads them.
     * @param failOnUnknownColumnName
     *            True to fail when a name matches no column; false to log a
     *            warning instead.
     */
    VerifyColumnNamesCheck(final String tableName, final Column[] tableColumns,
            final boolean failOnUnknownColumnName)
    {
        this.tableName = tableName;
        this.tableColumns = tableColumns;
        this.failOnUnknownColumnName = failOnUnknownColumnName;
    }

    /**
     * Reports each column name to include that matches no column of the table,
     * by failing or by logging a warning. The names match as
     * {@link DefaultColumnFilter} matches them: without regard to case, and
     * with its wildcards.
     *
     * @param includeColumns
     *            The column names to include; null or empty for none.
     * @throws DatabaseUnitException
     *             If a name matches no column of the table and the check fails
     *             for it.
     */
    void checkIncludeColumns(final String[] includeColumns)
            throws DatabaseUnitException
    {
        if (includeColumns == null)
        {
            return;
        }

        for (final String includeColumn : includeColumns)
        {
            if (!isMatchingAnyColumn(includeColumn))
            {
                final String problem = String.format(Locale.ENGLISH,
                        "The column name '%s' to include for table %s matches"
                                + " no column of the table, whose columns are %s,"
                                + " so the table would be verified without it.",
                        includeColumn, tableName, describeTableColumns());
                reportProblem(problem,
                        "Correct the name in the VerifyTableDefinition"
                                + " or @DbUnitVerifyTable");
            }
        }
    }

    /**
     * Reports each column comparer that the verification would not use, by
     * failing or by logging a warning: one whose column name matches no column
     * of the table, and one whose column the include or exclude column names
     * leave out of the comparison. The names of the comparers match without
     * regard to case, as the comparers are keyed, and the include and exclude
     * names as {@link DefaultColumnFilter} matches them. A null name names no
     * column and is left out, as the comparison leaves it out.
     *
     * @param columnValueComparers
     *            The comparers by column name; null or empty for none.
     * @param excludeColumns
     *            The column names to exclude; null or empty for none.
     * @param includeColumns
     *            The column names to include; null or empty for none.
     * @throws DatabaseUnitException
     *             If a comparer would not be used and the check fails for it.
     */
    void checkColumnValueComparers(
            final Map<String, ValueComparer> columnValueComparers,
            final String[] excludeColumns, final String[] includeColumns)
            throws DatabaseUnitException
    {
        if (columnValueComparers == null)
        {
            return;
        }

        for (final String columnName : columnValueComparers.keySet())
        {
            if (columnName == null)
            {
                continue;
            }

            final Column tableColumn = findColumnNamed(columnName);
            if (tableColumn == null)
            {
                final String problem = String.format(Locale.ENGLISH,
                        "The column name '%s' of a column comparer for table %s"
                                + " matches no column of the table, whose columns"
                                + " are %s, so the comparer would not be used.",
                        columnName, tableName, describeTableColumns());
                reportProblem(problem,
                        "Correct the name in the VerifyTableDefinition"
                                + " or @DbUnitColumnComparer");
            } else if (isLeftOut(tableColumn, includeColumns, true))
            {
                reportLeftOutComparer(columnName, "include", includeColumns,
                        "Add the column to the include names or remove its"
                                + " comparer");
            } else if (isLeftOut(tableColumn, excludeColumns, false))
            {
                reportLeftOutComparer(columnName, "exclude", excludeColumns,
                        "Remove the column from the exclude names or remove"
                                + " its comparer");
            }
        }
    }

    private void reportLeftOutComparer(final String columnName,
            final String filterKind, final String[] filterColumns,
            final String correction) throws DatabaseUnitException
    {
        final String problem = String.format(Locale.ENGLISH,
                "The column comparer for column '%s' of table %s is left out"
                        + " of the comparison by the %s names %s, so the"
                        + " comparer would not be used.",
                columnName, tableName, filterKind,
                Arrays.toString(filterColumns));
        reportProblem(problem, correction + " in the VerifyTableDefinition,"
                + " @DbUnitVerifyTable or @DbUnitColumnComparer");
    }

    private boolean isLeftOut(final Column tableColumn,
            final String[] filterColumns, final boolean isInclude)
    {
        if (filterColumns == null || filterColumns.length == 0)
        {
            return false;
        }

        final DefaultColumnFilter columnFilter = new DefaultColumnFilter();
        for (final String filterColumn : filterColumns)
        {
            if (isInclude)
            {
                columnFilter.includeColumn(filterColumn);
            } else
            {
                columnFilter.excludeColumn(filterColumn);
            }
        }
        return !columnFilter.accept(tableName, tableColumn);
    }

    private void reportProblem(final String problem,
            final String correction) throws DatabaseUnitException
    {
        if (failOnUnknownColumnName)
        {
            final String message = problem + " " + correction
                    + ", or set the feature "
                    + DatabaseConfig.FEATURE_FAIL_ON_UNKNOWN_VERIFY_COLUMN_NAME
                    + " to false to log a warning instead of failing.";
            throw new DatabaseUnitException(message);
        }

        log.warn("{} {}.", problem, correction);
    }

    private boolean isMatchingAnyColumn(final String includeColumn)
    {
        final DefaultColumnFilter columnFilter = new DefaultColumnFilter();
        columnFilter.includeColumn(includeColumn);
        for (final Column tableColumn : tableColumns)
        {
            if (columnFilter.accept(tableName, tableColumn))
            {
                return true;
            }
        }
        return false;
    }

    private Column findColumnNamed(final String columnName)
    {
        for (final Column tableColumn : tableColumns)
        {
            final String tableColumnName = tableColumn.getColumnName();
            if (tableColumnName.equalsIgnoreCase(columnName))
            {
                return tableColumn;
            }
        }
        return null;
    }

    private String describeTableColumns()
    {
        final List<String> names = new ArrayList<>();
        for (final Column tableColumn : tableColumns)
        {
            names.add(tableColumn.getColumnName());
        }
        if (names.isEmpty())
        {
            return "none";
        }
        return String.join(", ", names);
    }
}
