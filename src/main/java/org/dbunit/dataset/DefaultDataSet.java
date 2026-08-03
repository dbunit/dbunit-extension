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

package org.dbunit.dataset;

import org.dbunit.database.AmbiguousTableNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple implementation of a dataset backed by {@link ITable} objects which can
 * be added dynamically.
 * 
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Feb 18, 2002)
 */
public class DefaultDataSet extends AbstractDataSet
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultDataSet.class);

    public DefaultDataSet()
    {
    	super();
    }

    /**
     * Creates a default dataset which is empty initially
     * @param caseSensitiveTableNames
     * @since 2.4.2
     */
    public DefaultDataSet(boolean caseSensitiveTableNames)
    {
        super(caseSensitiveTableNames);
    }

    public DefaultDataSet(ITable table) throws AmbiguousTableNameException
    {
        this(new ITable[]{table});
    }

    public DefaultDataSet(ITable table1, ITable table2) throws AmbiguousTableNameException
    {
        this(new ITable[] {table1, table2});
    }

    public DefaultDataSet(ITable[] tables) throws AmbiguousTableNameException
    {
        this(tables, false);
    }
    
    /**
     * Creates a default dataset which consists of the given tables
     * @param caseSensitiveTableNames
     * @since 2.4.2
     */
    public DefaultDataSet(ITable[] tables, boolean caseSensitiveTableNames) throws AmbiguousTableNameException
    {
        super(caseSensitiveTableNames);
        
        for (int i = 0; i < tables.length; i++)
        {
            addTable(tables[i]);
        }
    }

    /**
     * Add a new table in this dataset.
     * @throws AmbiguousTableNameException 
     */
    public void addTable(ITable table) throws AmbiguousTableNameException
    {
        logger.debug("addTable(table={}) - start", table);
        
        this.initialize();
        
        super._orderedTableNameMap.add(table.getTableMetaData().getTableName(), table);
    }

    /**
     * Initializes the {@link _orderedTableNameMap} of the parent class if it is not initialized yet.
     * @since 2.4.6
     */
    protected void initialize()
    {
        logger.debug("initialize() - start");
        
        if(_orderedTableNameMap != null)
        {
            logger.debug("The table name map has already been initialized.");
            // already initialized
            return;
        }
       
        // Gather all tables in the OrderedTableNameMap which also makes the duplicate check
        _orderedTableNameMap = this.createTableNameMap();

    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    protected ITableIterator createIterator(boolean reversed)
            throws DataSetException
    {
        logger.debug("createIterator(reversed={}) - start", Boolean.toString(reversed));
        
        this.initialize();
        
        ITable[] tables = (ITable[])_orderedTableNameMap.orderedValues().toArray(new ITable[0]);
        return new DefaultTableIterator(tables, reversed);
    }
}






