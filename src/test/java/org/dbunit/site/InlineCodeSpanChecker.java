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

/**
 * Finds the places in an AsciiDoc text where Asciidoctor cannot render an inline code span, so the
 * page shows a stray backtick and styles the wrong text as code.
 *
 * <p>Asciidoctor closes a span at a backtick only when no letter, digit, underscore, apostrophe or
 * double quote follows it, and opens one only when no letter, digit, underscore, colon, semicolon
 * or closing brace precedes it. A possessive or a plural right after the closing backtick, as in
 * {@code `Foo`'s} or {@code `Foo`s}, therefore never closes, and the opening backtick then pairs
 * with a later one. A backtick left without its partner is shown as it is. Each rule was verified
 * against the rendered site.
 *
 * <p>To keep the wording and still render, write {@code {apos}} after the closing backtick of a
 * possessive, or double the backticks of a plural, as in {@code ``Foo``s}. Both render.
 *
 * <p>Backticks are paired in order within a paragraph, see {@link AsciiDocParagraphs}. A pair that
 * starts right after a quote character is the curved-quote syntax, not a code span, and is not
 * checked. A backtick escaped with a backslash and a run of three or more are not counted, nor is a
 * run of two next to a quote character, where a code span ends or starts inside curved quotes.
 * Other runs of exactly two, the unconstrained form, must come in pairs.
 */
class InlineCodeSpanChecker
{
    private static final int EXCERPT_BEFORE = 30;
    private static final int EXCERPT_AFTER = 20;
    private static final int EXCERPT_OF_PARAGRAPH = 60;

    private final AsciiDocParagraphs paragraphs;

    /**
     * Creates a checker.
     *
     * @param paragraphs Splits a source into the paragraphs to check.
     */
    InlineCodeSpanChecker(final AsciiDocParagraphs paragraphs)
    {
        this.paragraphs = paragraphs;
    }

    /**
     * Checks one AsciiDoc source.
     *
     * @param source The name of the source, such as its path, to report its problems under.
     * @param asciiDoc The text of the source.
     * @return The places where an inline code span cannot render, in source order.
     */
    List<InlineCodeProblem> check(final String source, final String asciiDoc)
    {
        final List<InlineCodeProblem> problems = new ArrayList<>();
        for (final AsciiDocParagraph paragraph : paragraphs.split(asciiDoc))
        {
            check(source, paragraph, problems);
        }
        return problems;
    }

    private void check(final String source, final AsciiDocParagraph paragraph,
            final List<InlineCodeProblem> problems)
    {
        final String text = paragraph.getText();
        final List<Integer> singles = new ArrayList<>();
        int doubles = 0;
        for (final BacktickRun run : backtickRuns(text))
        {
            if (run.length == 1)
            {
                singles.add(run.index);
            } else if (run.length == 2)
            {
                doubles++;
            }
        }
        if (singles.size() % 2 == 1)
        {
            final String what = "odd number of single backticks (" + singles.size()
                    + "), so one is shown literally";
            problems.add(paragraphProblem(source, paragraph, what));
        } else
        {
            checkPairs(source, paragraph, singles, problems);
        }
        if (doubles % 2 == 1)
        {
            final String what = "odd number of double backticks (" + doubles + ")";
            problems.add(paragraphProblem(source, paragraph, what));
        }
    }

    private void checkPairs(final String source, final AsciiDocParagraph paragraph,
            final List<Integer> singles, final List<InlineCodeProblem> problems)
    {
        final String text = paragraph.getText();
        for (int pair = 0; pair < singles.size(); pair += 2)
        {
            final int open = singles.get(pair);
            final int close = singles.get(pair + 1);
            if (!isCurvedQuote(text, open))
            {
                checkOpening(source, paragraph, open, problems);
                checkClosing(source, paragraph, close, problems);
            }
        }
    }

    private void checkOpening(final String source, final AsciiDocParagraph paragraph,
            final int open, final List<InlineCodeProblem> problems)
    {
        final String text = paragraph.getText();
        if (open > 0)
        {
            final char before = text.charAt(open - 1);
            if (Character.isLetterOrDigit(before) || before == '_' || before == ':'
                    || before == ';' || before == '}')
            {
                final String what = "opening backtick directly after " + describe(before);
                problems.add(problemAt(source, paragraph, open, what));
            }
        }
    }

