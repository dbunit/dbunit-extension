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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits an AsciiDoc text into the paragraphs that inline formatting, such as a code span, applies
 * within.
 *
 * <p>A paragraph ends at a blank line, a heading, a list item, a table row or a literal block. A
 * list item or a table row starts the next paragraph, and the lines that follow it up to the next
 * such boundary continue it, as a cell or an item may wrap. The lines of a literal block (a
 * listing, literal, passthrough or comment block, each fenced by a line of four or more of the same
 * marker) and single-line comments belong to no paragraph, as they are shown verbatim or not at
 * all.
 */
class AsciiDocParagraphs
{
    private static final Pattern BLOCK_DELIMITER = Pattern.compile("(-{4,}|\\.{4,}|\\+{4,}|/{4,})");
    private static final Pattern HEADING = Pattern.compile("=+\\s.*");
    private static final Pattern LIST_ITEM = Pattern.compile("([*.-]+|\\d+\\.)\\s.*");

    /**
     * Splits the text.
     *
     * @param asciiDoc The text of an AsciiDoc source, with either kind of line ending.
     * @return The paragraphs, in source order.
     */
    List<AsciiDocParagraph> split(final String asciiDoc)
    {
        final Scan scan = new Scan();
        final String[] lines = asciiDoc.split("\r?\n", -1);
        for (int index = 0; index < lines.length; index++)
        {
            scan.accept(index + 1, lines[index]);
        }
        return scan.finish();
    }

    /** Reads the lines of one text in order, collecting the paragraphs they form. */
    private static class Scan
    {
        private final List<AsciiDocParagraph> paragraphs = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private int firstLine;
        private String openBlock;

        void accept(final int lineNumber, final String line)
        {
            final String trimmed = line.trim();
            if (openBlock == null)
            {
                acceptOutsideBlock(lineNumber, line, trimmed);
            } else if (trimmed.equals(openBlock))
            {
                openBlock = null;
            }
        }

        List<AsciiDocParagraph> finish()
        {
            endParagraph();
            return paragraphs;
        }

        private void acceptOutsideBlock(final int lineNumber, final String line,
                final String trimmed)
        {
            if (BLOCK_DELIMITER.matcher(trimmed).matches())
            {
                endParagraph();
                openBlock = trimmed;
            } else if (trimmed.isEmpty())
            {
                endParagraph();
            } else if (HEADING.matcher(trimmed).matches())
            {
                endParagraph();
                add(lineNumber, line);
                endParagraph();
            } else if (!trimmed.startsWith("//"))
            {
                if (startsParagraph(trimmed))
                {
                    endParagraph();
                }
                add(lineNumber, line);
            }
        }

        private boolean startsParagraph(final String trimmed)
        {
            return trimmed.startsWith("|") || LIST_ITEM.matcher(trimmed).matches();
        }

        private void add(final int lineNumber, final String line)
        {
            if (text.length() == 0)
            {
                firstLine = lineNumber;
            } else
            {
                text.append('\n');
            }
            text.append(line);
        }

        private void endParagraph()
        {
            if (text.length() > 0)
            {
                paragraphs.add(new AsciiDocParagraph(firstLine, text.toString()));
                text.setLength(0);
            }
        }
    }
}
