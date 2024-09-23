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

import java.util.Arrays;

import org.dbunit.dataset.Column;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public class OperationData
{

    private final String _sql;
    private final Column[] _columns;

    /**
     * @param sql
     * @param columns
     */
    public OperationData(String sql, Column[] columns)
    {
        _sql = sql;
        _columns = columns;
    }

    public String getSql()
    {
        return _sql;
    }

    public Column[] getColumns()
    {
        return _columns;
    }
    
    public String toString()
    {
    	final StringBuilder sb = new StringBuilder();
    	sb.append(getClass().getName()).append("[");
    	sb.append("_sql=").append(_sql);
    	sb.append(", _columns=").append(_columns==null ? "null" : Arrays.asList(_columns).toString());
    	sb.append("]");
    	return sb.toString();
    }
    
}



