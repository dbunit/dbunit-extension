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
package org.dbunit.assertion;

import java.sql.SQLException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.assertion.comparer.value.ValueComparers;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.UnknownDataType;
import org.dbunit.dataset.filter.DefaultColumnFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of DbUnit assertions, based on the original methods
 * present at {@link org.dbunit.Assertion}.
 *
 * All are equality comparisons.
 *
 * @author Felipe Leme (dbunit@felipeal.net)
 * @author gommma (gommma AT users.sourceforge.net)
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
public class DbUnitAssert extends DbUnitAssertBase
{
    private static final Logger logger =
            LoggerFactory.getLogger(DbUnitAssert.class);

    /**
     * Compare one table present in two datasets ignoring specified columns.
     *
     * @param expectedDataset
     *            First dataset.
     * @param actualDataset
     *            Second dataset.
     * @param tableName
     *            Table name of the table to be compared.
     * @param ignoreCols
     *            Columns to be ignored in comparison.
     * @throws org.dbunit.DatabaseUnitException
     *             If an error occurs.
     */
    public void assertEqualsIgnoreCols(final IDataSet expectedDataset,
            final IDataSet actualDataset, final String tableName,
            final String[] ignoreCols) throws DatabaseUnitException
    {
        logger.debug(
                "assertEqualsIgnoreCols(expectedDataset={}, actualDataset={}, tableName={}, ignoreCols={}) - start",
                expectedDataset, actualDataset, tableName, ignoreCols);

        assertEqualsIgnoreCols(expectedDataset.getTable(tableName),
                actualDataset.getTable(tableName), ignoreCols);
    }

    /**
     * Compare the given tables ignoring specified columns.
     *
     * @param expectedTable
     *            First table.
     * @param actualTable
     *            Second table.
     * @param ignoreCols
     *            Columns to be ignored in comparison.
     * @throws org.dbunit.DatabaseUnitException
     *             If an error occurs.
     */
    public void assertEqualsIgnoreCols(final ITable expectedTable,
            final ITable actualTable, final String[] ignoreCols)
            throws DatabaseUnitException
    {
        logger.debug(
                "assertEqualsIgnoreCols(expectedTable={}, actualTable={}, ignoreCols={}) - start",
                expectedTable, actualTable, ignoreCols);

        final ITable expectedTableFiltered = DefaultColumnFilter
                .excludedColumnsTable(expectedTable, ignoreCols);
        final ITable actualTableFiltered = DefaultColumnFilter
                .excludedColumnsTable(actualTable, ignoreCols);
        assertEquals(expectedTableFiltered, actualTableFiltered);
    }

    /**
     * Compare a table from a dataset with a table generated from an sql query.
     *
     * @param expectedDataset
     *            Dataset to retrieve the first table from.
     * @param connection
     *            Connection to use for the SQL statement.
     * @param sqlQuery
     *            SQL query that will build the data in returned second table
     *            rows.
     * @param tableName
     *            Table name of the table to compare.
     * @param ignoreCols
     *            Columns to be ignored in comparison.
     * @throws DatabaseUnitException
     *             If an error occurs while performing the comparison.
     * @throws java.sql.SQLException
     *             If an SQL error occurs.
     */
    public void assertEqualsByQuery(final IDataSet expectedDataset,
            final IDatabaseConnection connection, final String sqlQuery,
            final String tableName, final String[] ignoreCols)
            throws DatabaseUnitException, SQLException
    {
        logger.debug(
                "assertEqualsByQuery(expectedDataset={}, connection={}, tableName={}, sqlQuery={}, ignoreCols={}) - start",
                expectedDataset, connection, tableName, sqlQuery, ignoreCols);

        final ITable expectedTable = expectedDataset.getTable(tableName);
        assertEqualsByQuery(expectedTable, connection, tableName, sqlQuery,
                ignoreCols);
    }

    /**
     * Compare a table with a table generated from an sql query.
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param connection
     *            Connection to use for the SQL statement.
     * @param tableName
     *            The name of the table to query from the database.
     * @param sqlQuery
     *            SQL query that will build the data in returned second table
     *            rows.
     * @param ignoreCols
     *            Columns to be ignored in comparison.
     * @throws DatabaseUnitException
     *             If an error occurs while performing the comparison.
     * @throws java.sql.SQLException
     *             If an SQL error occurs.
     */
    public void assertEqualsByQuery(final ITable expectedTable,
            final IDatabaseConnection connection, final String tableName,
            final String sqlQuery, final String[] ignoreCols)
            throws DatabaseUnitException, SQLException
    {
        logger.debug(
                "assertEqualsByQuery(expectedTable={}, connection={}, tableName={}, sqlQuery={}, ignoreCols={}) - start",
                expectedTable, connection, tableName, sqlQuery, ignoreCols);

        final ITable expected = DefaultColumnFilter
                .excludedColumnsTable(expectedTable, ignoreCols);
        final ITable queriedTable =
                connection.createQueryTable(tableName, sqlQuery);
        final ITable actual = DefaultColumnFilter
                .excludedColumnsTable(queriedTable, ignoreCols);
        assertEquals(expected, actual);
    }

