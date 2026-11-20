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
package org.dbunit.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AsciiDocParagraphsTest
{
    private final AsciiDocParagraphs splitter = new AsciiDocParagraphs();

    @Test
    void testSplit_blankLinesBetweenParagraphs_returnsEachWithItsFirstLine()
    {
        final List<AsciiDocParagraph> paragraphs = splitter.split("first\nsecond\n\n\nthird");

        assertThat(paragraphs)
                .as("A blank line must end a paragraph, and each must record its first line.")
                .containsExactly(new AsciiDocParagraph(1, "first\nsecond"),
                        new AsciiDocParagraph(5, "third"));
    }

    @Test
    void testSplit_listItems_startEachItsOwnParagraph()
    {
        final List<AsciiDocParagraph> paragraphs =
                splitter.split("* one\n  continued\n* two\n. three");

        assertThat(paragraphs)
                .as("Each list item must start a paragraph that its indented lines continue.")
                .containsExactly(new AsciiDocParagraph(1, "* one\n  continued"),
                        new AsciiDocParagraph(3, "* two"), new AsciiDocParagraph(4, ". three"));
    }

    @Test
    void testSplit_tableRowsWithAWrappedCell_startEachRowItsOwnParagraph()
    {
        final List<AsciiDocParagraph> paragraphs =
                splitter.split("|===\n|a |b\ncontinued\n|c |d\n|===");

        assertThat(paragraphs)
                .as("Each row must start a paragraph that the lines wrapping its last cell"
                        + " continue.")
                .containsExactly(new AsciiDocParagraph(1, "|==="),
                        new AsciiDocParagraph(2, "|a |b\ncontinued"),
                        new AsciiDocParagraph(4, "|c |d"), new AsciiDocParagraph(5, "|==="));
    }

    @Test
    void testSplit_headingBetweenTextLines_standsAlone()
    {
        final List<AsciiDocParagraph> paragraphs = splitter.split("text\n== Head\nmore");

        assertThat(paragraphs)
                .as("A heading must end the paragraph before it and stand as one of its own.")
                .containsExactly(new AsciiDocParagraph(1, "text"),
                        new AsciiDocParagraph(2, "== Head"), new AsciiDocParagraph(3, "more"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "----", "....", "++++", "////" })
    void testSplit_literalBlock_belongsToNoParagraph(final String delimiter)
    {
        final List<AsciiDocParagraph> paragraphs =
                splitter.split("before\n" + delimiter + "\nsome `code`\n" + delimiter + "\nafter");

        assertThat(paragraphs)
                .as("The lines between the fences of a block must be skipped, and the fences"
                        + " must end the paragraphs around them.")
                .containsExactly(new AsciiDocParagraph(1, "before"),
                        new AsciiDocParagraph(5, "after"));
    }

    @Test
    void testSplit_blockContainingAShorterFence_endsOnlyAtTheSameFence()
    {
        final List<AsciiDocParagraph> paragraphs =
                splitter.split("a\n------\nx\n----\ny\n------\nb");

        assertThat(paragraphs)
                .as("A shorter fence inside a block must not end it.")
                .containsExactly(new AsciiDocParagraph(1, "a"), new AsciiDocParagraph(7, "b"));
    }

    @Test
    void testSplit_commentLineInsideAParagraph_isDroppedWithoutSplittingIt()
    {
        final List<AsciiDocParagraph> paragraphs = splitter.split("text\n// a `comment\nmore");

        assertThat(paragraphs)
                .as("A single-line comment must vanish and leave the paragraph in one piece.")
                .containsExactly(new AsciiDocParagraph(1, "text\nmore"));
    }

    @Test
    void testSplit_windowsLineEndings_returnsTheParagraphsOfUnixOnes()
    {
        final List<AsciiDocParagraph> paragraphs = splitter.split("one\r\ntwo\r\n\r\nthree");

        assertThat(paragraphs)
                .as("Carriage returns must neither stay in the text nor shift the line numbers.")
                .containsExactly(new AsciiDocParagraph(1, "one\ntwo"),
                        new AsciiDocParagraph(4, "three"));
    }
}
