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
 * One place in an AsciiDoc source where Asciidoctor cannot render an inline code span, so the page
 * shows a stray backtick there.
 */
class InlineCodeProblem
{
    private final String source;
    private final int line;
    private final String description;

    /**
     * Creates a problem.
     *
     * @param source The name of the AsciiDoc source, such as its path.
     * @param line The 1-based number of the line the problem is on.
     * @param description What is wrong there, with the text around it.
     */
    InlineCodeProblem(final String source, final int line, final String description)
    {
        this.source = source;
        this.line = line;
        this.description = description;
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
        final InlineCodeProblem that = (InlineCodeProblem) other;
        return line == that.line && source.equals(that.source)
                && description.equals(that.description);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(source, line, description);
    }

    @Override
    public String toString()
    {
        return source + ":" + line + ": " + description;
    }
}
