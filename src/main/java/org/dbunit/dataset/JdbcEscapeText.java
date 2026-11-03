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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;

/**
 * Turns the date, time and timestamp values of a dataset into the JDBC escape text that the
 * date and time column types parse. A writer of a text format uses it, so that a written
 * dataset reads back the same day and the same time in any time zone, where a number of
 * milliseconds since the epoch, or an instant in UTC, reads back as another day or time in
 * another time zone.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class JdbcEscapeText
{
    private JdbcEscapeText()
    {
    }

    /**
     * Returns a date, a time or a timestamp as the text a column type parses, the JDBC escape
     * format of {@link java.sql.Timestamp#toString()} and its siblings: a {@code java.sql} or
     * {@code java.time} date, time or date and time as it is, a {@code java.time} time to the
     * second, and an instant, such as a {@link java.util.Date} or an
     * {@link java.time.OffsetDateTime}, in the time zone of the JVM. A date then reads back as
     * the same day, and a timestamp as the same date and time, in any time zone, as from a flat
     * XML file. Returns any other {@code java.time} value as its {@code toString()}, and any
     * other value as it is.
     *
     * @param value The value of a cell; not {@code null}.
     * @return The JDBC escape text if the value is a date, a time or a timestamp, the
     *         {@code toString()} text of any other {@code java.time} value, otherwise the
     *         value itself.
     */
    public static Object of(final Object value)
    {
        if (value instanceof java.sql.Date || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp)
        {
            return value.toString();
        }
        if (value instanceof java.util.Date)
        {
            final long millis = ((java.util.Date) value).getTime();
            return new java.sql.Timestamp(millis).toString();
        }
        if (value instanceof LocalDate)
        {
            return java.sql.Date.valueOf((LocalDate) value).toString();
        }
        if (value instanceof LocalDateTime)
        {
            return java.sql.Timestamp.valueOf((LocalDateTime) value).toString();
        }
        if (value instanceof LocalTime)
        {
            return java.sql.Time.valueOf((LocalTime) value).toString();
        }
        if (value instanceof Instant || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime)
        {
            final Instant instant = Instant.from((TemporalAccessor) value);
            return java.sql.Timestamp.from(instant).toString();
        }
        if (value instanceof TemporalAccessor)
        {
            return value.toString();
        }
        return value;
    }
}
