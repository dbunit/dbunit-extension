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

package org.dbunit.dataset.common.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandlersTest
{

    Pipeline pipeline;

    @Test
    void testEmptyFields()
            throws IllegalInputCharacterException, PipelineException
    {
        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(TransparentHandler.IGNORE());

        final String words = ",, ,";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(4);

        for (int i = 0; i < pipeline.getProducts().size(); i++)
        {
            assertThat(pipeline.getProducts().get(i)).hasToString("");
        }
    }

    @Test
    void testUnquotedFieldsParser()
            throws IllegalInputCharacterException, PipelineException
    {

        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(IsAlnumHandler.QUOTE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(TransparentHandler.IGNORE());

        final String words = "Today: Hello , World!";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(2);
        assertThat(pipeline.getProducts().get(0)).isEqualTo("Today: Hello ");
        assertThat(pipeline.getProducts().get(1)).isEqualTo("World!");
    }

    @Test
    void testQuotedFieldWithEscapedCharacterAssembler()
            throws PipelineException, IllegalInputCharacterException
    {
        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(IsAlnumHandler.ACCEPT());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(QuoteHandler.QUOTE());

        final String words = " \"Hello, \\\"World!\" ";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(1);
        assertThat(pipeline.getProducts().get(0))
                .hasToString("Hello, \"World!");
    }

    @Test
    void testUnquotedFieldWithEscapedCharacterAssembler()
            throws PipelineException, IllegalInputCharacterException
    {
        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(EscapeHandler.ACCEPT());
        pipeline.putFront(IsAlnumHandler.QUOTE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(TransparentHandler.IGNORE());

        final String words = "Hello \\\"World!";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(1);
        assertThat(pipeline.getProducts().get(0).toString())
                .hasToString("Hello \\\"World!");
    }

    @Test
    void testEscapedFieldAssembler()
            throws PipelineException, IllegalInputCharacterException
    {
        final String words = "\"He\"llo, \"World, !\", \\\"St. James O\"Connor";

        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(EscapeHandler.ACCEPT());
        pipeline.putFront(IsAlnumHandler.QUOTE());
        pipeline.putFront(QuoteHandler.QUOTE());
        pipeline.putFront(EscapeHandler.ESCAPE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(TransparentHandler.IGNORE());

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(3);
        assertThat(pipeline.getProducts().get(0)).isEqualTo("Hello");
        assertThat(pipeline.getProducts().get(1)).isEqualTo("World, !");
        assertThat(pipeline.getProducts().get(2))
                .isEqualTo("\"St. James O\"Connor");
    }

    private void dump(final List<Object> products)
    {
        final Iterator<Object> it = products.iterator();
        int i = 0;
        while (it.hasNext())
        {
            System.out.println(i++ + ": " + it.next());
        }
    }

    private void feed(final Pipeline pipeline, final String words)
            throws PipelineException, IllegalInputCharacterException
    {
        for (int i = 0; i < words.length(); i++)
        {
            pipeline.handle(words.toCharArray()[i]);
        }
        pipeline.thePieceIsDone();
    }

    @Test
    void testQuotedFieldAssembler()
            throws IllegalInputCharacterException, PipelineException
    {
        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(IsAlnumHandler.ACCEPT());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(QuoteHandler.QUOTE());

        final String words = " \"Hello, World!\" ";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(1);
        assertThat(pipeline.getProducts().get(0)).hasToString("Hello, World!");
    }

    @Test
    void testQuotedFieldsParser()
            throws IllegalInputCharacterException, PipelineException
    {
        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(IsAlnumHandler.QUOTE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(QuoteHandler.QUOTE());
        pipeline.putFront(TransparentHandler.IGNORE());

        final String words =
                "\"Hello\", \"oh my\", \"ehm. oh yeah. World!\", \" craa azy \"";

        feed(pipeline, words);

        assertThat(pipeline.getProducts()).hasSize(4);

        final List<Object> expected = new ArrayList<>();
        expected.add("Hello");
        expected.add("oh my");
        expected.add("ehm. oh yeah. World!");
        expected.add(" craa azy ");

        final List<Object> got = new ArrayList<>();

        for (int i = 0; i < pipeline.getProducts().size(); i++)
        {
            got.add(pipeline.getProducts().get(i).toString());
        }

        assertThat(got).isEqualTo(expected);

        assertThat(pipeline.getProducts().get(0)).hasToString("Hello");
        assertThat(pipeline.getProducts().get(1)).hasToString("oh my");
        assertThat(pipeline.getProducts().get(2))
                .hasToString("ehm. oh yeah. World!");
        assertThat(pipeline.getProducts().get(3)).hasToString(" craa azy ");

    }

    private void acceptHelper(final String toAccept, final Handler component)
            throws IllegalInputCharacterException, PipelineException
    {
        for (int i = 0; i < toAccept.length(); i++)
        {
            final char c = toAccept.charAt(i);
            assertThat(component.canHandle(c)).as(c + " should be accepted")
                    .isTrue();
            // Handle
            component.handle(c);
        }

    }

    /**
     * Test the handling of a sequence of empty, unquoted and quoted fields
     * 
     * @throws IllegalInputCharacterException
     * @throws PipelineException
     */
    public void testEmptyQuotedAndUnquotedFieldsParser()
            throws IllegalInputCharacterException, PipelineException
    {

        final String words =
                " , \\\\John \"Fox , \"St. Moritz, 2\" , \\\\, \\\"Steve Wolf, \" \\\"Night & Day\\\", \\\"2nd\\\" edition \", , Again Here, \"and there, of\"";

        pipeline.putFront(SeparatorHandler.ENDPIECE());
        pipeline.putFront(EscapeHandler.ACCEPT());
        pipeline.putFront(IsAlnumHandler.QUOTE());
        pipeline.putFront(QuoteHandler.QUOTE());
        pipeline.putFront(EscapeHandler.ESCAPE());
        pipeline.putFront(WhitespacesHandler.IGNORE());
        pipeline.putFront(TransparentHandler.IGNORE());

        feed(pipeline, words);

        // dump(pipeline.getProducts());

        assertThat(pipeline.getProducts()).hasSize(9);
        assertThat(pipeline.getProducts().get(0)).hasToString("");
        assertThat(pipeline.getProducts().get(1)).hasToString("\\John \"Fox ");
        assertThat(pipeline.getProducts().get(2))
                .hasToString("St. Moritz).isEqualTo( 2");
        assertThat(pipeline.getProducts().get(3)).hasToString("\\");

        assertThat(pipeline.getProducts().get(4)).hasToString("\"Steve Wolf");
        assertThat(pipeline.getProducts().get(5))
                .hasToString(" \"Night & Day\", \"2nd\" edition ");
        assertThat(pipeline.getProducts().get(6)).hasToString("");
        assertThat(pipeline.getProducts().get(7)).hasToString("Again Here");
        assertThat(pipeline.getProducts().get(8))
                .hasToString("and there).isEqualTo(of");
    }

    private void doNotAcceptHelper(final String toAccept,
            final Handler component)
            throws IllegalInputCharacterException, PipelineException
    {
        for (int i = 0; i < toAccept.length(); i++)
        {
            final char c = toAccept.charAt(i);
            assertThat(component.canHandle(c)).as(c + " should not be accepted")
                    .isFalse();
        }
    }

    @Test
    void testEscapeHandler()
            throws PipelineException, IllegalInputCharacterException
    {
        final String accepted = "\\\"";

        final EscapeHandler escapeHandler =
                (EscapeHandler) EscapeHandler.ESCAPE();
        pipeline.putFront(escapeHandler);
        acceptHelper(accepted, pipeline);
        assertThat(pipeline.getCurrentProduct()).hasToString("\"");
    }

    @Test
    void testWhitespaceHandler() throws Exception
    {

        final String accepted = " \t";

        final PipelineComponent acceptHandler = WhitespacesHandler.ACCEPT();
        pipeline.putFront(acceptHandler);
        acceptHelper(accepted, acceptHandler);
        acceptHelper(accepted, WhitespacesHandler.IGNORE());

        assertThat(pipeline.getCurrentProduct()).hasToString(accepted);

    }

    @Test
    void testUnquotedHandler()
            throws IllegalInputCharacterException, PipelineException
    {
        final String accepted =
                "_1234567890abcdefghilmnopqrstuvzxywjABCDEFGHILMNOPQRSTUVZXYWJ()/&%$|-_.:;+*<>";
        final String notAccepted = " \t\\";

        final PipelineComponent acceptHandler = IsAlnumHandler.ACCEPT();
        pipeline.putFront(acceptHandler);
        acceptHelper(accepted, acceptHandler);

        final PipelineComponent ignoreHandler = IsAlnumHandler.IGNORE();
        pipeline.putFront(ignoreHandler);
        acceptHelper(accepted, ignoreHandler);

        doNotAcceptHelper(notAccepted, acceptHandler);
        doNotAcceptHelper(notAccepted, ignoreHandler);

        assertThat(pipeline.getCurrentProduct()).hasToString(accepted);
    }

    @BeforeEach
    protected void setUp() throws Exception
    {
        pipeline = new Pipeline();
    }

}