    private void checkClosing(final String source, final AsciiDocParagraph paragraph,
            final int close, final List<InlineCodeProblem> problems)
    {
        final String text = paragraph.getText();
        if (close + 1 < text.length())
        {
            final char after = text.charAt(close + 1);
            if (Character.isLetterOrDigit(after) || after == '_' || after == '\''
                    || after == '"')
            {
                final String what = "closing backtick directly followed by " + describe(after);
                problems.add(problemAt(source, paragraph, close, what));
            }
        }
    }

    private boolean isCurvedQuote(final String text, final int open)
    {
        return open > 0 && isQuote(text.charAt(open - 1));
    }

    private boolean touchesQuote(final String text, final int start, final int end)
    {
        final boolean quoteBefore = start > 0 && isQuote(text.charAt(start - 1));
        final boolean quoteAfter = end < text.length() && isQuote(text.charAt(end));
        return quoteBefore || quoteAfter;
    }

    private boolean isQuote(final char character)
    {
        return character == '"' || character == '\'';
    }

    private List<BacktickRun> backtickRuns(final String text)
    {
        final List<BacktickRun> runs = new ArrayList<>();
        int index = 0;
        while (index < text.length())
        {
            if (text.charAt(index) == '`')
            {
                int end = index;
                while (end < text.length() && text.charAt(end) == '`')
                {
                    end++;
                }
                final boolean escaped = index > 0 && text.charAt(index - 1) == '\\';
                final boolean curvedQuoteEdge = end - index == 2 && touchesQuote(text, index, end);
                if (!escaped && !curvedQuoteEdge)
                {
                    runs.add(new BacktickRun(index, end - index));
                }
                index = end;
            } else
            {
                index++;
            }
        }
        return runs;
    }

    private String describe(final char character)
    {
        if (character == '\'')
        {
            return "an apostrophe";
        }
        if (character == '"')
        {
            return "a double quote";
        }
        if (character == '_')
        {
            return "an underscore";
        }
        if (character == ':')
        {
            return "a colon";
        }
        if (character == ';')
        {
            return "a semicolon";
        }
        if (character == '}')
        {
            return "a closing brace";
        }
        if (Character.isDigit(character))
        {
            return "the digit '" + character + "'";
        }
        return "the letter '" + character + "'";
    }

    private InlineCodeProblem problemAt(final String source, final AsciiDocParagraph paragraph,
            final int index, final String what)
    {
        final String text = paragraph.getText();
        final int from = Math.max(0, index - EXCERPT_BEFORE);
        final int to = Math.min(text.length(), index + EXCERPT_AFTER);
        final String ellipsisBefore = from == 0 ? "" : "...";
        final String ellipsisAfter = to == text.length() ? "" : "...";
        final String excerpt = ellipsisBefore + text.substring(from, to).replace('\n', ' ')
                + ellipsisAfter;
        return new InlineCodeProblem(source, lineOf(paragraph, index),
                what + ": \"" + excerpt + "\"");
    }

    private InlineCodeProblem paragraphProblem(final String source,
            final AsciiDocParagraph paragraph, final String what)
    {
        final String text = paragraph.getText();
        final int to = Math.min(text.length(), EXCERPT_OF_PARAGRAPH);
        final String ellipsis = to == text.length() ? "" : "...";
        final String excerpt = text.substring(0, to).replace('\n', ' ') + ellipsis;
        return new InlineCodeProblem(source, paragraph.getFirstLine(),
                what + ": \"" + excerpt + "\"");
    }

    private int lineOf(final AsciiDocParagraph paragraph, final int index)
    {
        final String text = paragraph.getText();
        int line = paragraph.getFirstLine();
        for (int position = 0; position < index; position++)
        {
            if (text.charAt(position) == '\n')
            {
                line++;
            }
        }
        return line;
    }

    /** A run of consecutive backticks in a paragraph. */
    private static class BacktickRun
    {
        private final int index;
        private final int length;

        BacktickRun(final int index, final int length)
        {
            this.index = index;
            this.length = length;
        }
    }
}
