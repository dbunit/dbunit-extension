/*
 * Copyright (C) 2011, Red Hat, Inc.
 * Written by Darryl L. Pierce <dpierce@redhat.com>.
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
package org.dbunit.ext.mssql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.dbunit.dataset.datatype.TypeCastException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * <code>UniqueIdentifierTypeTest</code> ensures that the
 * {@link UniqueIdentifierType} works as expected.
 *
 * @author Darryl L. Pierce <dpierce@redhat.com>
 */
// TODO add tests for setSqlValue(Object, int, PreparedStatement)
@ExtendWith(MockitoExtension.class)
class UniqueIdentifierTypeTest
{
    @Mock
    private ResultSet mockResultSet;

    private UUID existingUuid;
    private UniqueIdentifierType uuidType;

    @BeforeEach
    protected void setUp() throws Exception
    {
        uuidType = new UniqueIdentifierType();

        existingUuid = UUID.randomUUID();
    }

    @AfterEach
    protected void tearDown() throws Exception
    {
        verify(mockResultSet, times(1)).getString(anyInt());
    }

    /**
     * Ensures that an exception occurs if the UUID value is invalid.
     *
     * @throws SQLException
     */
    @Test
    void testGetSqlValueWithBadValue() throws SQLException
    {
        when(mockResultSet.getString(anyInt()))
                .thenAnswer(invocation -> existingUuid.toString() + "Z");

        assertThrows(TypeCastException.class,
                () -> uuidType.getSqlValue(1, mockResultSet),
                "Method should have throw an exception");
    }

    /**
     * Ensures that unmarshalling a UUID value works correctly.
     *
     * @throws SQLException
     * @throws TypeCastException
     */
    @Test
    void testGetValue() throws TypeCastException, SQLException
    {
        when(mockResultSet.getString(anyInt()))
                .thenAnswer(invocation -> existingUuid.toString());

        final UUID result = (UUID) uuidType.getSqlValue(1, mockResultSet);

        assertThat(result).isEqualTo(existingUuid);

        verify(mockResultSet, times(1)).getString(anyInt());
    }
}
