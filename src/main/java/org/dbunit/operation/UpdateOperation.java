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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoPrimaryKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates the database from the dataset contents. This operation assumes that
 * table data already exists in the database and fails if this is not the case.

 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class UpdateOperation extends AbstractBatchOperation
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(UpdateOperation.class);

    UpdateOperation()
    {
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractBatchOperation class

    public OperationData getOperationData(ITableMetaData metaData, BitSet ignoreMapping, IDatabaseConnection connection) throws DataSetException
    {
        if(logger.isDebugEnabled())
            logger.debug("getOperationData(metaData={}, ignoreMapping={}, connection={}) - start", 
                new Object[]{metaData, ignoreMapping, connection});

        Column[] columns = metaData.getColumns();
        Column[] primaryKeys = metaData.getPrimaryKeys();

        // cannot construct where clause if no primary key
        if (primaryKeys.length == 0)
        {
            throw new NoPrimaryKeyException(metaData.getTableName());
        }

        // update table
        final StringBuilder sqlBuffer = new StringBuilder(128);
        sqlBuffer.append("update ");
        sqlBuffer.append(getQualifiedName(connection.getSchema(),
                metaData.getTableName(), connection));

        // set
        boolean firstSet = true;
        List columnList = new ArrayList(columns.length);
        sqlBuffer.append(" set ");
        for (int i = 0; i < columns.length; i++)
        {
            Column column = columns[i];

            // set if not primary key
            if (Columns.getColumn(column.getColumnName(), primaryKeys) == null)
            {
                if (!firstSet)
                {
                    sqlBuffer.append(", ");
                }
                firstSet = false;

                // escape column name
                String columnName = getQualifiedName(null,
                        column.getColumnName(), connection);
                sqlBuffer.append(columnName);
                sqlBuffer.append(" = ?");
                columnList.add(column);
            }
        }

        // where
        sqlBuffer.append(" where ");
        for (int i = 0; i < primaryKeys.length; i++)
        {
            Column column = primaryKeys[i];

            if (i > 0)
            {
                sqlBuffer.append(" and ");
            }

            // escape column name
            String columnName = getQualifiedName(null,
                    column.getColumnName(), connection);
            sqlBuffer.append(columnName);
            sqlBuffer.append(" = ?");
            columnList.add(column);
        }

        return new OperationData(sqlBuffer.toString(),
                (Column[])columnList.toArray(new Column[0]));
    }

}
