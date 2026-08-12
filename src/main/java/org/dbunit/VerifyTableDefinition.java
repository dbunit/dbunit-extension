/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit;

import java.util.Arrays;
import java.util.Map;

import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.assertion.comparer.value.verifier.DefaultVerifyTableDefinitionVerifier;
import org.dbunit.assertion.comparer.value.verifier.VerifyTableDefinitionVerifier;

/**
 * Defines a database table to verify (assert on data), specifying include and
 * exclude column filters and optional {@link ValueComparer}s.
 *
 * @author Jeff Jensen jeffjensen AT users.sourceforge.net
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.8
 */
public class VerifyTableDefinition
{
    /** The name of the table. */
    private final String tableName;

    /** The columns to exclude in table comparisons. */
    private final String[] columnExclusionFilters;

    /** The columns to include in table comparisons. */
    private final String[] columnInclusionFilters;

    /**
     * {@link ValueComparer} to use with column value comparisons when the
     * column name for the table is not in the {@link columnValueComparers}
     * {@link Map}. Can be <code>null</code> and will default.
     *
     * @since 2.6.0
     */
    private final ValueComparer defaultValueComparer;

    /**
     * Map of column names to {@link ValueComparer}s to use for comparison.
     * 
     * @since 2.6.0
     */
    private final Map<String, ValueComparer> columnValueComparers;

    private VerifyTableDefinitionVerifier verifyTableDefinitionVerifier =
            new DefaultVerifyTableDefinitionVerifier();

    /**
     * Whether to sort the expected and actual tables by only this table's
     * filtered columns (post exclude/include) instead of by all of the actual
     * table's native columns. Default is <code>false</code>, preserving the
     * historical sort-by-all-columns behavior for backward compatibility.
     * <p>
     * Set <code>true</code> for tables whose first column - or any excluded
     * column - is a generated/surrogate value not under the test's control
     * (e.g. an identity column assigned in an insertion order production code
     * does not guarantee, such as with Hibernate batch inserts): sorting by all
     * columns then sorts the actual table primarily by that unpredictable value
     * while the expected table usually sorts differently by its data content -
     * misaligning same-data rows and failing the comparison.
     *
     * @since 3.5.0
     */
    private boolean sortOnFilteredColumnsOnly;

