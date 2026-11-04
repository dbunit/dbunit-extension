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

package org.dbunit.dataset.json;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.RequiredLibrary;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.dataset.JdbcEscapeText;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Writes an {@link IDataSet} to a {@link Writer} in JSON format.
 *
 * <p>The writer leaves a null cell, or a cell with no value ({@link ITable#NO_VALUE}), out of
 * the JSON output, except in the first row of a table. That row carries every column of the
 * table, with {@code null} for such a cell, so a column that is null in every row still
 * appears in the output and survives a read-back. The output is pretty-printed.
 *
 * <p>The writer writes a date, a time or a timestamp as the JDBC escape text that the column
 * types parse, see {@link JdbcEscapeText}, not as the number of milliseconds since the epoch
 * that Jackson writes by default, so that a date reads back as the same day, and a timestamp
 * as the same date and time, in any time zone.
 *
 * @author Jeff Jensen
 */
class JsonWriter
{
    private final Writer _out;

    /**
     * Creates a writer that writes to the given {@link Writer}.
     *
     * @param out The writer to write JSON to.
     */
    JsonWriter(final Writer out)
    {
        RequiredLibrary.JACKSON.requireUnchecked(JsonWriter.class);
        this._out = out;
    }

    /**
     * Writes the given dataset to the configured writer as JSON.
     *
     * @param dataSet The dataset to serialize.
     * @throws DataSetException If reading from the dataset or writing to the writer fails.
     */
    void write(final IDataSet dataSet) throws DataSetException
    {
        try
        {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
            final Map<String, List<Map<String, Object>>> dataSetMap = buildDataSetMap(dataSet);
            mapper.writeValue(_out, dataSetMap);
            _out.flush();
        }
        catch (final IOException e)
        {
            throw new DataSetException("Error writing JSON dataset", e);
        }
    }

    private Map<String, List<Map<String, Object>>> buildDataSetMap(final IDataSet dataSet)
            throws DataSetException
    {
        final Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        final ITableIterator iterator = dataSet.iterator();
        while (iterator.next())
        {
            final ITableMetaData metaData = iterator.getTableMetaData();
            final ITable table = iterator.getTable();
            final Column[] columns = metaData.getColumns();
            final List<Map<String, Object>> rows =
                    result.computeIfAbsent(metaData.getTableName(), name -> new ArrayList<>());
            if (columns.length == 0)
            {
                continue;
            }
            final int rowCount = rowCountOf(table);
            try
            {
                for (int row = 0; row < rowCount; row++)
                {
                    final boolean firstRow = rows.isEmpty();
                    final Map<String, Object> rowMap = new LinkedHashMap<>();
                    for (final Column column : columns)
                    {
                        final String columnName = column.getColumnName();
                        final Object value = table.getValue(row, columnName);
                        final boolean noValue = value == null || value == ITable.NO_VALUE;
                        if (noValue)
                        {
                            // The first row carries every column, even for a null or a cell with
                            // no value, so a column that is null in every row still appears in
                            // the output and survives a read-back.
                            if (firstRow)
                            {
                                rowMap.put(columnName, null);
                            }
                        }
                        else
                        {
                            rowMap.put(columnName, JdbcEscapeText.of(value));
                        }
                    }
                    rows.add(rowMap);
                }
            } catch (final RowOutOfBoundsException e)
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
    private static int rowCountOf(final ITable table)
    {
        try
        {
            return table.getRowCount();
        } catch (final UnsupportedOperationException e)
        {
            return Integer.MAX_VALUE;
        }
    }
}
