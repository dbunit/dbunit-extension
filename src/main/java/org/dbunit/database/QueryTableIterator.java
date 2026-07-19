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

import java.sql.SQLException;
import java.util.List;

import org.dbunit.database.QueryDataSet.TableEntry;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterator used to iterate over a list of tables using a specific query for retrieving
 * data for every table.
 * 
 * @author Manuel Laflamme
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5.6 (Sep 15, 2003)
 */
public class QueryTableIterator implements ITableIterator
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(QueryTableIterator.class);

    private final List _tableEntries;
    private final IDatabaseConnection _connection;
    private IResultSetTable _currentTable;
    private int _index = -1;

    /**
     * @param tableEntries list of {@link TableEntry} objects
     * @param connection The database connection needed to load data
     */
    public QueryTableIterator(List tableEntries, IDatabaseConnection connection)
    {
    	if (tableEntries == null) {
			throw new NullPointerException(
					"The parameter 'tableEntries' must not be null");
		}
    	if (connection == null) {
			throw new NullPointerException(
					"The parameter 'connection' must not be null");
		}
    	
        _tableEntries = tableEntries;
        _connection = connection;
        _currentTable = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableIterator interface

    /**
     * {@inheritDoc}
     */
    public boolean next() throws DataSetException
    {
        logger.debug("next() - start");

        _index++;

        // Ensure previous table is closed
        if (_currentTable != null)
        {
            _currentTable.close();
            _currentTable = null;
        }

        return _index < _tableEntries.size();
    }

    public boolean nextWithoutClosing()
    {
        _index++;
        _currentTable = null;

        return _index < _tableEntries.size();
    }

    /**
     * {@inheritDoc}
     */
    public ITableMetaData getTableMetaData() throws DataSetException
    {
        logger.debug("getTableMetaData() - start");

        return getTable().getTableMetaData();
    }

    /**
     * {@inheritDoc}
     */
    public ITable getTable() throws DataSetException
    {
        logger.debug("getTable() - start");

        if (_currentTable == null)
        {
            try
            {
                QueryDataSet.TableEntry entry = (QueryDataSet.TableEntry)_tableEntries.get(_index);

                // No query specified, use table from dataset
                if (entry.getQuery() == null)
                {
                    _currentTable = (IResultSetTable)_connection.createTable(entry.getTableName());
                }
                else
                {
                    DatabaseConfig config = _connection.getConfig();
                    IResultSetTableFactory factory = (IResultSetTableFactory)config.getProperty(
                            DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY);

                    _currentTable = factory.createTable(entry.getTableName(), entry.getQuery(), _connection);
                }
            }
            catch (SQLException e)
            {
                throw new DataSetException(e);
            }
        }
        return _currentTable;
    }
}
