/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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

package org.dbunit.util.fileloader;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

/**
 * {@link DataFileLoader} that dispatches to another {@link DataFileLoader} based on the
 * loaded file's extension: {@code .xml} to {@link FlatXmlDataFileLoader}, {@code .json} to
 * {@link JsonDataFileLoader}, {@code .yaml}/{@code .yml} to {@link YamlDataFileLoader}, and
 * {@code .xls}/{@code .xlsx} to {@link XlsDataFileLoader}.
 *
 * <p>{@code .csv} is deliberately not dispatched here: dbUnit's CSV format is
 * directory-based ({@link org.dbunit.dataset.csv.CsvURLDataSet} expects a directory
 * containing {@code table-ordering.txt}), so it cannot share this loader's
 * one-file-per-path convention. Use {@link CsvDataFileLoader} directly, pointed at a
 * directory, instead. Similarly, full (non-flat) XML has no extension of its own to
 * dispatch on; use {@link FullXmlDataFileLoader} directly.
 *
 * <p>The delegate loaders are used only for their {@link #loadDataSet(URL)} method, so
 * this loader's own replacement objects and substrings - not any set on a delegate - are
 * the ones applied, exactly once, to every file regardless of format. Because that method
 * touches no delegate state, the delegates are shared {@code static} instances rather than
 * rebuilt for each {@code FileExtensionDataFileLoader} - one per test method, otherwise.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class FileExtensionDataFileLoader extends AbstractDataFileLoader
{
    private static final DataFileLoader FLAT_XML_DATA_FILE_LOADER = new FlatXmlDataFileLoader();
    private static final DataFileLoader JSON_DATA_FILE_LOADER = new JsonDataFileLoader();
    private static final DataFileLoader YAML_DATA_FILE_LOADER = new YamlDataFileLoader();
    private static final DataFileLoader XLS_DATA_FILE_LOADER = new XlsDataFileLoader();

    /** Create new instance. */
    public FileExtensionDataFileLoader()
    {
    }

    /**
     * Create new instance with replacement objects.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public FileExtensionDataFileLoader(Map ro)
    {
        super(ro);
    }

    /**
     * Create new instance with replacement objects and replacement substrings.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param rs
     *            The replacement substrings for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public FileExtensionDataFileLoader(Map ro, Map rs)
    {
        super(ro, rs);
    }

    /**
     * {@inheritDoc}
     */
    public IDataSet loadDataSet(URL url) throws DataSetException, IOException
    {
        final String extension = extensionOf(url);
        switch (extension)
        {
            case "xml":
                return FLAT_XML_DATA_FILE_LOADER.loadDataSet(url);
            case "json":
                return JSON_DATA_FILE_LOADER.loadDataSet(url);
            case "yaml":
            case "yml":
                return YAML_DATA_FILE_LOADER.loadDataSet(url);
            case "xls":
            case "xlsx":
                return XLS_DATA_FILE_LOADER.loadDataSet(url);
            default:
                final String extensionDescription =
                        extension.isEmpty() ? "(none)" : "." + extension;
                throw new IllegalArgumentException("Unsupported dataset file extension: "
                        + extensionDescription + ". Supported extensions: .xml (flat XML),"
                        + " .json, .yaml, .yml, .xls, .xlsx. For .csv, use CsvDataFileLoader"
                        + " directly, pointed at a directory; for full (non-flat) XML, use"
                        + " FullXmlDataFileLoader directly.");
        }
    }

    private String extensionOf(final URL url)
    {
        final String path = url.getPath();
        final String fileName = path.substring(path.lastIndexOf('/') + 1);
        final int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ENGLISH) : "";
    }
}
