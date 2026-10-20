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
package org.dbunit.annotation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.dbunit.IOperationListener;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassicOperationListenerTest
{
    private final IOperationListener delegate = mock(IOperationListener.class);
    private final IDatabaseConnection connection = mock(IDatabaseConnection.class);

    @Test
    void testConnectionRetrieved_active_runsDelegateThenHook()
    {
        final List<String> order = new ArrayList<>();
        final IOperationListener recordingDelegate = new RecordingConnectionRetrieved(order);
        final ClassicOperationListener listener =
                new ClassicOperationListener(recordingDelegate, c -> order.add("hook"));

        listener.connectionRetrieved(connection);

        assertThat(order)
                .as("The delegate configures the DatabaseConfig first, then the connection is"
                        + " offered to the row count baseline hook - so the hook never reads a"
                        + " stale config.")
                .containsExactly("delegate", "hook");
    }

    @Test
    void testConnectionRetrieved_deactivated_onlyForwardsToDelegate()
    {
        final List<String> order = new ArrayList<>();
        final ClassicOperationListener listener =
                new ClassicOperationListener(delegate, c -> order.add("hook"));
        listener.deactivate();

        listener.connectionRetrieved(connection);

        verify(delegate).connectionRetrieved(connection);
        assertThat(order).as("A finished executor's listener must capture nothing, though it"
                + " can stay in the tester's chain.").isEmpty();
    }

    @Test
    void testOperationFinished_active_forwardedToDelegateForEveryConnection()
    {
        final ClassicOperationListener listener = new ClassicOperationListener(delegate, c ->
        {
        });

        listener.operationSetUpFinished(connection);
        listener.operationTearDownFinished(connection);

        verify(delegate).operationSetUpFinished(connection);
        verify(delegate).operationTearDownFinished(connection);
    }

    @Test
    void testOperationFinished_deactivated_stillForwardedToDelegate()
    {
        final ClassicOperationListener listener = new ClassicOperationListener(delegate, c ->
        {
        });
        listener.deactivate();

        listener.operationSetUpFinished(connection);
        listener.operationTearDownFinished(connection);

        verify(delegate).operationSetUpFinished(connection);
        verify(delegate).operationTearDownFinished(connection);
    }

    @Test
    void testGetDelegate_created_isTheTestersOwnListener()
    {
        final ClassicOperationListener listener = new ClassicOperationListener(delegate, c ->
        {
        });

        assertThat(listener.getDelegate()).as("Put back when the test is over.")
                .isSameAs(delegate);
    }

    private static final class RecordingConnectionRetrieved implements IOperationListener
    {
        private final List<String> order;

        private RecordingConnectionRetrieved(final List<String> order)
        {
            this.order = order;
        }

        @Override
        public void connectionRetrieved(final IDatabaseConnection connection)
        {
            order.add("delegate");
        }

        @Override
        public void operationSetUpFinished(final IDatabaseConnection connection)
        {
        }

        @Override
        public void operationTearDownFinished(final IDatabaseConnection connection)
        {
        }
    }
}
