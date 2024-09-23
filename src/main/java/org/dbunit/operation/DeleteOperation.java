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

import java.util.BitSet;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.NoPrimaryKeyException;

/**
 * Deletes only the dataset contents from the database. This operation does not
 * delete the entire table contents but only data that are present in the
 * dataset.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 19, 2002
 */
public class DeleteOperation extends AbstractBatchOperation
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DeleteOperation.class);

    DeleteOperation()
    {
        _reverseRowOrder = true;
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractBatchOperation class

    protected ITableIterator iterator(IDataSet dataSet) throws DatabaseUnitException
    {
        logger.debug("iterator(dataSet={}) - start", dataSet);
        return dataSet.reverseIterator();
    }

    public OperationData getOperationData(ITableMetaData metaData, BitSet ignoreMapping, IDatabaseConnection connection) throws DataSetException
    {
    	if (logger.isDebugEnabled())
    	{
    		logger.debug("getOperationData(metaData={}, ignoreMapping={}, connection={}) - start",
    				metaData, ignoreMapping, connection);
    	}

        // cannot construct where clause if no primary key
        Column[] primaryKeys = metaData.getPrimaryKeys();
        if (primaryKeys.length == 0)
        {
            throw new NoPrimaryKeyException(metaData.getTableName());
        }

        // delete from
        final StringBuilder sqlBuffer = new StringBuilder(128);
        sqlBuffer.append("delete from ");
        sqlBuffer.append(getQualifiedName(connection.getSchema(),
                metaData.getTableName(), connection));

        // where
        sqlBuffer.append(" where ");
        for (int i = 0; i < primaryKeys.length; i++)
        {
            // escape column name
            String columnName = getQualifiedName(null,
                    primaryKeys[i].getColumnName(), connection);
            sqlBuffer.append(columnName);

            sqlBuffer.append(" = ?");
            if (i + 1 < primaryKeys.length)
            {
                sqlBuffer.append(" and ");
            }
        }

        return new OperationData(sqlBuffer.toString(), primaryKeys);
    }

}
