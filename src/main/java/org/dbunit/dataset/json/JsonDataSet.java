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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * Reads and writes flat JSON-based dataset documents.
 *
 * <p>Contrary to the flat XML layout, columns are calculated by parsing the entire dataset, not
 * just the first row. Each table is a top-level key in the JSON object, and its value is an array
 * of row objects. Columns with {@code null} values are omitted from the JSON representation.
 *
 * <p>Example:
 *
 * <pre>
 * {
 *   "TEST_TABLE": [
 *     {"COL0": "row 0 col 0", "COL1": "row 0 col 1"},
 *     {"COL0": "row 1 col 0"}
 *   ],
 *   "EMPTY_TABLE": []
 * }
 * </pre>
 *
 * @author Jeff Jensen
 */
public class JsonDataSet extends CachedDataSet
{
    /**
     * Creates a JSON dataset from the given file.
     *
     * @param file The JSON file to read.
     * @throws IOException If the file cannot be opened.
     * @throws DataSetException If the JSON content cannot be parsed as a dataset.
     */
    public JsonDataSet(final File file) throws IOException, DataSetException
    {
        super(new JsonProducer(file), true);
    }

    /**
     * Creates a JSON dataset from the given input stream. The stream is left open once reading
     * completes; the caller remains responsible for closing it.
     *
     * @param inputStream The stream containing JSON dataset data.
     * @throws DataSetException If the JSON content cannot be parsed as a dataset.
     */
    public JsonDataSet(final InputStream inputStream) throws DataSetException
    {
        super(new JsonProducer(inputStream), true);
    }

    /**
     * Writes the specified dataset to the given output stream as JSON, encoded in UTF-8, matching
     * what {@link JsonProducer} decodes.
     *
     * @param dataSet The dataset to write.
     * @param out The stream to write to.
     * @throws DataSetException If the dataset cannot be serialized.
     */
    public static void write(final IDataSet dataSet, final OutputStream out) throws DataSetException
    {
        write(dataSet, new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    /**
     * Writes the specified dataset to the given writer as JSON.
     *
     * @param dataSet The dataset to write.
     * @param out The writer to write to.
     * @throws DataSetException If the dataset cannot be serialized.
     */
    public static void write(final IDataSet dataSet, final Writer out) throws DataSetException
    {
        new JsonWriter(out).write(dataSet);
    }
}
