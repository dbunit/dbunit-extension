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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TransactionOperation} using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class TransactionOperationTest
{
    @Mock
    private DatabaseOperation delegate;

    @Mock
    private IDatabaseConnection connection;

    @Mock
    private Connection jdbcConnection;

    @Mock
    private IDataSet dataSet;

    @Test
    void testExecute_whenDelegateSucceeds_commitsThenRestoresAutoCommit()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);

        new TransactionOperation(delegate).execute(connection, dataSet);

        final InOrder inOrder = Mockito.inOrder(jdbcConnection, delegate);
        inOrder.verify(jdbcConnection).setAutoCommit(false);
        inOrder.verify(delegate).execute(connection, dataSet);
        inOrder.verify(jdbcConnection).commit();
        inOrder.verify(jdbcConnection).setAutoCommit(true);
        verify(jdbcConnection, never()).rollback();
    }

    @Test
    void testExecute_whenDelegateThrowsError_rollsBackWithoutCommittingAndRestoresAutoCommit()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final AssertionError failure = new AssertionError("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("An Error propagating from the delegate operation must"
                        + " still be rethrown unchanged, not swallowed or"
                        + " wrapped.")
                .isSameAs(failure);
        verify(jdbcConnection).rollback();
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection)
                .setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsErrorAndRollbackAlsoFails_suppressesRollbackFailureAndRethrowsOriginalError()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final AssertionError failure = new AssertionError("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);
        final SQLException rollbackFailure = new SQLException("rollback boom");
        doThrow(rollbackFailure).when(jdbcConnection).rollback();

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The original Error must remain the thrown exception even"
                        + " when rollback also fails.")
                .isSameAs(failure);
        assertThat(failure.getSuppressed())
                .as("The rollback failure must be attached to the original"
                        + " Error as suppressed instead of replacing it.")
                .containsExactly(rollbackFailure);
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsSQLException_rollsBackAndRethrows()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final SQLException failure = new SQLException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The pre-existing SQLException rollback path must still"
                        + " roll back and rethrow unchanged.")
                .isSameAs(failure);
        verify(jdbcConnection).rollback();
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsSQLExceptionAndRollbackAlsoFails_suppressesRollbackFailureAndRethrowsOriginalException()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final SQLException failure = new SQLException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);
        final SQLException rollbackFailure = new SQLException("rollback boom");
        doThrow(rollbackFailure).when(jdbcConnection).rollback();

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The original SQLException must remain the thrown"
                        + " exception even when rollback also fails.")
                .isSameAs(failure);
        assertThat(failure.getSuppressed())
                .as("The rollback failure must be attached to the original"
                        + " SQLException as suppressed instead of replacing"
                        + " it.")
                .containsExactly(rollbackFailure);
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsDatabaseUnitException_rollsBackAndRethrows()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final DatabaseUnitException failure = new DatabaseUnitException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The DatabaseUnitException rollback path must roll back"
                        + " and rethrow unchanged.")
                .isSameAs(failure);
        verify(jdbcConnection).rollback();
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsDatabaseUnitExceptionAndRollbackAlsoFails_suppressesRollbackFailureAndRethrowsOriginalException()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final DatabaseUnitException failure = new DatabaseUnitException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);
        final SQLException rollbackFailure = new SQLException("rollback boom");
        doThrow(rollbackFailure).when(jdbcConnection).rollback();

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The original DatabaseUnitException must remain the"
                        + " thrown exception even when rollback also fails.")
                .isSameAs(failure);
        assertThat(failure.getSuppressed())
                .as("The rollback failure must be attached to the original"
                        + " DatabaseUnitException as suppressed instead of"
                        + " replacing it.")
                .containsExactly(rollbackFailure);
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsRuntimeException_rollsBackAndRethrows()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The RuntimeException rollback path must roll back and"
                        + " rethrow unchanged.")
                .isSameAs(failure);
        verify(jdbcConnection).rollback();
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void testExecute_whenDelegateThrowsRuntimeExceptionAndRollbackAlsoFails_suppressesRollbackFailureAndRethrowsOriginalException()
            throws Exception
    {
        when(connection.getConnection()).thenReturn(jdbcConnection);
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        final RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(delegate).execute(connection, dataSet);
        final SQLException rollbackFailure = new SQLException("rollback boom");
        doThrow(rollbackFailure).when(jdbcConnection).rollback();

        final TransactionOperation operation = new TransactionOperation(delegate);
        final Throwable thrown =
                catchThrowable(() -> operation.execute(connection, dataSet));

        assertThat(thrown)
                .as("The original RuntimeException must remain the thrown"
                        + " exception even when rollback also fails.")
                .isSameAs(failure);
        assertThat(failure.getSuppressed())
                .as("The rollback failure must be attached to the original"
                        + " RuntimeException as suppressed instead of"
                        + " replacing it.")
                .containsExactly(rollbackFailure);
        verify(jdbcConnection, never()).commit();
        verify(jdbcConnection).setAutoCommit(true);
    }
}