    /**
     * Create a valid instance with all columns compared except exclude the
     * specified columns.
     *
     * @param table
     *            The name of the table - required.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns)
    {
        this(table, excludeColumns, null, null, null);
    }

    /**
     * Create a valid instance with all columns compared and use the specified
     * defaultValueComparer for all column comparisons not in the
     * columnValueComparers {@link Map}.
     *
     * @param table
     *            The name of the table - required.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is {@link ValueComparer} to
     *            use for comparison of that column. Can be <code>null</code>
     *            and will default to defaultValueComparer for all columns in
     *            all tables.
     * @since 2.6.0
     */
    public VerifyTableDefinition(final String table,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
    {
        this(table, null, null, defaultValueComparer, columnValueComparers);
    }

    /**
     * Create a valid instance with all columns compared and exclude the
     * specified columns, and use the specified defaultValueComparer for all
     * column comparisons not in the columnValueComparers {@link Map}.
     *
     * @param table
     *            The name of the table - required.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is {@link ValueComparer} to
     *            use for comparison of that column. Can be <code>null</code>
     *            and will default to defaultValueComparer for all columns in
     *            all tables.
     * @since 2.6.0
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
    {
        this(table, excludeColumns, null, defaultValueComparer,
                columnValueComparers);
    }

    /**
     * Create a valid instance specifying exclude and include columns.
     *
     * @param table
     *            The name of the table.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     * @param includeColumns
     *            The columns in the table to include in expected vs actual
     *            comparisons; null to include all columns, empty array to
     *            include no columns.
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns, final String[] includeColumns)
    {
        this(table, excludeColumns, includeColumns, null, null);
    }

    /**
     * Create a valid instance specifying exclude and include columns and use
     * the specified defaultValueComparer for all column comparisons not in the
     * columnValueComparers {@link Map}.
     *
     * @param table
     *            The name of the table.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     * @param includeColumns
     *            The columns in the table to include in expected vs actual
     *            comparisons; null to include all columns, empty array to
     *            include no columns.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is {@link ValueComparer} to
     *            use for comparison of that column. Can be <code>null</code>
     *            and will default to defaultValueComparer for all columns in
     *            all tables.
     * @since 2.6.0
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns, final String[] includeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
    {
        this(table, excludeColumns, includeColumns, defaultValueComparer,
                columnValueComparers, false);
    }

    /**
     * Create a valid instance with all columns compared and exclude the
     * specified columns, use the specified defaultValueComparer for all
     * column comparisons not in the columnValueComparers {@link Map}, and
     * specify whether to sort by only the filtered columns. The common case
     * for opting into {@link #sortOnFilteredColumnsOnly} when include filters
     * are not also needed.
     *
     * @param table
     *            The name of the table - required.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is {@link ValueComparer} to
     *            use for comparison of that column. Can be <code>null</code>
     *            and will default to defaultValueComparer for all columns in
     *            all tables.
     * @param sortOnFilteredColumnsOnly
     *            True to sort the expected and actual tables by only this
     *            table's filtered columns instead of by all of the actual
     *            table's native columns. See {@link #sortOnFilteredColumnsOnly}.
     * @since 3.5.0
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers,
            final boolean sortOnFilteredColumnsOnly)
    {
        this(table, excludeColumns, null, defaultValueComparer,
                columnValueComparers, sortOnFilteredColumnsOnly);
    }

    /**
     * Create a valid instance specifying exclude and include columns, value
     * comparers, and whether to sort by only the filtered columns.
     *
     * @param table
     *            The name of the table.
     * @param excludeColumns
     *            The columns in the table to ignore (filter out) in expected vs
     *            actual comparisons; null or empty array to exclude no columns.
     * @param includeColumns
     *            The columns in the table to include in expected vs actual
     *            comparisons; null to include all columns, empty array to
     *            include no columns.
     * @param defaultValueComparer
     *            {@link ValueComparer} to use with column value comparisons
     *            when the column name for the table is not in the
     *            columnValueComparers {@link Map}. Can be <code>null</code> and
     *            will default.
     * @param columnValueComparers
     *            {@link Map} of {@link ValueComparer}s to use for specific
     *            columns. Key is column name, value is {@link ValueComparer} to
     *            use for comparison of that column. Can be <code>null</code>
     *            and will default to defaultValueComparer for all columns in
     *            all tables.
     * @param sortOnFilteredColumnsOnly
     *            True to sort the expected and actual tables by only this
     *            table's filtered columns instead of by all of the actual
     *            table's native columns. See {@link #sortOnFilteredColumnsOnly}.
     * @since 3.5.0
     */
    public VerifyTableDefinition(final String table,
            final String[] excludeColumns, final String[] includeColumns,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers,
            final boolean sortOnFilteredColumnsOnly)
    {
        if (table == null)
        {
            throw new IllegalArgumentException("table is null.");
        }

        tableName = table;
        columnExclusionFilters = excludeColumns;
        columnInclusionFilters = includeColumns;
        this.defaultValueComparer = defaultValueComparer;
        this.columnValueComparers = columnValueComparers;
        this.sortOnFilteredColumnsOnly = sortOnFilteredColumnsOnly;

        verifyTableDefinitionVerifier.verify(this);
    }

    /**
     * Returns the table name.
     *
     * @return the table name.
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * Returns the column names excluded from verification.
     *
     * @return the column names excluded from verification.
     */
    public String[] getColumnExclusionFilters()
    {
        return columnExclusionFilters;
    }

    /**
     * Returns the column names included in verification.
     *
     * @return the column names included in verification.
     */
    public String[] getColumnInclusionFilters()
    {
        return columnInclusionFilters;
    }

    /**
     * Returns the default value comparer used for columns without a specific one.
     *
     * @return the default value comparer used for columns without a specific one.
     * @since 2.6.0
     */
    public ValueComparer getDefaultValueComparer()
    {
        return defaultValueComparer;
    }

    /**
     * Returns the per-column value comparers.
     *
     * @return the per-column value comparers.
     * @since 2.6.0
     */
    public Map<String, ValueComparer> getColumnValueComparers()
    {
        return columnValueComparers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        final String exclusionString = arrayToString(columnExclusionFilters);
        final String inclusionString = arrayToString(columnInclusionFilters);

        final StringBuilder sb = new StringBuilder(1000);
        sb.append("tableName='").append(tableName).append("'");
        sb.append(", columnExclusionFilters='").append(exclusionString)
                .append("'");
        sb.append(", columnInclusionFilters='").append(inclusionString)
                .append("'");
        sb.append(", defaultValueComparer='").append(defaultValueComparer)
                .append("'");
        sb.append(", columnValueComparers='").append(columnValueComparers)
                .append("'");
        return sb.toString();
    }

    /**
     * Returns a null-safe string representation of the given array.
     *
     * @param array the array to format, possibly <code>null</code>.
     * @return a null-safe string representation of the given array.
     */
    protected String arrayToString(final String[] array)
    {
        return array == null ? "" : Arrays.toString(array);
    }

    /**
     * Returns the verifier used to validate this instance.
     *
     * @return the verifier used to validate this instance.
     */
    public VerifyTableDefinitionVerifier getVerifyTableDefinitionVerifier()
    {
        return verifyTableDefinitionVerifier;
    }

    /**
     * Sets the verifier used to validate this instance.
     *
     * @param verifyTableDefinitionVerifier the verifier used to validate this instance.
     */
    public void setVerifyTableDefinitionVerifier(
            final VerifyTableDefinitionVerifier verifyTableDefinitionVerifier)
    {
        this.verifyTableDefinitionVerifier = verifyTableDefinitionVerifier;
    }

    /**
     * Returns whether this table sorts by only its filtered columns instead
     * of all native columns.
     *
     * @see #sortOnFilteredColumnsOnly
     *
     * @return True if it sorts by only its filtered columns, false if by all
     *         native columns.
     * @since 3.5.0
     */
    public boolean isSortOnFilteredColumnsOnly()
    {
        return sortOnFilteredColumnsOnly;
    }

    /**
     * Sets whether this table sorts by only its filtered columns instead of
     * all native columns. Default is <code>false</code>.
     *
     * @see #sortOnFilteredColumnsOnly
     *
     * @param sortOnFilteredColumnsOnly
     *            True to sort by only its filtered columns, false to sort by
     *            all native columns.
     * @since 3.5.0
     */
    public void setSortOnFilteredColumnsOnly(
            final boolean sortOnFilteredColumnsOnly)
    {
        this.sortOnFilteredColumnsOnly = sortOnFilteredColumnsOnly;
    }
}
