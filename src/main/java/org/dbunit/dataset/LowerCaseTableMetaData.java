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

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Specialized ITableMetaData implementation that convert the table name and
 * column names to lower case. Used in DbUnit own test suite to verify that
 * operations are case insensitive.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 14, 2003
 */
public class LowerCaseTableMetaData extends AbstractTableMetaData
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(LowerCaseTableMetaData.class);

    private final String _tableName;
    private final Column[] _columns;
    private final Column[] _primaryKeys;

    /**
     * Creates metadata with lower-cased table and column names, and no primary keys.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     */
    public LowerCaseTableMetaData(String tableName, Column[] columns)
            //throws DataSetException
    {
        this(tableName, columns, new Column[0]);
    }

    /**
     * Creates metadata with lower-cased table and column names, and the given primary key column names.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     * @param primaryKeys the names of the primary key columns.
     */
    public LowerCaseTableMetaData(String tableName, Column[] columns,
            String[] primaryKeys) //throws DataSetException
    {
        this(tableName, columns, Columns.getColumns(primaryKeys, columns) );
    }

    /**
     * Creates metadata with the lower-cased table and column names of the given metadata.
     *
     * @param metaData the metadata to lower-case.
     * @throws DataSetException if the given metadata's columns cannot be retrieved.
     */
    public LowerCaseTableMetaData(ITableMetaData metaData) throws DataSetException
    {
        this(metaData.getTableName(), metaData.getColumns(),
                metaData.getPrimaryKeys());
    }

    /**
     * Creates metadata with lower-cased table and column names, and the given primary key columns.
     *
     * @param tableName the table name.
     * @param columns the table columns.
     * @param primaryKeys the primary key columns.
     */
    public LowerCaseTableMetaData(String tableName, Column[] columns,
            Column[] primaryKeys) //throws DataSetException
    {
        _tableName = tableName.toLowerCase(Locale.ENGLISH);
        _columns = createLowerColumns(columns);
        _primaryKeys = createLowerColumns(primaryKeys);
    }

    private Column[] createLowerColumns(Column[] columns)
    {
        logger.debug("createLowerColumns(columns={}) - start", (Object) columns);

        Column[] lowerColumns = new Column[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            lowerColumns[i] = createLowerColumn(columns[i]);
        }

        return lowerColumns;
    }

    private Column createLowerColumn(Column column)
    {
        logger.debug("createLowerColumn(column={}) - start", column);

        return new Column(
                column.getColumnName().toLowerCase(Locale.ENGLISH),
                column.getDataType(),
                column.getSqlTypeName(),
                column.getNullable(),
                column.getDefaultValue());
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
