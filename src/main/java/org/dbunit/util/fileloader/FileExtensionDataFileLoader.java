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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;

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
 * dispatch on; use {@link FullXmlDataFileLoader} directly. A {@code .xml} file of the full
 * XML shape would load as flat XML, as tables named {@code table}, {@code column},
 * {@code row} and {@code value}, so the loader rejects it with an
 * {@link IllegalArgumentException} that says to use {@link FullXmlDataFileLoader}. That includes
 * a full XML file with a {@code DOCTYPE} that names the DTD {@code dataset.dtd}, or any DTD that
 * cannot be read, as the flat XML load of such a file fails before the loader can look at what
 * it loaded: the exception then has that failure as its cause.
 *
 * <p>The delegate loaders are used only for their {@link #loadDataSet(URL)} method, so
 * this loader's own replacement objects and substrings - not any set on a delegate - are
 * the ones applied, exactly once, to every file regardless of format. Because that method
 * touches no delegate state, the delegates are shared {@code static} instances rather than
 * rebuilt for each {@code FileExtensionDataFileLoader} - one per test method, otherwise. A
 * loader given a {@link FlatXmlDataSetBuilder} has a flat XML delegate of its own over it.
 *
 * <p>Flat XML takes a table's columns from its first row and ignores, with a warning, a column
 * that only a later row has. To load such a column, give this loader a
 * {@link FlatXmlDataSetBuilder} with column sensing on. {@code @DbUnitConfig(dataFileLoader)}
 * builds a loader class with its no-argument constructor, so name a subclass that passes the
 * builder on:
 * <pre>{@code
 * public class ColumnSensingDataFileLoader extends FileExtensionDataFileLoader {
 *     public ColumnSensingDataFileLoader() {
 *         super(new FlatXmlDataSetBuilder().setColumnSensing(true));
 *     }
 * }
 * }</pre>
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

    private static final String FULL_XML_TABLE_ELEMENT = "table";
    private static final String FULL_XML_NAME_ATTRIBUTE = "name";
    private static final List<String> FULL_XML_ELEMENTS_WITHOUT_ATTRIBUTES =
            Arrays.asList("column", "row", "value", "null", "none");

    private final DataFileLoader flatXmlDataFileLoader;

    /** Create new instance. */
    public FileExtensionDataFileLoader()
    {
        flatXmlDataFileLoader = FLAT_XML_DATA_FILE_LOADER;
    }

    /**
     * Create new instance with replacement objects.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     */
    public FileExtensionDataFileLoader(Map<?, ?> ro)
    {
        super(ro);
        flatXmlDataFileLoader = FLAT_XML_DATA_FILE_LOADER;
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
    public FileExtensionDataFileLoader(Map<?, ?> ro, Map<?, ?> rs)
    {
        super(ro, rs);
        flatXmlDataFileLoader = FLAT_XML_DATA_FILE_LOADER;
    }

    /**
     * Create new instance that loads the flat XML files with the given builder, for example one
     * with column sensing on.
     *
     * @param flatXmlBuilder
     *            The builder that loads the {@code .xml} files.
     * @throws IllegalArgumentException
     *             If the builder is {@code null}.
     */
    public FileExtensionDataFileLoader(final FlatXmlDataSetBuilder flatXmlBuilder)
    {
        flatXmlDataFileLoader = makeFlatXmlDataFileLoader(flatXmlBuilder);
    }

    /**
     * Create new instance with replacement objects and replacement substrings that loads the
     * flat XML files with the given builder, for example one with column sensing on.
     *
     * @param ro
     *            The replacement objects for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param rs
     *            The replacement substrings for use with
     *            {@link org.dbunit.dataset.ReplacementDataSet}.
     * @param flatXmlBuilder
     *            The builder that loads the {@code .xml} files.
     * @throws IllegalArgumentException
     *             If the builder is {@code null}.
     */
    public FileExtensionDataFileLoader(final Map<?, ?> ro, final Map<?, ?> rs,
            final FlatXmlDataSetBuilder flatXmlBuilder)
    {
        super(ro, rs);
        flatXmlDataFileLoader = makeFlatXmlDataFileLoader(flatXmlBuilder);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException
     *             If the extension is not supported, or the {@code .xml} file is a full XML
     *             dataset, which the loader would otherwise read as flat XML.
     */
    public IDataSet loadDataSet(URL url) throws DataSetException, IOException
    {
        final String extension = extensionOf(url);
        switch (extension)
        {
            case "xml":
                return loadFlatXml(url);
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
                throw new IllegalArgumentException("The file " + url
                        + " has an unsupported dataset file extension: "
                        + extensionDescription + ". Supported extensions: .xml (flat XML),"
                        + " .json, .yaml, .yml, .xls, .xlsx. For .csv, use CsvDataFileLoader"
                        + " directly, pointed at a directory; for full (non-flat) XML, use"
                        + " FullXmlDataFileLoader directly.");
        }
    }

    private IDataSet loadFlatXml(final URL url) throws DataSetException, IOException
    {
        final IDataSet dataSet;
        try
        {
            dataSet = flatXmlDataFileLoader.loadDataSet(url);
        } catch (final DataSetException e)
        {
            final boolean failedOnFullXml = new FullXmlFileShape().isFullXml(url);
            if (failedOnFullXml)
            {
                throw makeFullXmlRejection(url, e);
            }
            throw e;
        }

        final boolean fullXml = looksLikeFullXml(dataSet);
        if (fullXml)
        {
            throw makeFullXmlRejection(url, null);
        }
        return dataSet;
    }

    private static IllegalArgumentException makeFullXmlRejection(final URL url,
            final DataSetException cause)
    {
        final String message = "The file " + url + " looks like a full XML"
                + " dataset, which FileExtensionDataFileLoader reads as flat XML, as tables"
                + " named table, column, row and value. Load it with FullXmlDataFileLoader,"
                + " for example @DbUnitConfig(dataFileLoader = FullXmlDataFileLoader.class).";
        return new IllegalArgumentException(message, cause);
    }

    /**
     * Returns whether a flat XML dataset is what the flat XML loader reads from a full XML
     * dataset. The flat XML loader reads each element name as a table and each attribute as a
     * column. The full XML element names are lower case, and only the {@code table} element has
     * an attribute, {@code name}. So a full XML dataset with a column or a row reads as a table
     * named {@code table} with the one column {@code name}, a table named {@code column} or
     * {@code row}, or both, and maybe tables named {@code value}, {@code null} and {@code none},
     * where every table but {@code table} has no columns. A flat XML dataset with any other table
     * or column is no full XML dataset. A flat XML dataset of only such tables reads the same, so
     * the loader rejects it too.
     *
     * @param dataSet The dataset the flat XML loader read.
     * @return {@code true} if the dataset is what the flat XML loader reads from a full XML
     *         dataset.
     * @throws DataSetException If the dataset cannot tell its tables.
     */
    private static boolean looksLikeFullXml(final IDataSet dataSet) throws DataSetException
    {
        boolean tableTable = false;
        boolean columnOrRowTable = false;
        for (final String tableName : dataSet.getTableNames())
        {
            final ITableMetaData metaData = dataSet.getTableMetaData(tableName);
            final Column[] columns = metaData.getColumns();
            final boolean fullXmlTable;
            if (FULL_XML_TABLE_ELEMENT.equals(tableName))
            {
                tableTable = true;
                fullXmlTable = columns.length == 1
                        && FULL_XML_NAME_ATTRIBUTE.equals(columns[0].getColumnName());
            }
            else
            {
                columnOrRowTable = columnOrRowTable || "column".equals(tableName)
                        || "row".equals(tableName);
                fullXmlTable = columns.length == 0
                        && FULL_XML_ELEMENTS_WITHOUT_ATTRIBUTES.contains(tableName);
            }
            if (!fullXmlTable)
            {
                return false;
            }
        }
        return tableTable && columnOrRowTable;
    }

    private static DataFileLoader makeFlatXmlDataFileLoader(
            final FlatXmlDataSetBuilder flatXmlBuilder)
    {
        if (flatXmlBuilder == null)
        {
            throw new IllegalArgumentException("The flat XML builder is null.");
        }
        return new FlatXmlDataFileLoader(flatXmlBuilder);
    }

    private String extensionOf(final URL url)
    {
        final String path = url.getPath();
        final String fileName = path.substring(path.lastIndexOf('/') + 1);
        final int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ENGLISH) : "";
    }
}
