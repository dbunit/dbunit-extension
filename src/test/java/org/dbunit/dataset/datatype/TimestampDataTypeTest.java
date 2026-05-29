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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class TimestampDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.TIMESTAMP;

    @Mock
    private ResultSet mockedResultSet;

    @Mock
    private PreparedStatement mockedStatement;

    @Override
    @Test
    public void testToString_withDataType_returnsExpectedString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("TIMESTAMP");
    }

    @Override
    @Test
    public void testGetTypeClass_returnsExpectedClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(Timestamp.class);
    }

    @Override
    @Test
    public void testIsNumber_returnsExpectedBoolean() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isFalse();
    }

    @Override
    @Test
    public void testIsDateTime_returnsExpectedBoolean() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isTrue();
    }

    private static Timestamp makeTimestamp(final int year, final int month,
            final int day, final int hour, final int minute, final int second,
            final int millis, final TimeZone timeZone)
    {
        final Calendar cal = new GregorianCalendar(timeZone);
        cal.clear();
        cal.set(year, month, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, millis);
        return new Timestamp(cal.getTime().getTime());
    }

    private static Timestamp makeTimestamp(final int year, final int month,
            final int day, final int hour, final int minute, final int second,
            final int millis, final String timeZone)
    {
        return makeTimestamp(year, month, day, hour, minute, second, millis,
                TimeZone.getTimeZone(timeZone));
    }

    private static Timestamp makeTimestamp(final int year, final int month,
            final int day, final int hour, final int minute, final int second,
            final int millis)
    {
        return makeTimestamp(year, month, day, hour, minute, second, millis,
                TimeZone.getDefault());
    }

    private static Timestamp makeTimestamp(final int year, final int month,
            final int day, final int hour, final int minute, final int second,
            final String timeZone)
    {
        return makeTimestamp(year, month, day, hour, minute, second, 0,
                TimeZone.getTimeZone(timeZone));
    }

    private static Timestamp makeTimestamp(final int year, final int month,
            final int day, final int hour, final int minute, final int second)
    {
        return makeTimestamp(year, month, day, hour, minute, second, 0,
                TimeZone.getDefault());
    }

    @Test
    void testWithTimezone_LocalTZ() throws Exception
    {
        final Timestamp ts1 =
                makeTimestamp(2013, 0, 27, 1, 22, 41, 900, "GMT+1");
        final String ts2 = "2013-01-27 01:22:41.900 +0100";
        assertThat(THIS_TYPE.typeCast(ts2)).isEqualTo(ts1);
    }

    @Test
    void testWithTimezone_GMT6() throws Exception
    {
        final Timestamp ts1 =
                makeTimestamp(2013, 0, 27, 1, 22, 41, 900, "GMT+6");
        final String ts2 = "2013-01-27 01:22:41.900 +0600";
        assertThat(THIS_TYPE.typeCast(ts2)).isEqualTo(ts1);
    }

    @Test
    void testWithTimezone_DaylightSavingTime() throws Exception
    {
        final Timestamp ts1 =
                makeTimestamp(2021, 5, 26, 18, 42, 50, 900, "Europe/Helsinki");
        final String ts2 = "2021-06-26 18:42:50.900 +0300";
        assertThat(THIS_TYPE.typeCast(ts2)).isEqualTo(ts1);
    }

    @Test
    void testWithTimezone_StandardTime() throws Exception
    {
        final Timestamp ts1 =
                makeTimestamp(2021, 1, 14, 18, 42, 50, 900, "Europe/Helsinki");
        final String ts2 = "2021-02-14 18:42:50.900 +0200";
        assertThat(THIS_TYPE.typeCast(ts2)).isEqualTo(ts1);
    }

    @Test
    void testTypeCast_withNonZeroMinutesTimezoneOffset_returnsCorrectTimestamp()
            throws Exception
    {
        final Timestamp ts1 =
                makeTimestamp(2021, 5, 26, 18, 42, 50, 900, "Asia/Kolkata");
        final String ts2 = "2021-06-26 18:42:50.900 +0530";
        assertThat(THIS_TYPE.typeCast(ts2)).as("IST +0530 timestamp.").isEqualTo(ts1);
    }

    @Test
    void testTypeCast_withDateOnlyStringAndTimezoneOffset_returnsUtcMidnightTimestamp()
            throws Exception
    {
        final Timestamp ts1 = makeTimestamp(2013, 0, 27, 0, 0, 0, "GMT+00:00");
        final String ts2 = "2013-01-27 +0000";
        assertThat(THIS_TYPE.typeCast(ts2))
                .as("date-only string with UTC offset.")
                .isEqualTo(ts1);
    }

    @Override
    @Test
    public void testTypeCast_withCompatibleInput_returnsExpectedValue() throws Exception
    {
        // Useful when manually testing this for other timezones
        // Default setting is to test from default timezone
        // TimeZone testTimeZone = TimeZone.getTimeZone("America/New_York");
        // TimeZone testTimeZone = TimeZone.getTimeZone("Europe/Berlin");
        // TimeZone.setDefault(testTimeZone);

        // @formatter:off
        final Object[] values = {
                null,
                new Timestamp(1234),
                new Date(1234),
                new Time(1234),
                new Timestamp(1234).toString(),
                new Date(1234).toString(),
                new java.util.Date(1234),
                "1995-01-07 01:22:41.9 -0500",
                "1995-01-07 01:22:41.923 -0500",
                "1995-01-07 01:22:41.9",
                "1995-01-07 01:22:41.923",
                "1995-01-07 01:22:41 -0500",
                "1995-01-07 01:22:41",
                "2008-11-27 14:52:38 +0100"
        };

        final Timestamp[] expected = {
                null,
                new Timestamp(1234),
                new Timestamp(new Date(1234).getTime()),
                new Timestamp(new Time(1234).getTime()),
                new Timestamp(1234),
                new Timestamp(Date.valueOf((new Date(1234).toString())).getTime()),
                new Timestamp(1234),
                makeTimestamp(1995, 0, 7, 1, 22, 41, 900, "America/New_York"),
                makeTimestamp(1995, 0, 7, 1, 22, 41, 923, "America/New_York"),
                makeTimestamp(1995, 0, 7, 1, 22, 41, 900),
                makeTimestamp(1995, 0, 7, 1, 22, 41, 923),
                makeTimestamp(1995, 0, 7, 1, 22, 41, "America/New_York"),
                makeTimestamp(1995, 0, 7, 1, 22, 41),
                makeTimestamp(2008, 10, 27, 14, 52, 38, "Europe/Berlin")
        };
        // @formatter:on

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(THIS_TYPE.typeCast(values[i])).as("typecast " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testTypeCastNone_withNullInput_returnsNull() throws Exception
    {
        assertThat(THIS_TYPE.typeCast(ITable.NO_VALUE)).as("typecast").isNull();
    }

    @Test
    void testTypeCast_withTimestampInput_returnsSameInstance() throws Exception
    {
        final Timestamp input = new Timestamp(1234);
        assertThat(THIS_TYPE.typeCast(input))
                .as("typeCast of a Timestamp should return the same instance.")
                .isSameAs(input);
    }

    @Test
    void testTypeCast_withLongValue_returnsTimestampWithSameEpochMillis() throws Exception
    {
        final long epochMillis = 1359245761900L;
        assertThat(THIS_TYPE.typeCast(epochMillis))
                .as("Long value should be cast to a Timestamp with the same epoch millis.")
                .isEqualTo(new Timestamp(epochMillis));
    }

    @Test
    void testTypeCast_withDateOnlyStringWithoutTimezone_returnsTimestampAtLocalMidnight()
            throws Exception
    {
        final String dateString = "2013-01-27";
        final Timestamp expected =
                new Timestamp(Date.valueOf(dateString).getTime());
        assertThat(THIS_TYPE.typeCast(dateString))
                .as("date-only string without timezone should parse as local-timezone midnight.")
                .isEqualTo(expected);
    }

    @Test
    void testTypeCast_withDateOnlyStringAndNegativeTimezoneOffset_returnsCorrectTimestamp()
            throws Exception
    {
        final Timestamp expected = makeTimestamp(2013, 0, 27, 0, 0, 0, "GMT-05:00");
        final String ts = "2013-01-27 -0500";
        assertThat(THIS_TYPE.typeCast(ts))
                .as("date-only string with negative UTC offset.")
                .isEqualTo(expected);
    }

    @Test
    void testTypeCast_withNanosecondPrecisionAndTimezoneOffset_preservesNanoseconds()
            throws Exception
    {
        final Timestamp expected =
                makeTimestamp(2013, 0, 27, 1, 22, 41, 123, "GMT+01:00");
        expected.setNanos(123456789);
        final String ts = "2013-01-27 01:22:41.123456789 +0100";
        assertThat(THIS_TYPE.typeCast(ts))
                .as("sub-millisecond nanoseconds should survive the timezone offset arithmetic.")
                .isEqualTo(expected);
    }

    @Override
    @Test
    public void testTypeCastInvalid_withIncompatibleInput_throwsTypeCastException() throws Exception
    {
        // @formatter:off
        final Object[] values = {
                Integer.valueOf(1234),
                new Object(),
                "bla",
                "2000.05.05",
        };
        // @formatter:on

        for (int i = 0; i < values.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.typeCast(values[id]),
                    "Should throw TypeCastException - " + id);
        }
    }

    @Test
    void testTypeCastRelative_withRelativeTimestampSyntax_returnsExpectedTimestamp() throws Exception
    {
        // @formatter:off
        final Object[] values = {
                "[NOW]",
                "[now+1h]",
                "[NOW +4d 11:22:33]",
                "[Now-2y 19:00]",
        };

        final Clock clock = DataType.RELATIVE_DATE_TIME_PARSER.getClock();

        final LocalDateTime now      = LocalDateTime.now(clock);
        final Timestamp[]   expected = {
                Timestamp.valueOf(now),
                Timestamp.valueOf(now.plus(1, ChronoUnit.HOURS)),
                Timestamp.valueOf(LocalDateTime.of(now.toLocalDate(),
                        LocalTime.of(11, 22, 33)).plus(4, ChronoUnit.DAYS)),
                Timestamp.valueOf(LocalDateTime.of(now.toLocalDate(),
                        LocalTime.of(19, 0)).plus(-2, ChronoUnit.YEARS)),
        };
        // @formatter:on

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        // Create a new instance to test relative date/time.
        final TimestampDataType thisType = new TimestampDataType();
        for (int i = 0; i < values.length; i++)
        {
            assertThat(((Timestamp) thisType.typeCast(values[i])).getTime())
                    .as("typecast " + i).isEqualTo(expected[i].getTime());
        }
    }

    @Test
    void testTypeCastRelative_InvalidTimeFormat() throws Exception
    {

        final TypeCastException exception = assertThrows(
                TypeCastException.class, () -> THIS_TYPE.typeCast("[NOW 1:23]"),
                "DateTimeParseException must be thrown when time format is invalid.");

        assertThat(exception).hasMessageContaining("[NOW 1:23]");
    }

    @Override
    @Test
    public void testCompareEquals_withEqualValues_returnsZero() throws Exception
    {
        // @formatter:off
        final Object[] values1 = {
                null,
                new Timestamp(1234),
                new Date(1234),
                new Time(1234),
                new Timestamp(1234).toString(),
                new java.util.Date(1234),
                "1970-01-01 00:00:00.0",
        };

        final Timestamp[] values2 = {
                null,
                new Timestamp(1234),
                new Timestamp(new Date(1234).getTime()),
                new Timestamp(new Time(1234).getTime()),
                Timestamp.valueOf(new Timestamp(1234).toString()),
                new Timestamp(1234),
                Timestamp.valueOf("1970-01-01 00:00:00.0"),
        };

        assertThat(values2).as("values count").hasSameSizeAs(values1);
        // @formatter:on
        for (int i = 0; i < values1.length; i++)
        {
            assertThat(THIS_TYPE.compare(values1[i], values2[i]))
                    .as("compare1 " + i).isZero();
            assertThat(THIS_TYPE.compare(values2[i], values1[i]))
                    .as("compare2 " + i).isZero();
        }
    }

    @Override
    @Test
    public void testCompareInvalid_withInvalidInput_throwsTypeCastException() throws Exception
    {
        // @formatter:off
        final Object[] values1 = {
                Integer.valueOf(1234),
                new Object(),
                "bla",
                "2000.05.05",
        };
        final Object[] values2 = {
                null,
                null,
                null,
                null,
        };
        // @formatter:on

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should throw TypeCastException - " + id);

            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should throw TypeCastException - " + id);

        }
    }

    @Override
    @Test
    public void testCompareDifferent_withDifferentValues_returnsNonZero() throws Exception
    {
        // @formatter:off
        final Object[] less = {
                null,
                new java.sql.Date(0),
                "1974-06-23 23:40:00.0"
        };

        final Object[] greater = {
                new java.sql.Date(1234),
                new java.sql.Date(System.currentTimeMillis()),
                Timestamp.valueOf("2003-01-30 11:42:00.0"),
        };
        // @formatter:on

        assertThat(greater).as("values count").hasSameSizeAs(less);

        for (int i = 0; i < less.length; i++)
        {
            assertThat(THIS_TYPE.compare(less[i], greater[i])).as("less " + i)
                    .isNegative();
            assertThat(THIS_TYPE.compare(greater[i], less[i]))
                    .as("greater " + i).isPositive();
        }
    }

    @Override
    @Test
    public void testSqlType_returnsExpectedSqlType() throws Exception
    {
        assertThat(DataType.forSqlType(Types.TIMESTAMP)).isEqualTo(THIS_TYPE);
        assertThat(DataType.forSqlTypeName(THIS_TYPE.toString()))
                .as("forSqlTypeName").isEqualTo(THIS_TYPE);
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.TIMESTAMP);
    }

    @Override
    @Test
    public void testForObject_withValidInput_returnsDataType() throws Exception
    {
        assertThat(DataType.forObject(new Timestamp(1234)))
                .isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString_withValidInput_returnsStringRepresentation() throws Exception
    {
        // @formatter:off
        final java.sql.Timestamp[] values = {
                new java.sql.Timestamp(1234),
        };
        // @formatter:on

        // @formatter:off
        final String[] expected = {
                new java.sql.Timestamp(1234).toString(),
        };
        // @formatter:on

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(DataType.asString(values[i])).as("asString " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetSqlValue_withValidStatement_returnsExpectedValue() throws Exception
    {
        // @formatter:off
        final Timestamp[] expected = {
                null,
                new Timestamp(1234),
        };
        // @formatter:on

        lenient().when(mockedResultSet.getTimestamp(2)).thenReturn(expected[1]);
        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

    /**
     * Assert calls ResultSet.getTimestamp(columnIndex) before
     * ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder_afterGetSqlValue_callsGetTimestampBeforeWasNull()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.TIMESTAMP.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getTimestamp(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }

    @Test
    void testGetSqlValue_whenWasNullIsTrue_returnsNullRegardlessOfColumnValue()
            throws TypeCastException, SQLException
    {
        when(mockedResultSet.getTimestamp(1)).thenReturn(new Timestamp(1234));
        when(mockedResultSet.wasNull()).thenReturn(true);

        assertThat(THIS_TYPE.getSqlValue(1, mockedResultSet))
                .as("getSqlValue should return null when wasNull() is true.")
                .isNull();
    }

    @Test
    void testSetSqlValue_withUtcOffsetString_callsSetTimestampWithUtcCalendar()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Calendar> calCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        new TimestampDataType().setSqlValue("2024-03-31 00:00:00 +0000", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class),
                calCaptor.capture());
        assertThat(calCaptor.getValue().getTimeZone().getRawOffset())
                .as("calendar timezone raw offset for GMT+00:00.")
                .isZero();
    }

    @Test
    void testSetSqlValue_withPositiveOffsetString_callsSetTimestampWithMatchingCalendar()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Calendar> calCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        new TimestampDataType().setSqlValue("2024-03-31 00:00:00 +0800", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class),
                calCaptor.capture());
        assertThat(calCaptor.getValue().getTimeZone().getRawOffset())
                .as("calendar timezone raw offset for GMT+08:00.")
                .isEqualTo(8 * 3600 * 1000);
    }

    @Test
    void testSetSqlValue_withNegativeOffsetString_callsSetTimestampWithMatchingCalendar()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Calendar> calCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        new TimestampDataType().setSqlValue("1995-01-07 01:22:41.9 -0500", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class),
                calCaptor.capture());
        assertThat(calCaptor.getValue().getTimeZone().getRawOffset())
                .as("calendar timezone raw offset for GMT-05:00.")
                .isEqualTo(-5 * 3600 * 1000);
    }

    @Test
    void testSetSqlValue_withNoTimezoneString_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue("1995-01-07 01:22:41.9", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withTimestampObject_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue(new Timestamp(1234), 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withUtilDateObject_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue(new java.util.Date(1234), 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withLongValue_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue(1234L, 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withNullValue_callsSetTimestampWithNullTimestamp()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Timestamp> tsCaptor =
                ArgumentCaptor.forClass(Timestamp.class);

        new TimestampDataType().setSqlValue(null, 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), tsCaptor.capture());
        assertThat(tsCaptor.getValue()).as("timestamp for null input should be null.").isNull();
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withDateOnlyString_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue("2024-03-31", 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withRelativeSyntaxString_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue("[NOW]", 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withTimeObject_callsSetTimestampWithoutCalendar()
            throws TypeCastException, SQLException
    {
        new TimestampDataType().setSqlValue(new java.sql.Time(1234), 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class));
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testSetSqlValue_withNonZeroMinutesOffsetString_callsSetTimestampWithMatchingCalendar()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Calendar> calCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        new TimestampDataType().setSqlValue("2021-06-26 18:42:50.900 +0530", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class),
                calCaptor.capture());
        assertThat(calCaptor.getValue().getTimeZone().getRawOffset())
                .as("calendar timezone raw offset for GMT+05:30.")
                .isEqualTo((5 * 60 + 30) * 60 * 1000);
    }

    @Test
    void testSetSqlValue_withDateOnlyStringAndTimezone_callsSetTimestampWithCalendar()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Calendar> calCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        new TimestampDataType().setSqlValue("2013-01-27 +0000", 1,
                mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), any(Timestamp.class),
                calCaptor.capture());
        assertThat(calCaptor.getValue().getTimeZone().getRawOffset())
                .as("calendar timezone raw offset for GMT+00:00.")
                .isZero();
    }

    @Test
    void testSetSqlValue_withTimezoneString_passesTypeCastResultAsTimestamp()
            throws TypeCastException, SQLException
    {
        final String tsString = "2013-01-27 01:22:41.900 +0100";
        final TimestampDataType type = new TimestampDataType();
        final Timestamp expectedTs = (Timestamp) type.typeCast(tsString);
        final ArgumentCaptor<Timestamp> tsCaptor =
                ArgumentCaptor.forClass(Timestamp.class);

        type.setSqlValue(tsString, 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), tsCaptor.capture(),
                any(Calendar.class));
        assertThat(tsCaptor.getValue())
                .as("timestamp passed to setTimestamp should match typeCast result.")
                .isEqualTo(expectedTs);
    }

    @Test
    void testSetSqlValue_withNoValue_callsSetTimestampWithNull()
            throws TypeCastException, SQLException
    {
        final ArgumentCaptor<Timestamp> tsCaptor =
                ArgumentCaptor.forClass(Timestamp.class);

        new TimestampDataType().setSqlValue(ITable.NO_VALUE, 1, mockedStatement);

        verify(mockedStatement).setTimestamp(eq(1), tsCaptor.capture());
        assertThat(tsCaptor.getValue())
                .as("ITable.NO_VALUE should result in a null Timestamp.")
                .isNull();
        verify(mockedStatement, never()).setTimestamp(anyInt(),
                any(Timestamp.class), any(Calendar.class));
    }

    @Test
    void testCompare_withSameMomentInDifferentTimezones_returnsZero()
            throws Exception
    {
        final String ts1 = "2013-01-27 01:22:41.900 +0100";
        final String ts2 = "2013-01-27 00:22:41.900 +0000";
        assertThat(THIS_TYPE.compare(ts1, ts2))
                .as("same moment expressed in UTC+1 and UTC should compare equal.")
                .isZero();
    }

    @Test
    void testCompare_withDifferentMomentsInDifferentTimezones_returnsNonZero()
            throws Exception
    {
        final String earlier = "2013-01-27 01:22:41.900 +0100";
        final String later = "2013-01-27 01:22:41.900 +0000";
        assertThat(THIS_TYPE.compare(earlier, later))
                .as("UTC+1 wall-clock time is 1 hour earlier than same wall-clock in UTC.")
                .isNegative();
    }

    @Test
    void testCompare_withNullAndTimezoneString_returnsNegative() throws Exception
    {
        assertThat(THIS_TYPE.compare(null, "2013-01-27 01:22:41.900 +0100"))
                .as("null should compare less than a non-null timezone-aware timestamp.")
                .isNegative();
    }

    @Test
    void testCompare_withTimestampAndEquivalentTimezoneString_returnsZero()
            throws Exception
    {
        final Timestamp ts = makeTimestamp(2013, 0, 27, 1, 22, 41, 900, "GMT+01:00");
        final String tsStr = "2013-01-27 01:22:41.900 +0100";
        assertThat(THIS_TYPE.compare(ts, tsStr))
                .as("Timestamp and its equivalent timezone string should compare equal.")
                .isZero();
    }
}
