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

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;

import java.sql.SQLException;
import java.util.Arrays;

/**
 * This class is a composite that combines multiple database operation in a
 * single one.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 18, 2002
 */
public class CompositeOperation extends DatabaseOperation
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(CompositeOperation.class);

    private final DatabaseOperation[] _actions;

    /**
     * Creates a new composite operation combining the two specified operations.
     */
    public CompositeOperation(DatabaseOperation action1, DatabaseOperation action2)
    {
        _actions = new DatabaseOperation[]{action1, action2};
    }

    /**
     * Creates a new composite operation combining the specified operations.
     */
    public CompositeOperation(DatabaseOperation[] actions)
    {
        _actions = actions;
    }

    ////////////////////////////////////////////////////////////////////////////
    // DatabaseOperation class

    public void execute(IDatabaseConnection connection, IDataSet dataSet)
            throws DatabaseUnitException, SQLException
    {
        logger.debug("execute(connection={}, , dataSet={}) - start", connection, dataSet);

        for (int i = 0; i < _actions.length; i++)
        {
            DatabaseOperation action = _actions[i];
            action.execute(connection, dataSet);
        }
    }
    
    public String toString()
    {
    	final StringBuilder sb = new StringBuilder();
    	sb.append(getClass().getName()).append("[");
    	sb.append("_actions=").append(this._actions==null ? "null" : Arrays.asList(this._actions).toString());
    	sb.append("]");
    	return sb.toString();
    }
}





