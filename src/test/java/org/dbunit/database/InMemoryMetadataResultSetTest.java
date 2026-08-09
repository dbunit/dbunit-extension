/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2026, DbUnit.org
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryMetadataResultSet}, focused on {@code merge()}'s handling of
 * closing multiple source result sets - {@code filter()}'s single-source row-selection behavior
 * is already exercised via {@link org.dbunit.ext.h2.H2MetadataHandlerTest}.
 *
 * @since 3.4.1
 */
class InMemoryMetadataResultSetTest
{
    @Test
    void testMerge_withOneSourceFailingToClose_stillClosesTheOthersAndRethrowsTheFirstFailure()
            throws SQLException
    {
        final ResultSet first = mockEmptyResultSet();
        final ResultSet second = mockEmptyResultSet();
        final SQLException closeFailure = new SQLException("boom");
        doThrow(closeFailure).when(second).close();
        final ResultSet third = mockEmptyResultSet();

        assertThatThrownBy(
                () -> InMemoryMetadataResultSet.merge(Arrays.asList(first, second, third)))
                        .as("the failure closing the middle source must still propagate.")
                        .isSameAs(closeFailure);

        verify(first).close();
        verify(second).close();
        verify(third).close();
    }

    private static ResultSet mockEmptyResultSet() throws SQLException
    {
        final ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(0);
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenReturn(false);
        return resultSet;
    }
}
