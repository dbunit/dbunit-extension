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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * time zone. It writes a value of any type other than a string, a number, a boolean, an array, a
 * collection or a map, such as a {@link java.util.UUID}, as its {@code toString()} text.
 *
 * <p>The writer writes text that holds a control character, such as a form feed, a NUL or an
 * escape character, as a double-quoted value with YAML escapes, so that it reads back as the
 * same text. SnakeYAML's default would write such text as a {@code !!binary} value, which the
 * reader returns as bytes, and a text column would store the Base64 text of the value. A byte
 * array, a BLOB value, is not text, and the writer still writes it as {@code !!binary}.
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
        // Text must stay text on read-back: the default style writes a string with a control
        // character as !!binary, which the reader returns as bytes.
        options.setNonPrintableStyle(DumperOptions.NonPrintableStyle.ESCAPE);
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
                addEmptyRows(tableRows, table);
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
                            rowMap.put(columnName, yamlValueOf(value));
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
     * Adds an empty row for each row of a table with no columns, so the row count survives a
     * read-back. A table that cannot tell its row count, such as a streaming table, gets no row,
     * as its rows cannot be counted without a column to read.
     */
    private static void addEmptyRows(List<LinkedHashMap<String, Object>> rows, ITable table)
    {
        int rowCount = rowCountOf(table);
        boolean countKnown = rowCount != Integer.MAX_VALUE;
        if (countKnown)
        {
            for (int row = 0; row < rowCount; row++)
            {
                rows.add(new LinkedHashMap<>());
            }
        }
    }

    /**
     * Returns the value as SnakeYAML writes it as a plain value: a string, a number, a boolean, a
     * byte array as they are, and any other value, such as a {@link java.util.UUID}, as its
     * {@code toString()} text. SnakeYAML writes the other value with a global tag, which the
     * reader refuses, or as an empty mapping, which loses the value. An array, a collection or
     * a map stays as it is, as YAML has a sequence and a mapping for it, which the reader rejects
     * in a cell with a message that names the table, the row and the column, where the text of
     * the array object would read back without a word.
     */
    private static Object yamlValueOf(Object value)
    {
        Object escaped = JdbcEscapeText.of(value);
        if (escaped instanceof String || escaped instanceof Number || escaped instanceof Boolean
                || escaped instanceof Collection || escaped instanceof Map
                || escaped.getClass().isArray())
        {
            return escaped;
        }
        return escaped.toString();
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
