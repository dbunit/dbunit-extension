/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2009, DbUnit.org
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultOperationListener}.
 *
 * @author DbUnit.org
 * @since 2.4.4
 */
class DefaultOperationListenerTest
{
    @Test
    void testConnectionRetrieved_withValidConnection_doesNotThrow() throws SQLException
    {
        final DefaultOperationListener listener = new DefaultOperationListener();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        assertThatCode(() -> listener.connectionRetrieved(connection))
                .as("connectionRetrieved() should not throw any exception.")
                .doesNotThrowAnyException();
    }

    @Test
    void testOperationSetUpFinished_withValidConnection_closesConnection() throws SQLException
    {
        final DefaultOperationListener listener = new DefaultOperationListener();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        listener.operationSetUpFinished(connection);
        verify(connection)
                .close();
    }

    @Test
    void testOperationTearDownFinished_withValidConnection_closesConnection() throws SQLException
    {
        final DefaultOperationListener listener = new DefaultOperationListener();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        listener.operationTearDownFinished(connection);
        verify(connection)
                .close();
    }

    @Test
    void testOperationSetUpFinished_whenConnectionThrowsSqlException_doesNotPropagateException()
            throws SQLException
    {
        final DefaultOperationListener listener = new DefaultOperationListener();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        doThrow(new SQLException("close failed"))
                .when(connection).close();
        assertThatCode(() -> listener.operationSetUpFinished(connection))
                .as("operationSetUpFinished() should swallow SQLException from close().")
                .doesNotThrowAnyException();
    }

    @Test
    void testOperationTearDownFinished_whenConnectionThrowsSqlException_doesNotPropagateException()
            throws SQLException
    {
        final DefaultOperationListener listener = new DefaultOperationListener();
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        doThrow(new SQLException("close failed"))
                .when(connection).close();
        assertThatCode(() -> listener.operationTearDownFinished(connection))
                .as("operationTearDownFinished() should swallow SQLException from close().")
                .doesNotThrowAnyException();
    }
}
