package org.dbunit.assertion.comparer.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Holds the compiled form of the regular expressions a {@link RegularExpressionValueComparer}
 * has been given, so the expression a column holds in every row is compiled once rather than
 * for every cell compared. Bounded: past its limit the expression used least recently is
 * dropped, and compiled again if asked for later.
 *
 * <p>
 * Safe to share between threads, as the comparer is when it is the shared
 * {@link ValueComparers#regularExpressionValueComparer} or an instance a test class's
 * annotations name. Two threads asking for an expression neither holds both compile it, which
 * is harmless: a {@link Pattern} is immutable.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class CompiledPatternCache
{
    private final Map<String, Pattern> patternsByRegex;

    /**
     * Creates an empty cache.
     *
     * @param maxSize
     *            The most expressions held at once.
     */
    CompiledPatternCache(final int maxSize)
    {
        patternsByRegex = Collections.synchronizedMap(
                new LinkedHashMap<String, Pattern>(16, 0.75f, true)
                {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected boolean removeEldestEntry(
                            final Map.Entry<String, Pattern> eldest)
                    {
                        return size() > maxSize;
                    }
                });
    }

    /**
     * Returns the compiled form of the regular expression, compiling it first if this cache
     * does not hold it. An invalid expression is not held.
     *
     * @param regex
     *            The regular expression.
     * @return The compiled expression.
     * @throws PatternSyntaxException
     *             If the expression is not valid.
     */
    Pattern get(final String regex)
    {
        final Pattern held = patternsByRegex.get(regex);
        if (held != null)
        {
            return held;
        }

        final Pattern compiled = Pattern.compile(regex);
        patternsByRegex.put(regex, compiled);
        return compiled;
    }
}
