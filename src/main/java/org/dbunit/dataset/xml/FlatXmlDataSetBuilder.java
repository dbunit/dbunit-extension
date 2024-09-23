/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * Builder for the creation of {@link FlatXmlDataSet} instances.
 * 
 * @see FlatXmlDataSet
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.7
 */
public final class FlatXmlDataSetBuilder
{
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(FlatXmlDataSetBuilder.class);

    /**
     * The metadata (column information etc.) for the flat XML to be built. 
     * If this is set the builder properties
     * <ul>
     * <li>{@link #columnSensing}</li>
     * <li>{@link #caseSensitiveTableNames}</li>
     * <li>{@link #dtdMetadata}</li>
     * </ul>
     * are <b>not</b> regarded.
     */
    private IDataSet metaDataSet = null;
    
    /**
     * Whether or not DTD metadata is available to parse via a DTD handler. Defaults to {@value}
     */
    private boolean dtdMetadata = true;
    
//TODO Think about this: should we use "columnSensing=true" by default if no DTD is specified? To avoid e.g. bug reports like #2812985 https://sourceforge.net/tracker/?func=detail&atid=449491&aid=2812985&group_id=47439
    /**
     * Since DBUnit 2.3.0 there is a functionality called "column sensing" which basically
     * reads in the whole XML into a buffer and dynamically adds new columns as they appear.
     * Defaults to {@value}
     */
    private boolean columnSensing = false;
    /**
    * Whether or not the created dataset should use case sensitive table names
    * Defaults to {@value}
    */
    private boolean caseSensitiveTableNames = false;
    
    
    /**
     * Default constructor
     */
    public FlatXmlDataSetBuilder()
    {
    }
    
    /**
     * Sets the flat XML input source from which the {@link FlatXmlDataSet} is to be built
     * @param inputSource The flat XML input as {@link InputSource}
     * @return The created {@link FlatXmlDataSet}
     * @throws DataSetException 
     */
    public FlatXmlDataSet build(InputSource inputSource) throws DataSetException
    {
        return buildInternal(inputSource);
    }

    /**
     * Sets the flat XML input source from which the {@link FlatXmlDataSet} is to be built
     * @param xmlInputFile The flat XML input as {@link File}
     * @return The created {@link FlatXmlDataSet}
     * @throws DataSetException 
     */
    public FlatXmlDataSet build(File xmlInputFile) throws MalformedURLException, DataSetException
    {
        URL xmlInputUrl = xmlInputFile.toURL();
        InputSource inputSource = createInputSourceFromUrl(xmlInputUrl);
        return buildInternal(inputSource);
    }
    
    /**
     * Sets the flat XML input source from which the {@link FlatXmlDataSet} is to be built
     * @param xmlInputUrl The flat XML input as {@link URL}
     * @return The created {@link FlatXmlDataSet}
     * @throws DataSetException 
     */
    public FlatXmlDataSet build(URL xmlInputUrl) throws DataSetException
    {
        InputSource inputSource = createInputSourceFromUrl(xmlInputUrl);
        return buildInternal(inputSource);
    }
    
    /**
     * Sets the flat XML input source from which the {@link FlatXmlDataSet} is to be built
     * @param xmlReader The flat XML input as {@link Reader}
     * @return The created {@link FlatXmlDataSet}
     * @throws DataSetException 
     */
    public FlatXmlDataSet build(Reader xmlReader) throws DataSetException
    {
        InputSource inputSource = new InputSource(xmlReader);
        return buildInternal(inputSource);
    }

    /**
     * Sets the flat XML input source from which the {@link FlatXmlDataSet} is to be built
     * @param xmlInputStream The flat XML input as {@link InputStream}
     * @return The created {@link FlatXmlDataSet}
     * @throws DataSetException 
     */
    public FlatXmlDataSet build(InputStream xmlInputStream) throws DataSetException
    {
        InputSource inputSource = new InputSource(xmlInputStream);
        return buildInternal(inputSource);
    }
    
    /**
     * Utility method to create an {@link InputSource} object from a URL
     * @param xmlInputUrl
     * @return
     */
    private InputSource createInputSourceFromUrl(URL xmlInputUrl)
    {
        String stringUrl = xmlInputUrl.toString();
        return new InputSource(stringUrl);
    }
    
