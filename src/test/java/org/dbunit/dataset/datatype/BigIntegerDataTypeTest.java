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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;

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
class BigIntegerDataTypeTest extends AbstractDataTypeTest
{
    private static final String NUMBER_LARGER_THAN_LONG =
            "17446744073709551630";
    private final static DataType THIS_TYPE = DataType.BIGINT;

    @Mock
    private ResultSet mockedResultSet;

    @Mock
    private PreparedStatement statement;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("BIGINT");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(BigInteger.class);
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
                Double.valueOf(1.5E6), new BigDecimal((double) 1234),
                NUMBER_LARGER_THAN_LONG,
                new BigDecimal(NUMBER_LARGER_THAN_LONG),};

        final BigInteger[] expected = {null, new BigInteger("5"),
                new BigInteger("1234"), new BigInteger("" + Long.MAX_VALUE),
                new BigInteger("" + Long.MIN_VALUE), new BigInteger("-7500"),
                new BigInteger("" + Long.MAX_VALUE),
                new BigInteger("" + Long.MIN_VALUE), new BigInteger("0"),
                new BigInteger("0"), new BigInteger("5"), new BigInteger("-99"),
                new BigInteger("1500000"), new BigInteger("1234"),
                new BigInteger(NUMBER_LARGER_THAN_LONG),
                new BigInteger(NUMBER_LARGER_THAN_LONG),};
        assertThat(values).as("actual vs expected count")
                .hasSize(expected.length);

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

        for (final Object value : values)
        {
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.typeCast(value),
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
        assertThat(values1).as("values count").hasSize(values2.length);

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

        assertThat(values1).as("values count").hasSize(values2.length);

        for (int i = 0; i < values1.length; i++)
        {
            final int r = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[r], values2[r]),
                    "Should throw TypeCastException");

            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values2[r], values1[r]),
                    "Should throw TypeCastException");
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, null, "-7500",};

        final Object[] greater = {"0", Long.valueOf(-5), Long.valueOf(5),};

        assertThat(less).as("values count").hasSize(greater.length);

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
        assertThat(THIS_TYPE).isEqualTo(DataType.forSqlType(Types.BIGINT))
                .as("forSqlTypeName")
                .isEqualTo(DataType.forSqlTypeName(THIS_TYPE.toString()));
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.BIGINT);
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(new BigInteger("1234")))
                .isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Long[] values = {(long) 1234,};

        final String[] expected = {"1234",};

        assertThat(values).as("actual vs expected count")
                .hasSize(expected.length);

        for (int i = 0; i < values.length; i++)
        {
            assertThat(DataType.asString(values[i])).as("asString" + i)
                    .isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetSqlValue() throws Exception
    {
        final BigInteger[] expected = {null, new BigInteger("5"),
                new BigInteger("1234"), new BigInteger("" + Long.MAX_VALUE),
                new BigInteger("" + Long.MIN_VALUE), new BigInteger("-7500"),
                new BigInteger("0"),};
        final LinkedList<BigInteger> results = new LinkedList<>();
        final LinkedList<BigInteger> resultIsNull = new LinkedList<>();
        Arrays.asList(expected).forEach(results::add);
        // Internally BigIntegerDataType uses resultSet.getBigDecimal() on the
        // JDBC API because there is no resultSet.getBigInteger().
        when(mockedResultSet.getBigDecimal(anyInt())).thenAnswer(invocation -> {
            final BigInteger i = results.removeFirst();
            if (Objects.isNull(i))
            {
                return null;
            } else
            {
                return new BigDecimal(i);
            }
        });
        // Our resultSet wasNull call the array
        Arrays.asList(expected).forEach(resultIsNull::add);
        when(mockedResultSet.wasNull()).thenAnswer(invocation -> {
            final BigInteger bigI = resultIsNull.removeFirst();
            if (Objects.isNull(bigI))
            {
                return true;
            } else
            {
                return false;
            }
        });

        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i, mockedResultSet);
            if (expectedValue != null && actualValue != null)
            {
                assertThat(actualValue.getClass()).as("type mismatch")
                        .isEqualTo(expectedValue.getClass());
            }
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

    /** Issue 361: NPE when value is null. */
    @Test
    void testSetSqlValue_Null() throws Exception
    {
        final Object value = null;
        final int column = 1;
        assertDoesNotThrow(
                () -> THIS_TYPE.setSqlValue(value, column, statement));
    }

    @Test
    void testSetSqlValue_Integer() throws Exception
    {
        final Object value = 1;
        final int column = 1;

        assertDoesNotThrow(
                () -> THIS_TYPE.setSqlValue(value, column, statement));
    }

    /**
     * Assert calls ResultSet.getInt(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        when(mockedResultSet.getBigDecimal(columnIndex))
                .thenReturn(BigDecimal.TEN);

        DataType.BIGINT.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getBigDecimal(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
