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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Produces a dataset by reading a JSON document.
 *
 * <p>The expected JSON format is a top-level object whose keys are table names and whose values
 * are arrays of row objects. Each row object maps column names to cell values. Missing keys within
 * a row are treated as {@code null} for that column. Empty tables are represented by empty arrays.
 *
 * <pre>
 * {
 *   "TABLE_NAME": [
 *     {"COLUMN0": "value", "COLUMN1": "value"},
 *     {"COLUMN0": "value"}
 *   ],
 *   "EMPTY_TABLE": []
 * }
 * </pre>
 *
 * @author Jeff Jensen
 */
public class JsonProducer implements IDataSetProducer
{
    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();
    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>()
            {
            };

    private IDataSetConsumer _consumer = EMPTY_CONSUMER;
    private final InputStream _inputStream;
    private final boolean _autoCloseInputStream;

    /**
     * Creates a producer that reads from the given file.
     *
     * @param file The JSON file to read.
     * @throws IOException If the file cannot be opened.
     */
    public JsonProducer(final File file) throws IOException
    {
        this(Files.newInputStream(file.toPath()), true);
    }

    /**
     * Creates a producer that reads from the given stream. The stream is left open once reading
     * completes; the caller remains responsible for closing it.
     *
     * @param inputStream The stream containing JSON dataset data.
     */
    public JsonProducer(final InputStream inputStream)
    {
        this(inputStream, false);
    }

    private JsonProducer(final InputStream inputStream, final boolean autoCloseInputStream)
    {
        this._inputStream = inputStream;
        this._autoCloseInputStream = autoCloseInputStream;
    }

    @Override
    public void setConsumer(final IDataSetConsumer consumer)
    {
        _consumer = consumer;
    }

    @Override
    public void produce() throws DataSetException
    {
        try
        {
            _consumer.startDataSet();
            final ObjectMapper mapper = new ObjectMapper();
            try (JsonParser parser = mapper.createParser(_inputStream))
            {
                parser.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, _autoCloseInputStream);
                produceFromParser(parser, mapper);
            }
            _consumer.endDataSet();
        }
        catch (final DataSetException e)
        {
            throw e;
        }
        catch (final IOException e)
        {
            throw new DataSetException("Error reading JSON dataset", e);
        }
    }

    private void produceFromParser(final JsonParser parser, final ObjectMapper mapper)
            throws IOException, DataSetException
    {
        if (parser.nextToken() != JsonToken.START_OBJECT)
        {
            throw new DataSetException("JSON dataset must be a JSON object at the root level");
        }
        final Set<String> seenTables = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT)
        {
            final String tableName = parser.currentName();
            if (!seenTables.add(tableName))
            {
                throw new AmbiguousTableNameException(tableName);
            }
            parser.nextToken();
            final List<Map<String, Object>> rows = parseRows(tableName, parser, mapper);
            final ITableMetaData meta = buildMetaData(tableName, rows);
            _consumer.startTable(meta);
            for (final Map<String, Object> row : rows)
            {
                _consumer.row(buildRow(meta, row));
            }
            _consumer.endTable();
        }
        if (parser.nextToken() != null)
        {
            throw new DataSetException("JSON dataset must not contain content after the root object");
        }
    }

    private List<Map<String, Object>> parseRows(final String tableName, final JsonParser parser,
            final ObjectMapper mapper) throws IOException, DataSetException
    {
        final JsonToken token = parser.currentToken();
        if (token != JsonToken.START_ARRAY)
        {
            throw new DataSetException(
                    "Table '" + tableName + "' must be a JSON array, but was " + token);
        }
        final List<Map<String, Object>> rows = new ArrayList<>();
        int rowIndex = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY)
        {
            final JsonToken rowToken = parser.currentToken();
            if (rowToken == JsonToken.VALUE_NULL)
            {
                rows.add(new LinkedHashMap<String, Object>());
            }
            else if (rowToken == JsonToken.START_OBJECT)
            {
                rows.add(mapper.readValue(parser, ROW_TYPE));
            }
            else
            {
                throw new DataSetException("Row " + rowIndex + " of table '" + tableName
                        + "' must be a JSON object or null, but was " + rowToken);
            }
            rowIndex++;
        }
        return rows;
    }

    private ITableMetaData buildMetaData(final String tableName, final List<Map<String, Object>> rows)
    {
        final Set<String> columnNames = new LinkedHashSet<>();
        for (final Map<String, Object> row : rows)
        {
            columnNames.addAll(row.keySet());
        }
        final List<Column> columns = new ArrayList<>(columnNames.size());
        for (final String colName : columnNames)
        {
            columns.add(new Column(colName, DataType.UNKNOWN));
        }
        return new DefaultTableMetaData(tableName, columns.toArray(new Column[0]));
    }

    private Object[] buildRow(final ITableMetaData meta, final Map<String, Object> row)
            throws DataSetException
    {
        final Column[] columns = meta.getColumns();
        final Object[] result = new Object[columns.length];
        for (int i = 0; i < columns.length; i++)
        {
            result[i] = row.get(columns[i].getColumnName());
        }
        return result;
    }
}
