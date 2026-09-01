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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.dbunit.database.IDatabaseConnection;
import org.junit.jupiter.api.Test;

class ConnectionPreservingOperationListenerTest
{
    private final IOperationListener delegate = mock(IOperationListener.class);
    private final IDatabaseConnection protectedConnection = mock(IDatabaseConnection.class);
    private final IDatabaseConnection otherConnection = mock(IDatabaseConnection.class);

    @Test
    void testScoped_protectedConnection_suppressesSetUpAndTearDownFinishedButForwardsConnectionRetrieved()
    {
        final ConnectionPreservingOperationListener listener =
                new ConnectionPreservingOperationListener(delegate, () -> protectedConnection);

        listener.connectionRetrieved(protectedConnection);
        listener.operationSetUpFinished(protectedConnection);
        listener.operationTearDownFinished(protectedConnection);

        verify(delegate).connectionRetrieved(protectedConnection);
        verify(delegate, never()).operationSetUpFinished(protectedConnection);
        verify(delegate, never()).operationTearDownFinished(protectedConnection);
    }

    @Test
    void testScoped_anyOtherConnection_forwardsEveryCallback()
    {
        final ConnectionPreservingOperationListener listener =
                new ConnectionPreservingOperationListener(delegate, () -> protectedConnection);

        listener.operationSetUpFinished(otherConnection);
        listener.operationTearDownFinished(otherConnection);

        verify(delegate).operationSetUpFinished(otherConnection);
        verify(delegate).operationTearDownFinished(otherConnection);
    }

    @Test
    void testScoped_protectedConnectionNotResolvedYet_forwardsForEveryConnection()
    {
        final ConnectionPreservingOperationListener listener =
                new ConnectionPreservingOperationListener(delegate, () -> null);

        listener.operationSetUpFinished(protectedConnection);

        verify(delegate).operationSetUpFinished(protectedConnection);
    }

    @Test
    void testBlanket_everyConnection_suppressesSetUpAndTearDownFinished()
    {
        final ConnectionPreservingOperationListener listener =
                new ConnectionPreservingOperationListener(delegate);

        listener.connectionRetrieved(otherConnection);
        listener.operationSetUpFinished(otherConnection);
        listener.operationTearDownFinished(protectedConnection);

        verify(delegate).connectionRetrieved(otherConnection);
        verify(delegate, never()).operationSetUpFinished(otherConnection);
        verify(delegate, never()).operationTearDownFinished(protectedConnection);
    }

    @Test
    void testUnwrap_existingWrapper_returnsItsDelegateSoLayersDoNotStack()
    {
        final ConnectionPreservingOperationListener wrapper =
                new ConnectionPreservingOperationListener(delegate);

        assertThat(ConnectionPreservingOperationListener.unwrap(wrapper)).isSameAs(delegate);
    }

    @Test
    void testUnwrap_plainListener_returnsItUnchanged()
    {
        assertThat(ConnectionPreservingOperationListener.unwrap(delegate)).isSameAs(delegate);
    }

    @Test
    void testUnwrap_null_returnsAFreshDefaultOperationListener()
    {
        assertThat(ConnectionPreservingOperationListener.unwrap(null))
                .isInstanceOf(DefaultOperationListener.class);
    }

    @Test
    void testUnwrapsToNoOp_noOpDirectly_returnsTrue()
    {
        assertThat(ConnectionPreservingOperationListener
                .unwrapsToNoOp(IOperationListener.NO_OP_OPERATION_LISTENER)).isTrue();
    }

    @Test
    void testUnwrapsToNoOp_noOpBehindAWrapper_returnsTrue()
    {
        final ConnectionPreservingOperationListener wrapped =
                new ConnectionPreservingOperationListener(
                        IOperationListener.NO_OP_OPERATION_LISTENER);

        assertThat(ConnectionPreservingOperationListener.unwrapsToNoOp(wrapped)).isTrue();
    }

    @Test
    void testUnwrapsToNoOp_plainClosingListener_returnsFalse()
    {
        assertThat(ConnectionPreservingOperationListener
                .unwrapsToNoOp(new DefaultOperationListener())).isFalse();
    }
}
