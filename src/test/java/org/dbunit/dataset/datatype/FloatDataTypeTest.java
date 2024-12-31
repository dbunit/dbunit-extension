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
public class FloatDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.REAL;

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("REAL");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class").isEqualTo(Float.class);
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        assertThat(THIS_TYPE.isNumber()).as("is number").isEqualTo(true);
    }

    @Override
    @Test
    public void testIsDateTime() throws Exception
    {
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isEqualTo(false);
    }

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, "5.555", Double.valueOf(Float.MAX_VALUE),
                Double.valueOf(Float.MIN_VALUE), "-7500", "2.34E3",
                Double.valueOf(0.666), Double.valueOf(5.49879), "-99.9",
                new BigDecimal((double) 1234),};

        final Float[] expected = {null, 5.555F, Float.MAX_VALUE,
                Float.MIN_VALUE, -7500F, Float.valueOf("2.34E3"), 0.666F,
                5.49879F, -99.9F, 1234F,};

        assertThat(expected.length).as("actual vs expected count")
                .isEqualTo(values.length);

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
        assertThat(THIS_TYPE.typeCast(ITable.NO_VALUE)).as("typecast")
                .isEqualTo(null);
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
        final Object[] values1 =
                {null, "5.555", Double.valueOf(Float.MAX_VALUE),
                        Double.valueOf(Float.MIN_VALUE), "-7500", "2.34E3",
                        Double.valueOf(0.666), Double.valueOf(5.49879), "-99.9",
                        new BigDecimal((double) 1234),};

        final Float[] values2 = {null, 5.555F, Float.MAX_VALUE, Float.MIN_VALUE,
                -7500F, Float.valueOf("2.34E3"), 0.666F, 5.49879F, -99.9F,
                1234F,};

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
        final Object[] values1 = {new Object(), "bla", new java.util.Date()};
        final Object[] values2 = {null, null, null};

        assertThat(values2).as("values count").hasSize(values1.length);

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
        final Object[] less = {null, "-7500", Double.valueOf(Float.MIN_VALUE),};

        final Object[] greater =
                {"0", "5.555", Float.valueOf(Float.MAX_VALUE),};

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
        assertThat(DataType.forSqlType(Types.REAL)).isEqualTo(THIS_TYPE);
        assertThat(DataType.forSqlTypeName(THIS_TYPE.toString()))
                .as("forSqlTypeName").isEqualTo(THIS_TYPE);
        assertThat(THIS_TYPE.getSqlType()).isEqualTo(Types.REAL);
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(1234F)).isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Object[] values = {1234F, 12.34F,};

        final String[] expected = {"1234.0", "12.34",};

        assertThat(expected.length).as("actual vs expected count")
                .isEqualTo(values.length);

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
        final Float[] expected = {null, 5.555F, Float.MAX_VALUE,
                Float.MIN_VALUE, -7500F, Float.valueOf("2.34E3"), 0.666F,
                5.49879F, -99.9F, 1234F,};

        lenient().when(mockedResultSet.getFloat(2)).thenReturn(expected[1]);
        lenient().when(mockedResultSet.getFloat(3)).thenReturn(expected[2]);
        lenient().when(mockedResultSet.getFloat(4)).thenReturn(expected[3]);
        lenient().when(mockedResultSet.getFloat(5)).thenReturn(expected[4]);
        lenient().when(mockedResultSet.getFloat(6)).thenReturn(expected[5]);
        lenient().when(mockedResultSet.getFloat(7)).thenReturn(expected[6]);
        lenient().when(mockedResultSet.getFloat(8)).thenReturn(expected[7]);
        lenient().when(mockedResultSet.getFloat(9)).thenReturn(expected[8]);
        lenient().when(mockedResultSet.getFloat(10)).thenReturn(expected[9]);
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
}
