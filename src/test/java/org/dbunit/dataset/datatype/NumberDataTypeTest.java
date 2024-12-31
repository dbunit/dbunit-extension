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
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.Types;

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
public class NumberDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType[] TYPES =
            {DataType.NUMERIC, DataType.DECIMAL};

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        final String[] expected = {"NUMERIC", "DECIMAL"};

        assertThat(TYPES).as("type count").hasSameSizeAs(expected);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i]).as("name").hasToString(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.getTypeClass()).as("class")
                    .isEqualTo(BigDecimal.class);
        }
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.isNumber()).as("is number").isTrue();
        }
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.isDateTime()).as("is date/time").isFalse();
        }
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, new BigDecimal((double) 1234), "1234",
                "12.34", Boolean.TRUE, Boolean.FALSE, Double.valueOf(2.1),
                Float.valueOf(3.1F), Integer.valueOf(4), Long.valueOf(5),
                Short.valueOf((short) 6), new BigInteger("12345")};
        final BigDecimal[] expected = {null, new BigDecimal((double) 1234),
                new BigDecimal((double) 1234), new BigDecimal("12.34"),
                new BigDecimal("1"), new BigDecimal("0"), new BigDecimal("2.1"),
                new BigDecimal("3.1"), new BigDecimal("4"), new BigDecimal("5"),
                new BigDecimal("6"), new BigDecimal("12345")};

        assertThat(expected).as("actual vs expected count")
                .hasSameSizeAs(values);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values.length; j++)
            {
                assertThat(element.typeCast(values[j])).as("typecast " + j)
                        .isEqualTo(expected[j]);
            }
        }
    }

    @Override
    @Test
    public void testTypeCastNone() throws Exception
    {
        for (final DataType type : TYPES)
        {
            assertThat(type.typeCast(ITable.NO_VALUE)).as("typecast " + type)
                    .isNull();
        }
    }

    @Override
    @Test
    public void testTypeCastInvalid() throws Exception
    {
        final Object[] values = {new Object(), "bla"};

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                final int id = i;
                final int jd = j;
                assertThrows(TypeCastException.class,
                        () -> TYPES[id].typeCast(values[jd]),
                        "Should throw TypeCastException");
            }
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 = {null, new BigDecimal((double) 1234), "1234",
                "12.34", Boolean.TRUE, Boolean.FALSE, new BigDecimal(123.4),
                "123",};
        final Object[] values2 = {null, new BigDecimal((double) 1234),
                new BigDecimal(1234), new BigDecimal("12.34"),
                new BigDecimal("1"), new BigDecimal("0"),
                new BigDecimal(123.4000), new BigDecimal("123.0"),};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values1.length; j++)
            {
                assertThat(element.compare(values1[j], values2[j]))
                        .as("compare1 " + j).isZero();
                assertThat(element.compare(values2[j], values1[j]))
                        .as("compare2 " + j, 0).isZero();
            }
        }
    }

    @Override
    @Test
    public void testCompareInvalid() throws Exception
    {
        final Object[] values1 = {new Object(), "bla", new java.util.Date()};
        final Object[] values2 = {null, null, null};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values1.length; j++)
            {
                final int id = i;
                final int jd = j;
                assertThrows(TypeCastException.class,
                        () -> TYPES[id].compare(values1[jd], values2[jd]),
                        "Should throw TypeCastException");

                assertThrows(TypeCastException.class,
                        () -> TYPES[id].compare(values2[jd], values1[jd]),
                        "Should throw TypeCastException");
            }
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, "-7500", new BigDecimal("-0.01"),
                new BigInteger("1234"),};

        final Object[] greater = {"0", "5.555", new BigDecimal("0.01"),
                new BigDecimal("1234.5"),};

        assertThat(greater).as("values count").hasSameSizeAs(less);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < less.length; j++)
            {
                assertThat(element.compare(less[j], greater[j])).as("less " + j)
                        .isNegative();
                assertThat(element.compare(greater[j], less[j]))
                        .as("greater " + j).isPositive();
            }
        }
    }

    @Override
    @Test
    public void testSqlType() throws Exception
    {
        final int[] sqlTypes = {Types.NUMERIC, Types.DECIMAL};

        assertThat(TYPES).as("count").hasSameSizeAs(sqlTypes);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(DataType.forSqlType(sqlTypes[i])).as("forSqlType")
                    .isEqualTo(TYPES[i]);
            assertThat(DataType.forSqlTypeName(TYPES[i].toString()))
                    .as("forSqlTypeName").isEqualTo(TYPES[i]);
            assertThat(TYPES[i].getSqlType()).as("getSqlType")
                    .isEqualTo(sqlTypes[i]);
        }
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(new BigDecimal((double) 1234)))
                .isEqualTo(DataType.NUMERIC);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final BigDecimal[] values = {new BigDecimal("1234"),};

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
        final BigDecimal[] expected = {null, new BigDecimal("12.34"),};

        lenient().when(mockedResultSet.getBigDecimal(2))
                .thenReturn(expected[1]);

        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];

            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final Object actualValue =
                        dataType.getSqlValue(i + 1, mockedResultSet);
                assertThat(actualValue).as("value " + j)
                        .isEqualTo(expectedValue);
            }
        }
    }
}
