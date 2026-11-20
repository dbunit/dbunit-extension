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
import java.io.StringReader;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Tells whether an XML file is a full XML dataset, by reading its elements without loading any
 * DTD it declares: a {@code dataset} root element with a {@code table} element that has a
 * {@code name} attribute and a {@code column} or {@code row} element. It serves
 * {@link FileExtensionDataFileLoader} for a file that its flat XML load rejected, as a flat XML
 * load that reads a DTD fails on the first full XML element, or on the DTD itself, before the
 * loader can look at what it loaded.
 *
 * <p>
 * This class is internal to dbUnit's file loaders; do not use it directly.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class FullXmlFileShape
{
    private static final Logger log = LoggerFactory.getLogger(FullXmlFileShape.class);

    private static final String ROOT_ELEMENT = "dataset";
    private static final String TABLE_ELEMENT = "table";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String COLUMN_ELEMENT = "column";
    private static final String ROW_ELEMENT = "row";

    /**
     * Returns whether the file is a full XML dataset with a column or a row.
     *
     * @param url
     *            The URL of the XML file.
     * @return True if it is; false if it is not or cannot be read as XML.
     */
    boolean isFullXml(final URL url)
    {
        final ShapeHandler handler = new ShapeHandler();
        try
        {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            final SAXParser parser = factory.newSAXParser();
            parser.parse(url.toString(), handler);
        } catch (final FullXmlFound found)
        {
            return true;
        } catch (final ParserConfigurationException | SAXException | IOException e)
        {
            log.debug("isFullXml: Cannot read {} as XML", url, e);
            return false;
        }
        return false;
    }

    private static class FullXmlFound extends SAXException
    {
        private static final long serialVersionUID = 1L;

        FullXmlFound()
        {
            super("full XML");
        }
    }

    private static class ShapeHandler extends DefaultHandler
    {
        private int depth;
        private boolean rootIsDataset;
        private boolean inNamedTable;

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId)
        {
            return new InputSource(new StringReader(""));
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName,
                final Attributes attributes) throws SAXException
        {
            depth++;
            if (depth == 1)
            {
                rootIsDataset = ROOT_ELEMENT.equals(qName);
            } else if (rootIsDataset && depth == 2)
            {
                inNamedTable = TABLE_ELEMENT.equals(qName)
                        && attributes.getValue(NAME_ATTRIBUTE) != null;
            } else if (inNamedTable && depth == 3
                    && (COLUMN_ELEMENT.equals(qName) || ROW_ELEMENT.equals(qName)))
            {
                throw new FullXmlFound();
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName)
        {
            depth--;
        }
    }
}
