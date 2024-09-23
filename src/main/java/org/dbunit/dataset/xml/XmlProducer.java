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
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.stream.DefaultConsumer;
import org.dbunit.dataset.stream.IDataSetConsumer;
import org.dbunit.dataset.stream.IDataSetProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses an XML and produces a dataset from it.
 * 
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Apr 30, 2003
 */
public class XmlProducer extends DefaultHandler
        implements IDataSetProducer, ContentHandler, ErrorHandler
{

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(XmlProducer.class);

    private static final IDataSetConsumer EMPTY_CONSUMER = new DefaultConsumer();

    private static final String DATASET = "dataset";
    private static final String TABLE = "table";
    private static final String NAME = "name";
    private static final String COLUMN = "column";
    private static final String ROW = "row";
    private static final String VALUE = "value";
    private static final String NULL = "null";
    private static final String NONE = "none";

    private final InputSource _inputSource;
    private boolean _validating = false;

    private IDataSetConsumer _consumer = EMPTY_CONSUMER;


    private String _activeTableName;
    private ITableMetaData _activeMetaData;

    private List _activeColumnNames;
    private StringBuilder _activeCharacters;
    private List _activeRowValues;

    public XmlProducer(InputSource inputSource)
    {
        _inputSource = inputSource;
    }

    private ITableMetaData createMetaData(String tableName, List columnNames)
    {
        logger.debug("createMetaData(tableName={}, _columnNames={}) - start", tableName, columnNames);

        Column[] columns = new Column[columnNames.size()];
        for (int i = 0; i < columns.length; i++)
        {
            String columnName = (String)columnNames.get(i);
            columns[i] = new Column(columnName, DataType.UNKNOWN);
        }
        DefaultTableMetaData metaData = new DefaultTableMetaData(tableName, columns);
        return metaData;
    }

    public void setValidating(boolean validating)
    {
        _validating = validating;
    }

    ////////////////////////////////////////////////////////////////////////////
    // IDataSetProducer interface

    public void setConsumer(IDataSetConsumer consumer) throws DataSetException
    {
        logger.debug("setConsumer(consumer={}) - start", consumer);
        _consumer = consumer;
    }

    public void produce() throws DataSetException
    {
        logger.debug("produce() - start");

        try
        {
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            saxParserFactory.setValidating(_validating);
            XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();

            xmlReader.setContentHandler(this);
            xmlReader.setEntityResolver(this);
            xmlReader.setErrorHandler(this);
            xmlReader.parse(_inputSource);
        }
        catch (ParserConfigurationException e)
        {
            throw new DataSetException(e);
        }
        catch (SAXException e)
        {
            DataSetException exceptionToRethrow = XmlProducer.buildException(e);
            throw exceptionToRethrow;
        }
        catch (IOException e)
        {
            throw new DataSetException(e);
        }
    }

    /**
     * Wraps a {@link SAXException} into a {@link DataSetException}
     * @param cause The cause to be wrapped into a {@link DataSetException}
     * @return A {@link DataSetException} that wraps the given {@link SAXException}
     */
    protected final static DataSetException buildException(SAXException cause) 
    {
        int lineNumber = -1;
        if (cause instanceof SAXParseException)
        {
            lineNumber = ((SAXParseException)cause).getLineNumber();
        }
        Exception exception = cause.getException() == null ? cause : cause.getException();
        String message;
        
        if (lineNumber >= 0)
        {
            message = "Line " + lineNumber + ": " + exception.getMessage();
        }
        else {
            message = exception.getMessage();
        }

        if(exception instanceof DataSetException) {
            return (DataSetException) exception;
        }
        else {
            return new DataSetException(message, exception);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // EntityResolver interface

    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException
    {
        logger.debug("resolveEntity(publicId={}, systemId={}) - start", publicId, systemId);

        InputStream in = getClass().getClassLoader().getResourceAsStream(
                "org/dbunit/dataset/xml/dataset.dtd");
        return (new InputSource(in));
    }

    ////////////////////////////////////////////////////////////////////////
    // ContentHandler interface

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
    	logger.debug("startElement(uri={}, localName={}, qName={}, attributes={}) - start",
    		uri, localName, qName, attributes);

        try
        {
            // dataset
            if (qName.equals(DATASET))
            {
                _consumer.startDataSet();
                return;
            }

            // table
            if (qName.equals(TABLE))
            {
                _activeTableName = attributes.getValue(NAME);
                _activeColumnNames = new LinkedList();
                return;
            }

            // column
            if (qName.equals(COLUMN))
            {
                _activeCharacters = new StringBuilder();
                return;
            }

            // row
            if (qName.equals(ROW))
            {
                // End of metadata at first row
                if (_activeColumnNames != null)
                {
                    _activeMetaData = createMetaData(_activeTableName,_activeColumnNames);
                    _consumer.startTable(_activeMetaData);
                    _activeColumnNames = null;

                }

                _activeRowValues = new LinkedList();
                return;
            }

            // value
            if (qName.equals(VALUE))
            {
                _activeCharacters = new StringBuilder();
                return;
            }

            // null
            if (qName.equals(NULL))
            {
                _activeRowValues.add(null);
                return;
            }

            // none
            if (qName.equals(NONE))
            {
                _activeRowValues.add(ITable.NO_VALUE);
                return;
            }
        }
        catch (DataSetException e)
        {
            throw new SAXException(e);
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException
    {
    	logger.debug("endElement(uri={}, localName={}, qName={}) - start", uri, localName, qName);

        try
        {
            // dataset
            if (qName.equals(DATASET))
            {
                _consumer.endDataSet();
                return;
            }

            // table
            if (qName.equals(TABLE))
            {
                // End of metadata
                if (_activeColumnNames != null)
                {
                    _activeMetaData = createMetaData(_activeTableName, _activeColumnNames);
                    _consumer.startTable(_activeMetaData);
                    _activeColumnNames = null;
                }

                _consumer.endTable();
                _activeTableName = null;
                _activeMetaData = null;
                return;
            }

            // column
            if (qName.equals(COLUMN))
            {
                _activeColumnNames.add(_activeCharacters.toString());
                _activeCharacters = null;
                return;
            }

            // row
            if (qName.equals(ROW))
            {
                final int length = Math.max(_activeRowValues.size(), _activeMetaData.getColumns().length);
                Object[] values = new Object[length];
                for (int i = 0; i < values.length; i++)
                {
                    values[i] = (i >= _activeRowValues.size()) ? ITable.NO_VALUE : _activeRowValues.get(i);
                }
                _consumer.row(values);
                _activeRowValues = null;
                return;
            }

            // value
            if (qName.equals(VALUE))
            {
                _activeRowValues.add(_activeCharacters.toString());
                _activeCharacters = null;
                return;
            }

            // null
            if (qName.equals(NULL))
            {
                // Nothing to do, already processed in startElement()
                return;
            }

            // none
            if (qName.equals(NONE))
            {
                // Nothing to do, already processed in startElement()
                return;
            }
        }
        catch (DataSetException e)
        {
            throw new SAXException(e);
        }
    }

    public void characters(char ch[], int start, int length)
            throws SAXException
    {
        if (_activeCharacters != null)
        {
            _activeCharacters.append(ch, start, length);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // ErrorHandler interface

//    public void warning(SAXParseException e)
//            throws SAXException
//    {
//        throw e;
//    }

    public void error(SAXParseException e)
            throws SAXException
    {
        throw e;
    }

//    public void fatalError(SAXParseException e)
//            throws SAXException
//    {
//        throw e;
//    }


}
