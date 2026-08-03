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
package org.dbunit.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IResultSetTableFactory} that creates fully cached, disconnected {@link IResultSetTable} instances.
 *
 * @author manuel.laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.0 (Jul 31, 2003)
 */
public class CachedResultSetTableFactory implements IResultSetTableFactory
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CachedResultSetTableFactory.class);

    public IResultSetTable createTable(String tableName, String selectStatement,
            IDatabaseConnection connection) throws SQLException, DataSetException
    {
    	if (logger.isTraceEnabled())	
    		logger.trace("createTable(tableName={}, selectStatement={}, connection={}) - start", 
    				new Object[] { tableName, selectStatement, connection });

        return new CachedResultSetTable(new ForwardOnlyResultSetTable(
                tableName, selectStatement, connection));
    }

    public IResultSetTable createTable(ITableMetaData metaData,
            IDatabaseConnection connection) throws SQLException, DataSetException
    {
        logger.trace("createTable(metaData={}, connection={}) - start", metaData, connection);
        ForwardOnlyResultSetTable resultSetTable = new ForwardOnlyResultSetTable(metaData, connection);
        return new CachedResultSetTable(resultSetTable);
    }
    
    public IResultSetTable createTable(String tableName,
            PreparedStatement preparedStatement, IDatabaseConnection connection) 
    throws SQLException, DataSetException
    {
        if (logger.isTraceEnabled())
            logger.trace("createTable(tableName={}, preparedStatement={}, connection={}) - start",
                new Object[]{ tableName, preparedStatement, connection});
        
        // Reuse method from ForwardOnly factory
        ForwardOnlyResultSetTable table = new ForwardOnlyResultSetTableFactory()
                    .createForwardOnlyResultSetTable(tableName, preparedStatement, connection);
        return new CachedResultSetTable(table);
    }

}
