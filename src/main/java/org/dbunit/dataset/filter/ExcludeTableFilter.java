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
package org.dbunit.dataset.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.DataSetException;


/**
 * This filter hides specified tables from the filtered dataset. This
 * implementation do not modify the original table order from the filtered
 * dataset and support duplicate table names.
 *
 * @author Manuel Laflamme
 * @since Mar 7, 2003
 * @version $Revision$
 */
public class ExcludeTableFilter extends AbstractTableFilter implements ITableFilter
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ExcludeTableFilter.class);

    private final PatternMatcher _patternMatcher = new PatternMatcher();

    /**
     * Create a new empty ExcludeTableFilter. Use {@link #excludeTable} to hide
     * some tables.
     */
    public ExcludeTableFilter()
    {
    }

    /**
     * Create a new ExcludeTableFilter which prevent access to specified tables.
     * @param tableNames the names of the tables to hide.
     */
    public ExcludeTableFilter(String[] tableNames)
    {
        for (int i = 0; i < tableNames.length; i++)
        {
            String tableName = tableNames[i];
            excludeTable(tableName);
        }
    }

    /**
     * Add a new refused table pattern name.
     * The following wildcard characters are supported:
     * '*' matches zero or more characters,
     * '?' matches one character.
     * @param patternName the table name pattern to hide.
     */
    public void excludeTable(String patternName)
    {
        logger.debug("excludeTable(patternName=" + patternName + ") - start");

        _patternMatcher.addPattern(patternName);
    }

    /**
     * Returns whether no tables have been excluded yet.
     * @return <code>true</code> if no tables have been excluded yet.
     */
    public boolean isEmpty()
    {
        logger.debug("isEmpty() - start");

        return _patternMatcher.isEmpty();
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableFilter interface

    public boolean isValidName(String tableName) throws DataSetException
    {
        logger.debug("isValidName(tableName=" + tableName + ") - start");

        return !_patternMatcher.accept(tableName);
    }
}