    /**
     * Asserts that the two specified dataset are equals. This method ignore the
     * tables order.
     *
     * @param expectedDataSet dataset containing all expected results.
     * @param actualDataSet dataset containing all actual results.
     * @throws DatabaseUnitException if an error occurs during comparison.
     */
    public void assertEquals(final IDataSet expectedDataSet,
            final IDataSet actualDataSet) throws DatabaseUnitException
    {
        logger.debug(
                "assertEquals(expectedDataSet={}, actualDataSet={}) - start",
                expectedDataSet, actualDataSet);
        assertEquals(expectedDataSet, actualDataSet, null);
    }

    /**
     * Asserts that the two specified dataset are equals. This method ignore the
     * tables order.
     *
     * @param expectedDataSet dataset containing all expected results.
     * @param actualDataSet dataset containing all actual results.
     * @param failureHandler the failure handler used to report mismatches. Can be <code>null</code>.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @since 2.4
     */
    public void assertEquals(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final FailureHandler failureHandler)
            throws DatabaseUnitException
    {
        assertWithValueComparer(expectedDataSet, actualDataSet, failureHandler,
                null, null);
    }

    /**
     * Asserts each expected table against its corresponding actual table, using the default
     * equality-based value comparer.
     *
     * @param expectedDataSet the dataset containing all expected results.
     * @param actualDataSet the dataset containing all actual results.
     * @param expectedNames the table names to compare, in comparison order.
     * @param failureHandler the failure handler used if the assert fails because of a data mismatch.
     * @throws DatabaseUnitException if a table comparison fails.
     */
    protected void compareTables(final IDataSet expectedDataSet,
            final IDataSet actualDataSet, final String[] expectedNames,
            final FailureHandler failureHandler) throws DatabaseUnitException
    {
        compareTables(expectedDataSet, actualDataSet, expectedNames,
                failureHandler, null, null);
    }

    /**
     * Asserts that the two specified tables are equals. This method ignores the
     * table names, the columns order, the columns data type and which columns
     * are composing the primary keys.
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param actualTable
     *            Table containing all actual results.
     * @throws DatabaseUnitException if an error occurs during comparison.
     */
    public void assertEquals(final ITable expectedTable,
            final ITable actualTable) throws DatabaseUnitException
    {
        logger.debug("assertEquals(expectedTable={}, actualTable={}) - start",
                expectedTable, actualTable);
        assertEquals(expectedTable, actualTable, (Column[]) null);
    }

    /**
     * Asserts that the two specified tables are equals. This method ignores the
     * table names, the columns order, the columns data type and which columns
     * are composing the primary keys. <br />
     * Example: <pre><code>
     * ITable actualTable = ...;
     * ITable expectedTable = ...;
     * ITableMetaData metaData = actualTable.getTableMetaData();
     * Column[] additionalInfoCols = Columns.getColumns(new String[] {"MY_PK_COLUMN"}, metaData.getColumns());
     * assertEquals(expectedTable, actualTable, additionalInfoCols);
     * </code></pre>
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param actualTable
     *            Table containing all actual results.
     * @param additionalColumnInfo
     *            The columns to be printed out if the assert fails because of a
     *            data mismatch. Provides some additional column values that may
     *            be useful to quickly identify the columns for which the
     *            mismatch occurred (for example a primary key column). Can be
     *            <code>null</code>.
     * @throws DatabaseUnitException if an error occurs during comparison.
     */
    public void assertEquals(final ITable expectedTable,
            final ITable actualTable, final Column[] additionalColumnInfo)
            throws DatabaseUnitException
    {
        logger.debug(
                "assertEquals(expectedTable={}, actualTable={}, additionalColumnInfo={}) - start",
                expectedTable, actualTable, additionalColumnInfo);

        FailureHandler failureHandler = null;
        if (additionalColumnInfo != null)
        {
            failureHandler = getDefaultFailureHandler(additionalColumnInfo);
        }

        assertEquals(expectedTable, actualTable, failureHandler);
    }

