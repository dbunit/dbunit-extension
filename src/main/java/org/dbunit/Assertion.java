/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

import java.sql.SQLException;
import java.util.Map;

import org.dbunit.assertion.DbUnitAssert;
import org.dbunit.assertion.DbUnitValueComparerAssert;
import org.dbunit.assertion.FailureHandler;
import org.dbunit.assertion.comparer.value.ValueComparer;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;

/**
 * Provides static methods for the most common DbUnit assertion needs.
 *
 * Although the methods are static, they rely on a {@link DbUnitAssert} instance
 * to do the work. So, if you need to customize this class behavior, you can
 * create your own {@link DbUnitAssert} extension.
 *
 * @author Manuel Laflamme
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.3 (Mar 22, 2002)
 */
public class Assertion
{
    /** Assert using equals comparisons. */
    private static final DbUnitAssert EQUALS_INSTANCE = new DbUnitAssert();

    /** Assert using compare comparisons. @since 2.6.0 */
    private static final DbUnitValueComparerAssert VALUE_COMPARE_INSTANCE =
            new DbUnitValueComparerAssert();

    private Assertion()
    {
        throw new UnsupportedOperationException(
                "this class has only static methods");
    }

    /**
     * Asserts that the two specified datasets' tables are equal, ignoring the given columns.
     *
     * @param expectedDataset dataset containing the expected results.
     * @param actualDataset dataset containing the actual results.
     * @param tableName name of the table to compare.
     * @param ignoreCols columns to ignore during comparison.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEqualsIgnoreCols(IDataSet, IDataSet, String,
     *      String[])
     */
    public static void assertEqualsIgnoreCols(final IDataSet expectedDataset,
            final IDataSet actualDataset, final String tableName,
            final String[] ignoreCols) throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEqualsIgnoreCols(expectedDataset, actualDataset,
                tableName, ignoreCols);
    }

    /**
     * Asserts that the two specified tables are equal, ignoring the given columns.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param ignoreCols columns to ignore during comparison.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEqualsIgnoreCols(ITable, ITable, String[])
     */
    public static void assertEqualsIgnoreCols(final ITable expectedTable,
            final ITable actualTable, final String[] ignoreCols)
            throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEqualsIgnoreCols(expectedTable, actualTable,
                ignoreCols);
    }

    /**
     * Asserts that the specified dataset table matches the given query's result, ignoring the given columns.
     *
     * @param expectedDataset dataset containing the expected results.
     * @param connection connection used to query the actual results.
     * @param sqlQuery SQL query used to obtain the actual results.
     * @param tableName name of the table being compared.
     * @param ignoreCols columns to ignore during comparison.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @throws SQLException if an error occurs while executing the query.
     * @see DbUnitAssert#assertEqualsByQuery(IDataSet, IDatabaseConnection,
     *      String, String, String[])
     */
    public static void assertEqualsByQuery(final IDataSet expectedDataset,
            final IDatabaseConnection connection, final String sqlQuery,
            final String tableName, final String[] ignoreCols)
            throws DatabaseUnitException, SQLException
    {
        EQUALS_INSTANCE.assertEqualsByQuery(expectedDataset, connection,
                sqlQuery, tableName, ignoreCols);
    }

    /**
     * Asserts that the specified table matches the given query's result, ignoring the given columns.
     *
     * @param expectedTable table containing the expected results.
     * @param connection connection used to query the actual results.
     * @param tableName name of the table being compared.
     * @param sqlQuery SQL query used to obtain the actual results.
     * @param ignoreCols columns to ignore during comparison.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @throws SQLException if an error occurs while executing the query.
     * @see DbUnitAssert#assertEqualsByQuery(ITable, IDatabaseConnection,
     *      String, String, String[])
     */
    public static void assertEqualsByQuery(final ITable expectedTable,
            final IDatabaseConnection connection, final String tableName,
            final String sqlQuery, final String[] ignoreCols)
            throws DatabaseUnitException, SQLException
    {
        EQUALS_INSTANCE.assertEqualsByQuery(expectedTable, connection,
                tableName, sqlQuery, ignoreCols);
    }

    /**
     * Asserts that the two specified datasets are equal.
     *
     * @param expectedDataSet dataset containing the expected results.
     * @param actualDataSet dataset containing the actual results.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEquals(IDataSet, IDataSet)
     */
    public static void assertEquals(final IDataSet expectedDataSet,
            final IDataSet actualDataSet) throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEquals(expectedDataSet, actualDataSet);
    }

    /**
     * Asserts that the two specified datasets are equal.
     *
     * @param expectedDataSet dataset containing the expected results.
     * @param actualDataSet dataset containing the actual results.
     * @param failureHandler the failure handler used to report mismatches.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEquals(IDataSet, IDataSet, FailureHandler)
     * @since 2.4
     */
    public static void assertEquals(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final FailureHandler failureHandler)
            throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEquals(expectedDataSet, actualDataSet,
                failureHandler);
    }

    /**
     * Asserts that the two specified tables are equal.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEquals(ITable, ITable)
     */
    public static void assertEquals(final ITable expectedTable,
            final ITable actualTable) throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEquals(expectedTable, actualTable);
    }

    /**
     * Asserts that the two specified tables are equal.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param additionalColumnInfo additional columns to include in failure messages.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEquals(ITable, ITable, Column[])
     */
    public static void assertEquals(final ITable expectedTable,
            final ITable actualTable, final Column[] additionalColumnInfo)
            throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEquals(expectedTable, actualTable,
                additionalColumnInfo);
    }

    /**
     * Asserts that the two specified tables are equal.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param failureHandler the failure handler used to report mismatches.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitAssert#assertEquals(ITable, ITable, FailureHandler)
     * @since 2.4
     */
    public static void assertEquals(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler)
            throws DatabaseUnitException
    {
        EQUALS_INSTANCE.assertEquals(expectedTable, actualTable,
                failureHandler);
    }

    /**
     * Asserts that the two specified datasets are equal, using the given value comparers.
     *
     * @param expectedDataSet dataset containing the expected results.
     * @param actualDataSet dataset containing the actual results.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param tableColumnValueComparers the per-table, per-column value comparers to use.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitValueComparerAssert#assertWithValueComparer(IDataSet,
     *      IDataSet, ValueComparer, Map)
     * @since 2.6.0
     */
    public static void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet,
            final ValueComparer defaultValueComparer,
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
            throws DatabaseUnitException
    {
        VALUE_COMPARE_INSTANCE.assertWithValueComparer(expectedDataSet,
                actualDataSet, defaultValueComparer, tableColumnValueComparers);
    }

    /**
     * Asserts that the two specified tables are equal, using the given value comparers.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param columnValueComparers the per-column value comparers to use.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitValueComparerAssert#assertWithValueComparer(ITable, ITable,
     *      ValueComparer, Map)
     * @since 2.6.0
     */
    public static void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        VALUE_COMPARE_INSTANCE.assertWithValueComparer(expectedTable,
                actualTable, defaultValueComparer, columnValueComparers);
    }

    /**
     * Asserts that the two specified datasets are equal, using the given value comparers.
     *
     * @param expectedDataSet dataset containing the expected results.
     * @param actualDataSet dataset containing the actual results.
     * @param failureHandler the failure handler used to report mismatches.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param tableColumnValueComparers the per-table, per-column value comparers to use.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitValueComparerAssert#assertWithValueComparer(IDataSet,
     *      IDataSet, FailureHandler, ValueComparer, Map)
     * @since 2.6.0
     */
    public static void assertWithValueComparer(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, Map<String, ValueComparer>> tableColumnValueComparers)
            throws DatabaseUnitException
    {
        VALUE_COMPARE_INSTANCE.assertWithValueComparer(expectedDataSet,
                actualDataSet, failureHandler, defaultValueComparer,
                tableColumnValueComparers);
    }

    /**
     * Asserts that the two specified tables are equal, using the given value comparers.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param additionalColumnInfo additional columns to include in failure messages.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param columnValueComparers the per-column value comparers to use.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitValueComparerAssert#assertWithValueComparer(ITable, ITable,
     *      Column[], ValueComparer, Map)
     * @since 2.6.0
     */
    public static void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final Column[] additionalColumnInfo,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        VALUE_COMPARE_INSTANCE.assertWithValueComparer(expectedTable,
                actualTable, additionalColumnInfo, defaultValueComparer,
                columnValueComparers);
    }

    /**
     * Asserts that the two specified tables are equal, using the given value comparers.
     *
     * @param expectedTable table containing the expected results.
     * @param actualTable table containing the actual results.
     * @param failureHandler the failure handler used to report mismatches.
     * @param defaultValueComparer the value comparer used when no more specific comparer is configured.
     * @param columnValueComparers the per-column value comparers to use.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @see DbUnitValueComparerAssert#assertWithValueComparer(ITable, ITable,
     *      FailureHandler, ValueComparer, Map)
     * @since 2.6.0
     */
    public static void assertWithValueComparer(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler,
            final ValueComparer defaultValueComparer,
            final Map<String, ValueComparer> columnValueComparers)
            throws DatabaseUnitException
    {
        VALUE_COMPARE_INSTANCE.assertWithValueComparer(expectedTable,
                actualTable, failureHandler, defaultValueComparer,
                columnValueComparers);
    }

    /**
     * Returns the shared {@link DbUnitAssert} instance used for equals-based comparisons.
     *
     * @return the shared {@link DbUnitAssert} instance.
     */
    public static DbUnitAssert getEqualsInstance()
    {
        return EQUALS_INSTANCE;
    }

    /**
     * Returns the shared {@link DbUnitValueComparerAssert} instance used for value-comparer-based
     * comparisons.
     *
     * @return the shared {@link DbUnitValueComparerAssert} instance.
     */
    public static DbUnitValueComparerAssert getValueCompareInstance()
    {
        return VALUE_COMPARE_INSTANCE;
    }
}
