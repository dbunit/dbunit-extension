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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.database.AmbiguousTableNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Associates a table name with an arbitrary object. Moreover the
 * order of the added table names is maintained and the ordered table
 * names can be retrieved via {@link #getTableNames()}.
 * <p>
 * The map ensures that one table name can only be added once.
 * </p>
 * 
 * TODO In the future it might be discussed if a ListOrderedMap (apache-commons-collections) can/should be used.
 * 
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$
 * @since 2.4.0
 */
public class OrderedTableNameMap
{
    /**
     * Logger for this class
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedTableNameMap.class);

	/**
	 * The map for fast access to the existing table names and for
	 * associating an arbitrary object with a table name
	 */
	private Map _tableMap = new HashMap();
	/**
	 * Chronologically ordered list of table names - keeps the order
	 * in which the table names have been added as well as the case in
	 * which the table has been added
	 */
	private List _tableNames = new ArrayList();
	
	private String _lastTableNameOverride;
	
	/**
	 * Whether or not case sensitive table names should be used. Defaults to false.
	 */
	private boolean _caseSensitiveTableNames = false;
	
	
	/**
     * Creates a new map which does strictly force that one table can only occur once.
     * @param caseSensitiveTableNames Whether or not table names should be case sensitive
     */
    public OrderedTableNameMap(boolean caseSensitiveTableNames)
    {
        _caseSensitiveTableNames = caseSensitiveTableNames;
    }

	/**
	 * Returns the object associated with the given table name
	 * @param tableName The table name for which the associated object is retrieved
	 * @return The object that has been associated with the given table name
	 */
	public Object get(String tableName) 
	{
	    String correctedCaseTableName = this.getTableName(tableName);
		return this._tableMap.get(correctedCaseTableName);
	}


	/**
	 * Provides the ordered table names having the same order in which the table
	 * names have been added via {@link #add(String, Object)}.
	 * @return The list of table names ordered in the sequence as
	 * they have been added to this map
	 */
	public String[] getTableNames() 
	{
		return (String[])this._tableNames.toArray(new String[0]);
	}

	/**
	 * Checks if this map contains the given table name
	 * @param tableName
	 * @return Returns <code>true</code> if the map of tables contains the given table name
	 */
	public boolean containsTable(String tableName) 
	{
	    String correctedCaseTableName = this.getTableName(tableName);
		return _tableMap.containsKey(correctedCaseTableName);
	}

    /**
     * @param tableName The table name to check
     * @return <code>true</code> if the given tableName matches the last table that has been added to this map.
     */
    public boolean isLastTable(String tableName) 
    {
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("isLastTable(tableName={}) - start", tableName);
        
        if(this._tableNames.size() == 0)
        {
            return false;
        }
        else 
        {
            String lastTable = getLastTableName();
            String lastTableCorrectCase = this.getTableName(lastTable);
            String inputTableCorrectCase = this.getTableName(tableName);
            return lastTableCorrectCase.equals(inputTableCorrectCase);
        }
    }

    /**
     * @return The name of the last table that has been added to this map. Returns <code>null</code> if no 
     * table has been added yet.
     */
    public String getLastTableName()
    {
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("getLastTableName() - start");
        
        if(_lastTableNameOverride != null)
        {
            return _lastTableNameOverride;
        }
        
        if(_tableNames.size()>0)
        {
            String lastTable = (String) _tableNames.get(this._tableNames.size()-1);
            return lastTable;
        }
        else
        {
            return null;
        }
    }
    

    public void setLastTable(String tableName) throws NoSuchTableException 
    {
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("setLastTable(name{}) - start", tableName);
        
        if(!this.containsTable(tableName))
        {
            throw new NoSuchTableException(tableName);
        }
        
        this._lastTableNameOverride = tableName;
    }

	/**
	 * Adds the given table name to the map of table names, associating 
	 * it with the given object.
	 * @param tableName The table name to be added
	 * @param object Object to be associated with the given table name. Can be null
	 * @throws AmbiguousTableNameException If the given table name already exists
	 */
	public void add(String tableName, Object object) throws AmbiguousTableNameException 
	{
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("add(tableName={}, object={}) - start", tableName, object);
	    
	    // Get the table name in the correct case
        String tableNameCorrectedCase = this.getTableName(tableName);
        // prevent table name conflict
        if (this.containsTable(tableNameCorrectedCase))
        {
            throw new AmbiguousTableNameException(tableNameCorrectedCase);
        }
        else {
            this._tableMap.put(tableNameCorrectedCase, object);
            this._tableNames.add(tableName);
            // Reset the override of the lastTableName
            this._lastTableNameOverride = null;
        }
	}
	
    /**
     * @return The values of this map ordered in the sequence they have been added
     */
    public Collection orderedValues() 
    {
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("orderedValues() - start");

        List orderedValues = new ArrayList(this._tableNames.size());
        for (Iterator iterator = _tableNames.iterator(); iterator.hasNext();) {
            String tableName = (String) iterator.next();
            Object object = this.get(tableName);
            orderedValues.add(object);
        }
        return orderedValues;
    }
    
	/**
	 * Updates the value associated with the given table name. Must be invoked if
	 * the table name has already been added before.
	 * @param tableName The table name for which the association should be updated
	 * @param object The new object to be associated with the given table name
	 */
	public void update(String tableName, Object object) 
	{
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("update(tableName={}, object={}) - start", tableName, object);

        // prevent table name conflict
        if (!this.containsTable(tableName))
        {
        	throw new IllegalArgumentException("The table name '" + tableName + "' does not exist in the map");
        }
        tableName = this.getTableName(tableName);
        this._tableMap.put(tableName, object);
	}

    /**
     * Returns the table name in the correct case (for example as upper case string)
     * @param tableName The input table name to be resolved
     * @return The table name for the given string in the correct case.
     */
    public String getTableName(String tableName) 
    {
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("getTableName(tableName={}) - start", tableName);
        
        String result = tableName;
        if(!_caseSensitiveTableNames)
        {
            // "Locale.ENGLISH" Fixes bug #1537894 when clients have a special
            // locale like turkish. (for release 2.4.3)
            result = tableName.toUpperCase(Locale.ENGLISH);
        }

        if(LOGGER.isDebugEnabled())
            LOGGER.debug("getTableName(tableName={}) - end - result={}", tableName, result);
        
        return result;
    }

	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(getClass().getName()).append("[");
		sb.append("_tableNames=").append(_tableNames);
		sb.append(", _tableMap=").append(_tableMap);
		sb.append(", _caseSensitiveTableNames=").append(_caseSensitiveTableNames);
		sb.append("]");
		return sb.toString();
	}
	
}