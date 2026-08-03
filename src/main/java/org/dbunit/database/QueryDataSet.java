/*
 *
 *  The DbUnit Database Testing Framework
 *  Copyright (C)2002-2004, DbUnit.org
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dbunit.dataset.AbstractDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.OrderedTableNameMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds collection of tables resulting from database query.
 *
 * @author Eric Pugh
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Dec 4, 2002
 */
public class QueryDataSet extends AbstractDataSet
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(QueryDataSet.class);

    private final IDatabaseConnection _connection;
    private final OrderedTableNameMap _tables;


    /**
     * Create a QueryDataSet by passing in the connection to the database to use.
     *
     * @param connection The connection object to the database.
     */
    public QueryDataSet(IDatabaseConnection connection)
    {
        this(connection, connection.getConfig().getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES));
    }

    /**
     * Create a QueryDataSet by passing in the connection to the database to use.
     *
     * @param connection The connection object to the database.
     * @param caseSensitiveTableNames Whether or not this dataset should use case sensitive table names
     * @since 2.4.2
     */
    public QueryDataSet(IDatabaseConnection connection, boolean caseSensitiveTableNames)
    {
        super(caseSensitiveTableNames);
        if (connection == null) {
            throw new NullPointerException("The parameter 'connection' must not be null");
        }
        _connection = connection;
        _tables = super.createTableNameMap();
    }

    /**
     *  Adds a table and it's associated query to this dataset.
     *
     * @param tableName The name of the table
     * @param query The query to retrieve data with for this table. Can be null which will select
     * all data (see {@link #addTable(String)} for details)
     * @throws AmbiguousTableNameException if the given table name was already added.
     */
    public void addTable(String tableName, String query) throws AmbiguousTableNameException
    {
        logger.debug("addTable(tableName={}, query={}) - start", tableName, query);
        _tables.add(tableName, new TableEntry(tableName, query));
    }

    /**
     *  Adds a table with using 'SELECT * FROM <code>tableName</code>' as query.
     *
     * @param tableName The name of the table
     * @throws AmbiguousTableNameException if the given table name was already added.
     */
    public void addTable(String tableName) throws AmbiguousTableNameException
    {
        logger.debug("addTable(tableName={}) - start", tableName);
        this.addTable(tableName, null);
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed) throws DataSetException
    {
    	if(logger.isDebugEnabled())
    		logger.debug("createIterator(reversed={}) - start", String.valueOf(reversed));
    	
        List tableEntries = new ArrayList(_tables.orderedValues());
        if (reversed)
        {
            Collections.reverse(tableEntries);
        }

        return new QueryTableIterator(tableEntries, _connection);
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSet interface

    public String[] getTableNames() throws DataSetException
    {
        logger.debug("getTableNames() - start");
        return this._tables.getTableNames();
    }

    /**
     * Represents a table and a SQL query that should be used to retrieve the
     * data for this table.
     */
    static class TableEntry
    {
        private final String _tableName;
        private final String _query;

        public TableEntry(String tableName, String query)
        {
            _tableName = tableName;
            _query = query;
        }

        public String getTableName()
        {
            return _tableName;
        }

        public String getQuery()
        {
            return _query;
        }
    }
}


