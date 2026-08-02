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

package org.dbunit.operation;

import java.sql.SQLException;
import java.util.BitSet;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.statement.IPreparedBatchStatement;
import org.dbunit.database.statement.IStatementFactory;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for database operation that are executed in batch.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public abstract class AbstractBatchOperation extends AbstractOperation
{
    /**
     * Logger for this class
     */
    private static final Logger logger =
            LoggerFactory.getLogger(AbstractBatchOperation.class);

    private static final BitSet EMPTY_BITSET = new BitSet();
    protected boolean _reverseRowOrder = false;

    static boolean isEmpty(ITable table) throws DataSetException
    {
        logger.debug("isEmpty(table={}) - start", table);

        Column[] columns = table.getTableMetaData().getColumns();

        // No columns = empty
        if (columns.length == 0)
        {
            return true;
        }

        // Try to fetch first table value
        try
        {
            table.getValue(0, columns[0].getColumnName());
            return false;
        } catch (RowOutOfBoundsException e)
        {
            // Not able to access first row thus empty
            return true;
        }
    }

    /**
     * Returns list of tables this operation is applied to. This method allow
     * subclass to do filtering.
     */
    protected ITableIterator iterator(IDataSet dataSet)
            throws DatabaseUnitException
    {
        return dataSet.iterator();
    }

    /**
     * Returns mapping of columns to ignore by this operation. Each bit set
     * represent a column to ignore.
     */
    BitSet getIgnoreMapping(ITable table, int row) throws DataSetException
    {
        return EMPTY_BITSET;
    }

    /**
     * Returns false if the specified table row have a different ignore mapping
     * than the specified mapping.
     */
    boolean equalsIgnoreMapping(BitSet ignoreMapping, ITable table, int row)
            throws DataSetException
    {
        return true;
    }

    abstract OperationData getOperationData(ITableMetaData metaData,
            BitSet ignoreMapping, IDatabaseConnection connection)
            throws DataSetException;

    ////////////////////////////////////////////////////////////////////////////
    // DatabaseOperation class

    @Override
    public void execute(IDatabaseConnection connection, IDataSet dataSet)
            throws DatabaseUnitException, SQLException
    {
        logger.debug("execute(connection={}, dataSet={}) - start", connection,
                dataSet);

        DatabaseConfig databaseConfig = connection.getConfig();
        IStatementFactory factory = (IStatementFactory) databaseConfig
                .getProperty(DatabaseConfig.PROPERTY_STATEMENT_FACTORY);
        boolean allowEmptyFields = connection.getConfig()
                .getFeature(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS);

        // for each table
        ITableIterator iterator = iterator(dataSet);
        while (iterator.next())
        {
            ITable table = iterator.getTable();

            String tableName = table.getTableMetaData().getTableName();
            logger.trace("execute: processing table='{}'", tableName);

            // Do not process empty table
            if (isEmpty(table))
            {
                continue;
            }

            ITableMetaData metaData =
                    getOperationMetaData(connection, table.getTableMetaData());
            ITableMetaData tableMetaData = table.getTableMetaData();
            BitSet ignoreMapping = null;
            OperationData operationData = null;
            IPreparedBatchStatement statement = null;
            Column[] columns = null;
            int[] tableColumnIndexes = null;

            try
            {
                // For each row
                int start = _reverseRowOrder ? table.getRowCount() - 1 : 0;
                int increment = _reverseRowOrder ? -1 : 1;

                try
                {
                    for (int i = start;; i = i + increment)
                    {
                        int row = i;

                        // If current row have a different ignore value mapping
                        // than previous one, we generate a new statement
                        if (ignoreMapping == null
                                || !equalsIgnoreMapping(ignoreMapping, table,
                                        row))
                        {
                            // Execute and close previous statement
                            if (statement != null)
                            {
                                statement.executeBatch();
                                statement.clearBatch();
                                statement.close();
                            }

                            ignoreMapping = getIgnoreMapping(table, row);
                            operationData = getOperationData(metaData,
                                    ignoreMapping, connection);
                            statement = factory.createPreparedBatchStatement(
                                    operationData.getSql(), connection);

                            // Pre-resolve column indices in the source table once per
                            // operationData to avoid a per-row, per-column HashMap
                            // lookup inside the inner loop below.
                            columns = operationData.getColumns();
                            tableColumnIndexes = resolveTableColumnIndexes(
                                    columns, ignoreMapping, tableMetaData);
                        }

                        // for each column
                        for (int j = 0; j < columns.length; j++)
                        {
                            // Bind value only if not in ignore mapping
                            if (!ignoreMapping.get(j))
                            {
                                Column column = columns[j];
                                String columnName = column.getColumnName();
                                try
                                {
                                    DataType dataType = column.getDataType();
                                    Object value = table.getValue(row,
                                            tableColumnIndexes[j]);

                                    if ("".equals(value) && !allowEmptyFields)
                                    {
                                        handleColumnHasNoValue(tableName,
                                                columnName);
                                    }

                                    statement.addValue(value, dataType);
                                } catch (TypeCastException e)
                                {
                                    final String msg =
                                            "Error casting value for table '"
                                                    + tableName
                                                    + "' and column '"
                                                    + columnName + "'";
                                    logger.error("execute: {}", msg);
                                    throw new TypeCastException(msg, e);
                                }
                            }
                        }
                        statement.addBatch();
                    }
                } catch (RowOutOfBoundsException e)
                {
                    // This exception occurs when records are exhausted
                    // and we reach the end of the table. Ignore this error

                    // end of table
                }

                statement.executeBatch();
                statement.clearBatch();
            } catch (SQLException e)
            {
                final String msg =
                        "Exception processing table name='" + tableName + "'";
                throw new DatabaseUnitException(msg, e);
            } finally
            {
                if (statement != null)
                {
                    statement.close();
                }
            }
        }
    }

    /**
     * Resolves the zero-based column index in the source table's metadata for each
     * operation column. This is done once per operationData so the inner row loop can
     * use {@link ITable#getValue(int, int)} (index-based) instead of
     * {@link ITable#getValue(int, String)} (HashMap-based), reducing per-cell
     * overhead from O(R&times;C) map lookups to O(C).
     *
     * @param columns The operation columns in binding order.
     * @param ignoreMapping Bits set for columns that are excluded from this operation.
     * @param tableMetaData The metadata of the source table being read.
     * @return An array of length {@code columns.length} where entry {@code j} is
     *         the column index in {@code tableMetaData} for {@code columns[j]}, or 0
     *         for ignored columns.
     * @throws DataSetException if a column name cannot be resolved.
     */
    private int[] resolveTableColumnIndexes(Column[] columns,
            BitSet ignoreMapping, ITableMetaData tableMetaData)
            throws DataSetException
    {
        int[] indexes = new int[columns.length];
        for (int j = 0; j < columns.length; j++)
        {
            if (!ignoreMapping.get(j))
            {
                indexes[j] = tableMetaData
                        .getColumnIndex(columns[j].getColumnName());
            }
        }
        return indexes;
    }

    protected void handleColumnHasNoValue(String tableName, String columnName)
    {
        final String tableColumnName = tableName + "." + columnName;
        final String msg = "table.column=" + tableColumnName
                + " value is empty but must contain a value"
                + " (to disable this feature check,"
                + " set DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS to true)";
        logger.error("execute: {}", msg);

        throw new IllegalArgumentException(msg);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("_reverseRowOrder=").append(this._reverseRowOrder);
        sb.append(", super=").append(super.toString());
        sb.append("]");
        return sb.toString();
    }
}
