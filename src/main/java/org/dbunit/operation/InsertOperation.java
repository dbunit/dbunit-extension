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
    	logger.debug("getIgnoreMapping(table={}, row={}) - start", table, row);

        Column[] columns = table.getTableMetaData().getColumns();

        BitSet ignoreMapping = new BitSet();
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];
            Object value = table.getValue(row, column.getColumnName());
            if (value == ITable.NO_VALUE
                || (value == null && column.isNotNullable() && column.hasDefaultValue()))
            {
                ignoreMapping.set(i);
            }
        }
        return ignoreMapping;
    }

    protected boolean equalsIgnoreMapping(BitSet ignoreMapping, ITable table,
            int row) throws DataSetException
    {
    	logger.debug("equalsIgnoreMapping(ignoreMapping={}, table={}, row={}) - start",
    			 ignoreMapping, table, row);

        Column[] columns = table.getTableMetaData().getColumns();

        for (int i = 0; i < columns.length; i++)
        {
            boolean bit = ignoreMapping.get(i);
            Object value = table.getValue(row, columns[i].getColumnName());
            if ((bit && value != ITable.NO_VALUE) || (!bit && value == ITable.NO_VALUE))
            {
                return false;
            }
        }

        return true;
    }
}
