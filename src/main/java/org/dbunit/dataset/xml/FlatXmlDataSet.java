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

package org.dbunit.dataset.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;

import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Reads and writes flat XML dataset document. Each XML element corresponds to a table row.
 *       Each XML element name corresponds to a table name. The XML attributes
 *       correspond to table columns.
 * <p>
 * Flat XML dataset document sample:
 * <p>
 * <pre>
 * &lt;!DOCTYPE dataset SYSTEM "my-dataset.dtd"&gt;
 * &lt;dataset&gt;
 *     &lt;TEST_TABLE COL0="row 0 col 0"
 *         COL1="row 0 col 1"
 *         COL2="row 0 col 2"/&gt;
 *     &lt;TEST_TABLE COL1="row 1 col 1"/&gt;
 *     &lt;SECOND_TABLE COL0="row 0 col 0"
 *           COL1="row 0 col 1" /&gt;
 *     &lt;EMPTY_TABLE/&gt;
 * &lt;/dataset&gt;</pre>
 * <p>
 * To specify null values, omit corresponding attribute.
 * In the above example, missing COL0 and COL2 attributes of TEST_TABLE second row represents null values.
 * <p>
 * Table metadata is deduced from the first row of each table by default. 
 * <b>Beware that DbUnit may think a table misses some columns if the first row of that table has one or more null values.</b>
 * You can do one of the following things to avoid this:
 * <ul>
 * <li>Use a DTD. DbUnit will use the columns declared in the DTD as table metadata. 
 * DbUnit only supports external system URI. The URI can be absolute or relative.
 * </li>
 * <li>Since DBUnit 2.3.0 there is a functionality called "column sensing" which basically 
 * reads in the whole XML into a buffer and dynamically adds new columns as they appear. 
 * It can be used as demonstrated in the following example:
 * <pre>
 *   // since dbunit 2.4.7
 *   FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
 *   builder.setInputSource(new File("src/xml/flatXmlTableTest.xml"));
 *   builder.setColumnSensing(true);
 *   IDataSet dataSet = builder.build();
 *   
 *   // or dbunit release <= 2.4.6:
 *   boolean enableColumnSensing = true;
 *   IDataSet dataSet = new FlatXmlDataSet(
 *            new File("src/xml/flatXmlTableTest.xml"), false, enableColumnSensing);
 * </pre>
 * </li>
 * </ul>
 * </p>
 * 
 * @author Manuel Laflamme
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Mar 12, 2002) 
 */
