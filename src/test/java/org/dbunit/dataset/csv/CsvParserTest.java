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
package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import org.dbunit.dataset.common.handlers.IllegalInputCharacterException;
import org.dbunit.dataset.common.handlers.PipelineException;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvParserTest
{

    CsvParser parser;

    /*
     * public void testNewParserHasNotNullPipeline() {
     * assertThat(parser.getPipeline()).isNotNull(); }
     *
     * public void testAfterEachParsingThePipelineIsEmpty() throws
     * PipelineException, IllegalInputCharacterException {
     *
     * class MockPipeline extends Pipeline { boolean setProductCalled = false;
     *
     * protected void setProducts(List products) { assertThat(
     * products.size()).isEqualTo(0); super.setProducts(products);
     * setProductCalled = true; } }
     *
     * MockPipeline mockPipeline = new MockPipeline();
     * parser.setPipeline(mockPipeline); parser.parse("");
     * assertTrue("the set product method should be called to prepare a new list of products"
     * , mockPipeline.setProductCalled); }
     */

    @Test
    void testCanParseNonQuotedStrings()
            throws PipelineException, IllegalInputCharacterException
    {
        final String csv = "Hello, world";
        final List parsed = parser.parse(csv);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).isEqualTo("Hello");
        assertThat(parsed.get(1)).isEqualTo("world");
    }

    @Test
    void testAFieldCanContainANewLine()
            throws PipelineException, IllegalInputCharacterException
    {
        assertThat(parser
                .parse("Hello, World\nIt's today, the day before tomorrow"))
                        .as("").hasSize(3);
    }

    @Test
    void testDontAcceptIncompleteFields()
            throws PipelineException, IllegalInputCharacterException
    {
        final String incompleteFields = "AAAAA,\"BB";

        assertThrows(IllegalStateException.class,
                () -> parser.parse(incompleteFields),
                "should have thrown an exception");
    }

    @Test
    void testAFileCanContainFieldWithNewLine()
            throws IOException, CsvParserException
    {
        final String pathname = "csv/with-newlines.csv";
        final List list = parser.parse(TestUtils.getFile(pathname));
        assertThat(list).as("wrong number of lines parsed from " + pathname)
                .hasSize(2);
        final List row = (List) list.get(1);
        assertThat(row.get(0)).isEqualTo("AA\nAAA");
        assertThat(row.get(1)).isEqualTo("BB\nBBB");
    }

    @Test
    void testRaiseACSVParserExceptonWhenParsingAnEmptyFile() throws IOException
    {
        failParsing(TestUtils.getFile("csv/empty-file.csv"));
    }

    @Test
    void testRaiseACSVParserExceptonWhenParsingFileWithDifferentNumberOfColumns()
            throws IllegalInputCharacterException, IOException,
            PipelineException
    {
        failParsing(TestUtils.getFile("csv/different-column-numbers-last.csv"));
        failParsing(
                TestUtils.getFile("csv/different-column-numbers-first.csv"));
    }

    private void failParsing(final File sample) throws IOException
    {
        try
        {
            parser.parse(sample);
            fail("should have thrown a CsvParserException");
        } catch (final CsvParserException e)
        {
            assertTrue(true);
        }
    }

    @Test
    void testSample() throws Exception
    {

        final File sample = TestUtils.getFile("csv/sample.csv");
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sample)));
        final LineNumberReader lineNumberReader = new LineNumberReader(reader);
        String line;
        while ((line = lineNumberReader.readLine()) != null)
        {
            if (line.startsWith("#") || line.trim().length() == 0)
                continue;
            // System.out.println("line: " + line);
            final List actual = parser.parse(line);
            assertThat(actual)
                    .as("wrong tokens on line "
                            + lineNumberReader.getLineNumber() + " " + line)
                    .hasSize(3);
        }
    }

    @Test
    void testWhitespacePreservedOnQuotedStrings()
            throws PipelineException, IllegalInputCharacterException
    {
        String csv = "\" Hello, \",world";
        List parsed = parser.parse(csv);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).isEqualTo(" Hello, ");
        assertThat(parsed.get(1)).isEqualTo("world");
        csv = " Hello, world";
        parsed = parser.parse(csv);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).isEqualTo("Hello");
        assertThat(parsed.get(1)).isEqualTo("world");
        csv = "\" Hello, \",\" world \"";
        parsed = parser.parse(csv);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).isEqualTo(" Hello, ");
        assertThat(parsed.get(1)).isEqualTo(" world ");
    }

    @BeforeEach
    protected void setUp() throws Exception
    {
        parser = new CsvParserImpl();
    }

}
