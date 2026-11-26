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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            removeCodePointLimit(options);
            DumperOptions dumperOptions = new DumperOptions();
            _yaml = new Yaml(new Constructor(options), new Representer(dumperOptions),
                    dumperOptions, options, new TextValueResolver());
        }

        /**
         * Lifts the limit on the size of a document, which SnakeYAML sets to 3,145,728 code
         * points to protect a service from hostile input. A dataset is a fixture of the test's
         * own project, and a document {@link YamlDataSet#write} wrote must be readable. A
         * SnakeYAML older than 1.32, such as 1.30, has no such limit and no method to set it.
         */
        private static void removeCodePointLimit(final LoaderOptions options)
        {
            try
            {
                options.setCodePointLimit(Integer.MAX_VALUE);
            }
            catch (final NoSuchMethodError e)
            {
                // SnakeYAML before 1.32: there is no limit to lift.
            }
        }

        /**
         * Resolves the implicit type of an unquoted value as SnakeYAML does, except that it keeps
         * the text of the document for a date, a timestamp without an offset and the numbers that
         * the data types parse as text, or that no data type wants as the number SnakeYAML would
         * read. The data type then parses the text as it does for every other format. The
         * resolver sees the text only, not the column.
         *
         * <p>
         * Such a value is a date, or a timestamp without an offset in any form that YAML 1.1 reads
         * as a timestamp, such as {@code 2024-03-01}, {@code 2024-03-01 10:15:30} or
         * {@code 2024-03-01T10:15:30}, a base-60 number such as the time {@code 10:30:00}, the
         * clock time {@code 12:30}, the ratio {@code 16:9} or {@code 1:30.5}, an integer with
         * leading zeros such as {@code 01234}, an integer with a leading plus such as
         * {@code +4930123456}, and a decimal number such as {@code 12.50} or {@code 1.5e3}.
         * SnakeYAML would read the date as an instant at midnight UTC, the day before in a time
         * zone west of UTC, and a timestamp without an offset as an instant in UTC, so that a text
         * column got the {@code toString()} of a {@link java.util.Date} and a timestamp column the
         * time shifted by the offset of the JVM's time zone. It would read a base-60 number as the
         * sum of its parts, so that {@code 10:30:00} became 37800 and {@code 12:30} became 750,
         * an integer with leading zeros as an octal number, so that {@code 01234} became 668, an
         * integer with a leading plus as the integer without the plus, and a number with a
         * fraction as a double, which drops the digits past about 17 significant ones.
         *
         * <p>
         * A timestamp with an offset, such as {@code 2024-03-01T10:15:30Z} or
         * {@code 2024-03-01 10:15:30 +01:00}, resolves to the instant SnakeYAML reads, as before
         * 3.6.0: a {@code TIMESTAMP} column stores that instant in the JVM's time zone and compares
         * it as that instant, where the text would be stored as written in its offset and
         * compared as the instant, which differ in a JVM outside that offset. A number that no
         * data type parses as text, such as {@code 0x1F}, {@code 0b101}, {@code 1_000},
         * {@code 1_000.5} or {@code .inf}, resolves to the integer or the double SnakeYAML reads.
         * Every other value, such as a boolean like {@code yes} or {@code off}, resolves as
         * SnakeYAML resolves it. A value with an explicit tag, such as {@code !!timestamp}, keeps
         * the type the tag names.
         */
        private static class TextValueResolver extends Resolver
        {
            private static final Pattern ZERO_PADDED_INTEGER = Pattern.compile("[-+]?0[0-9]+");

            /**
             * The end of a YAML 1.1 timestamp that has an offset: the seconds, a fraction, then
             * {@code Z} or an offset of one of the forms {@code +h}, {@code +hh} and
             * {@code +hh:mm}, which may follow spaces or tabs.
             */
            private static final Pattern TIMESTAMP_WITH_OFFSET = Pattern.compile(
                    ".*:[0-9][0-9](?:\\.[0-9]*)?[ \\t]*(?:Z|[-+][0-9][0-9]?(?::[0-9][0-9])?)");

            /**
             * {@inheritDoc}
             * <p>
             * Resolves a date, a timestamp without an offset, a base-60 number, an integer or a
             * float to a string instead when the data types parse the text, or when no data type
             * wants the number SnakeYAML reads from it.
             */
            @Override
            public Tag resolve(final NodeId kind, final String value, final boolean implicit)
            {
                final Tag tag = super.resolve(kind, value, implicit);
                final boolean integerTag = Tag.INT.equals(tag);
                final boolean floatTag = Tag.FLOAT.equals(tag);
                final boolean numberTag = integerTag || floatTag;
                final boolean timestampText =
                        Tag.TIMESTAMP.equals(tag) && !isTimestampWithOffset(value);
                final boolean decimalText = floatTag && isDecimalText(value);
                final boolean base60Text = numberTag && isBase60Number(value);
                final boolean paddedIntegerText = integerTag && isZeroPaddedIntegerText(value);
                final boolean plusSignedIntegerText = integerTag && isPlusSignedIntegerText(value);
                if (timestampText || decimalText || base60Text || paddedIntegerText
                        || plusSignedIntegerText)
                {
                    return Tag.STR;
                }
                return tag;
            }

            /**
             * Returns whether a YAML 1.1 timestamp has an offset, {@code Z} or one such as
             * {@code +01:00}, which SnakeYAML reads as the instant it names. A date alone, such
             * as {@code 2024-03-01}, has none, though it ends in a minus and digits.
             *
             * @param value The text of a YAML 1.1 timestamp.
             * @return {@code true} if the timestamp has an offset.
             */
            private static boolean isTimestampWithOffset(final String value)
            {
                return TIMESTAMP_WITH_OFFSET.matcher(value).matches();
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
             * Returns whether the text is a base-60 number, such as the time {@code 10:30:00}, the
             * clock time {@code 12:30} or the ratio {@code 16:9}, which YAML 1.1 reads as the sum
             * of its parts. Only the base-60 forms of an integer and of a number with a fraction
             * contain a colon, so the check looks for one. No data type wants the sum, and a text
             * column would get its digits, such as 750 for {@code 12:30}.
             *
             * @param value The text of an integer or of a number with a fraction.
             * @return {@code true} if the text is a base-60 number.
             */
            private static boolean isBase60Number(final String value)
            {
                return value.indexOf(':') >= 0;
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

            /**
             * Returns whether the text is an integer with a leading plus, such as
             * {@code +4930123456}, that the integer data types parse as text, as
             * {@link BigDecimal#BigDecimal(String)} reads it. SnakeYAML reads the integer without
             * its plus, so that a text column would get {@code 4930123456}. A hexadecimal, binary
             * or digit group integer, such as {@code +0x1F}, is no decimal number, so it stays the
             * integer SnakeYAML reads.
             *
             * @param value The text of an integer.
             * @return {@code true} if the text has a leading plus and the data types parse it.
             */
            private static boolean isPlusSignedIntegerText(final String value)
            {
                final boolean plusSigned = value.startsWith("+");
                return plusSigned && isDecimalText(value);
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
     * parsed, such as one with a syntax error or a second document, a key named twice, a column
     * named twice in one row in two cases, such as {@code ID} and {@code id}, a table
     * that is not a list of rows, a row that is not a mapping of column names to values, a
     * table or column name that is not a string, and a cell that is a list or a mapping, which
     * YAML builds from an unquoted value that starts with a square bracket or a curly brace,
     * such as {@code [NULL]}, so such a value needs quotes. A row with no value is a row of
     * nulls, as in {@link org.dbunit.dataset.json.JsonProducer}. Column names are matched
     * ignoring case, as in flat XML: a row that spells a column in another case than an earlier
     * row gives its value to the column, which keeps the spelling of its first appearance. The
     * consumer is told the
     * dataset ended once
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
     *             a row or has a cell that is a list or a mapping.
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
     * @throws DataSetException If the entry is not a mapping of column names to values, or one
     *             of its values is a list or a mapping.
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
        Map<String, String> spellingByColumnKey = new HashMap<String, String>();
        for (Map.Entry<?, ?> column : ((Map<?, ?>) entry).entrySet())
        {
            String columnName = nameOf(column.getKey(),
                    "Column name in row " + rowIndex + " of table '" + tableName + "'");
            Object value = column.getValue();
            requireValue(tableName, rowIndex, columnName, value);
            requireOnceInTheRow(tableName, rowIndex, columnName, spellingByColumnKey);
            row.put(columnName, value);
        }
        return row;
    }

    /**
     * Checks that the row does not name the column twice in two cases, such as {@code ID} and
     * {@code id}, which dbUnit takes for one column. SnakeYAML rejects a key named twice in
     * the same case itself.
     *
     * @param spellingByColumnKey The first spelling of each column the row named so far, by its
     *            upper-case name; this method adds the column to it.
     * @throws DataSetException If the row named the column before, in another case.
     */
    private static void requireOnceInTheRow(String tableName, int rowIndex, String columnName,
            Map<String, String> spellingByColumnKey) throws DataSetException
    {
        String columnKey = columnName.toUpperCase(Locale.ENGLISH);
        String firstSpelling = spellingByColumnKey.putIfAbsent(columnKey, columnName);
        if (firstSpelling != null)
        {
            throw new DataSetException("Row " + rowIndex + " of table '" + tableName
                    + "' names column '" + firstSpelling + "' more than once, also as '"
                    + columnName + "': a column is named once in a row");
        }
    }

    /**
     * Checks that a cell holds a value. YAML builds a list from an unquoted value that starts
     * with a square bracket, such as {@code [NULL]} or {@code [now-1d]}, and a mapping from one
     * that starts with a curly brace, and no column type can use either.
     *
     * @throws DataSetException If the cell is a list or a mapping.
     */
    private static void requireValue(String tableName, int rowIndex, String columnName,
            Object value) throws DataSetException
    {
        final boolean mapping = value instanceof Map;
        final boolean list = value instanceof Collection;
        if (mapping || list)
        {
            final String kind = mapping ? "a mapping" : "a list";
            throw new DataSetException("Column '" + columnName + "' of row " + rowIndex
                    + " of table '" + tableName + "' must be a value, but was " + kind + ", "
                    + value + ": YAML reads an unquoted value that starts with '[' as a list and"
                    + " one that starts with '{' as a mapping. Quote the value, such as '[NULL]'"
                    + " or '[now-1d]', to give it as text.");
        }
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
        Map<String, String> firstSpellingByColumnKey = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : rows)
        {
            for (String columnName : row.keySet())
            {
                firstSpellingByColumnKey.putIfAbsent(columnName.toUpperCase(Locale.ENGLISH),
                        columnName);
            }
        }
        List<Column> list = new ArrayList<Column>(firstSpellingByColumnKey.size());
        for (String s : firstSpellingByColumnKey.values())
        {
            list.add(new Column(s, DataType.UNKNOWN));
        }
        return new DefaultTableMetaData(tableName, list.toArray(new Column[list.size()]));
    }

    private Object[] getRow(ITableMetaData meta, Map<String, Object> row) throws DataSetException
    {
        Object[] result = new Object[meta.getColumns().length];
        for (Map.Entry<String, Object> cell : row.entrySet())
        {
            int columnIndex = meta.getColumnIndex(cell.getKey());
            result[columnIndex] = cell.getValue();
        }
        return result;
    }

}
