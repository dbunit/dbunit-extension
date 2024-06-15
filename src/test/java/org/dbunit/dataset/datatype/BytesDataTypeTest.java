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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.dbunit.dataset.ITable;
import org.dbunit.testutil.FileAsserts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@ExtendWith(MockitoExtension.class)
class BytesDataTypeTest extends AbstractDataTypeTest
{

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet mockedResultSet;

    private final static DataType[] TYPES =
            {DataType.BINARY, DataType.VARBINARY, DataType.LONGVARBINARY,
            // DataType.BLOB,
            };

    @Override
    @Test
    public void testToString() throws Exception
    {
        final String[] expected = {"BINARY", "VARBINARY", "LONGVARBINARY",
                // "BLOB",
        };

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
                    .isEqualTo(byte[].class);
        }
    }

    @Override
    @Test
    public void testIsNumber() throws Exception
    {
        for (int i = 0; i < TYPES.length; i++)
        {
            assertThat(TYPES[i].isNumber()).as("is number").isFalse();
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
        final Object[] values = {null, "", "YWJjZA==",
                new byte[] {0, 1, 2, 3, 4, 5},
                "[text]This is text with UTF-8 (the default) characters >>àéç<<",
                "[text UTF-8]This is text with UTF-8 (the default) characters >>àéç<<",
                "[text]c27ccbf5-6ca1-4bdd-8cb0-bacfea6a5a8b",
                "[base64]VGhpcyBpcyBhIHRlc3QgZm9yIGJhc2U2NC4K=="};

        final byte[][] expected = {null, new byte[0],
                new byte[] {'a', 'b', 'c', 'd'}, new byte[] {0, 1, 2, 3, 4, 5},
                values[4].toString().replaceAll("\\[.*?\\]", "")
                        .getBytes("UTF-8"),
                values[5].toString().replaceAll("\\[.*?\\]", "")
                        .getBytes("UTF-8"),
                values[6].toString().replaceAll("\\[.*?\\]", "").getBytes(
                        "UTF-8"),
                "This is a test for base64.\n".getBytes(),};

        assertThat(expected).as("actual vs expected count")
                .hasNumberOfRows(values.length);

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                final byte[] actual = (byte[]) TYPES[i].typeCast(values[j]);
                assertThat(actual).as("typecast " + j).isEqualTo(expected[j]);
            }
        }
    }

    @Test
    void testTypeCastFileName() throws Exception
    {
        final File file = new File("LICENSE.txt");

        final Object[] values = {"[file]" + file.toString(), file.toString(),
                file.getAbsolutePath(), file.toURI().toURL().toString(), file,
                file.toURI().toURL(), "[url]" + file.toURI().toURL(),};

        assertThat(file).as("exists").exists();

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                final byte[] actual = (byte[]) TYPES[i].typeCast(values[j]);
                FileAsserts.assertEquals(new ByteArrayInputStream(actual),
                        file);
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
        final Object[] values = {new Object(), Integer.valueOf(1234),};

        for (int i = 0; i < TYPES.length; i++)
        {
            for (int j = 0; j < values.length; j++)
            {
                final int id = i;
                final int jd = j;
                assertThrows(TypeCastException.class,
                        () -> TYPES[id].typeCast(values[jd]),
                        "Should throw TypeCastException: " + values[jd]);
            }
        }
    }

    @Override
    @Test
    public void testCompareEquals() throws Exception
    {
        final Object[] values1 =
                {null, "", "YWJjZA==", new byte[] {0, 1, 2, 3, 4, 5},};

        final byte[][] values2 =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                        new byte[] {0, 1, 2, 3, 4, 5},};

        assertThat(values2).as("values count").hasNumberOfRows(values1.length);

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
        final Object[] values1 = {new Object(), new java.util.Date()};
        final Object[] values2 = {null, null};

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
        final Object[] less = {null, new byte[] {'a', 'a', 'c', 'd'},
                new byte[] {0, 1, 2, 3, 4, 5},};
        final Object[] greater = {new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                new byte[] {0, 1, 2, 3, 4, 5, 6},};

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
        final int[] sqlTypes =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY,
                // Types.BLOB,
                };

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
        assertThat(DataType.forObject(new byte[0]))
                .isEqualTo(DataType.VARBINARY);
    }

    @Override
    @Test
    public void testAsString() throws Exception
    {
        final byte[][] values = {new byte[0], new byte[] {'a', 'b', 'c', 'd'},};

        final String[] expected = {"", "YWJjZA==",};

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
        final byte[][] expected =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},
                        new byte[] {0, 1, 2, 3, 4, 5},};

        when(mockedResultSet.getBytes(1)).thenReturn(expected[0]);
        when(mockedResultSet.getBytes(2)).thenReturn(expected[1]);
        when(mockedResultSet.getBytes(3)).thenReturn(expected[2]);
        when(mockedResultSet.getBytes(4)).thenReturn(expected[3]);
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

    @Test
    void testSetSqlValue() throws Exception
    {

        final Object[] expected =
                {null, new byte[0], new byte[] {'a', 'b', 'c', 'd'},};

        final int[] expectedSqlTypesForDataType =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY};

        for (int i = 0; i < expected.length; i++)
        {
            final Object expectedValue = expected[i];

            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final int expectedSqlType = expectedSqlTypesForDataType[j];

                dataType.setSqlValue(expectedValue, 1, preparedStatement);
                // Check the results immediately
                verify(preparedStatement, times(1)).setObject(1, expectedValue,
                        expectedSqlType);
            }
        }
    }
}
