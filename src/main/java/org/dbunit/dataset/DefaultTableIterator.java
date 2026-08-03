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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ITableIterator} over a fixed, in-memory array of {@link ITable}s.
 *
 * @author Manuel Laflamme
 * @since Apr 5, 2003
 * @version $Revision$
 */
public class DefaultTableIterator implements ITableIterator
{
	private Logger logger = LoggerFactory.getLogger(DefaultTableIterator.class);
	
    private final ITable[] _tables;
    private int _index = -1;

    public DefaultTableIterator(ITable[] tables)
    {
        _tables = tables;
    }

    public DefaultTableIterator(ITable[] tables, boolean reversed)
    {
        if (reversed)
        {
            ITable[] reverseTables = new ITable[tables.length];
            for (int i = 0; i < tables.length; i++)
            {
                reverseTables[tables.length - 1 - i] = tables[i];
            }
            tables = reverseTables;
        }

        _tables = tables;
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableIterator interface

    public boolean next() throws DataSetException
    {
        _index++;
        return _index < _tables.length;
    }

    public ITableMetaData getTableMetaData() throws DataSetException
    {
    	logger.debug("getTableMetaData() - start");
    	
        return getTable().getTableMetaData();
    }

    public ITable getTable() throws DataSetException
    {
    	logger.debug("getTable() - start");

    	return _tables[_index];
    }
}