    /**
     * Asserts that the two specified tables are equals. This method ignores the
     * table names, the columns order, the columns data type and which columns
     * are composing the primary keys. <br />
     * Example: <pre><code>
     * ITable actualTable = ...;
     * ITable expectedTable = ...;
     * ITableMetaData metaData = actualTable.getTableMetaData();
     * FailureHandler failureHandler = new DefaultFailureHandler();
     * assertEquals(expectedTable, actualTable, failureHandler);
     * </code></pre>
     *
     * @param expectedTable
     *            Table containing all expected results.
     * @param actualTable
     *            Table containing all actual results.
     * @param failureHandler
     *            The failure handler used if the assert fails because of a data
     *            mismatch. Provides some additional information that may be
     *            useful to quickly identify the rows for which the mismatch
     *            occurred (for example by printing an additional primary key
     *            column). Can be <code>null</code>.
     * @throws DatabaseUnitException if an error occurs during comparison.
     * @since 2.4
     */
    public void assertEquals(final ITable expectedTable,
            final ITable actualTable, final FailureHandler failureHandler)
            throws DatabaseUnitException
    {
        assertWithValueComparer(expectedTable, actualTable, failureHandler,
                ValueComparers.isActualEqualToExpectedWithEmptyFailMessage,
                null);
    }

    /**
     * Represents a single column to be used for the comparison of table data.
     * It contains the {@link DataType} to be used for comparing the given
     * column. This {@link DataType} matches the expected and actual column's
     * datatype.
     *
     * @author gommma (gommma AT users.sourceforge.net)
     * @author Last changed by: $Author: gommma $
     * @version $Revision: 864 $ $Date: 2008-11-07 06:27:26 -0800 (Fri, 07 Nov
     *          2008) $
     * @since 2.4.0
     */
    public static class ComparisonColumn
    {
        private static final Logger logger =
                LoggerFactory.getLogger(ComparisonColumn.class);

        private String columnName;
        private DataType dataType;

        /**
         * Creates a comparison column, resolving the {@link DataType} to use for comparison.
         *
         * @param tableName
         *            The table name which is only needed for debugging output.
         * @param expectedColumn
         *            The expected column needed to resolve the {@link DataType}
         *            to use for the actual comparison.
         * @param actualColumn
         *            The actual column needed to resolve the {@link DataType}
         *            to use for the actual comparison.
         * @param failureHandler
         *            The {@link FailureHandler} to be used when no datatype can
         *            be determined.
         */
        public ComparisonColumn(final String tableName,
                final Column expectedColumn, final Column actualColumn,
                final FailureHandler failureHandler)
        {
            this.columnName = expectedColumn.getColumnName();
            this.dataType = getComparisonDataType(tableName, expectedColumn,
                    actualColumn, failureHandler);
        }

        /**
         * Returns the column actually being compared.
         *
         * @return The column actually being compared.
         */
        public String getColumnName()
        {
            return this.columnName;
        }

        /**
         * Returns the {@link DataType} to use for the actual comparison.
         *
         * @return The {@link DataType} to use for the actual comparison.
         */
        public DataType getDataType()
        {
            return this.dataType;
        }

        /**
         * @param tableName
         *            The table name which is only needed for debugging output.
         * @param expectedColumn
         * @param actualColumn
         * @param failureHandler
         *            The {@link FailureHandler} to be used when no datatype can
         *            be determined.
         * @return The dbunit {@link DataType} to use for comparing the given
         *         column.
         */
        private DataType getComparisonDataType(final String tableName,
                final Column expectedColumn, final Column actualColumn,
                final FailureHandler failureHandler)
        {
            logger.debug(
                    "getComparisonDataType(tableName={}, expectedColumn={}, actualColumn={}, failureHandler={}) - start",
                    tableName, expectedColumn, actualColumn, failureHandler);

            final DataType expectedDataType = expectedColumn.getDataType();
            final DataType actualDataType = actualColumn.getDataType();

            // The two columns have different data type
            if (!expectedDataType.getClass().isInstance(actualDataType))
            {
                // Expected column data type is unknown, use actual column data
                // type
                if (expectedDataType instanceof UnknownDataType)
                {
                    return actualDataType;
                }

                // Actual column data type is unknown, use expected column data
                // type
                if (actualDataType instanceof UnknownDataType)
                {
                    return expectedDataType;
                }

                // Impossible to determine which data type to use
                final String msg = "Incompatible data types: (table="
                        + tableName + ", col=" + expectedColumn.getColumnName()
                        + ")";
                throw failureHandler.createFailure(msg,
                        String.valueOf(expectedDataType),
                        String.valueOf(actualDataType));
            }

            // Both columns have same data type, return any one of them
            return expectedDataType;
        }
    }
}
