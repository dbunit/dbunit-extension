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
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class DateDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.DATE;

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("DATE");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(java.sql.Date.class);
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
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, new java.sql.Date(1234), new Time(1234),
                new Timestamp(1234), new java.sql.Date(1234).toString(),
                new Timestamp(1234).toString(), new java.util.Date(1234),};

        final java.sql.Date[] expected = {null, new java.sql.Date(1234),
                new java.sql.Date(new Time(1234).getTime()),
                new java.sql.Date(new Timestamp(1234).getTime()),
                java.sql.Date.valueOf(new java.sql.Date(1234).toString()),
                new java.sql.Date(Timestamp
                        .valueOf(new Timestamp(1234).toString()).getTime()),
                new java.sql.Date(1234),};

        assertThat(expected).as("actual vs expected count")
                .hasSize(values.length);

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
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.typeCast(values[id]),
                    "Should throw TypeCastException - " + id);
        }
    }

    @Test
    void testTypeCastRelative() throws Exception
    {
        // @formatter:off
        final Object[] values = {
                "[now]",
                "[Now+1d]",
                "[noW+24h]",
                "[nOw -4y-2d]",
                "[NOW+5d-2M+3y]",
        };

        final Clock clock = DataType.RELATIVE_DATE_TIME_PARSER.getClock();

        final LocalDate today = LocalDate.now(clock);
        final java.sql.Date[] expected = {
                java.sql.Date.valueOf(today),
                java.sql.Date.valueOf(today.plus(1, ChronoUnit.DAYS)),
                java.sql.Date.valueOf(today.plus(1, ChronoUnit.DAYS)),
                java.sql.Date.valueOf(today.plus(-2, ChronoUnit.DAYS)
                        .plus(-4L, ChronoUnit.YEARS)),
                java.sql.Date.valueOf(today.plus(5, ChronoUnit.DAYS)
                        .plus(-2L, ChronoUnit.MONTHS)
                        .plus(3L, ChronoUnit.YEARS)),
        };
        // @formatter:on

        assertThat(expected).as("actual vs expected count")
                .hasSize(values.length);

        // Create a new instance to test relative date/time.
        final DateDataType thisType = new DateDataType();
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
        final Object[] values1 = {null, new java.sql.Date(1234), new Time(1234),
                new Timestamp(1234), new java.sql.Date(1234).toString(),
                new java.util.Date(1234), "2003-01-30"};

        final Object[] values2 = {null, new java.sql.Date(1234),
                new java.sql.Date(new Time(1234).getTime()),
                new java.sql.Date(new Timestamp(1234).getTime()),
                java.sql.Date.valueOf(new java.sql.Date(1234).toString()),
                new java.sql.Date(1234), java.sql.Date.valueOf("2003-01-30"),};

        assertThat(values2).as("values count").hasSize(values1.length);

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

        assertThat(values2).as("values count").hasSize(values1.length);

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
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, new java.sql.Date(0), "1974-06-23"};

        final Object[] greater = {new java.sql.Date(1234),
                new java.sql.Date(System.currentTimeMillis()),
                java.sql.Date.valueOf("2003-01-30"),};

        assertThat(greater).as("values count").hasSize(less.length);

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
        assertThat(DataType.forSqlType(Types.DATE)).isEqualTo(THIS_TYPE);
        assertThat(DataType.forSqlTypeName(THIS_TYPE.toString()))
                .as("forSqlTypeName").isEqualTo(THIS_TYPE);
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.DATE);
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(new java.sql.Date(1234)))
                .isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final java.sql.Date[] values = {new java.sql.Date(1234),};

        final String[] expected = {new java.sql.Date(1234).toString(),};

        assertThat(values).as("actual vs expected count")
                .hasSize(expected.length);

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
        final java.sql.Date[] expected = {null, new java.sql.Date(1234),};

        when(mockedResultSet.getDate(1)).thenReturn(expected[0]);
        when(mockedResultSet.getDate(2)).thenReturn(expected[1]);
        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

    /**
     * Assert calls ResultSet.getDate(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.DATE.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getDate(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
