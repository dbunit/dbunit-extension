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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AbstractDatabaseConnection}, run against a {@link DatabaseConnection}
 * wrapping a Mockito-mocked JDBC {@link Connection} so the emitted SQL can be captured without a
 * real database.
 */
class AbstractDatabaseConnectionTest
{
    @Test
    void testGetRowCount_withEscapePatternConfigured_escapesQualifiedTableName()
            throws Exception
    {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(6);
        final Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.createStatement()).thenReturn(statement);

        final IDatabaseConnection connection = new DatabaseConnection(jdbcConnection);
        connection.getConfig().setProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN, "\"?\"");

        final int rowCount = connection.getRowCount("MY_TABLE");

        assertThat(rowCount).as("Row count read from the mocked result set.").isEqualTo(6);
        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement).executeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .as("The emitted SQL must contain the escaped, quoted table name.")
                .contains("\"MY_TABLE\"");
    }

    @Test
    void testGetRowCount_withNoEscapePatternConfigured_usesPlainQualifiedTableName()
            throws Exception
    {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(0);
        final Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        final Connection jdbcConnection = mock(Connection.class);
        when(jdbcConnection.createStatement()).thenReturn(statement);

        final IDatabaseConnection connection = new DatabaseConnection(jdbcConnection);

        connection.getRowCount("MY_TABLE");

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement).executeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .as("Without a configured escape pattern the table name must stay unescaped.")
                .contains("select count(*) from MY_TABLE")
                .doesNotContain("\"MY_TABLE\"");
    }
}
