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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Keeps the AsciiDoc sources of the project's pages free of inline code that Asciidoctor cannot
 * render, see {@link InlineCodeSpanChecker} for the rules.
 *
 * <p>It runs with the unit tests, so a page that brings the problem back fails the build of its
 * branch, while the site itself is only built and published after a merge.
 */
class SiteAsciiDocTest
{
    private static final Path README = Paths.get("README.adoc");
    private static final Path PAGES = Paths.get("src", "site", "asciidoc");

    private static final String FIX_HINT = "Asciidoctor renders these inline code spans with a"
            + " stray backtick. Reword so that no letter, digit, underscore, apostrophe or double"
            + " quote directly follows a closing backtick, and none of those, a colon, a semicolon"
            + " or a closing brace directly precedes an opening one, for example 'the X of `Y`'"
            + " instead of '`Y`'s X'. To keep the wording, write {apos} after the closing backtick"
            + " of a possessive, or double the backticks of a plural: ``Y``s.";

    private final InlineCodeSpanChecker checker =
            new InlineCodeSpanChecker(new AsciiDocParagraphs());

    @Test
    void testAsciiDocSources_inlineCodeSpans_allRender() throws IOException
    {
        assertThat(README)
                .as("README.adoc must be found; run the tests from the project base directory.")
                .exists();
        assertThat(PAGES)
                .as("The pages under src/site/asciidoc must be found; run the tests from the"
                        + " project base directory.")
                .isDirectory();

        final List<InlineCodeProblem> problems = new ArrayList<>();
        for (final Path source : asciiDocSources())
        {
            final String name = source.toString().replace('\\', '/');
            problems.addAll(checker.check(name, read(source)));
        }

        assertThat(problems).as(FIX_HINT).isEmpty();
    }

    private List<Path> asciiDocSources() throws IOException
    {
        final List<Path> sources = new ArrayList<>();
        sources.add(README);
        try (Stream<Path> pages = Files.walk(PAGES))
        {
            sources.addAll(pages.filter(page -> page.toString().endsWith(".adoc")).sorted()
                    .collect(Collectors.toList()));
        }
        return sources;
    }

    private String read(final Path source) throws IOException
    {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }
}
