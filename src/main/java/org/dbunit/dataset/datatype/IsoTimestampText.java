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

package org.dbunit.dataset.datatype;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the text of a timestamp that is written in the ISO 8601 and YAML 1.1 form, such as
 * {@code 2024-01-15T10:00:00}, {@code 2024-01-15T10:00:00Z} or
 * {@code 2024-01-15t10:00:00.5+01:00}, or with more than one space, a tab or a fraction without
 * digits, which YAML 1.1 also reads as a timestamp, such as {@code 2024-01-15  10:00:00} or
 * {@code 2024-01-15 10:00:00.}, into the JDBC timestamp escape form that
 * {@link java.sql.Timestamp#valueOf(String)} reads, {@code yyyy-mm-dd hh:mm:ss[.f]}, with the
 * offset behind it, if there is one, as {@code +hhmm} after a space, the form of the zone that
 * {@link TimestampDataType} reads. The time zone of a text without an offset stays the JVM's,
 * as for the JDBC form of a timestamp.
 *
 * <p>Other text, such as a timestamp in the JDBC form, is returned as it is.
 *
 * @since 3.6.0
 */
class IsoTimestampText
{
    private static final Pattern ISO_TIMESTAMP = Pattern.compile("(\\d{4}-\\d{1,2}-\\d{1,2})"
            + "(?:[Tt]|\\s+)(\\d{1,2}:\\d{2}:\\d{2})(?:\\.(\\d*))?\\s*"
            + "(?:(Z|z)|([+-])(\\d{1,2})(?::?(\\d{2}))?)?");

    private IsoTimestampText()
    {
    }

    /**
     * Rewrites a timestamp text in the ISO 8601 or YAML 1.1 form into the JDBC form.
     *
     * @param text The text of a timestamp.
     * @return The text in the JDBC form, with the offset as {@code +hhmm} after a space, or
     *         {@code text} itself when it is not in the ISO form.
     */
    static String toJdbcTimestampText(final String text)
    {
        final boolean mayBeIsoForm = mayBeIsoForm(text);
        if (!mayBeIsoForm)
        {
            return text;
        }

        final Matcher matcher = ISO_TIMESTAMP.matcher(text);
        if (!matcher.matches())
        {
            return text;
        }

        final StringBuilder jdbcText = new StringBuilder(text.length() + 8);
        jdbcText.append(matcher.group(1)).append(' ').append(matcher.group(2));
        final String fraction = matcher.group(3);
        if (fraction != null && !fraction.isEmpty())
        {
            jdbcText.append('.').append(fraction);
        }
        appendOffset(matcher, jdbcText);
        return jdbcText.toString();
    }

    /**
     * Rules out the text of a plain JDBC timestamp cheaply, so that only text with a {@code T},
     * whitespace other than one space after the date, a {@code Z}, an offset of one of the forms
     * {@code +hh} and {@code +hh:mm}, or a dot at the end pays for the regular expression.
     */
    private static boolean mayBeIsoForm(final String text)
    {
        final int length = text.length();
        if (length < 16)
        {
            return false;
        }

        final char last = text.charAt(length - 1);
        final boolean hasIsoSeparator = hasIsoSeparatorAfterTheDate(text);
        final boolean endsWithEmptyFraction = last == '.';
        final boolean endsWithUtc = last == 'Z' || last == 'z';
        final boolean endsWithHourOffset = isDigit(last)
                && (isSign(text.charAt(length - 3)) || isSign(text.charAt(length - 2)));
        final boolean endsWithColonOffset = length >= 6 && isSign(text.charAt(length - 6))
                && text.charAt(length - 3) == ':';
        return hasIsoSeparator || endsWithEmptyFraction || endsWithUtc || endsWithHourOffset
                || endsWithColonOffset;
    }

    /**
     * Returns whether the date ends in a separator other than the one space of the JDBC form: a
     * {@code T}, a {@code t}, a tab or more than one space. The date has four digits for the
     * year and one or two for the month and the day, so it ends between the eighth and the
     * tenth character.
     */
    private static boolean hasIsoSeparatorAfterTheDate(final String text)
    {
        for (int index = 8; index <= 10; index++)
        {
            final char character = text.charAt(index);
            if (character == 'T' || character == 't')
            {
                return true;
            }
            if (Character.isWhitespace(character))
            {
                final boolean oneSpace =
                        character == ' ' && !Character.isWhitespace(text.charAt(index + 1));
                return !oneSpace;
            }
        }
        return false;
    }

    private static boolean isSign(final char character)
    {
        return character == '+' || character == '-';
    }

    private static boolean isDigit(final char character)
    {
        return character >= '0' && character <= '9';
    }

    private static void appendOffset(final Matcher matcher, final StringBuilder jdbcText)
    {
        final boolean utc = matcher.group(4) != null;
        if (utc)
        {
            jdbcText.append(" +0000");
            return;
        }

        final String sign = matcher.group(5);
        if (sign == null)
        {
            return;
        }

        final String hours = matcher.group(6);
        final String minutes = matcher.group(7);
        jdbcText.append(' ').append(sign);
        if (hours.length() == 1)
        {
            jdbcText.append('0');
        }
        jdbcText.append(hours);
        jdbcText.append(minutes == null ? "00" : minutes);
    }
}
