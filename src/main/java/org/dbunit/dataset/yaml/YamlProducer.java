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

import org.dbunit.database.AmbiguousTableNameException;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RequiredLibrary;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link IDataSetProducer} that parses a YAML dataset document and streams
 * table/row events.
 *
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */

public class YamlProducer implements IDataSetProducer
{
    private static final Logger log = LoggerFactory.getLogger(YamlProducer.class);

    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();

    /**
     * The consumer which is responsible for creating the datasets and tables
     */
    private IDataSetConsumer _consumer = EMPTY_CONSUMER;

    private InputStream _inputStream;

    private final boolean _closeInputStream;

    private SnakeYamlDocument _document;

    /**
     * Creates a producer reading YAML from the given file, closing the stream it opens on the
     * file once it has produced the dataset.
     *
     * @param file the YAML file to read.
     * @throws IOException if the file cannot be opened.
     */
    public YamlProducer(File file) throws IOException
    {
        this(openAfterCheckingForSnakeYaml(file), true);
    }

    /**
     * Creates a producer reading YAML from the given stream, which is left open: it belongs to
     * the caller.
     *
     * @param inputStream the stream to read YAML from.
     */
    public YamlProducer(InputStream inputStream)
    {
        this(inputStream, false);
    }

    /**
     * Creates a producer reading YAML from the given stream.
     *
     * @param inputStream the stream to read YAML from.
     * @param closeInputStream whether the producer closes {@code inputStream} once it has produced
     *            the dataset, which it does for a stream it opened itself.
     */
    YamlProducer(InputStream inputStream, boolean closeInputStream)
    {
        RequiredLibrary.SNAKEYAML.requireUnchecked(YamlProducer.class);
        this._inputStream = inputStream;
        this._closeInputStream = closeInputStream;
        _document = new SnakeYamlDocument();
    }

    /**
     * Opens {@code file} only once SnakeYAML is known to be present, so a missing library is
     * reported without leaving a file open.
     */
    private static InputStream openAfterCheckingForSnakeYaml(File file) throws IOException
    {
        RequiredLibrary.SNAKEYAML.requireUnchecked(YamlProducer.class);
        return Files.newInputStream(file.toPath());
    }

    /**
     * The only code in this producer that uses SnakeYAML directly, kept in a class of its own
     * so that loading {@link YamlProducer} does not itself need SnakeYAML and its constructor
     * can say what is missing.
     */
    private static final class SnakeYamlDocument
    {
        private final Yaml _yaml;

        SnakeYamlDocument()
        {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            _yaml = new Yaml(options);
        }

