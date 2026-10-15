package org.dbunit.assertion.comparer.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

class CompiledPatternCacheTest
{
    @Test
    void testGet_sameRegexTwice_returnsTheSameCompiledPattern()
    {
        final CompiledPatternCache sut = new CompiledPatternCache(4);

        final Pattern first = sut.get("[0-9]+");
        final Pattern second = sut.get("[0-9]+");

        assertThat(second)
                .as("The expression a column holds in every row must be compiled once.")
                .isSameAs(first);
    }

    @Test
    void testGet_differentRegexes_returnsAPatternForEach()
    {
        final CompiledPatternCache sut = new CompiledPatternCache(4);

        final Pattern digits = sut.get("[0-9]+");
        final Pattern letters = sut.get("[a-z]+");

        assertThat(digits.pattern()).as("The first expression keeps its own pattern.")
                .isEqualTo("[0-9]+");
        assertThat(letters.pattern()).as("The second expression gets its own pattern.")
                .isEqualTo("[a-z]+");
    }

    @Test
    void testGet_moreRegexesThanTheLimit_forgetsTheLeastRecentlyUsedOne()
    {
        final CompiledPatternCache sut = new CompiledPatternCache(2);
        final Pattern first = sut.get("a");
        sut.get("b");
        sut.get("a");
        sut.get("c");

        final Pattern firstAgain = sut.get("a");
        final Pattern secondAgain = sut.get("b");

        assertThat(firstAgain)
                .as("The expression used most recently before the limit was reached stays held.")
                .isSameAs(first);
        assertThat(secondAgain.pattern())
                .as("The expression that went longest unused was dropped, and is compiled"
                        + " afresh when asked for again.")
                .isEqualTo("b");
    }

    @Test
    void testGet_invalidRegex_throwsPatternSyntaxException()
    {
        final CompiledPatternCache sut = new CompiledPatternCache(4);

        assertThatExceptionOfType(PatternSyntaxException.class)
                .as("An invalid expression is reported as java.util.regex does, every time it is"
                        + " asked for, rather than held.")
                .isThrownBy(() -> sut.get("["));
        assertThatExceptionOfType(PatternSyntaxException.class).isThrownBy(() -> sut.get("["));
    }

    @Test
    void testGet_askedForFromSeveralThreads_everyThreadGetsAWorkingPattern() throws Exception
    {
        final CompiledPatternCache sut = new CompiledPatternCache(2);
        final ExecutorService executor = Executors.newFixedThreadPool(4);
        try
        {
            final List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 200; i++)
            {
                final int length = i % 5 + 1;
                final String regex = "[a-z]{" + length + "}";
                final String input = "abcde".substring(0, length);
                final Callable<Boolean> task = () -> sut.get(regex).matcher(input).matches();
                results.add(executor.submit(task));
            }

            for (final Future<Boolean> result : results)
            {
                assertThat(result.get())
                        .as("A pattern the cache hands out must match as the expression says,"
                                + " whichever thread asked for it.")
                        .isTrue();
            }
        } finally
        {
            executor.shutdownNow();
        }
    }
}
