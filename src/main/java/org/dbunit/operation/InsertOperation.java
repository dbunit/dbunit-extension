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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoSuchColumnException;

import java.util.BitSet;

/**
 * Inserts the dataset contents into the database. This operation assumes that
 * table data does not exist in the database and fails if this is not the case.
 * To prevent problems with foreign keys, tables must be sequenced appropriately
 * in dataset.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public class InsertOperation extends AbstractBatchOperation
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(InsertOperation.class);

    InsertOperation()
    {
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractBatchOperation class

    public OperationData getOperationData(ITableMetaData metaData,
            BitSet ignoreMapping, IDatabaseConnection connection) throws DataSetException
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("getOperationData(metaData={}, ignoreMapping={}, connection={}) - start",
    				metaData, ignoreMapping, connection);
    	}

        Column[] columns = metaData.getColumns();

        // insert
        final StringBuilder sqlBuffer = new StringBuilder(128);
        sqlBuffer.append("insert into ");
        sqlBuffer.append(getQualifiedName(connection.getSchema(),
                metaData.getTableName(), connection));

        // columns
        sqlBuffer.append(" (");
        String columnSeparator = "";
        for (int i = 0; i < columns.length; i++)
        {
            if (!ignoreMapping.get(i))
            {
                // escape column name
                String columnName = getQualifiedName(null,
                        columns[i].getColumnName(), connection);
                sqlBuffer.append(columnSeparator);
                sqlBuffer.append(columnName);
                columnSeparator = ", ";
            }
        }

        // values
        sqlBuffer.append(") values (");
        String valueSeparator = "";
        for (int i = 0; i < columns.length; i++)
        {
            if (!ignoreMapping.get(i))
            {
                sqlBuffer.append(valueSeparator);
                sqlBuffer.append("?");
                valueSeparator = ", ";
            }
        }
        sqlBuffer.append(")");

        return new OperationData(sqlBuffer.toString(), columns);
    }

    protected BitSet getIgnoreMapping(ITable table, int row) throws DataSetException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("getIgnoreMapping(table={}, row={}) - start", table, row);
        }

        Column[] columns = table.getTableMetaData().getColumns();

        BitSet ignoreMapping = new BitSet();
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            Object value = getValueOrNoValueIfMissing(table, row, column);
            if (wouldIgnore(column, value))
            {
                ignoreMapping.set(i);
            }
        }
        return ignoreMapping;
    }

    protected boolean equalsIgnoreMapping(BitSet ignoreMapping, ITable table,
            int row) throws DataSetException
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("equalsIgnoreMapping(ignoreMapping={}, table={}, row={}) - start",
                    ignoreMapping, table, row);
        }

        Column[] columns = table.getTableMetaData().getColumns();

        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            Object value = getValueOrNoValueIfMissing(table, row, column);
            if (wouldIgnore(column, value) != ignoreMapping.get(i))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Reads the row's value for the given column, treating a column the row's
     * underlying table does not itself declare as not supplied rather than an
     * error. This tolerates a {@code CompositeDataSet} merging same-named
     * tables whose column sets differ -- e.g. two flat-XML files feeding the
     * same table where only one declares an optional column -- since such a
     * table's own metadata (searched by {@link #getIgnoreMapping} and
     * {@link #equalsIgnoreMapping} to build {@code columns}) can list a column
     * that a specific row's backing part never itself had.
     * <p>
     * Deliberately local to {@code InsertOperation}: a missing column is only
     * safe to treat as "not supplied" here because {@link #wouldIgnore} then
     * omits it from the generated insert statement entirely. Other operations
     * (update, delete) bind every requested column's value directly with no
     * equivalent ignore-mapping, so a genuinely missing column there must keep
     * throwing {@link NoSuchColumnException} instead of silently binding
     * {@code NULL} into a {@code WHERE} clause.
     *
     * @param table
     *            The table being read.
     * @param row
     *            The row index.
     * @param column
     *            The column to read.
     * @return The row's value for the column, or {@link ITable#NO_VALUE} if
     *         the row's underlying table does not have this column at all.
     * @throws DataSetException
     *             if the value cannot be retrieved for any other reason.
     */
    private static Object getValueOrNoValueIfMissing(final ITable table,
            final int row, final Column column) throws DataSetException
    {
        try
        {
            return table.getValue(row, column.getColumnName());
        } catch (final NoSuchColumnException e)
        {
            return ITable.NO_VALUE;
        }
    }

    /**
     * Determines whether a column's value would be omitted from the insert
     * statement: either because no value was supplied at all, or because the
     * value is {@code null} for a not-nullable column that has a database
     * default, in which case the column is left out so the database applies
     * its default instead of a {@code NULL} insert failing the constraint.
     * Used by both {@link #getIgnoreMapping(ITable, int)} and
     * {@link #equalsIgnoreMapping(BitSet, ITable, int)} so the two can never
     * diverge.
     *
     * @param column
     *            The column being evaluated.
     * @param value
     *            The row's value for that column.
     * @return {@code true} if the column would be omitted from the insert.
     */
    private static boolean wouldIgnore(final Column column, final Object value)
    {
        return value == ITable.NO_VALUE || (value == null
                && column.isNotNullable() && column.hasDefaultValue());
    }
}