    /**
     * Set the metadata information (column info etc.) to be used. May come from a DTD.
     * This has precedence to the other builder's properties.
     * @param metaDataSet
     * @return this
     */
    public FlatXmlDataSetBuilder setMetaDataSet(IDataSet metaDataSet) 
    {
        this.metaDataSet = metaDataSet;
        return this;
    }

    /**
     * Set the metadata information (column info etc.) to be used from the given DTD input.
     * This has precedence to the other builder's properties.
     * @param dtdReader A reader that provides the DTD content
     * @throws DataSetException
     * @throws IOException
     * @return this
     */
    public FlatXmlDataSetBuilder setMetaDataSetFromDtd(Reader dtdReader) throws DataSetException, IOException
    {
        this.metaDataSet = new FlatDtdDataSet(dtdReader);
        return this;
    }
    
    /**
     * Set the metadata information (column info etc.) to be used from the given DTD input.
     * This has precedence to the other builder's properties.
     * @param dtdStream
     * @throws DataSetException
     * @throws IOException
     * @return this
     */
    public FlatXmlDataSetBuilder setMetaDataSetFromDtd(InputStream dtdStream) throws DataSetException, IOException
    {
        this.metaDataSet = new FlatDtdDataSet(dtdStream);
        return this;
    }
    
    public boolean isDtdMetadata() {
        return dtdMetadata;
    }

    /**
     * Whether or not DTD metadata is available to parse via a DTD handler.
     * @param dtdMetadata
     * @return this
     */
    public FlatXmlDataSetBuilder setDtdMetadata(boolean dtdMetadata) {
        this.dtdMetadata = dtdMetadata;
        return this;
    }

    public boolean isColumnSensing() {
        return columnSensing;
    }

    /**
     * Since DBUnit 2.3.0 there is a functionality called "column sensing" which basically
     * reads in the whole XML into a buffer and dynamically adds new columns as they appear.
     * @param columnSensing
     * @return this
     */
    public FlatXmlDataSetBuilder setColumnSensing(boolean columnSensing) {
        this.columnSensing = columnSensing;
        return this;
    }

    public boolean isCaseSensitiveTableNames() {
        return caseSensitiveTableNames;
    }

    /**
     * Whether or not the created dataset should use case sensitive table names
     * @param caseSensitiveTableNames
     * @return this
     */
    public FlatXmlDataSetBuilder setCaseSensitiveTableNames(boolean caseSensitiveTableNames) {
        this.caseSensitiveTableNames = caseSensitiveTableNames;
        return this;
    }


    /**
     * Builds the {@link FlatXmlDataSet} from the parameters that are currently set on this builder
     * @param inputSource The XML input to be built
     * @return The {@link FlatXmlDataSet} built from the configuration of this builder.
     * @throws DataSetException
     */
    private FlatXmlDataSet buildInternal(InputSource inputSource) throws DataSetException
    {
        logger.trace("build(inputSource={}) - start", inputSource);
        
        // Validate required parameters
        if(inputSource==null)
        {
            throw new NullPointerException("The parameter 'inputSource' must not be null");
        }
        
        // Create the flat XML IDataSet
        logger.debug("Creating FlatXmlDataSet with builder parameters: {}", this);
        FlatXmlProducer producer = createProducer(inputSource);
        return new FlatXmlDataSet(producer);
    }

    /**
     * @param inputSource The XML input to be built
     * @return The producer which is used to create the {@link FlatXmlDataSet}
     */
    protected FlatXmlProducer createProducer(InputSource inputSource) 
    {
        logger.trace("createProducer(inputSource={}) - start", inputSource);
        
        FlatXmlProducer producer = null;
        if(this.metaDataSet!=null)
        {
            logger.debug("Creating FlatXmlProducer using the following metaDataSet: {}", this.metaDataSet);
            producer = new FlatXmlProducer(inputSource, this.metaDataSet);
        }
        else
        {
            logger.debug("Creating FlatXmlProducer using the properties of this builder: {}", this);
            producer = new FlatXmlProducer(
                    inputSource, this.dtdMetadata, this.columnSensing, this.caseSensitiveTableNames);
        }
        return producer;
    }
    
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append("[");
        sb.append("dtdMetadata=").append(dtdMetadata);
        sb.append(", columnSensing=").append(columnSensing);
        sb.append(", caseSensitiveTableNames=").append(caseSensitiveTableNames);
        sb.append(", metaDataSet=").append(metaDataSet);
        sb.append("]");
        return sb.toString();
    }
}