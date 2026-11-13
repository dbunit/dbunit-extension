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

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.RequiredLibrary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes flat YAML-based dataset documents. Contrary to the flat XML layout,
 * columns are calculated by parsing the entire data set, not just the first row.
 * <br/><br/>
 * The format looks like this:
 * <br/>
 * <pre>
 * &lt;table_name&gt;:
 *   - &lt;column&gt;: &lt;value&gt;
 *     &lt;column&gt;: &lt;value&gt;
 *             ...
 *   - &lt;column&gt;: &lt;value&gt;
 *     &lt;column&gt;: &lt;value&gt;
 *             ...
 *        ...
 *    ...
 * </pre>
 * <br/>
 * I.e.:
 * <br/>
 * <pre>
 * TEST_TABLE:
 *   - COL0: "row 0 col 0"
 *     COL1: "row 0 col 1"
 *     COL2: "row 0 col 2"
 *   - COL1: "row 1 col 1"
 * SECOND_TABLE:
 *   - COL0: "row 0 col 0"
 *     COL1: "row 0 col 1"
 * EMPTY_TABLE: []
 * </pre>
 *
 * <p>
 * A value that starts with a square bracket or a curly brace needs quotes, such as
 * {@code '[NULL]'} or {@code '[now-1d]'}: YAML reads an unquoted one as a list or a mapping,
 * or fails on it as a syntax error. The reader rejects a list or a mapping in a cell with a
 * {@link DataSetException} that names the table, the row and the column.
 *
 * <p>
 * Writing a dataset leaves a null cell out of every row of a table but the first. That row
 * carries every column of the table, with {@code null} for a null cell, so a column that is
 * null in every row survives a read-back.
 *
 * @author Björn Beskow
 * @version $Revision$ $Date$
 */
public class YamlDataSet extends CachedDataSet
{

    /**
     * Creates a YAML dataset based on a yaml file
     *
     * @param file the YAML file to load.
     * @throws IOException if the file cannot be read.
     * @throws DataSetException If the document cannot be parsed, or SnakeYAML is not on the
     *             classpath.
     */
    public YamlDataSet(File file) throws IOException, DataSetException
    {
        super(newProducer(file), true);
    }

    /**
     * Creates a YAML dataset based on an inputstream
     *
     * @param inputStream An inputstream pointing to a YAML dataset
     * @throws DataSetException If the document cannot be parsed, or SnakeYAML is not on the
     *             classpath.
     */
    public YamlDataSet(InputStream inputStream) throws DataSetException
    {
        super(newProducer(inputStream), true);
    }

    /**
     * Writes the specified dataset to the specified output stream as YAML,
     * encoded in UTF-8, matching what {@link YamlProducer} decodes.
     *
     * @param dataSet the dataset to write.
     * @param out the stream to write the YAML document to.
     * @throws DataSetException if the dataset cannot be read.
     */
    public static void write(IDataSet dataSet, OutputStream out)
    throws DataSetException
    {
        write(dataSet, new BufferedWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    /**
     * Write the specified dataset to the specified writer as YAML.
     *
     * @param dataSet the dataset to write.
     * @param out the writer to write the YAML document to.
     * @throws DataSetException If the dataset cannot be read, or SnakeYAML is not on the
     *             classpath.
     */
    public static void write(IDataSet dataSet, Writer out)
    throws DataSetException
    {
        requireSnakeYaml();
        YamlWriter yamlWriter = new YamlWriter(out);
        yamlWriter.write(dataSet);
    }

    private static YamlProducer newProducer(File file) throws IOException, DataSetException
    {
        requireSnakeYaml();
        return new YamlProducer(file);
    }

    private static YamlProducer newProducer(InputStream inputStream) throws DataSetException
    {
        requireSnakeYaml();
        return new YamlProducer(inputStream);
    }

    private static void requireSnakeYaml() throws DataSetException
    {
        RequiredLibrary.SNAKEYAML.require(YamlDataSet.class);
    }

}
