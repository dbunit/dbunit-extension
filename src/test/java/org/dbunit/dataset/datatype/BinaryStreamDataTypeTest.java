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
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author gommma (gommma AT users.sourceforge.net)
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 2.4.0
 */
@ExtendWith(MockitoExtension.class)
class BinaryStreamDataTypeTest
{
    private final BinaryStreamDataType type =
            new BinaryStreamDataType("BLOB", Types.BLOB);

    @Mock
    private ResultSet mockedResultSet;

    @Test
    void test2Chars() throws Exception
    {
        final String value = "tu";
        final byte[] result = (byte[]) type.typeCast(value);
        // Cannot be converted since it is not valid Base64 because it only has
        // 2 chars
        assertThat(result).isEqualTo(new byte[] {});
    }

    @Test
    void test4Chars() throws Exception
    {
        final String value = "tutu";
        final byte[] result = (byte[]) type.typeCast(value);
        assertThat(result).isEqualTo(new byte[] {-74, -21, 110});
    }

    /**
     * Assert calls ResultSet.getBinaryStream(columnIndex) before
     * ResultSet.wasNull().
     */
    @Test
    public void testGetSqlValueCallOrder()
            throws TypeCastException, SQLException
    {
        final int columnIndex = 1;

        when(mockedResultSet.wasNull()).thenReturn(true);
        type.getSqlValue(columnIndex, mockedResultSet);

        final InOrder inOrder = Mockito.inOrder(mockedResultSet);
        inOrder.verify(mockedResultSet).getBinaryStream(columnIndex);
        inOrder.verify(mockedResultSet).wasNull();
    }
}
