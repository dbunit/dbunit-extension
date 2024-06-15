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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.FileReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.AbstractDatabaseIT;
import org.dbunit.DatabaseUnitException;
import org.dbunit.TestFeature;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 21, 2002
 */
@ExtendWith(MockitoExtension.class)
class TransactionOperationIT extends AbstractDatabaseIT
{

    @Mock
    private IDatabaseConnection connection;

    @Mock
    private DatabaseOperation mockOperation;

    @Override
    protected boolean runTest(final String testName)
    {
        return environmentHasFeature(TestFeature.TRANSACTION);
    }

    @Test
    void testExecuteCommit() throws Exception
    {
        final String tableName = "TEST_TABLE";
        final Reader in = new FileReader(
                TestUtils.getFile("xml/transactionOperationTest.xml"));
        final IDataSet xmlDataSet = new XmlDataSet(in);
        final Connection jdbcConnection = _connection.getConnection();

        final ITable tableBefore =
                _connection.createDataSet().getTable(tableName);
        assertThat(tableBefore.getRowCount()).as("before row count")
                .isEqualTo(6);
        assertThat(jdbcConnection.getAutoCommit()).as("autocommit before")
                .isTrue();

        DatabaseOperation operation = new CompositeOperation(
                DatabaseOperation.DELETE_ALL, DatabaseOperation.INSERT);
        operation = new TransactionOperation(operation);
        operation.execute(_connection, xmlDataSet);

        // snapshot after operation
        final ITable tableAfter =
                _connection.createDataSet().getTable(tableName);
        assertThat(tableAfter.getRowCount()).as("after row count").isEqualTo(1);
        assertThat(jdbcConnection.getAutoCommit()).as("autocommit after")
                .isTrue();
    }

    @Test
    void testExclusiveTransaction() throws Exception
    {
        final String tableName = "TEST_TABLE";
        final Reader in = new FileReader(
                TestUtils.getFile("xml/transactionOperationTest.xml"));
        final IDataSet xmlDataSet = new XmlDataSet(in);
        final Connection jdbcConnection = _connection.getConnection();

        jdbcConnection.setAutoCommit(false);

        // before operation
        assertThat(jdbcConnection.getAutoCommit()).as("autocommit before")
                .isFalse();
        final ITable tableBefore =
                _connection.createDataSet().getTable(tableName);
        assertThat(tableBefore.getRowCount()).as("before exclusive")
                .isEqualTo(6);

        assertThrows(ExclusiveTransactionException.class, () -> {
            // try with exclusive transaction
            final DatabaseOperation operation =
                    new TransactionOperation(DatabaseOperation.DELETE);
            operation.execute(_connection, xmlDataSet);
        }, "Should throw ExclusiveTransactionException");
        jdbcConnection.setAutoCommit(true);

        // after operation
        final ITable tableAfter =
                _connection.createDataSet().getTable(tableName);
        assertThat(tableAfter.getRowCount()).as("after").isEqualTo(6);
    }

    @Test
    void testExecuteRollback() throws Exception
    {
        final String tableName = "TEST_TABLE";
        final Reader in = new FileReader(
                TestUtils.getFile("xml/transactionOperationTest.xml"));
        final IDataSet xmlDataSet = new XmlDataSet(in);
        final Exception[] exceptions = new Exception[] {new SQLException(),
                new DatabaseUnitException(), new RuntimeException(),};
        final Connection jdbcConnection = _connection.getConnection();

        for (int i = 0; i < exceptions.length; i++)
        {

            // snapshot before operation
            final ITable tableBefore =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableBefore.getRowCount()).as("before row count")
                    .isEqualTo(6);
            assertThat(jdbcConnection.getAutoCommit()).as("autocommit before")
                    .isTrue();

            doThrow(exceptions[i]).when(mockOperation).execute(any(), any());
            assertThrows(Exception.class, () -> {
                DatabaseOperation operation = new CompositeOperation(
                        DatabaseOperation.DELETE_ALL, mockOperation);
                operation = new TransactionOperation(operation);
                operation.execute(_connection, xmlDataSet);
            }, "Should throw an exception");

            verify(mockOperation, atMost(3)).execute(any(), any());

            // snapshot after operation
            final ITable tableAfter =
                    _connection.createDataSet().getTable(tableName);
            assertThat(tableAfter.getRowCount()).as("after row count")
                    .isEqualTo(6);
            assertThat(jdbcConnection.getAutoCommit()).as("autocommit after")
                    .isTrue();

        }
    }
}
