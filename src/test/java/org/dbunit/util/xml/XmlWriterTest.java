/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2008, DbUnit.org
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
package org.dbunit.util.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * @author gommma
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.3.0
 */
class XmlWriterTest
{

    @Test
    void testLiterallyFalse() throws Exception
    {
        final String text = "text1\ntext2\rtext3";
        final String expectedXml =
                "<COLUMN1 ATTR=\"" + text + "\">" + text + "</COLUMN1>\n";
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeAttribute("ATTR", text);
        xmlWriter.writeText(text);
        xmlWriter.endElement();
        xmlWriter.close();
        final String actualXml = writer.toString();
        assertThat(actualXml).isEqualTo(expectedXml);
    }

    @Test
    void testLiterallyTrue() throws Exception
    {
        final String expectedText = "text1&#xA;text2&#xD;text3";
        final String expectedXml = "<COLUMN1 ATTR=\"" + expectedText + "\">"
                + expectedText + "</COLUMN1>\n";

        final boolean literally = true;
        final String text = "text1\ntext2\rtext3";
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeAttribute("ATTR", text, literally);
        xmlWriter.writeText(text, literally);
        xmlWriter.endElement();
        xmlWriter.close();
        final String actualXml = writer.toString();
        assertThat(actualXml).isEqualTo(expectedXml);
    }

    @Test
    void testWriteAttributesAfterText() throws Exception
    {
        final String text = "bla";
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeText(text);
        try
        {
            xmlWriter.writeAttribute("ATTR", text);
            fail("Should not be able to add attributes afterwards with the current XmlWriter implementation (which could be better...)");
        } catch (final IllegalStateException expected)
        {
            // all right
        }
    }

    @Test
    void testWriteNestedCDATAWithoutSurrounder() throws Exception
    {
        final String text =
                "<![CDATA[Text that itself is in a CDATA section]]>";
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeCData(text);
        xmlWriter.endElement();
        xmlWriter.close();
        final String actualXml = writer.toString();

        // Input should be equal to output because the text already starts with
        // a CDATA section
        assertThat(actualXml).isEqualTo("<COLUMN1>" + text + "</COLUMN1>\n");
    }

    @Test
    void testWriteNestedCDATAWithSurrounder() throws Exception
    {
        final String text = "<myXmlText>" + XmlWriter.CDATA_START
                + "Text that itself is in a CDATA section" + XmlWriter.CDATA_END
                + "</myXmlText>";
        final String expectedResultText = "<myXmlText>" + XmlWriter.CDATA_START
                + "Text that itself is in a CDATA section]]"
                + XmlWriter.CDATA_END + XmlWriter.CDATA_START + "></myXmlText>";
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeCData(text);
        xmlWriter.endElement();
        xmlWriter.close();
        final String actualXml = writer.toString();

        final String expectedXml = "<COLUMN1>" + XmlWriter.CDATA_START
                + expectedResultText + XmlWriter.CDATA_END + "</COLUMN1>\n";
        assertThat(actualXml).isEqualTo(expectedXml);
    }

    @Test
    void testOutputStreamWithNullEncoding() throws Exception
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Use a different encoding than the default
        final XmlWriter xmlWriter = new XmlWriter(out, StandardCharsets.UTF_8);
        xmlWriter.writeDeclaration();
        xmlWriter.writeEmptyElement("COLUMN1");
        xmlWriter.close();

        final String expected =
                "<?xml version='1.0' encoding='UTF-8'?>\n" + "<COLUMN1/>\n";
        assertThat(out.toString("UTF-8")).isEqualTo(expected);
    }

    @Test
    void testOutputStreamWithNonDefaultEncoding() throws Exception
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Use a different encoding than the default
        final XmlWriter xmlWriter = new XmlWriter(out, StandardCharsets.ISO_8859_1);
        xmlWriter.writeDeclaration();
        xmlWriter.writeEmptyElement("COLUMN1");
        xmlWriter.close();

        final String expected = "<?xml version='1.0' encoding='ISO-8859-1'?>\n"
                + "<COLUMN1/>\n";
        assertThat(out.toString("ISO-8859-1")).isEqualTo(expected);
    }

    @Test
    void testEncodedXmlChar() throws Exception
    {
        final String expectedText = "\u00AEtext1&#xA;text2&#xD;text3\u00AE";
        final String expectedXml = "<COLUMN1 ATTR=\"" + expectedText + "\">"
                + expectedText + "</COLUMN1>\n";

        final boolean literally = true;
        final StringBuilder textBuilder = new StringBuilder();
        final String registeredSymbol = new String(new char[] {0xAE});
        textBuilder.append(registeredSymbol);
        textBuilder.append("text1\ntext2\rtext3");
        textBuilder.append(registeredSymbol);
        final String text = textBuilder.toString();
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeAttribute("ATTR", text, literally);
        xmlWriter.writeText(text, literally);
        xmlWriter.endElement();
        xmlWriter.close();
        final String actualXml = writer.toString();
        assertThat(actualXml).isEqualTo(expectedXml);
    }

    @Test
    void testNonAsciiValidXmlCharactersInAttributeValue() throws Exception
    {
        final String expectedText = "привет";
        final String expectedXml = "<COLUMN1 ATTR=\"" + expectedText + "\"/>\n";

        final boolean literally = true;
        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeAttribute("ATTR", expectedText, literally);
        xmlWriter.endElement();
        xmlWriter.close();

        final String actualXml = writer.toString();
        assertThat(actualXml).isEqualTo(expectedXml);
    }
}
