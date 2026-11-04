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

package org.dbunit.dataset.yaml;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.RequiredLibrary;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.dataset.JdbcEscapeText;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Writes an {@link IDataSet} to a {@link Writer} in YAML format.
 *
 * <p>The writer leaves a null cell, or a cell with no value, out of the mapping of a row,
 * except in the first row of a table. That row carries every column of the table, with
 * {@code null} for such a cell, so a column that is null in every row still appears in
 * the output and survives a read-back.
 *
 * <p>The writer writes a date, a time or a timestamp as the JDBC escape text that the column
 * types parse, see {@link JdbcEscapeText}, where SnakeYAML would write an instant in UTC, so
 * that a date reads back as the same day, and a timestamp as the same date and time, in any
 * time zone.
 *
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */
class YamlWriter
{

    private Yaml _yaml;
    private Writer _out;
    private boolean _useFlowStyle;

    public YamlWriter(Writer out)
    {
        this(out, false);
    }

    public YamlWriter(Writer out, boolean useFlowStyle)
    {
        RequiredLibrary.SNAKEYAML.requireUnchecked(YamlWriter.class);
        this._out = out;
        this._useFlowStyle = useFlowStyle;
    }

    public void setUseFlowStyle(boolean useFlowStyle)
    {
        this._useFlowStyle = useFlowStyle;
    }

    public void write(IDataSet dataSet) throws DataSetException
    {
        LinkedHashMap<String, List<LinkedHashMap<String, Object>>> dataSetAsMap = dataSetAsMap(dataSet);
        DumperOptions options = new DumperOptions();
        if (_useFlowStyle)
        {
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
            options.setPrettyFlow(true);
        } else
        {
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(4);
            options.setIndicatorIndent(2);
        }
        _yaml = new Yaml(options);
        _yaml.dump(dataSetAsMap, _out);
    }

    private LinkedHashMap<String, List<LinkedHashMap<String, Object>>> dataSetAsMap(IDataSet dataSet) throws DataSetException
    {
        LinkedHashMap<String, List<LinkedHashMap<String, Object>>> result = new LinkedHashMap<>();
        ITableIterator iterator = dataSet.iterator();
        while (iterator.next())
        {
            ITableMetaData tableMetaData = iterator.getTableMetaData();
            List<LinkedHashMap<String, Object>> tableRows =
                    result.computeIfAbsent(tableMetaData.getTableName(), name -> new ArrayList<>());
            ITable table = iterator.getTable();
            Column[] columns = tableMetaData.getColumns();
            if (columns.length == 0)
            {
                continue;
            }
            int rowCount = rowCountOf(table);
            try
            {
                for (int row = 0; row < rowCount; row++)
                {
                    boolean firstRow = tableRows.isEmpty();
                    LinkedHashMap<String, Object> rowMap = new LinkedHashMap<>();
                    for (Column column : columns)
                    {
                        String columnName = column.getColumnName();
                        Object value = table.getValue(row, columnName);
                        boolean noValue = value == null || value == ITable.NO_VALUE;
                        if (noValue)
                        {
                            // The first row carries every column, so that a column that is null
                            // in every row still appears in the document and survives a read-back.
                            if (firstRow)
                            {
                                rowMap.put(columnName, null);
                            }
                        } else
                        {
                            rowMap.put(columnName, JdbcEscapeText.of(value));
                        }
                    }
                    tableRows.add(rowMap);
                }
            } catch (RowOutOfBoundsException e)
            {
                // The rows of the table are exhausted, as a streaming table reports it.
            }
        }
        return result;
    }

    /**
     * Returns the row count of the table, or an unbounded count for a table that cannot tell it,
     * such as a streaming table, whose rows end with a {@link RowOutOfBoundsException}.
     */
    private static int rowCountOf(ITable table)
    {
        try
        {
            return table.getRowCount();
        } catch (UnsupportedOperationException e)
        {
            return Integer.MAX_VALUE;
        }
    }
}
