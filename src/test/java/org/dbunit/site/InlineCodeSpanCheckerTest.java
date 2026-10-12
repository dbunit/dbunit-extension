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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class InlineCodeSpanCheckerTest
{
    private static final String PAGE = "page.adoc";

    private final InlineCodeSpanChecker checker =
            new InlineCodeSpanChecker(new AsciiDocParagraphs());

    @ParameterizedTest
    @MethodSource("charactersAfterAClosingBacktick")
    void testCheck_characterDirectlyAfterClosingBacktick_reportsTheSpan(final String text,
            final String character)
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, text);

        assertThat(problems)
                .as("A closing backtick directly followed by " + character
                        + " must be reported, as Asciidoctor does not close the span there.")
                .containsExactly(
                        problem(1, "closing backtick directly followed by " + character, text));
    }

    static Stream<Arguments> charactersAfterAClosingBacktick()
    {
        return Stream.of(Arguments.of("A `foo`'s end.", "an apostrophe"),
                Arguments.of("A `foo`s end.", "the letter 's'"),
                Arguments.of("A `foo`2 end.", "the digit '2'"),
                Arguments.of("A `foo`_x end.", "an underscore"),
                Arguments.of("A `foo`\" end.", "a double quote"),
                Arguments.of("A `foo`é end.", "the letter 'é'"));
    }

    @ParameterizedTest
    @MethodSource("charactersBeforeAnOpeningBacktick")
    void testCheck_characterDirectlyBeforeOpeningBacktick_reportsTheSpan(final String text,
            final String character)
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, text);

        assertThat(problems)
                .as("An opening backtick directly after " + character
                        + " must be reported, as Asciidoctor does not open a span there.")
                .containsExactly(problem(1, "opening backtick directly after " + character, text));
    }

    static Stream<Arguments> charactersBeforeAnOpeningBacktick()
    {
        return Stream.of(Arguments.of("x`foo` end.", "the letter 'x'"),
                Arguments.of("7`foo` end.", "the digit '7'"),
                Arguments.of("_`foo` end.", "an underscore"),
                Arguments.of("Q:`foo` end.", "a colon"),
                Arguments.of("a;`foo` end.", "a semicolon"),
                Arguments.of("a}`foo` end.", "a closing brace"));
    }

    @ParameterizedTest
    @MethodSource("textThatRenders")
    void testCheck_textThatRenders_reportsNothing(final String text)
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, text);

        assertThat(problems)
                .as("Code spans Asciidoctor renders must not be reported: " + text)
                .isEmpty();
    }

    static Stream<String> textThatRenders()
    {
        return Stream.of("", "no code at all.", "A `foo` and `bar`.",
                "(`foo`), `bar`; `baz`: `qux`!", "`foo`-bar and `foo`/`bar`[x].",
                "`'true'`/`'false'` values", "The `foo`\ncontinues here.",
                "A `span that wraps\nacross lines` stays one span.",
                "``Foo``s are plural and `Foo`{apos}s is possessive.",
                "\"`curved`\" and '`curved`' quotes", "\"`see `foo``\" ends in a span.",
                "a \\`literal\\` backtick");
    }

    @ParameterizedTest
    @ValueSource(strings = { "----", "....", "++++", "////" })
    void testCheck_problemInsideALiteralBlock_reportsNothing(final String delimiter)
    {
        final List<InlineCodeProblem> problems =
                checker.check(PAGE, delimiter + "\nthe `foo`'s value\n" + delimiter + "\n");

        assertThat(problems)
                .as("What a block shows verbatim is not rendered as AsciiDoc and must not be"
                        + " checked.")
                .isEmpty();
    }

    @Test
    void testCheck_problemOnALaterLine_reportsThatLine()
    {
        final List<InlineCodeProblem> problems =
                checker.check(PAGE, "first line\n\nsecond with `foo`'s");

        assertThat(problems)
                .as("The problem must be reported on the line of its backtick.")
                .containsExactly(problem(3, "closing backtick directly followed by an apostrophe",
                        "second with `foo`'s"));
    }

    @Test
    void testCheck_possessiveAfterASpanThatWraps_reportsTheLineOfTheClosingBacktick()
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, "a `foo\nbar`'s b");

        assertThat(problems)
                .as("The problem must be reported on the line of the closing backtick, and"
                        + " the excerpt must show the wrapped lines as one.")
                .containsExactly(problem(2, "closing backtick directly followed by an apostrophe",
                        "a `foo bar`'s b"));
    }

    @Test
    void testCheck_twoProblemsInOneParagraph_reportsBothInOrder()
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, "`a`'s and `b`s");

        assertThat(problems)
                .as("Every problem of a paragraph must be reported, in source order.")
                .containsExactly(
                        problem(1, "closing backtick directly followed by an apostrophe",
                                "`a`'s and `b`s"),
                        problem(1, "closing backtick directly followed by the letter 's'",
                                "`a`'s and `b`s"));
    }

    @Test
    void testCheck_oddNumberOfSingleBackticks_reportsTheParagraph()
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, "a `foo` b `c");

        assertThat(problems)
                .as("A backtick without a partner is shown literally and must be reported.")
                .containsExactly(problem(1,
                        "odd number of single backticks (3), so one is shown literally",
                        "a `foo` b `c"));
    }

    @Test
    void testCheck_oneBacktickInEachOfTwoTableRows_isNotPairedAcrossTheRows()
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, "|a `x |b\n|c y` d");

        assertThat(problems)
                .as("A code span cannot continue into the next table row, so each row must be"
                        + " judged alone.")
                .containsExactly(
                        problem(1, "odd number of single backticks (1), so one is shown literally",
                                "|a `x |b"),
                        problem(2, "odd number of single backticks (1), so one is shown literally",
                                "|c y` d"));
    }

    @Test
    void testCheck_touchingSpans_reportsTheUnpairedDoubleBackticks()
    {
        final List<InlineCodeProblem> problems = checker.check(PAGE, "`foo``bar`");

        assertThat(problems)
                .as("Two spans with no space between them leave one run of double backticks"
                        + " without a partner, which Asciidoctor shows literally.")
                .containsExactly(problem(1, "odd number of double backticks (1)", "`foo``bar`"));
    }

    @Test
    void testCheck_windowsLineEndings_reportsTheLineWithoutCarriageReturns()
    {
        final List<InlineCodeProblem> problems =
                checker.check(PAGE, "first\r\n\r\nsecond `foo`'s");

        assertThat(problems)
                .as("A carriage return must neither shift the line number nor reach the"
                        + " excerpt.")
                .containsExactly(problem(3, "closing backtick directly followed by an apostrophe",
                        "second `foo`'s"));
    }

    @Test
    void testCheck_problemInALongLine_excerptsTheTextAroundIt()
    {
        final String before = String.join("", Collections.nCopies(50, "a"));
        final String after = String.join("", Collections.nCopies(50, "z"));

        final List<InlineCodeProblem> problems =
                checker.check(PAGE, before + " `foo`'s " + after);

        final String expectedBefore = String.join("", Collections.nCopies(25, "a"));
        final String expectedAfter = String.join("", Collections.nCopies(16, "z"));
        assertThat(problems)
                .as("The excerpt must show 30 characters before the closing backtick and 20"
                        + " from it on, with an ellipsis on each cut side.")
                .containsExactly(problem(1, "closing backtick directly followed by an apostrophe",
                        "..." + expectedBefore + " `foo`'s " + expectedAfter + "..."));
    }

    private InlineCodeProblem problem(final int line, final String what, final String excerpt)
    {
        return new InlineCodeProblem(PAGE, line, what + ": \"" + excerpt + "\"");
    }
}
