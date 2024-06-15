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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;

import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
public class TimeDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.TIME;

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE.toString()).as("name").isEqualTo("TIME");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class").isEqualTo(Time.class);
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isFalse();
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isTrue();
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, new Time(1234), new java.sql.Date(1234),
                new Timestamp(1234), new Time(1234).toString(),
                new java.util.Date(1234),};

        final java.sql.Time[] expected = {null, new Time(1234),
                new Time(new java.sql.Date(1234).getTime()),
                new Time(new Timestamp(1234).getTime()),
                Time.valueOf(new Time(1234).toString()), new Time(1234),};

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
    public void testTypeCastNone() throws Exception
    {
        assertThat(THIS_TYPE.typeCast(ITable.NO_VALUE)).as("typecast").isNull();
    }

    @Override
    @Test
    public void testTypeCastInvalid() throws Exception
    {
        final Object[] values =
                {Integer.valueOf(1234), new Object(), "bla", "2000.05.05",};

        for (int i = 0; i < values.length; i++)
        {
            try
            {
                THIS_TYPE.typeCast(values[i]);
                fail("Should throw TypeCastException - " + i);
            } catch (final TypeCastException e)
            {
            }
        }
    }

    @Test
    void testTypeCastRelative() throws Exception
    {
        // @formatter:off
        final Object[] values = {
                "[now]",
                "[NOW +1h]",
                "[Now -3m -2h]",
                "[NOW+5s]",
        };

        final Clock clock = DataType.RELATIVE_DATE_TIME_PARSER.getClock();

        final LocalTime now      = LocalTime.now(clock);
        final Time[]    expected = {
                Time.valueOf(now),
                Time.valueOf(now.plus(1, ChronoUnit.HOURS)),
                Time.valueOf(now.plus(-3, ChronoUnit.MINUTES).plus(-2, ChronoUnit.HOURS)),
                Time.valueOf(now.plus(5, ChronoUnit.SECONDS)),
        };
        // @formatter:on

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        // Create a new instance to test relative date/time.
        final TimeDataType thisType = new TimeDataType();
        for (int i = 0; i < values.length; i++)
        {
            assertThat(thisType.typeCast(values[i])).as("typecast " + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 = {null, new Time(1234), new java.sql.Date(1234),
                new Timestamp(1234), new Time(1234).toString(),
                new java.util.Date(1234), "00:01:02",};

        final Object[] values2 = {null, new Time(1234),
                new Time(new java.sql.Date(1234).getTime()),
                new Time(new Timestamp(1234).getTime()),
                Time.valueOf(new Time(1234).toString()), new Time(1234),
                new Time(0, 1, 2),};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

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
    public void testCompareInvalid() throws Exception
    {
        final Object[] values1 =
                {Integer.valueOf(1234), new Object(), "bla", "2000.05.05",};
        final Object[] values2 = {null, null, null, null,};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            try
            {
                THIS_TYPE.compare(values1[i], values2[i]);
                fail("Should throw TypeCastException - " + i);
            } catch (final TypeCastException e)
            {
            }

            try
            {
                THIS_TYPE.compare(values1[i], values2[i]);
                fail("Should throw TypeCastException - " + i);
            } catch (final TypeCastException e)
            {
            }
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less =
                {null, new java.sql.Time(0), "08:00:00", "08:00:00",};

        final Object[] greater = {new java.sql.Time(1234),
                new java.sql.Time(System.currentTimeMillis()), "20:00:00",
                "08:00:01",};

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
    public void testSqlType() throws Exception
    {
        assertThat(DataType.forSqlType(Types.TIME)).isEqualTo(THIS_TYPE);
        assertThat(DataType.forSqlTypeName(THIS_TYPE.toString()))
                .as("forSqlTypeName").isEqualTo(THIS_TYPE);
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.TIME);
    }

    /**
     *
     */
    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(new Time(1234))).isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final java.sql.Time[] values = {new java.sql.Time(1234),};

        final String[] expected = {new java.sql.Time(1234).toString(),};

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
    public void testGetSqlValue() throws Exception
    {
        final java.sql.Time[] expected = {null, new Time(1234),
                new Time(new java.sql.Date(1234).getTime()),
                new Time(new Timestamp(1234).getTime()),
                Time.valueOf(new Time(1234).toString()), new Time(1234),};

        lenient().when(mockedResultSet.getTime(2)).thenReturn(expected[1]);
        lenient().when(mockedResultSet.getTime(3)).thenReturn(expected[2]);
        lenient().when(mockedResultSet.getTime(4)).thenReturn(expected[3]);
        lenient().when(mockedResultSet.getTime(5)).thenReturn(expected[4]);
        lenient().when(mockedResultSet.getTime(6)).thenReturn(expected[5]);
        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

}