public class FlatXmlDataSet extends CachedDataSet
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(FlatXmlDataSet.class);

    /**
     * Creates a new {@link FlatXmlDataSet} with the data of the given producer.
     * @param flatXmlProducer The producer that provides the {@link FlatXmlDataSet} content
     * @throws DataSetException 
     * @since 2.4.7
     */
    public FlatXmlDataSet(FlatXmlProducer flatXmlProducer) throws DataSetException
    {
        super(flatXmlProducer, flatXmlProducer.isCaseSensitiveTableNames());
    }
    
    /**
     * Creates an FlatXmlDataSet object with the specified InputSource.
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(InputSource source) throws IOException, DataSetException
    {
        super(new FlatXmlProducer(source));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlFile the xml file
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(File xmlFile) throws IOException, DataSetException
    {
        this(xmlFile, true);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlFile the xml file
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(File xmlFile, boolean dtdMetadata)
            throws IOException, DataSetException
    {
        this(xmlFile.toURL(), dtdMetadata);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlFile the xml file
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @param columnSensing Whether or not the columns should be sensed automatically. Every XML row
     * is scanned for columns that have not been there in a previous column.
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(File xmlFile, boolean dtdMetadata, boolean columnSensing)
            throws IOException, DataSetException
    {
        this(xmlFile.toURL(), dtdMetadata, columnSensing);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlFile the xml file
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @param columnSensing Whether or not the columns should be sensed automatically. Every XML row
     * is scanned for columns that have not been there in a previous column.
     * @param caseSensitiveTableNames Whether or not this dataset should use case sensitive table names
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(File xmlFile, boolean dtdMetadata, boolean columnSensing, boolean caseSensitiveTableNames)
    throws IOException, DataSetException
    {
        this(xmlFile.toURL(), dtdMetadata, columnSensing, caseSensitiveTableNames);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml URL.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlUrl the xml URL
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(URL xmlUrl) throws IOException, DataSetException
    {
        this(xmlUrl, true);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml URL.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlUrl the xml URL
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(URL xmlUrl, boolean dtdMetadata)
            throws IOException, DataSetException
    {
        this(xmlUrl, dtdMetadata, false);
    }
    

    /**
     * Creates an FlatXmlDataSet object with the specified xml URL.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlUrl the xml URL
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @param columnSensing Whether or not the columns should be sensed automatically. Every XML row
     * is scanned for columns that have not been there in a previous column.
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(URL xmlUrl, boolean dtdMetadata, boolean columnSensing)
    throws IOException, DataSetException
    {
        super(new FlatXmlProducer(
                new InputSource(xmlUrl.toString()), dtdMetadata, columnSensing));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlUrl the xml file
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @param columnSensing Whether or not the columns should be sensed automatically. Every XML row
     * is scanned for columns that have not been there in a previous column.
     * @param caseSensitiveTableNames Whether or not this dataset should use case sensitive table names
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(URL xmlUrl, boolean dtdMetadata, boolean columnSensing, boolean caseSensitiveTableNames)
    throws IOException, DataSetException
    {
        super(new FlatXmlProducer(
                  new InputSource(xmlUrl.toString()), dtdMetadata, columnSensing, caseSensitiveTableNames), 
              caseSensitiveTableNames);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml reader.
     * Relative DOCTYPE uri are resolved from the current working directory.
     *
     * @param xmlReader the xml reader
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(Reader xmlReader) throws IOException, DataSetException
    {
        this(xmlReader, true);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml reader.
     * Relative DOCTYPE uri are resolved from the current working directory.
     *
     * @param xmlReader the xml reader
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(Reader xmlReader, boolean dtdMetadata)
            throws IOException, DataSetException
    {
        this(xmlReader, dtdMetadata, false, false);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml file.
     * Relative DOCTYPE uri are resolved from the xml file path.
     *
     * @param xmlReader the xml reader
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @param columnSensing Whether or not the columns should be sensed automatically. Every XML row
     * is scanned for columns that have not been there in a previous column.
     * @param caseSensitiveTableNames Whether or not this dataset should use case sensitive table names
     * @since 2.4.3
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(Reader xmlReader, boolean dtdMetadata, boolean columnSensing, boolean caseSensitiveTableNames)
    throws IOException, DataSetException
    {
        super(new FlatXmlProducer(new InputSource(xmlReader), dtdMetadata, columnSensing, caseSensitiveTableNames),
                caseSensitiveTableNames);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml and dtd readers.
     *
     * @param xmlReader the xml reader
     * @param dtdReader the dtd reader
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(Reader xmlReader, Reader dtdReader)
            throws IOException, DataSetException
    {
        this(xmlReader, new FlatDtdDataSet(dtdReader));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml reader.
     *
     * @param xmlReader the xml reader
     * @param metaDataSet the dataset used as metadata source.
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(Reader xmlReader, IDataSet metaDataSet)
            throws IOException, DataSetException
    {
        super(new FlatXmlProducer(new InputSource(xmlReader), metaDataSet));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml input stream.
     * Relative DOCTYPE uri are resolved from the current working directory.
     *
     * @param xmlStream the xml input stream
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(InputStream xmlStream) throws IOException, DataSetException
    {
        this(xmlStream, true);
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml input stream.
     * Relative DOCTYPE uri are resolved from the current working directory.
     *
     * @param xmlStream the xml input stream
     * @param dtdMetadata if <code>false</code> do not use DTD as metadata
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(InputStream xmlStream, boolean dtdMetadata)
            throws IOException, DataSetException
    {
        super(new FlatXmlProducer(new InputSource(xmlStream), dtdMetadata));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml and dtd input
     * stream.
     *
     * @param xmlStream the xml input stream
     * @param dtdStream the dtd input stream
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(InputStream xmlStream, InputStream dtdStream)
            throws IOException, DataSetException
    {
        this(xmlStream, new FlatDtdDataSet(dtdStream));
    }

    /**
     * Creates an FlatXmlDataSet object with the specified xml input stream.
     *
     * @param xmlStream the xml input stream
     * @param metaDataSet the dataset used as metadata source.
     * @deprecated since 2.4.7 - use {@link FlatXmlDataSetBuilder} to create a {@link FlatXmlDataSet}
     */
    public FlatXmlDataSet(InputStream xmlStream, IDataSet metaDataSet)
            throws IOException, DataSetException
    {
        super(new FlatXmlProducer(new InputSource(xmlStream), metaDataSet));
    }

    /**
     * Write the specified dataset to the specified output stream as xml.
     */
    public static void write(IDataSet dataSet, OutputStream out)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}) - start", dataSet, out);

        FlatXmlWriter datasetWriter = new FlatXmlWriter(out);
        datasetWriter.setIncludeEmptyTable(true);
        datasetWriter.write(dataSet);
    }

    /**
     * Write the specified dataset to the specified writer as xml.
     */
    public static void write(IDataSet dataSet, Writer writer)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, writer={}) - start", dataSet, writer);
        write(dataSet, writer, null);
    }

    /**
     * Write the specified dataset to the specified writer as xml.
     */
    public static void write(IDataSet dataSet, Writer writer, Charset charset)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, writer={}, charset={}) - start",
                dataSet, writer, charset);

        final FlatXmlWriter datasetWriter = new FlatXmlWriter(writer, charset);
        datasetWriter.setIncludeEmptyTable(true);
        datasetWriter.write(dataSet);
    }

    /**
     * Write a DTD for the specified dataset to the specified output.
     * @deprecated use {@link FlatDtdDataSet#write}
     */
    public static void writeDtd(IDataSet dataSet, OutputStream out)
            throws IOException, DataSetException
    {
        logger.debug("writeDtd(dataSet={}, out={}) - start", dataSet, out);
        FlatDtdDataSet.write(dataSet, out);
    }
}












