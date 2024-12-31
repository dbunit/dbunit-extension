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
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
public class StringDataTypeTest extends AbstractDataTypeTest
{
    @Mock
    private ResultSet mockedResultSet;

    private final static DataType[] TYPES =
            {DataType.CHAR, DataType.VARCHAR, DataType.LONGVARCHAR,
            // DataType.CLOB,
            };

    @Override
    @Test
    public void testToString() throws Exception
    {
        final String[] expected = {"CHAR", "VARCHAR", "LONGVARCHAR",
                // "CLOB",
        };

        assertThat(TYPES).as("type count").hasSameSizeAs(expected);
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i].toString()).as("name").isEqualTo(expected[i]);
        }
    }

    @Override
    @Test
    public void testGetTypeClass() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.getTypeClass()).as("class")
                    .isEqualTo(String.class);
        }
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        for (final DataType element : TYPES)
        {
            assertThat(element.isNumber()).as("is number").isFalse();
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
        final Object[] values = {null, "bla", new java.sql.Date(1234),
                new java.sql.Time(1234), new java.sql.Timestamp(1234),
                Boolean.TRUE, Integer.valueOf(1234), Long.valueOf(1234),
                Double.valueOf(12.34), new byte[] {'a', 'b', 'c', 'd'},};
        final String[] expected =
                {null, "bla", new java.sql.Date(1234).toString(),
                        new java.sql.Time(1234).toString(),
                        new java.sql.Timestamp(1234).toString(), "true", "1234",
                        "1234", "12.34", "YWJjZA==",};

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

    /**
     * Return a bad clob that throws SQLException on all its operations.
     */
    private Object getBadClob()
    {
        // need to use proxy / reflection to work arround Clob differences
        // in jdk 1.4+
        final java.lang.reflect.InvocationHandler alwaysThrowSqlExceptionHandler =
                new java.lang.reflect.InvocationHandler()
                {
                    @Override
                    public Object invoke(final Object proxy,
                            final java.lang.reflect.Method method,
                            final Object[] args) throws Throwable
                    {
                        if ("toString".equals(method.getName()))
                        {
                            return this.toString();
                        } else if ("equals".equals(method.getName()))
                        {
                            return Boolean.FALSE;
                        }
                        throw new SQLException();
                    }
                };

        return java.lang.reflect.Proxy.newProxyInstance(
                java.sql.Clob.class.getClassLoader(),
                new Class[] {java.sql.Clob.class},
                alwaysThrowSqlExceptionHandler);
    }

    @Override
    @Test
    public void testTypeCastInvalid() throws Exception
    {
        final Object[] values = {new Object()
        {
            @Override
            public String toString()
            {
                return "ABC123";
            }
        }, new Object()
        {
            @Override
            public String toString()
            {
                return "XXXX";
            }
        }, new Object()
        {
            @Override
            public String toString()
            {
                return "X";
            }
        },};

        for (final DataType element : TYPES)
        {
            for (final Object value : values)
            {
                assertThat(value.toString()).isEqualTo(element.typeCast(value));
            }
        }

        final Object badClob = getBadClob();
        for (int i = 0; i < TYPES.length; i++)
        {
            final int id = i;
            assertThrows(TypeCastException.class,
                    () -> TYPES[id].typeCast(badClob),
                    "Should throw TypeCastException");
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 = {null, "bla", new java.sql.Date(1234),
                new java.sql.Time(1234), new java.sql.Timestamp(1234),
                Boolean.TRUE, Integer.valueOf(1234), Long.valueOf(1234),
                Double.valueOf(12.34), new byte[] {'a', 'b', 'c', 'd'},};
        final String[] values2 =
                {null, "bla", new java.sql.Date(1234).toString(),
                        new java.sql.Time(1234).toString(),
                        new java.sql.Timestamp(1234).toString(), "true", "1234",
                        "1234", "12.34", "YWJjZA==",};

        assertThat(values2).as("values count").hasSameSizeAs(values1);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values1.length; j++)
            {
                assertThat(element.compare(values1[j], values2[j]))
                        .as("compare1 " + j).isZero();
                assertThat(element.compare(values2[j], values1[j]))
                        .as("compare2 " + j).isZero();
            }
        }
    }

    @Override
    @Test
    public void testCompareInvalid() throws Exception
    {
        final Object[] values1 = {getBadClob(),};
        final Object[] values2 = {null,};

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
        final Object[] less = {null, "", "abcd", "123",};

        final Object[] greater = {"bla", "bla", "efgh", "1234",};

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
        final int[] sqlTypes = {Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                // Types.CLOB,
        };

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
        assertThat(DataType.forObject("")).isEqualTo(DataType.VARCHAR);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final Object[] values = {"1234",};

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
        final String[] expected = {null, "bla",};

        lenient().when(mockedResultSet.getString(2)).thenReturn(expected[1]);
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

    /**
     * Assert calls ResultSet.getString(columnIndex) before ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        DataType.CHAR.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getString(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
