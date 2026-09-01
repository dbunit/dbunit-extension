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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.dbunit.IOperationListener;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutorOperationListenerTest
{
    private final IOperationListener delegate = mock(IOperationListener.class);
    private final IDatabaseConnection connection = mock(IDatabaseConnection.class);

    @Test
    void testConnectionRetrieved_runsDelegateThenPropertiesThenHook() throws Exception
    {
        final DatabaseConfig config = mock(DatabaseConfig.class);
        when(connection.getConfig()).thenReturn(config);
        final List<String> order = new ArrayList<>();
        doAnswer(invocation ->
        {
            order.add("properties");
            return null;
        }).when(config).setPropertiesByString(any());
        final IOperationListener recordingDelegate = new RecordingConnectionRetrieved(order);
        final ExecutorOperationListener listener = new ExecutorOperationListener(
                propertiesWith("batchSize", "50"), () -> null, recordingDelegate,
                c -> order.add("hook"));

        listener.connectionRetrieved(connection);

        assertThat(order)
                .as("The delegate configures the DatabaseConfig first, then @DbUnitProperty"
                        + " values are applied on top, then the connection is offered to the"
                        + " row count baseline hook - so the hook never reads a stale config.")
                .containsExactly("delegate", "properties", "hook");
    }

    @Test
    void testConnectionRetrieved_noProperties_stillForwardsToDelegateAndCallsHook()
    {
        final boolean[] hookCalled = {false};
        final ExecutorOperationListener listener = new ExecutorOperationListener(new Properties(),
                () -> null, delegate, c -> hookCalled[0] = true);

        listener.connectionRetrieved(connection);

        verify(delegate).connectionRetrieved(connection);
        verify(connection, never()).getConfig();
        assertThat(hookCalled[0]).as("The hook runs even with no @DbUnitProperty values.")
                .isTrue();
    }

    @Test
    void testOperationSetUpFinished_protectedConnection_notForwardedSoDelegateCannotCloseIt()
    {
        final ExecutorOperationListener listener = new ExecutorOperationListener(new Properties(),
                () -> connection, delegate, c ->
                {
                });

        listener.operationSetUpFinished(connection);

        verify(delegate, never()).operationSetUpFinished(connection);
    }

    @Test
    void testOperationSetUpFinished_otherConnection_forwardedAsBefore()
    {
        final IDatabaseConnection other = mock(IDatabaseConnection.class);
        final ExecutorOperationListener listener = new ExecutorOperationListener(new Properties(),
                () -> connection, delegate, c ->
                {
                });

        listener.operationSetUpFinished(other);

        verify(delegate).operationSetUpFinished(other);
    }

    private static Properties propertiesWith(final String name, final String value)
    {
        final Properties properties = new Properties();
        properties.setProperty(name, value);
        return properties;
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
