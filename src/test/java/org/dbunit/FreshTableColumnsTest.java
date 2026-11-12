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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.dbunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FreshTableColumns}.
 *
 * @since 3.6.0
 */
class FreshTableColumnsTest
{
    @Test
    void testRead_existingTable_returnsItsColumnsInOrder() throws Exception
    {
        final IDatabaseConnection connection = InMemoryDatabaseConnection.create();
        try
        {
            try (Statement statement = connection.getConnection().createStatement())
            {
                statement.execute("CREATE TABLE T (ID INT PRIMARY KEY, NAME VARCHAR(20))");
            }

            final Column[] columns = new FreshTableColumns(connection).read("T");

            assertThat(columns).extracting(Column::getColumnName)
                    .as("The columns are read in the table's order.")
                    .containsExactly("ID", "NAME");
        } finally
        {
            connection.close();
        }
    }

    @Test
    void testRead_connectionWithNoJdbcConnection_returnsNull() throws Exception
    {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConnection()).thenReturn(null);

        final Column[] columns = new FreshTableColumns(connection).read("T");

        assertThat(columns).as("With no JDBC connection to read through, there are no columns.")
                .isNull();
    }

    @Test
    void testRead_selectFails_throwsDataSetExceptionNamingTheTableWithTheDriverCause()
            throws Exception
    {
        final SQLException driverFailure = new SQLException("relation does not exist", "42P01");
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.createStatement()).thenReturn(mock(Statement.class));
        final Statement statement = jdbcConnection.createStatement();
        when(statement.executeQuery("select * from T where 1=0")).thenThrow(driverFailure);
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(connection.getConfig()).thenReturn(new DatabaseConfig());

        assertThatThrownBy(() -> new FreshTableColumns(connection).read("T"))
                .as("A failed select is reported where it happened, not swallowed, as it can"
                        + " leave the owner's transaction unusable.")
                .isInstanceOf(DataSetException.class)
                .hasMessageContaining("'T'")
                .hasCause(driverFailure);
    }
}
