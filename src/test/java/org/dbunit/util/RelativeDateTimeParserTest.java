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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class RelativeDateTimeParserTest
{
    private static Clock CLOCK =
            Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private static RelativeDateTimeParser parser =
            new RelativeDateTimeParser(CLOCK);

    @Test
    void testNullInput() throws Exception
    {
        final IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(null),
                "IllegalArgumentException must be thrown when input is null.");
        assertThat(e).hasMessageContaining(
                "Relative datetime input must not be null or empty.");
    }

    @Test
    void testEmptyInput() throws Exception
    {

        final IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(""),
                "IllegalArgumentException must be thrown when input is empty.");
        assertThat(e).hasMessageContaining(
                "Relative datetime input must not be null or empty.");
    }

    @Test
    void testInvalidInputs() throws Exception
    {
        // @formatter:off
        final String[] inputs = {
                "[+1d]", // missing 'now' prefix
                "[NOW+1d", // missing closing bracket
                "[NOW+1x]", // invalid unit
                "[now+1d3y]", // missing +- sign
        };
        // @formatter:on
        for (final String input : inputs)
        {
            verifyPatternMismatchError(input);
        }
    }

    private void verifyPatternMismatchError(final String input)
    {
        try
        {
            parser.parse(input);
            fail("IllegalArgumentException must be thrown for input '" + input
                    + "'");
        } catch (final IllegalArgumentException e)
        {
            assertEquals("'" + input
                    + "' does not match the expected pattern [now{diff}{time}]. "
                    + "Please see the data types documentation for the details. "
                    + "http://dbunit.sourceforge.net/datatypes.html#relativedatetime",
                    e.getMessage());
        }
    }

    @Test
    void testInvalidFormat_UnparsableTime() throws Exception
    {
        final DateTimeParseException e = assertThrows(
                DateTimeParseException.class, () -> parser.parse("[now 1:23]"),
                "DateTimeParseException should be thrown.");
        assertThat(e).hasMessageContaining(
                "Text '1:23' could not be parsed at index 0");
    }

    @Test
    void testUnitResolution() throws Exception
    {
        final LocalDateTime actual = parser.parse("[NOW+1y-2M+3d-4h+5m-6s]");
        final LocalDateTime expected = LocalDateTime.now(CLOCK)
                .plus(1, ChronoUnit.YEARS).plus(-2, ChronoUnit.MONTHS)
                .plus(3, ChronoUnit.DAYS).plus(-4, ChronoUnit.HOURS)
                .plus(5, ChronoUnit.MINUTES).plus(-6, ChronoUnit.SECONDS);
        assertThat(expected).isEqualTo(actual);
    }

    @Test
    void testOrderInsensitivity() throws Exception
    {
        final LocalDateTime actual = parser.parse("[NOW+1s-2m+3h-4d+5M-6y]");
        final LocalDateTime expected = LocalDateTime.now(CLOCK)
                .plus(1, ChronoUnit.SECONDS).plus(-2, ChronoUnit.MINUTES)
                .plus(3, ChronoUnit.HOURS).plus(-4, ChronoUnit.DAYS)
                .plus(5, ChronoUnit.MONTHS).plus(-6, ChronoUnit.YEARS);
        assertThat(expected).isEqualTo(actual);
    }

    @Test
    void testWhitespaces() throws Exception
    {
        final LocalDateTime actual =
                parser.parse("[NOW\t \r\n+1y  -2M\t\t+3d]");
        final LocalDateTime expected =
                LocalDateTime.now(CLOCK).plus(1, ChronoUnit.YEARS)
                        .plus(-2, ChronoUnit.MONTHS).plus(3, ChronoUnit.DAYS);
        assertThat(expected).isEqualTo(actual);
    }

    @Test
    void testNow() throws Exception
    {
        final LocalDateTime actual = parser.parse("[NOW]");
        final LocalDateTime expected = LocalDateTime.now(CLOCK);
        assertThat(expected).isEqualTo(actual);
    }

    @Test
    void testHoursMinutes() throws Exception
    {
        final LocalDateTime actual = parser.parse("[now 12:34]");
        final LocalDateTime expected =
                LocalDateTime.of(LocalDate.now(CLOCK), LocalTime.of(12, 34));
        assertThat(expected).isEqualTo(actual);
    }

    @Test
    void testHoursMinutesSeconds() throws Exception
    {
        final LocalDateTime actual = parser.parse("[Now02:34:56]");
        final LocalDateTime expected =
                LocalDateTime.of(LocalDate.now(CLOCK), LocalTime.of(2, 34, 56));
        assertThat(expected).isEqualTo(actual);
    }
}
