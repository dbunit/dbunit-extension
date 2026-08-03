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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

import org.dbunit.dataset.CachedDataSet;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * Reads and writes original XML dataset document. This format
 * is very verbose and must conform to the following DTD:
 * 
<pre>
&lt;?xml version="1.0" encoding="UTF-8"?&gt;
&lt;!ELEMENT dataset (table+)&gt;
&lt;!ELEMENT table (column*, row*)&gt;
&lt;!ATTLIST table name CDATA #REQUIRED&gt;
&lt;!ELEMENT column (#PCDATA)&gt;
&lt;!ELEMENT row (value | null | none)*&gt;
&lt;!ELEMENT value (#PCDATA)&gt;
&lt;!ELEMENT null EMPTY&gt;
&lt;!ELEMENT none EMPTY&gt;
</pre>

 *
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Feb 17, 2002)
 */
public class XmlDataSet extends CachedDataSet
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(XmlDataSet.class);


    /**
     * Creates an XmlDataSet with the specified xml reader.
     *
     * @param reader the reader to load the xml document from.
     * @throws DataSetException if the document cannot be parsed.
     */
    public XmlDataSet(Reader reader) throws DataSetException
    {
        super(new XmlProducer(new InputSource(reader)));
    }

    /**
     * Creates an XmlDataSet with the specified xml input stream.
     *
     * @param in the stream to load the xml document from.
     * @throws DataSetException if the document cannot be parsed.
     */
    public XmlDataSet(InputStream in) throws DataSetException
    {
        super(new XmlProducer(new InputSource(in)));
    }

    /**
     * Write the specified dataset to the specified output stream as xml.
     *
     * @param dataSet the dataset to write.
     * @param out the stream to write the xml document to.
     * @throws IOException if writing to the stream fails.
     * @throws DataSetException if the dataset cannot be read.
     */
    public static void write(IDataSet dataSet, OutputStream out)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}) - start", dataSet, out);
        XmlDataSet.write(dataSet, out, null);
    }

    /**
     * Write the specified dataset to the specified output stream as xml (using specified encoding).
     *
     * @param dataSet the dataset to write.
     * @param out the stream to write the xml document to.
     * @param charset the character encoding to write the document in.
     * @throws IOException if writing to the stream fails.
     * @throws DataSetException if the dataset cannot be read.
     */
    public static void write(IDataSet dataSet, OutputStream out, Charset charset)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, out={}, charset={}) - start",
                dataSet, out, charset);

        XmlDataSetWriter datasetWriter = new XmlDataSetWriter(out, charset);
        datasetWriter.write(dataSet);
    }

    /**
     * Write the specified dataset to the specified writer as xml.
     *
     * @param dataSet the dataset to write.
     * @param writer the writer to write the xml document to.
     * @throws IOException if writing to the writer fails.
     * @throws DataSetException if the dataset cannot be read.
     */
    public static void write(IDataSet dataSet, Writer writer)
            throws IOException, DataSetException
    {
        logger.debug("write(dataSet={}, writer={}) - start", dataSet, writer);
        write(dataSet, writer, Charset.defaultCharset());
    }

    /**
     * Write the specified dataset to the specified writer as xml.
     *
     * @param dataSet the dataset to write.
     * @param writer the writer to write the xml document to.
     * @param charset the character encoding to write the document in.
     * @throws IOException if writing to the writer fails.
     * @throws DataSetException if the dataset cannot be read.
     */
    public static void write(IDataSet dataSet, Writer writer, Charset charset)
            throws IOException, DataSetException
    {
    	logger.debug("write(dataSet={}, writer={}, charset={}) - start",
    		dataSet, writer, charset);

        XmlDataSetWriter datasetWriter = new XmlDataSetWriter(writer, charset);
        datasetWriter.write(dataSet);
    }
}
