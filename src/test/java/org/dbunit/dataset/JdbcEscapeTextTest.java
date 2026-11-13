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
package org.dbunit.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link JdbcEscapeText}, one for each kind of value it converts.
 *
 * @author Jeff Jensen
 */
class JdbcEscapeTextTest
{
    private static final Instant INSTANT = Instant.parse("2024-03-01T09:15:30Z");

    @Test
    void testOf_sqlDateTimeAndTimestamp_returnsTheirJdbcEscapeText()
    {
        final Object[] actual = {JdbcEscapeText.of(java.sql.Date.valueOf("2024-03-01")),
                JdbcEscapeText.of(java.sql.Time.valueOf("10:15:30")),
                JdbcEscapeText.of(java.sql.Timestamp.valueOf("2024-03-01 10:15:30"))};

        assertThat(actual)
                .as("A java.sql date, time and timestamp are written in the format of their"
                        + " toString().")
                .containsExactly("2024-03-01", "10:15:30", "2024-03-01 10:15:30.0");
    }

    @Test
    void testOf_javaTimeValuesWithoutZone_returnsTheirJdbcEscapeText()
    {
        final Object[] actual = {JdbcEscapeText.of(LocalDate.of(2024, 3, 1)),
                JdbcEscapeText.of(LocalDateTime.of(2024, 3, 1, 10, 15, 30)),
                JdbcEscapeText.of(LocalDateTime.of(2024, 3, 1, 10, 15, 30, 123_000_000)),
                JdbcEscapeText.of(LocalTime.of(10, 15, 30)),
                JdbcEscapeText.of(LocalTime.of(10, 15, 30, 999_000_000))};

        assertThat(actual)
                .as("A java.time date, date and time and time are written as their java.sql"
                        + " counterparts are, a time to the second.")
                .containsExactly("2024-03-01", "2024-03-01 10:15:30.0",
                        "2024-03-01 10:15:30.123", "10:15:30", "10:15:30");
    }

    @ParameterizedTest
    @CsvSource({"Europe/Berlin,2024-03-01 10:15:30.0", "America/Chicago,2024-03-01 03:15:30.0",
            "Asia/Kolkata,2024-03-01 14:45:30.0"})
    void testOf_instantValues_returnTheTimestampTextInTheZoneInForce(final String zoneId,
            final String expected)
    {
        final TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        try
        {
            final Object[] actual = {JdbcEscapeText.of(new java.util.Date(INSTANT.toEpochMilli())),
                    JdbcEscapeText.of(INSTANT),
                    JdbcEscapeText.of(OffsetDateTime.ofInstant(INSTANT, ZoneOffset.ofHours(-5))),
                    JdbcEscapeText.of(ZonedDateTime.ofInstant(INSTANT, ZoneId.of("Asia/Tokyo")))};

            assertThat(actual)
                    .as("A java.util.Date, an instant and a value with an offset or a zone are"
                            + " written as the date and time of the instant in the zone of the"
                            + " JVM, %s here.", zoneId)
                    .containsOnly(expected);
        } finally
        {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void testOf_otherJavaTimeValue_returnsItsToString()
    {
        final Object[] actual = {JdbcEscapeText.of(Year.of(2024)),
                JdbcEscapeText.of(YearMonth.of(2024, 3))};

        assertThat(actual).as("Any other java.time value is written as its toString().")
                .containsExactly("2024", "2024-03");
    }

    @Test
    void testOf_valueThatIsNoDateOrTime_returnsTheSameObject()
    {
        final String text = "2024-03-01";
        final Integer number = 5;
        final BigDecimal decimal = new BigDecimal("12.50");
        final byte[] bytes = {1, 2};

        assertThat(JdbcEscapeText.of(text)).as("A string.").isSameAs(text);
        assertThat(JdbcEscapeText.of(number)).as("A number.").isSameAs(number);
        assertThat(JdbcEscapeText.of(decimal)).as("A decimal.").isSameAs(decimal);
        assertThat(JdbcEscapeText.of(bytes)).as("A byte array.").isSameAs(bytes);
    }
}
