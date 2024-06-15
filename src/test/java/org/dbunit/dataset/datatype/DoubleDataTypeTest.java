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
public class DoubleDataTypeTest extends AbstractDataTypeTest
{

    @Mock
    private ResultSet mockedResultSet;

    private final static DataType[] TYPES = {DataType.FLOAT, DataType.DOUBLE};

    @Override
    @Test
    public void testToString() throws Exception
    {
        final String[] expected = {"FLOAT", "DOUBLE"};

        assertThat(TYPES).as("type count").hasSize(expected.length);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i]).as("name").hasToString(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i].getTypeClass()).as("class")
                    .isEqualTo(Double.class);
        }
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i].isNumber()).as("is number").isTrue();
        }
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i].isDateTime()).as("is date/time").isFalse();
        }
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, "5.555", Float.valueOf(Float.MAX_VALUE),
                Double.valueOf(Double.MIN_VALUE), "-7500", "2.34E23",
                Double.valueOf(0.666), Double.valueOf(5.49879), "-99.9",
                new BigDecimal((double) 1234),};

        final Double[] expected =
                {null, Double.valueOf(5.555), Double.valueOf(Float.MAX_VALUE),
                        Double.valueOf(Double.MIN_VALUE), Double.valueOf(-7500),
                        Double.valueOf("2.34E23"), Double.valueOf(0.666),
                        Double.valueOf(5.49879), Double.valueOf(-99.9),
                        Double.valueOf(1234),};

        assertThat(values).as("actual vs expected count")
                .hasSize(expected.length);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                assertThat(TYPES[i].typeCast(values[j])).as("typecast " + j)
                        .isEqualTo(expected[j]);
            }
        }
    }

    @Override
    @Test
    public void testTypeCastNone() throws Exception
    {
        for (int i = 0; i < TYPES.length; i++)
        {
            final DataType type = TYPES[i];
            assertThat(type.typeCast(ITable.NO_VALUE)).as("typecast " + type)
                    .isNull();
        }
    }

    @Override
    @Test
    public void testTypeCastInvalid() throws Exception
    {
        final Object[] values = {new Object(), "bla", new java.util.Date()};

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
        final Object[] values1 = {null, "5.555", Float.valueOf(Float.MAX_VALUE),
                Double.valueOf(Double.MIN_VALUE), "-7500", "2.34E23",
                Double.valueOf(0.666), Double.valueOf(5.49879), "-99.9",
                new BigDecimal((double) 1234), "123",};

        final Object[] values2 =
                {null, Double.valueOf(5.555), Double.valueOf(Float.MAX_VALUE),
                        Double.valueOf(Double.MIN_VALUE), Double.valueOf(-7500),
                        Double.valueOf("2.34E23"), Double.valueOf(0.666),
                        Double.valueOf(5.49879), Double.valueOf(-99.9),
                        Double.valueOf(1234), Double.valueOf(123.0)};

        assertThat(values2).as("values count").hasSize(values1.length);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values1.length; j++)
            {
                assertThat(TYPES[i].compare(values1[j], values2[j]))
                        .as("compare1 " + j).isZero();
                assertThat(TYPES[i].compare(values2[j], values1[j]))
                        .as("compare2 " + j).isZero();
            }
        }
    }

    @Override
    @Test
    public void testCompareInvalid() throws Exception
    {
        final Object[] values1 = {new Object(), "bla", new java.util.Date()};
        final Object[] values2 = {null, null, null};

        assertThat(values2).as("values count").hasSize(values1.length);

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
        final Object[] less = {null, "-7500", Double.valueOf(Float.MIN_VALUE),};

        final Object[] greater =
                {"0", "5.555", Float.valueOf(Float.MAX_VALUE),};

        assertThat(greater).as("values count").hasSize(less.length);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < less.length; j++)
            {
                assertThat(TYPES[i].compare(less[j], greater[j]))
                        .as("less " + j).isNegative();
                assertThat(TYPES[i].compare(greater[j], less[j]))
                        .as("greater " + j).isPositive();
            }
        }
    }

    @Override
    @Test
    public void testSqlType() throws Exception
    {
        final int[] sqlTypes = {Types.FLOAT, Types.DOUBLE};

        assertThat(TYPES).as("count").hasSize(sqlTypes.length);
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
        assertThat(DataType.forObject(Double.valueOf(1234)))
                .isEqualTo(DataType.DOUBLE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Object[] values =
                {Double.valueOf("1234"), Double.valueOf("12.34"),};

        final String[] expected = {"1234.0", "12.34",};

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
        final Double[] expected =
                {null, Double.valueOf(5.555), Double.valueOf(Float.MAX_VALUE),
                        Double.valueOf(Double.MIN_VALUE), Double.valueOf(-7500),
                        Double.valueOf("2.34E23"), Double.valueOf(0.666),
                        Double.valueOf(5.49879), Double.valueOf(-99.9),
                        Double.valueOf(1234),};
        lenient().when(mockedResultSet.getDouble(2)).thenReturn(expected[1]);
        lenient().when(mockedResultSet.getDouble(3)).thenReturn(expected[2]);
        lenient().when(mockedResultSet.getDouble(4)).thenReturn(expected[3]);
        lenient().when(mockedResultSet.getDouble(5)).thenReturn(expected[4]);
        lenient().when(mockedResultSet.getDouble(6)).thenReturn(expected[5]);
        lenient().when(mockedResultSet.getDouble(7)).thenReturn(expected[6]);
        lenient().when(mockedResultSet.getDouble(8)).thenReturn(expected[7]);
        lenient().when(mockedResultSet.getDouble(9)).thenReturn(expected[8]);
        lenient().when(mockedResultSet.getDouble(10)).thenReturn(expected[9]);
        lenient().when(mockedResultSet.wasNull()).thenReturn(true)
                .thenReturn(true).thenReturn(false);
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
