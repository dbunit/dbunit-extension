/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2006, DbUnit.org
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
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.datatype.TypeCastException;
import org.dbunit.dataset.stream.DataSetProducerAdapter;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.util.xml.XmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.5.5 (Jun 13, 2003)
 */
public class XmlDataSetWriter implements IDataSetConsumer
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(XmlDataSetWriter.class);

    private static final String DATASET = "dataset";
    private static final String TABLE = "table";
    private static final String NAME = "name";
    private static final String COLUMN = "column";
    private static final String ROW = "row";
    private static final String VALUE = "value";
    private static final String NULL = "null";
    private static final String NONE = "none";

    static char[] CDATA_DETECTION_CHARS = new char[] {
        0x20, '\n', '\r', '\t',     // whitespace
        '&', '<',                   // forbidden char
    };

    private XmlWriter _xmlWriter;
    private ITableMetaData _activeMetaData;
    private boolean includeColumnComments = false;


    /**
     * @param outputStream The stream to which the XML will be written.
     * @param encoding The encoding to be used for the {@link XmlWriter}.
     * Can be null. See {@link XmlWriter#XmlWriter(OutputStream, String)}.
     * @throws UnsupportedEncodingException
     */
    public XmlDataSetWriter(OutputStream outputStream, Charset charset)
    {
        _xmlWriter = new XmlWriter(outputStream, charset);
        _xmlWriter.enablePrettyPrint(true);
    }

    public XmlDataSetWriter(Writer writer)
    {
        _xmlWriter = new XmlWriter(writer);
        _xmlWriter.enablePrettyPrint(true);
    }

    public XmlDataSetWriter(Writer writer, Charset charset)
    {
        _xmlWriter = new XmlWriter(writer, charset);
        _xmlWriter.enablePrettyPrint(true);
    }

    /**
     * Enable or disable pretty print of the XML.
     * @param enabled <code>true</code> to enable pretty print (which is the default). 
     * <code>false</code> otherwise.
     * @since 2.4
     */
    public void setPrettyPrint(boolean enabled)
    {
        _xmlWriter.enablePrettyPrint(enabled);
    }

    /**
     * Whether or not to write the column name as comment into the XML
     * @param includeColumnComments Whether or not to write the column name as comment into the XML
     */
    public void setIncludeColumnComments(boolean includeColumnComments)
    {
      this.includeColumnComments = includeColumnComments;
    }

    /**
     * Writes the given {@link IDataSet} using this writer.
     * @param dataSet The {@link IDataSet} to be written
     * @throws DataSetException
     */
    public void write(IDataSet dataSet) throws DataSetException
    {
        logger.trace("write(dataSet{}) - start", dataSet);

        DataSetProducerAdapter provider = new DataSetProducerAdapter(dataSet);
        provider.setConsumer(this);
        provider.produce();
    }

    boolean needsCData(String text)
    {
        logger.trace("needsCData(text={}) - start", text);

        if (text == null)
        {
            return false;
        }

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            for (int j = 0; j < CDATA_DETECTION_CHARS.length; j++)
            {
                if (CDATA_DETECTION_CHARS[j] == c)
                {
                    return true;
                }
            }
        }
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetConsumer interface

    public void startDataSet() throws DataSetException
    {
        logger.trace("startDataSet() - start");

        try
        {
            _xmlWriter.writeDeclaration();
            _xmlWriter.writeElement(DATASET);
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }
    }

    public void endDataSet() throws DataSetException
    {
        logger.trace("endDataSet() - start");

        try
        {
            _xmlWriter.endElement();
            _xmlWriter.close();
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }
    }

    public void startTable(ITableMetaData metaData) throws DataSetException
    {
        logger.trace("startTable(metaData={}) - start", metaData);

        try
        {
            _activeMetaData = metaData;

            String tableName = _activeMetaData.getTableName();
            _xmlWriter.writeElement(TABLE);
            _xmlWriter.writeAttribute(NAME, tableName);

            Column[] columns = _activeMetaData.getColumns();
            for (int i = 0; i < columns.length; i++)
            {
                String columnName = columns[i].getColumnName();
                _xmlWriter.writeElementWithText(COLUMN, columnName);
            }
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }

    }

    public void endTable() throws DataSetException
    {
        logger.trace("endTable() - start");

        try
        {
            _xmlWriter.endElement();
            _activeMetaData = null;
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }
    }

    public void row(Object[] values) throws DataSetException
    {
        logger.trace("row(values={}) - start", values);

        try
        {
            _xmlWriter.writeElement(ROW);

            Column[] columns = _activeMetaData.getColumns();
            for (int i = 0; i < columns.length; i++)
            {
                String columnName = columns[i].getColumnName();
                Object value = values[i];
                
                // null
                if (value == null)
                {
                    _xmlWriter.writeEmptyElement(NULL);
                }
                // none
                else if (value == ITable.NO_VALUE)
                {
                    _xmlWriter.writeEmptyElement(NONE);
                }
                // values
                else
                {
                    try
                    {
                        String stringValue = DataType.asString(value);

                        _xmlWriter.writeElement(VALUE);
                        if (needsCData(stringValue))
                        {
                            writeValueCData(stringValue);
                        }
                        else if (stringValue.length() > 0)
                        {
                            writeValue(stringValue);
                        }
                        _xmlWriter.endElement();
                    }
                    catch (TypeCastException e)
                    {
                        throw new DataSetException("table=" +
                                _activeMetaData.getTableName() + ", row=" + i +
                                ", column=" + columnName +
                                ", value=" + value, e);
                    }
                }
                if ( this.includeColumnComments ) {
                  _xmlWriter.writeComment( columnName );
                }
            }
            _xmlWriter.endElement();
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }
    }

    /**
     * Writes the given String as CDATA using the {@link XmlWriter}.
     * Can be overridden to add custom behavior.
     * This implementation just invokes {@link XmlWriter#writeCData(String)}
     * @param stringValue The value to be written
     * @throws IOException
     * @since 2.4.4
     */
    protected void writeValueCData(String stringValue) throws IOException
    {
        logger.trace("writeValueCData(stringValue={}) - start", stringValue);
        _xmlWriter.writeCData(stringValue);
    }
    
    /**
     * Writes the given String as normal text using the {@link XmlWriter}.
     * Can be overridden to add custom behavior.
     * This implementation just invokes {@link XmlWriter#writeText(String)}.
     * @param stringValue The value to be written
     * @throws IOException
     * @since 2.4.4
     */
    protected void writeValue(String stringValue) throws IOException
    {
        logger.trace("writeValue(stringValue={}) - start", stringValue);
        _xmlWriter.writeText(stringValue);
    }

    /**
     * @return The {@link XmlWriter} that is used for writing out XML.
     * @since 2.4.4
     */
    protected final XmlWriter getXmlWriter()
    {
        return _xmlWriter;
    }
}
