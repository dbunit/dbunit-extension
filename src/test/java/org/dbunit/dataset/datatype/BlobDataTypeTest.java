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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author gommma
 * @version $Revision$
 * @since 2.3.0
 */
@ExtendWith(MockitoExtension.class)
class BlobDataTypeTest
{
    private DataType TYPE = DataType.BLOB;

    @Mock
    private PreparedStatement mockedStatement;

    @Test
    void testGetSqlType()
    {
        assertThat(TYPE.getSqlType()).isEqualTo(Types.BLOB);
    }

    @Test
    void testSetSqlValue() throws Exception
    {
        // Create a hsqldb specific blob
        final byte[] byteArray = new byte[] {1, 2, 3, 4, 5, 6};
        final Blob blob = new TestBlob(byteArray);

        TYPE.setSqlValue(blob, 1, mockedStatement);
        verify(mockedStatement, times(1)).setObject(anyInt(), any(Object.class),
                anyInt());
    }

    @Test
    void testAsString() throws Exception
    {
        assertThat(TYPE).as("name").hasToString("BLOB");
    }

    @Test
    void testGetTypeClass() throws Exception
    {
        assertThat(TYPE.getTypeClass()).as("class").isEqualTo(byte[].class);
    }

}
