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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
     * @param inputStream The stream to read YAML from.
     * @param closeInputStream Whether the producer closes {@code inputStream} once it has produced
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
            DumperOptions dumperOptions = new DumperOptions();
            _yaml = new Yaml(new Constructor(options), new Representer(dumperOptions),
                    dumperOptions, options, new TextValueResolver());
        }

        /**
         * SnakeYAML's resolver of implicit types, except that an unquoted timestamp, time, integer
         * or number with a fraction that the column's data type parses as text stays the text of
         * the document, which the data type then parses as in every other format: a date such as
         * {@code 2024-03-01}, a date and time such as {@code 2024-03-01 10:15:30}, a time such as
         * {@code 10:30:00}, an integer with leading zeros such as {@code 01234}, and a decimal
         * number such as {@code 12.50} or {@code 1.5e3}. As a timestamp, SnakeYAML would read a
         * date as an instant at midnight UTC, the day before in a time zone west of UTC; as an
         * integer, it would read a time from {@code 10:00:00} on as a base-60 number, so that
         * {@code 10:30:00} became 37800, and an integer with leading zeros as an octal number, so
         * that {@code 01234} became 668; as a float, it would drop the digits of a number past
         * about 17 significant ones. A timestamp or a number that no data type parses as text,
         * such as {@code 2024-03-01T10:15:30Z}, {@code 0x1F}, {@code 1:30}, {@code 1_000.5} or
         * {@code .inf}, resolves to the instant, the integer or the double SnakeYAML reads, and
         * every other value, such as a boolean like {@code yes} or {@code off}, resolves as
         * SnakeYAML resolves it. A value with an explicit tag, such as {@code !!timestamp}, keeps
         * the type the tag names.
         */
        private static class TextValueResolver extends Resolver
        {
            private static final Pattern ZERO_PADDED_INTEGER = Pattern.compile("[-+]?0[0-9]+");

            /**
             * {@inheritDoc}
             * <p>
             * Resolves a timestamp, an integer or a float that the column's data type parses as
             * text to a string instead.
             */
            @Override
            public Tag resolve(final NodeId kind, final String value, final boolean implicit)
            {
                final Tag tag = super.resolve(kind, value, implicit);
                final boolean timestampText =
                        Tag.TIMESTAMP.equals(tag) && isDateOrTimestampText(value);
                final boolean decimalText = Tag.FLOAT.equals(tag) && isDecimalText(value);
                final boolean integerTag = Tag.INT.equals(tag);
                final boolean timeText = integerTag && isTimeText(value);
                final boolean paddedIntegerText = integerTag && isZeroPaddedIntegerText(value);
                if (timestampText || decimalText || timeText || paddedIntegerText)
                {
                    return Tag.STR;
                }
                return tag;
            }

            /**
             * Returns whether the DATE and TIMESTAMP data types parse the text: as a date when it
             * has ten characters, otherwise as a date and time, as
             * {@link java.sql.Date#valueOf(String)} and {@link java.sql.Timestamp#valueOf(String)}
             * read them.
             *
             * @param value The text of a timestamp.
             * @return {@code true} if the data types parse the text.
             */
            private static boolean isDateOrTimestampText(final String value)
            {
                try
                {
                    if (value.length() == 10)
                    {
                        java.sql.Date.valueOf(value);
                    } else
                    {
                        java.sql.Timestamp.valueOf(value);
                    }
                    return true;
                } catch (final IllegalArgumentException e)
                {
                    return false;
                }
            }

            /**
             * Returns whether the numeric data types parse the text, as
             * {@link BigDecimal#BigDecimal(String)} reads it.
             *
             * @param value The text of a number with a fraction.
             * @return {@code true} if the data types parse the text.
             */
            private static boolean isDecimalText(final String value)
            {
                try
                {
                    new BigDecimal(value);
                    return true;
                } catch (final NumberFormatException e)
                {
                    return false;
                }
            }

            /**
             * Returns whether the TIME data type parses the text, as
             * {@link java.sql.Time#valueOf(String)} reads it. YAML 1.1 reads such a text as a
             * base-60 integer when it starts with a digit other than zero, as a time from
             * {@code 10:00:00} on does.
             *
             * @param value The text of an integer.
             * @return {@code true} if the data type parses the text.
             */
            private static boolean isTimeText(final String value)
            {
                try
                {
                    java.sql.Time.valueOf(value);
                    return true;
                } catch (final IllegalArgumentException e)
                {
                    return false;
                }
            }

            /**
             * Returns whether the text is an integer with leading zeros, such as {@code 01234},
             * which YAML 1.1 reads as an octal number and the integer data types read as a decimal
             * one.
             *
             * @param value The text of an integer.
             * @return {@code true} if the text has leading zeros.
             */
            private static boolean isZeroPaddedIntegerText(final String value)
            {
                return ZERO_PADDED_INTEGER.matcher(value).matches();
            }
        }

        /**
         * Parses the document into its tables, each name mapped to the rows of that table, as
         * SnakeYAML read them: {@link YamlProducer#produce()} checks their shape.
         *
         * @return The tables of the document, none for an empty or comment-only document.
         * @throws DataSetException If the document cannot be parsed, for example because of a
         *             syntax error or a second document in the stream, if the root of the
         *             document is not a mapping, or if a key is named twice, an
         *             {@link AmbiguousTableNameException} when it is a table name.
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
                throw failureOfDuplicateKey(e);
            }
            catch (YAMLException e)
            {
                throw new DataSetException("Error reading YAML dataset", e);
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

        /**
         * Returns the failure for a key a mapping names twice: an
         * {@link AmbiguousTableNameException} when the mapping is the root of the document,
         * whose keys are the table names, and otherwise a {@link DataSetException} that names the
         * key and where it is, as the keys below the root are column names.
         */
        private static DataSetException failureOfDuplicateKey(DuplicateKeyException e)
        {
            String duplicateKey = e.getProblem().replace("found duplicate key ", "");
            Mark mappingStart = e.getContextMark();
            boolean mappingIsTheRoot = mappingStart == null || mappingStart.getColumn() == 0;
            if (mappingIsTheRoot)
            {
                return new AmbiguousTableNameException(duplicateKey, e);
            }
            Mark duplicate = e.getProblemMark();
            return new DataSetException("Duplicate key " + duplicateKey + " in the YAML dataset"
                    + " (line " + (duplicate.getLine() + 1) + ", column "
                    + (duplicate.getColumn() + 1) + "): a column is named once in a row", e);
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
     * is not a mapping of table names to rows is rejected, as is a document that cannot be
     * parsed, such as one with a syntax error or a second document, a key named twice, a table
     * that is not a list of rows, a row that is not a mapping of column names to values, and a
     * table or column name that is not a string. A row with no value is a row of nulls, as in
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
     * @throws DataSetException If the table is not a list of rows, or one of its rows is not
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
     * @throws DataSetException If the entry is not a mapping of column names to values.
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
     * @throws DataSetException If the name is not a string.
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
