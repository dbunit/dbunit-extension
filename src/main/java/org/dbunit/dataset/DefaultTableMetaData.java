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

import java.util.Arrays;


/**
 * Straightforward {@link ITableMetaData} implementation holding an explicit table
 * name, column list, and primary keys.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 17, 2002
 */
public class DefaultTableMetaData extends AbstractTableMetaData
{

    private final String _tableName;
    private final Column[] _columns;
    private final Column[] _primaryKeys;

    /**
     * Creates metadata for a table with no primary keys.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     */
    public DefaultTableMetaData(String tableName, Column[] columns)
            //throws DataSetException
    {
        this(tableName, columns, new String[0]);
    }

    /**
     * Creates metadata for a table with the given primary key column names.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     * @param primaryKeys the names of the primary key columns.
     */
    public DefaultTableMetaData(String tableName, Column[] columns,
            String[] primaryKeys) //throws DataSetException
    {
        _tableName = tableName;
        _columns = columns;
        _primaryKeys = Columns.getColumns(primaryKeys, columns);
    }

    /**
     * Creates metadata for a table with the given primary key columns.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     * @param primaryKeys the primary key columns.
     */
    public DefaultTableMetaData(String tableName, Column[] columns,
            Column[] primaryKeys) //throws DataSetException
    {
        _tableName = tableName;
        _columns = columns;
        _primaryKeys = primaryKeys;
    }

    public String toString()
    {
        return "tableName=" + _tableName +
                ", columns=" + Arrays.asList(_columns) +
                ", keys=" + Arrays.asList(_primaryKeys) + "";
    }

    ////////////////////////////////////////////////////////////////////////////
    // ITableMetaData interface

    public String getTableName()
    {
        return _tableName;
    }

    public Column[] getColumns()
    {
        return _columns;
    }

    public Column[] getPrimaryKeys()
    {
        return _primaryKeys;
    }
}
