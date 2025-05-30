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
import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 */
@ExtendWith(MockitoExtension.class)
public class BooleanDataTypeTest extends AbstractDataTypeTest
{
    private final static DataType THIS_TYPE = DataType.BOOLEAN;

    @Mock
    private ResultSet mockedResultSet;

    @Override
    @Test
    public void testToString() throws Exception
    {
        assertThat(THIS_TYPE).as("name").hasToString("BOOLEAN");
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        assertThat(THIS_TYPE.getTypeClass()).as("class")
                .isEqualTo(Boolean.class);
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
        assertThat(THIS_TYPE.isDateTime()).as("is date/time").isFalse();
    }

    @Override
    public void testTypeCast() throws Exception
    {
        final Object[] values = {null, "1", // Strings
                "0", "true", "false", "4894358", // TODO should it be possible
                                                 // to cast this into a Boolean?
                Boolean.TRUE, // Booleans
                Boolean.FALSE, Integer.valueOf(1), // Numbers
                Integer.valueOf(0), Integer.valueOf(123), // TODO should it be
                                                          // possible to cast
                                                          // this into a
                                                          // Boolean?
                new BigDecimal("20.53"), // TODO should it be possible to cast
                                         // this into a Boolean?
        };
        final Boolean[] expected = {null, Boolean.TRUE, // Strings
                Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE,
                Boolean.TRUE, // Booleans
                Boolean.FALSE, Boolean.TRUE, // Numbers
                Boolean.FALSE, Boolean.TRUE, Boolean.TRUE,};

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
        final Object[] values = {"bla"};

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
        final Object[] values1 = {null, "1", "0", Boolean.TRUE, Boolean.FALSE,};
        final Object[] values2 =
                {null, Boolean.TRUE, Boolean.FALSE, "true", "false",};

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
        final Object[] values1 = {"bla", Boolean.FALSE,};
        final Object[] values2 = {Boolean.TRUE, "bla",};

        assertThat(values2).as("values count").hasSize(values1.length);

        for (int i = 0; i < values1.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values1[id], values2[id]),
                    "Should have throw TypeCastException");

            assertThrows(TypeCastException.class,
                    () -> THIS_TYPE.compare(values2[id], values1[id]),
                    "Should have throw TypeCastException");
        }
    }

    @Override
    @Test
    public void testCompareDifferent() throws Exception
    {
        final Object[] less = {null, null, Boolean.FALSE,};
        final Object[] greater = {Boolean.TRUE, Boolean.FALSE, Boolean.TRUE,};

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
        assertThat(DataType.forSqlType(Types.BOOLEAN)).as("forSqlType")
                .isEqualTo(THIS_TYPE);
        assertThat(DataType.forSqlTypeName(THIS_TYPE.toString()))
                .as("forSqlTypeName").isEqualTo(THIS_TYPE);
        assertThat(THIS_TYPE.getSqlType()).as("getSqlType")
                .isEqualTo(Types.BOOLEAN);
    }

    @Override
    @Test
    public void testForObject() throws Exception
    {
        assertThat(DataType.forObject(Boolean.TRUE)).isEqualTo(THIS_TYPE);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Boolean[] values = {Boolean.TRUE, Boolean.FALSE,};

        final String[] expected = {"true", "false",};

        assertThat(expected).as("actual vs expected count")
                .hasSize(values.length);

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
        final Object[] expected = {null, Boolean.TRUE, Boolean.FALSE,};
        // First invocation we want to say it was null using lenient to ignore
        // the first call
        lenient().when(mockedResultSet.wasNull()).thenReturn(true)
                .thenReturn(false);
        lenient().when(mockedResultSet.getBoolean(2))
                .thenReturn((boolean) expected[1]);
        lenient().when(mockedResultSet.getBoolean(3))
                .thenReturn((boolean) expected[2]);

        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];
            final Object actualValue =
                    THIS_TYPE.getSqlValue(i + 1, mockedResultSet);
            assertThat(actualValue).as("value").isEqualTo(expectedValue);
        }
    }

    /**
     * Assert calls ResultSet.getBoolean(columnIndex) before
     * ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.BOOLEAN.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getBoolean(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
