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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.InMemoryDatabaseConnection;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.operation.DatabaseOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for how {@link AbstractDatabaseTester} drives its {@link IOperationListener},
 * run against a real H2 in-memory connection (via {@link InMemoryDatabaseConnection}).
 *
 * @since 3.6.0
 */
class AbstractDatabaseTesterTest
{
    private IDatabaseConnection connection;
    private DefaultDatabaseTester tester;
    private IOperationListener listener;

    @BeforeEach
    void setUp() throws Exception
    {
        connection = InMemoryDatabaseConnection.create();
        tester = new DefaultDatabaseTester(connection);
        tester.setDataSet(new DefaultDataSet());
        listener = mock(IOperationListener.class);
        tester.setOperationListener(listener);
    }

    @AfterEach
    void closeConnection() throws Exception
    {
        connection.close();
    }

    @Test
    void testOnSetup_connectionRetrievedThrows_stillNotifiesOperationSetUpFinished()
            throws Exception
    {
        final IllegalStateException failure = new IllegalStateException("connectionRetrieved");
        doThrow(failure).when(listener).connectionRetrieved(connection);

        assertThatThrownBy(() -> tester.onSetup())
                .as("The listener's own failure must propagate unchanged.")
                .isSameAs(failure);

        verify(listener).operationSetUpFinished(connection);
    }

    @Test
    void testOnTearDown_connectionRetrievedThrows_stillNotifiesOperationTearDownFinished()
            throws Exception
    {
        tester.setTearDownOperation(DatabaseOperation.DELETE_ALL);
        final IllegalStateException failure = new IllegalStateException("connectionRetrieved");
        doThrow(failure).when(listener).connectionRetrieved(connection);

        assertThatThrownBy(() -> tester.onTearDown())
                .as("The listener's own failure must propagate unchanged.")
                .isSameAs(failure);

        verify(listener).operationTearDownFinished(connection);
    }
}
