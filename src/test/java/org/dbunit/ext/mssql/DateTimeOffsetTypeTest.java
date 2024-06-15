/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2019, DbUnit.org
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
package org.dbunit.ext.mssql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MAVEN_CMD_LINE_ARGS", matches = "(.*)mssql(.*)")
class DateTimeOffsetTypeTest
{
    private DateTimeOffsetType type;

    @BeforeEach
    protected void setUp() throws Exception
    {
        type = new DateTimeOffsetType();
    }

    @Test
    void testTypeCastWithNull() throws TypeCastException
    {
        final Object result = type.typeCast(null);
        assertThat(result).isNull();
    }

    @Test
    void testTypeCastWithOffsetDateTime() throws TypeCastException
    {
        final Object result = type.typeCast(OffsetDateTime.MIN);
        assertThat(result).isSameAs(OffsetDateTime.MIN);
    }

    @Test
    void testTypeCastWithValidTemporalAccessor() throws TypeCastException
    {
        final ZonedDateTime now = ZonedDateTime.now();
        final Object result = type.typeCast(now);
        assertThat(result).isEqualTo(now.toOffsetDateTime());
    }

    @Test
    void testTypeCastWithInvalidTemporalAccessor() throws TypeCastException
    {
        assertThrows(TypeCastException.class,
                () -> type.typeCast(LocalDateTime.now()),
                "Should not be possible to convert due to insufficient information");

    }

    @Test
    void testTypeCastWithISO_8601_String() throws TypeCastException
    {
        final Object result = type.typeCast("2000-01-01T01:00:00Z");
        assertThat(result).isEqualTo(
                OffsetDateTime.of(2000, 1, 1, 1, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void testTypeCastWithSqlServerStringWithoutNanos() throws TypeCastException
    {
        final Object result = type.typeCast("2000-01-01 01:00:00 +00:00");
        assertThat(result).isEqualTo(
                OffsetDateTime.of(2000, 1, 1, 1, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void testTypeCastWithSqlServerStringWithNanos() throws TypeCastException
    {
        final Object result =
                type.typeCast("2000-01-01 01:00:00.123000 +00:00");
        assertThat(result).isEqualTo(
                OffsetDateTime.of(2000, 1, 1, 1, 0, 0, 123000, ZoneOffset.UTC));
    }

    @Test
    void testTypeCastWithInvalidObject() throws TypeCastException
    {
        assertThrows(TypeCastException.class, () -> type.typeCast(new Object()),
                "Should not be possible to convert due to invalid string format");
    }
}
