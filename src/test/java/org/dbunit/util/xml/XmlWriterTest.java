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
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DataType;
import org.dbunit.dataset.xml.XmlDataSet;
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
    void testLiterallyFalse_withNewlinesInText_writesLiteralNewlines() throws Exception
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
    void testLiterallyTrue_withNewlinesInTextAndLiterallyTrue_writesEncodedNewlines() throws Exception
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
    void testWriteAttributesAfterText_afterWritingText_throwsIllegalStateException() throws Exception
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
    void testWriteNestedCDATAWithoutSurrounder_withCdataAlreadyPresent_writesAsIs() throws Exception
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
    void testWriteNestedCDATAWithSurrounder_withCdataEndSequenceInText_splitsCdataSection() throws Exception
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
    void testOutputStreamWithNullEncoding_withUtf8Encoding_writesUtf8Declaration() throws Exception
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
    void testOutputStreamWithNonDefaultEncoding_withIso8859Encoding_writesIso8859Declaration() throws Exception
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
    void testEncodedXmlChar_withNonAsciiAndSpecialChars_encodesCorrectly() throws Exception
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
    void testNonAsciiValidXmlCharactersInAttributeValue_withCyrillicChars_writesCharsAsIs() throws Exception
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

    @Test
    void testEscapeXml_withMarkupCharacters_escapesUnchanged() throws Exception
    {
        final String text = "a & b < c > d \" e ' f";
        final String expectedText =
                "a &amp; b &lt; c &gt; d &quot; e &apos; f";
        final String expectedXml = "<COLUMN1 ATTR=\"" + expectedText + "\">"
                + expectedText + "</COLUMN1>\n";

        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeAttribute("ATTR", text);
        xmlWriter.writeText(text);
        xmlWriter.endElement();
        xmlWriter.close();

        assertThat(writer.toString())
                .as("Markup characters must still be entity-escaped the same"
                        + " way after routing escapeXml() through code points.")
                .isEqualTo(expectedXml);
    }

    @Test
    void testWriteText_controlCharacter_replacedWithReplacementChar()
            throws Exception
    {
        final String replacementChar = String.valueOf((char) 0xFFFD);
        final String controlChar = String.valueOf((char) 0x01);
        final String col0 = "COL0";
        final Column[] columns = {new Column(col0, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable("TABLE1", columns);
        table.addRow();
        table.setValue(0, col0, "before" + controlChar + "after");
        final IDataSet dataSet = new DefaultDataSet(table);

        final StringWriter out = new StringWriter();
        XmlDataSet.write(dataSet, out);
        final String xml = out.toString();

        assertThat(xml)
                .as("A control character not representable in XML 1.0 must"
                        + " be replaced with U+FFFD instead of written raw.")
                .contains("before" + replacementChar + "after")
                .doesNotContain(controlChar);

        final IDataSet reread = new XmlDataSet(new StringReader(xml));
        assertThat(reread.getTable("TABLE1").getValue(0, col0))
                .as("The exported document must still be well-formed and"
                        + " re-parseable, recovering the replacement"
                        + " character.")
                .isEqualTo("before" + replacementChar + "after");
    }

    @Test
    void testWriteText_unpairedSurrogate_replacedWithReplacementChar()
            throws Exception
    {
        final String replacementChar = String.valueOf((char) 0xFFFD);
        final String loneHighSurrogate = String.valueOf((char) 0xD800);
        final String col0 = "COL0";
        final Column[] columns = {new Column(col0, DataType.UNKNOWN)};
        final DefaultTable table = new DefaultTable("TABLE1", columns);
        table.addRow();
        table.setValue(0, col0, "before" + loneHighSurrogate + "after");
        final IDataSet dataSet = new DefaultDataSet(table);

        final StringWriter out = new StringWriter();
        XmlDataSet.write(dataSet, out);
        final String xml = out.toString();

        assertThat(xml)
                .as("A high surrogate with no matching low surrogate is not"
                        + " representable in XML 1.0 (escapeXml() iterates"
                        + " by code point, and codePointAt() returns an"
                        + " unpaired surrogate as its own \"code point\"),"
                        + " so it must be replaced with U+FFFD instead of"
                        + " written raw or as a numeric entity to a"
                        + " forbidden surrogate code point.")
                .contains("before" + replacementChar + "after")
                .doesNotContain(loneHighSurrogate);

        final IDataSet reread = new XmlDataSet(new StringReader(xml));
        assertThat(reread.getTable("TABLE1").getValue(0, col0))
                .as("The exported document must still be well-formed and"
                        + " re-parseable, recovering the replacement"
                        + " character.")
                .isEqualTo("before" + replacementChar + "after");
    }

    @Test
    void testEscapeXml_withAdjacentControlAndMarkupCharacters_bothSubstitutedCorrectly()
            throws Exception
    {
        final String replacementChar = String.valueOf((char) 0xFFFD);
        final String controlChar = String.valueOf((char) 0x01);
        // The control char and '&' are adjacent, back-to-back substitutions
        // with nothing in between - exactly where index/last bookkeeping
        // bugs in escapeXml()'s buffer-copying loop would hide
        final String text = "a" + controlChar + "&b";
        final String expectedText = "a" + replacementChar + "&amp;b";
        final String expectedXml =
                "<COLUMN1>" + expectedText + "</COLUMN1>\n";

        final Writer writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.writeElement("COLUMN1");
        xmlWriter.writeText(text);
        xmlWriter.endElement();
        xmlWriter.close();

        assertThat(writer.toString())
                .as("Two adjacent substitutions (a replaced control"
                        + " character immediately followed by an escaped"
                        + " markup character) must both be applied, with"
                        + " nothing dropped or duplicated between them.")
                .isEqualTo(expectedXml);
    }

    @Test
    void testClose_bufferedUnderlyingWriter_flushesAllContent() throws Exception
    {
        final StringWriter writer = new StringWriter();
        final XmlWriter xmlWriter = new XmlWriter(writer);
        xmlWriter.enablePrettyPrint(false);
        final int elementCount = 2000;

        final StringBuilder expectedXml = new StringBuilder();
        for (int i = 0; i < elementCount; i++)
        {
            xmlWriter.writeEmptyElement("ROW" + i);
            expectedXml.append("<ROW").append(i).append("/>");
        }
        xmlWriter.close();

        assertThat(writer.toString())
                .as("All buffered XML output should be flushed to the underlying writer after close().")
                .isEqualTo(expectedXml.toString());
    }
}
