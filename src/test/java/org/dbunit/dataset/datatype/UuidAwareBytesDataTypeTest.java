/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2008, DbUnit.org
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Timur Strekalov
 */
@ExtendWith(MockitoExtension.class)
class UuidAwareBytesDataTypeTest extends BytesDataTypeTest
{

    private final static DataType[] TYPES =
            {DataType.BINARY, DataType.VARBINARY, DataType.LONGVARBINARY};

    @Mock
    private PreparedStatement preparedStatement;

    @Override
    @Test
    public void testTypeCast() throws Exception
    {
        final Object[] values =
                {null, "uuid'2aad615a-d8e1-11e2-b8ed-50e549c9b654'"};

        final byte[][] expected = {null,
                new byte[] {(byte) 0x2a, (byte) 0xad, (byte) 0x61, (byte) 0x5a,
                        (byte) 0xd8, (byte) 0xe1, (byte) 0x11, (byte) 0xe2,
                        (byte) 0xb8, (byte) 0xed, (byte) 0x50, (byte) 0xe5,
                        (byte) 0x49, (byte) 0xc9, (byte) 0xb6, (byte) 0x54}};

        assertThat(values).as("actual vs expected count")
                .hasSameSizeAs(expected);

        for (final DataType element : TYPES)
        {
            for (int j = 0; j < values.length; j++)
            {
                final byte[] actual = (byte[]) element.typeCast(values[j]);
                assertThat(actual).as("typecast " + j).isEqualTo(expected[j]);
            }
        }
    }

    @Test
    void testCompareEqualsUuidAware() throws Exception
    {
        final Object[] values1 =
                {null, "uuid'2aad615a-d8e1-11e2-b8ed-50e549c9b654'"};

        final byte[][] values2 = {null,
                new byte[] {(byte) 0x2a, (byte) 0xad, (byte) 0x61, (byte) 0x5a,
                        (byte) 0xd8, (byte) 0xe1, (byte) 0x11, (byte) 0xe2,
                        (byte) 0xb8, (byte) 0xed, (byte) 0x50, (byte) 0xe5,
                        (byte) 0x49, (byte) 0xc9, (byte) 0xb6, (byte) 0x54}};

        assertThat(values1).as("values count").hasSameSizeAs(values2);

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

    @Test
    void testSetSqlValueWithUuid() throws Exception
    {

        final String[] given = {"uuid'2aad615a-d8e1-11e2-b8ed-50e549c9b654'"};

        final byte[][] expected = {new byte[] {(byte) 0x2a, (byte) 0xad,
                (byte) 0x61, (byte) 0x5a, (byte) 0xd8, (byte) 0xe1, (byte) 0x11,
                (byte) 0xe2, (byte) 0xb8, (byte) 0xed, (byte) 0x50, (byte) 0xe5,
                (byte) 0x49, (byte) 0xc9, (byte) 0xb6, (byte) 0x54}};

        final int[] expectedSqlTypesForDataType =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY};

        for (int i = 0; i < expected.length; i++)
        {
            final String givenValue = given[i];
            final byte[] expectedValue = expected[i];

            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final int expectedSqlType = expectedSqlTypesForDataType[j];

                dataType.setSqlValue(givenValue, 1, preparedStatement);

                verify(preparedStatement, times(1)).setObject(1, expectedValue,
                        expectedSqlType);
            }
        }
    }

    /**
     * Historically, the wrongly formatted uuids of this test would be recorded
     * as 'null' in the database. However, now that the
     * {@link org.dbunit.dataset.datatype.BytesDataType} class attempts to save
     * any data it finds, these wrongly formatted uuids now do find their way
     * into the database, as any other text.
     *
     * @throws Exception
     */
    @Test
    void testSetSqlValueWithSomethingThatLooksLikeUuidButIsNot()
            throws Exception
    {

        final String[] given = {"2aad615a-d8e1-11e2-b8ed-50e549c9b654",
                "uuid'2aad615a-d8e1-11e2-b8ed-50e549c9b65'"};

        final byte[][] expected = {given[0].getBytes(), given[1].getBytes()};

        final int[] expectedSqlTypesForDataType =
                {Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY};

        for (int i = 0; i < expected.length; i++)
        {
            final String givenValue = given[i];
            final Object expectedValue = expected[i];

            for (int j = 0; j < TYPES.length; j++)
            {
                final DataType dataType = TYPES[j];
                final int expectedSqlType = expectedSqlTypesForDataType[j];

                dataType.setSqlValue(givenValue, 1, preparedStatement);
                verify(preparedStatement, times(1)).setObject(1, expectedValue,
                        expectedSqlType);
            }
        }
    }
}
