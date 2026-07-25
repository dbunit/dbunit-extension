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

package org.dbunit.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.MockDatabaseConnection;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.datatype.DataType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link RefreshOperation} using mock objects.
 *
 * @since 3.4.0
 */
class RefreshOperationTest
{
    @Test
    void testExecute_whenRowProcessingAndStatementCloseBothFail_throwsRowFailureWithCloseSuppressed()
            throws Exception
    {
        final PreparedStatement mockPreparedStatement =
                Mockito.mock(PreparedStatement.class);
        final SQLException rowProcessingFailure =
                new SQLException("row processing boom");
        Mockito.when(mockPreparedStatement.execute())
                .thenThrow(rowProcessingFailure);
        final SQLException closeFailure = new SQLException("close boom");
        Mockito.doThrow(closeFailure).when(mockPreparedStatement).close();

        final Connection mockJdbcConnection = Mockito.mock(Connection.class);
        Mockito.when(mockJdbcConnection.prepareStatement(anyString()))
                .thenReturn(mockPreparedStatement);

        final MockDatabaseConnection connection = new MockDatabaseConnection();
        connection.setupConnection(mockJdbcConnection);
        connection.setExpectedCloseCalls(0);

        final Column[] columns = {new Column("ID", DataType.INTEGER),
                new Column("NAME", DataType.VARCHAR)};
        final String[] primaryKeys = {"ID"};
        final ITableMetaData metaData =
                new DefaultTableMetaData("MY_TABLE", columns, primaryKeys);
        final DefaultTable table = new DefaultTable(metaData);
        table.addRow(new Object[] {1, "a"});
        final IDataSet dataSet = new DefaultDataSet(table);
        // getOperationMetaData() cross-checks columns against the
        // connection's own view of the table
        connection.setupDataSet(table);

        final Throwable thrown = catchThrowable(
                () -> DatabaseOperation.REFRESH.execute(connection, dataSet));

        assertThat(thrown)
                .as("The row-processing failure must propagate, not the"
                        + " statement-close failure that happens while"
                        + " cleaning up after it.")
                .isInstanceOf(DatabaseUnitException.class)
                .hasCause(rowProcessingFailure);
        assertThat(thrown.getSuppressed())
                .as("The close failure must be attached as suppressed"
                        + " instead of being lost.")
                .containsExactly(closeFailure);
    }
}