        /**
         * Parses the document into its tables, each name mapped to the rows of that table, as
         * SnakeYAML read them: {@link YamlProducer#produce()} checks their shape.
         *
         * @return the tables of the document, none for an empty or comment-only document.
         * @throws DataSetException if the root of the document is not a mapping, or names a
         *             table twice.
         */
        Map<?, ?> load(InputStream inputStream) throws DataSetException
        {
            final Object root;
            try
            {
                root = _yaml.load(inputStream);
            }
            catch (DuplicateKeyException e)
            {
                String problem = e.getProblem();
                String duplicateTable = problem.replace("found duplicate key ", "");
                throw new AmbiguousTableNameException(duplicateTable, e);
            }
            if (root == null)
            {
                return Collections.emptyMap();
            }
            if (!(root instanceof Map))
            {
                throw new DataSetException(
                        "YAML dataset must be a mapping of table names to rows at the root level");
            }
            return (Map<?, ?>) root;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetProducer interface

    public void setConsumer(IDataSetConsumer consumer)
    {
        _consumer = consumer;
    }

    /**
     * {@inheritDoc}
     *
     * <p>An empty or comment-only document produces an empty dataset, and a document whose root
     * is not a mapping of table names to rows is rejected, as is a table that is not a list of
     * rows, a row that is not a mapping of column names to values, and a table or column name
     * that is not a string. A row with no value is a row of nulls, as in
     * {@link org.dbunit.dataset.json.JsonProducer}. The consumer is told the dataset ended once
     * every table is produced. A stream the producer opened itself, for a {@link File}, is
     * closed afterward, whether or not producing succeeded.
     */
    public void produce() throws DataSetException
    {
        try
        {
            produceDataSet();
        }
        finally
        {
            closeInputStreamIfOwned();
        }
    }

    private void produceDataSet() throws DataSetException
    {
        _consumer.startDataSet();
        // get the base object tree from the stream
        Map<?, ?> dataset = _document.load(_inputStream);
        // iterate over the tables in the object tree
        for (Map.Entry<?, ?> table : dataset.entrySet())
        {
            String tableName = nameOf(table.getKey(), "Table name");
            List<Map<String, Object>> rows = rowsOf(tableName, table.getValue());
            ITableMetaData meta = getMetaData(tableName, rows);
            _consumer.startTable(meta);
            for (Map<String, Object> row : rows)
            {
                _consumer.row(getRow(meta, row));
            }
            _consumer.endTable();
        }
        _consumer.endDataSet();
    }

    /**
     * Returns the rows of a table: none for a table with no value, otherwise one for each entry
     * of its list.
     *
     * @throws DataSetException if the table is not a list of rows, or one of its rows is not
     *             a row.
     */
    private static List<Map<String, Object>> rowsOf(String tableName, Object value)
            throws DataSetException
    {
        if (value == null)
        {
            return Collections.emptyList();
        }
        if (!(value instanceof List))
        {
            throw new DataSetException("Table '" + tableName
                    + "' must be a list of rows, but was " + describe(value));
        }
        List<?> entries = (List<?>) value;
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(entries.size());
        for (int rowIndex = 0; rowIndex < entries.size(); rowIndex++)
        {
            rows.add(rowOf(tableName, rowIndex, entries.get(rowIndex)));
        }
        return rows;
    }

    /**
     * Returns one row of a table, its column names mapped to their values, with no columns for
     * an entry with no value, which makes a row of nulls.
     *
     * @throws DataSetException if the entry is not a mapping of column names to values.
     */
    private static Map<String, Object> rowOf(String tableName, int rowIndex, Object entry)
            throws DataSetException
    {
        if (entry == null)
        {
            return Collections.emptyMap();
        }
        if (!(entry instanceof Map))
        {
            throw new DataSetException("Row " + rowIndex + " of table '" + tableName
                    + "' must be a mapping of column names to values, but was "
                    + describe(entry));
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> column : ((Map<?, ?>) entry).entrySet())
        {
            String columnName = nameOf(column.getKey(),
                    "Column name in row " + rowIndex + " of table '" + tableName + "'");
            row.put(columnName, column.getValue());
        }
        return row;
    }

    /**
     * Returns a table or column name, which YAML reads as something other than a string when it
     * looks like a number, a boolean such as {@code on} or {@code no}, or null.
     *
     * @throws DataSetException if the name is not a string.
     */
    private static String nameOf(Object key, String description) throws DataSetException
    {
        if (key instanceof String)
        {
            return (String) key;
        }
        throw new DataSetException(description + " must be a string, but was " + key
                + "; quote it in the YAML document");
    }

    private static String describe(Object value)
    {
        if (value instanceof Map)
        {
            return "a mapping";
        }
        if (value instanceof List)
        {
            return "a list";
        }
        return "the value '" + value + "'";
    }

    private void closeInputStreamIfOwned()
    {
        if (!_closeInputStream)
        {
            return;
        }
        try
        {
            _inputStream.close();
        }
        catch (IOException e)
        {
            log.debug("Could not close the YAML input stream", e);
        }
    }


    private ITableMetaData getMetaData(String tableName, List<Map<String, Object>> rows)
    {
        Set<String> columns = new LinkedHashSet<String>();
        // iterate through the dataset and add the column names to a set
        for (Map<String, Object> row : rows)
        {
            columns.addAll(row.keySet());
        }
        List<Column> list = new ArrayList<Column>(columns.size());
        // create a list of DBUnit columns based on the column name set
        for (String s : columns)
        {
            list.add(new Column(s, DataType.UNKNOWN));
        }
        return new DefaultTableMetaData(tableName, list.toArray(new Column[list.size()]));
    }

    private Object[] getRow(ITableMetaData meta, Map<String, Object> row) throws DataSetException
    {
        Object[] result = new Object[meta.getColumns().length];
        for (int i = 0; i < meta.getColumns().length; i++)
        {
            result[i] = row.get(meta.getColumns()[i].getColumnName());
        }
        return result;
    }

}
