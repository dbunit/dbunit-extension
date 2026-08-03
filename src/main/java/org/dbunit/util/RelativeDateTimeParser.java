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

package org.dbunit.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * A parser for relative date time string.<br>
 * The basic format is <code>[now{diff...}{time}]</code>.<br>
 * 'diff' consists of two parts 1) a number with a leading plus or minus sign
 * and 2) a character represents temporal unit. See the table below for the
 * supported units. There can be multiple 'diff's and they can be specified in
 * any order.<br>
 * 'time' is a string that can be parsed by
 * <code>LocalTime#parse()</code>. If specified, it is used instead of the current time.<br>
 * Both 'diff' and 'time' are optional.<br>
 * Whitespaces are allowed before and after each 'diff'.
 * </p>
 * <h2>Unit</h2>
 * <ul>
 * <li>y : years</li>
 * <li>M : months</li>
 * <li>d : days</li>
 * <li>h : hours</li>
 * <li>m : minutes</li>
 * <li>s : seconds</li>
 * </ul>
 * <p>
 * Here are some examples.
 * </p>
 * <ul>
 * <li><code>[now]</code> : current date time.</li>
 * <li><code>[now-1d]</code> : the same time yesterday.</li>
 * <li><code>[now+1y+1M-2h]</code> : a year and a month from today, two hours
 * earlier.</li>
 * <li><code>[now+1d 10:00]</code> : 10 o'clock tomorrow.</li>
 * </ul>
 */
public class RelativeDateTimeParser
{
    private static final Pattern inputPattern = Pattern.compile(
            "^\\[[nN][oO][wW]\\s*(([-+][0-9]+[yMdhms]\\s*)*)([0-9:]*)?\\]$");
    private static final int GROUP_DIFFS = 1;
    private static final int GROUP_TIME = 3;
    private static final Pattern diffPattern =
            Pattern.compile("([+-][0-9]+[yMdhms])");

    private Clock clock;
    private LocalDateTime now;

    /**
     * Default constructor.
     */
    public RelativeDateTimeParser()
    {
        // Use fixed clock to provide consistent 'now' values.
        this(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
    }

    /**
     * Constructs a parser resolving <code>[now]</code> relative to the given clock.
     *
     * @param clock the clock used to resolve <code>[now]</code>.
     */
    public RelativeDateTimeParser(Clock clock)
    {
        this.clock = clock;
        cacheLocalDateTime(clock);
    }

    /**
     * Parses a relative datetime expression such as <code>[now-1d]</code>.
     *
     * @param input the relative datetime expression to parse.
     * @return the resolved date and time.
     */
    public LocalDateTime parse(String input)
    {
        if (input == null || input.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Relative datetime input must not be null or empty.");
        }

        Matcher matcher = inputPattern.matcher(input);
        if (!matcher.matches())
        {
            throw new IllegalArgumentException("'" + input
                    + "' does not match the expected pattern [now{diff}{time}]. "
                    + "Please see the data types documentation for the details. "
                    + "http://dbunit.sourceforge.net/datatypes.html#relativedatetime");
        }

        LocalDateTime datetime = initLocalDateTime(matcher);

        String diffStr = matcher.group(GROUP_DIFFS);
        if (diffStr.isEmpty())
        {
            return datetime;
        }

        Matcher diffMatcher = diffPattern.matcher(diffStr);
        while (diffMatcher.find())
        {
            String diff = diffMatcher.group();
            int amountLength = diff.length() - 1;
            TemporalUnit unit = resolveUnit(diff.charAt(amountLength));
            long amount = Long.parseLong(diff.substring(0, amountLength));
            datetime = datetime.plus(amount, unit);
        }
        return datetime;
    }

    /**
     * Returns the clock used to resolve <code>[now]</code>.
     *
     * @return the clock used to resolve <code>[now]</code>.
     */
    public Clock getClock()
    {
        return clock;
    }

    /**
     * Sets the clock used to resolve <code>[now]</code>.
     *
     * @param clock the clock used to resolve <code>[now]</code>.
     */
    public void setClock(Clock clock)
    {
        this.clock = clock;
        cacheLocalDateTime(clock);
    }

    private LocalDateTime initLocalDateTime(Matcher matcher)
    {
        String timeStr = matcher.group(GROUP_TIME);
        if (timeStr.isEmpty())
        {
            return now;
        } else
        {
            LocalTime time = LocalTime.parse(timeStr);
            return LocalDateTime.of(now.toLocalDate(), time);
        }
    }

    private static TemporalUnit resolveUnit(char c)
    {
        switch (c)
        {
        case 'y':
            return ChronoUnit.YEARS;
        case 'M':
            return ChronoUnit.MONTHS;
        case 'd':
            return ChronoUnit.DAYS;
        case 'h':
            return ChronoUnit.HOURS;
        case 'm':
            return ChronoUnit.MINUTES;
        case 's':
            return ChronoUnit.SECONDS;
        default:
            throw new IllegalArgumentException("'" + c
                    + "' is not a valid unit. It has to be one of 'yMdhms'.");
        }
    }

    private void cacheLocalDateTime(Clock clock)
    {
        this.now = LocalDateTime.now(clock);
    }
}
