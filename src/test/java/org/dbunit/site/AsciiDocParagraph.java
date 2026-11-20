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

import java.util.Objects;

/**
 * A run of AsciiDoc lines that inline formatting, such as a code span, applies within, with the
 * number of its first line in the source.
 */
class AsciiDocParagraph
{
    private final int firstLine;
    private final String text;

    /**
     * Creates a paragraph.
     *
     * @param firstLine The 1-based number of the paragraph's first line in its source.
     * @param text The paragraph's lines, joined with line feeds.
     */
    AsciiDocParagraph(final int firstLine, final String text)
    {
        this.firstLine = firstLine;
        this.text = text;
    }

    int getFirstLine()
    {
        return firstLine;
    }

    String getText()
    {
        return text;
    }

    @Override
    public boolean equals(final Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (other == null || getClass() != other.getClass())
        {
            return false;
        }
        final AsciiDocParagraph that = (AsciiDocParagraph) other;
        return firstLine == that.firstLine && text.equals(that.text);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(firstLine, text);
    }

    @Override
    public String toString()
    {
        return "line " + firstLine + ": " + text;
    }
}
