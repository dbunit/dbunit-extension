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
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Writes an {@link IDataSet} to a {@link Writer} in JSON format.
 *
 * <p>Null column values are omitted from the JSON output. The output is pretty-printed.
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
            final List<Map<String, Object>> rows = new ArrayList<>();
            for (int row = 0; row < table.getRowCount(); row++)
            {
                final Map<String, Object> rowMap = new LinkedHashMap<>();
                for (final Column column : columns)
                {
                    final String columnName = column.getColumnName();
                    final Object value = table.getValue(row, columnName);
                    // Row 0 always carries every column, even if null, so a column that is
                    // null in every row still appears in the output and survives a read-back.
                    if (value != null || row == 0)
                    {
                        rowMap.put(columnName, value);
                    }
                }
                rows.add(rowMap);
            }
            result.put(metaData.getTableName(), rows);
        }
        return result;
    }
}
