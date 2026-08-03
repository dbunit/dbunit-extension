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
 * This filter exposes only tables matching include patterns and not matching
 * exclude patterns. This implementation do not modify the original table
 * sequence from the filtered dataset and support duplicate table names.
 *
 * @author Manuel Laflamme
 * @since Apr 17, 2004
 * @version $Revision$
 */
public class DefaultTableFilter extends AbstractTableFilter implements ITableFilter
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultTableFilter.class);

    private final IncludeTableFilter _includeFilter = new IncludeTableFilter();
    private final ExcludeTableFilter _excludeFilter = new ExcludeTableFilter();

    /**
     * Add a new accepted table name pattern.
     * The following wildcard characters are supported:
     * '*' matches zero or more characters,
     * '?' matches one character.
     * @param patternName the table name pattern to accept.
     */
    public void includeTable(String patternName)
    {
        logger.debug("includeTable(patternName=" + patternName + ") - start");

        _includeFilter.includeTable(patternName);
    }

    /**
     * Add a new refused table pattern name.
     * The following wildcard characters are supported:
     * '*' matches zero or more characters,
     * '?' matches one character.
     * @param patternName the table name pattern to refuse.
     */
    public void excludeTable(String patternName)
    {
        logger.debug("excludeTable(patternName=" + patternName + ") - start");

        _excludeFilter.excludeTable(patternName);
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractTableFilter interface

    public boolean isValidName(String tableName) throws DataSetException
    {
        logger.debug("isValidName(tableName=" + tableName + ") - start");

        if (_includeFilter.isEmpty() || _includeFilter.accept(tableName))
        {
            return _excludeFilter.accept(tableName);
        }
        return false;
    }
}
