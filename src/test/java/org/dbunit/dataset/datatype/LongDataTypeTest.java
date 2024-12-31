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
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

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
public class LongDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.BIGINT_AUX_LONG;

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE.toString()).as("name").isEqualTo("BIGINT");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class").isEqualTo(Long.class);
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isTrue();
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isFalse();
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, "5", Long.valueOf(1234),
                Float.valueOf(Long.MAX_VALUE), Float.valueOf(Long.MIN_VALUE),
                "-7500", Double.valueOf(Long.MAX_VALUE),
                Double.valueOf(Long.MIN_VALUE), Float.valueOf("0.666"),
                Double.valueOf(0.666), Double.valueOf(5.49), "-99.9",
                Double.valueOf(1.5E6), new BigDecimal((double) 1234),};

        final Long[] expected = {null, 5L, 1234L, Long.MAX_VALUE,
                Long.MIN_VALUE, -7500L, Long.MAX_VALUE, Long.MIN_VALUE, 0L, 0L,
                5L, -99L, 1500000L, 1234L,};

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
        final Object[] values = {new Object(), "bla", new java.util.Date()};

        for (int i = 0; i < values.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.typeCast(values[id]),
                    "Should throw TypeCastException");
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 = {null, "5", Long.valueOf(1234),
                Float.valueOf(Long.MAX_VALUE), Float.valueOf(Long.MIN_VALUE),
                "-7500", Double.valueOf(Long.MAX_VALUE),
                Double.valueOf(Long.MIN_VALUE), Float.valueOf("0.666"),
                Double.valueOf(0.666), Double.valueOf(5.49), "-99.9",
                Double.valueOf(1.5E6), new BigDecimal((double) 1234),};

        final Object[] values2 = {null, Long.valueOf(5), Long.valueOf(1234),
                Long.valueOf(Long.MAX_VALUE), Long.valueOf(Long.MIN_VALUE),
                Long.valueOf(-7500), Long.valueOf(Long.MAX_VALUE),
                Long.valueOf(Long.MIN_VALUE), Long.valueOf(0), Long.valueOf(0),
                Long.valueOf(5), Long.valueOf(-99), Long.valueOf(1500000),
                Long.valueOf(1234),};

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
        final Object[] values1 = {new Object(), "bla", new java.util.Date()};
        final Object[] values2 = {null, null, null};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < values1.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should throw TypeCastException");

            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values2[id], values1[id]),
                    "Should throw TypeCastException");
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, null, "-7500",};

        final Object[] greater = {"0", Long.valueOf(-5), Long.valueOf(5),};

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
    public void testSqlType() throws Exception
    {
        // This test was commented out in release 2.4.6 because the LongDataType
        // is not used anymore
        // by default for the SQL type BIGINT. This is due to a bug with values
        // that have more than 19 digits
        // where a BigInteger is now favored.
        // assertThat( DataType.forSqlType(Types.BIGINT)).isEqualTo(THIS_TYPE);
        // assertThat(
        // DataType.forSqlTypeName(THIS_TYPE.toString())).as("forSqlTypeName").isEqualTo(
        // THIS_TYPE);
        // assertThat( THIS_TYPE.getSqlType()).isEqualTo(Types.BIGINT);
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        final DataType actual = DataType.forObject(Long.valueOf(1234));
        assertThat(actual).isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Long[] values = {(long) 1234,};

        final String[] expected = {"1234",};

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
        final Long[] expected =
                {null, 5L, 1234L, Long.MAX_VALUE, Long.MIN_VALUE, -7500L, 0L,};

        lenient().when(mockedResultSet.getLong(2)).thenReturn(expected[1]);
        lenient().when(mockedResultSet.getLong(3)).thenReturn(expected[2]);
        lenient().when(mockedResultSet.getLong(4)).thenReturn(expected[3]);
        lenient().when(mockedResultSet.getLong(5)).thenReturn(expected[4]);
        lenient().when(mockedResultSet.getLong(6)).thenReturn(expected[5]);
        lenient().when(mockedResultSet.getLong(7)).thenReturn(expected[6]);
        lenient().when(mockedResultSet.wasNull()).thenReturn(true)
                .thenReturn(false);
        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

    /**
     * Assert calls ResultSet.Long(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.BIGINT_AUX_LONG.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getLong(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
