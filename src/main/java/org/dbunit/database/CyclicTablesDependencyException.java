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

import java.util.Set;

import org.dbunit.dataset.DataSetException;

/**
 * Signals that a set of tables has a foreign-key dependency cycle that cannot be
 * resolved into a linear ordering.
 *
 * @author Manuel Laflamme
 * @since Mar 23, 2003
 * @version $Revision$
 */
public class CyclicTablesDependencyException extends DataSetException
{
    public CyclicTablesDependencyException(String message)
    {
        super(message);
    }
    
    /**
     * @param tableName
     * @param cyclicTableNames
     * @since 2.4.2
     */
    public CyclicTablesDependencyException(String tableName, Set cyclicTableNames)
    {
        this(buildMessage(tableName, cyclicTableNames));
    }

    private static String buildMessage(String tableName, Set cyclicTableNames) {
        return "Table: " + tableName + " (" + cyclicTableNames.toString() + ")";
    }
}
