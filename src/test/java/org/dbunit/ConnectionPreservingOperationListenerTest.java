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

    @Test
    void testUnwrapsToNoOp_noOpBehindSeveralWrappers_returnsTrue()
    {
        final ConnectionPreservingOperationListener wrapped =
                new ConnectionPreservingOperationListener(
                        new ConnectionPreservingOperationListener(
                                IOperationListener.NO_OP_OPERATION_LISTENER),
                        () -> protectedConnection);

        assertThat(ConnectionPreservingOperationListener.unwrapsToNoOp(wrapped))
                .as("Every layer of wrapper is looked through, not only the outermost.")
                .isTrue();
    }

    @Test
    void testUnwrapsToNoOp_null_returnsFalse()
    {
        assertThat(ConnectionPreservingOperationListener.unwrapsToNoOp(null))
                .as("A tester with no listener at all is not marked as managed elsewhere.")
                .isFalse();
    }

    @Test
    void testLeavesEveryConnectionOpen_noOpBehindAScopedWrapper_returnsTrue()
    {
        final ConnectionPreservingOperationListener wrapped =
                new ConnectionPreservingOperationListener(
                        IOperationListener.NO_OP_OPERATION_LISTENER, () -> protectedConnection);

        assertThat(ConnectionPreservingOperationListener.leavesEveryConnectionOpen(wrapped))
                .as("The no-op listener leaves every connection open, wrapped or not.")
                .isTrue();
    }

    @Test
    void testLeavesEveryConnectionOpen_blanketWrapperOverClosingListener_returnsTrue()
    {
        final ConnectionPreservingOperationListener blanket =
                new ConnectionPreservingOperationListener(new DefaultOperationListener());

        assertThat(ConnectionPreservingOperationListener.leavesEveryConnectionOpen(blanket))
                .as("A blanket wrapper keeps every connection from its delegate's close, so the"
                        + " tester's connections are managed elsewhere.")
                .isTrue();
    }

    @Test
    void testLeavesEveryConnectionOpen_blanketWrapperBeneathAScopedOne_returnsTrue()
    {
        final ConnectionPreservingOperationListener wrapped =
                new ConnectionPreservingOperationListener(
                        new ConnectionPreservingOperationListener(new DefaultOperationListener()),
                        () -> protectedConnection);

        assertThat(ConnectionPreservingOperationListener.leavesEveryConnectionOpen(wrapped))
                .as("A blanket layer counts beneath a scoped one, such as the one the"
                        + " annotation runtime adds.")
                .isTrue();
    }

    @Test
    void testLeavesEveryConnectionOpen_scopedWrapperOverClosingListener_returnsFalse()
    {
        final ConnectionPreservingOperationListener scoped =
                new ConnectionPreservingOperationListener(new DefaultOperationListener(),
                        () -> protectedConnection);

        assertThat(ConnectionPreservingOperationListener.leavesEveryConnectionOpen(scoped))
                .as("A scoped wrapper keeps only one connection open; the others still close.")
                .isFalse();
    }

    @Test
    void testLeavesEveryConnectionOpen_plainClosingListenerOrNull_returnsFalse()
    {
        assertThat(ConnectionPreservingOperationListener
                .leavesEveryConnectionOpen(new DefaultOperationListener()))
                        .as("The default listener closes every connection.").isFalse();
        assertThat(ConnectionPreservingOperationListener.leavesEveryConnectionOpen(null))
                .as("A tester with no listener at all is not marked as managed elsewhere.")
                .isFalse();
    }

    @Test
    void testUnwrap_severalWrappers_returnsTheInnermostDelegate()
    {
        final ConnectionPreservingOperationListener wrapped =
                new ConnectionPreservingOperationListener(
                        new ConnectionPreservingOperationListener(delegate),
                        () -> protectedConnection);

        assertThat(ConnectionPreservingOperationListener.unwrap(wrapped))
                .as("Every layer of wrapper is peeled off, down to the listener that does the"
                        + " work.")
                .isSameAs(delegate);
    }

    @Test
    void testUnwrap_notAWrapper_returnsItUnchanged()
    {
        assertThat(ConnectionPreservingOperationListener.unwrap(delegate))
                .as("A listener that wraps nothing is its own innermost delegate.")
                .isSameAs(delegate);
    }

    @Test
    void testUnwrap_null_returnsNull()
    {
        assertThat(ConnectionPreservingOperationListener.unwrap(null))
                .as("A missing listener stays missing.").isNull();
    }
}
