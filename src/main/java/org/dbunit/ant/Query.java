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

package org.dbunit.ant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>Query</code> class is just a step placeholder for a table name
 * within an <code>Export</code>.
 *
 * @author Eric Pugh
 * @version $Revision$
 * @since Dec 10, 2002
 */
public class Query
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Query.class);

    private String name;
    private String sql;

    /**
     * Default constructor.
     */
    public Query()
    {
    }

    /**
     * Returns the table name.
     *
     * @return the table name.
     */
    public String getName()
    {
        return name;
    }

    /**
     * Sets the table name.
     *
     * @param name the table name.
     */
    public void setName(String name)
    {
        logger.debug("setName(name={}) - start", name);

        this.name = name;
    }


    public String toString()
    {
        StringBuilder result = new StringBuilder();
        result.append("Query: ");
        result.append(" name=" + name);
        result.append(" sql=" + sql);


        return result.toString();
    }

    /**
     * Returns the query's SQL.
     *
     * @return the query's SQL.
     */
    public String getSql()
    {
        return sql;
    }

    /**
     * Sets the query's SQL.
     *
     * @param sql the query's SQL.
     */
    public void setSql(String sql)
    {
        logger.debug("setSql(sql={}) - start", sql);

        this.sql = sql;
    }
}













