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
package org.dbunit.database.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.ConnectionPreservingOperationListener;
import org.dbunit.DefaultOperationListener;
import org.dbunit.IOperationListener;
import org.junit.jupiter.api.Test;

/**
 * One row per line of the truth table in {@link ConnectionOwnership}'s Javadoc.
 */
class ConnectionOwnershipTest
{
    private static ConnectionOwnership ownership(final boolean closeConnectionAfterTest,
            final IOperationListener listener, final boolean borrowingLifecycleRan)
    {
        return new ConnectionOwnership(() -> closeConnectionAfterTest, () -> listener,
                () -> borrowingLifecycleRan);
    }

    @Test
    void testMayClose_closeConnectionAfterTestFalse_returnsFalse()
    {
        assertThat(ownership(false, new DefaultOperationListener(), true).mayClose())
                .as("closeConnectionAfterTest=false: a real owner closes it elsewhere.")
                .isFalse();
    }

    @Test
    void testMayClose_listenerUnwrapsToNoOp_returnsFalse()
    {
        assertThat(ownership(true, IOperationListener.NO_OP_OPERATION_LISTENER, true).mayClose())
                .as("A NO_OP listener is the signal the connection is managed elsewhere.")
                .isFalse();
    }

    @Test
    void testMayClose_noOpListenerBehindAWrapper_returnsFalse()
    {
        final IOperationListener wrapped = new ConnectionPreservingOperationListener(
                IOperationListener.NO_OP_OPERATION_LISTENER);

        assertThat(ownership(true, wrapped, true).mayClose())
                .as("A NO_OP listener still counts when wrapped by a"
                        + " ConnectionPreservingOperationListener.")
                .isFalse();
    }

    @Test
    void testMayClose_borrowingLifecycleDidNotRun_returnsFalse()
    {
        assertThat(ownership(true, new DefaultOperationListener(), false).mayClose())
                .as("The borrowing lifecycle never took ownership - e.g. an injected test case"
                        + " that threw before configureTest() - so leave the connection alone.")
                .isFalse();
    }

    @Test
    void testMayClose_flagSetLifecycleRanAndPlainListener_returnsTrue()
    {
        assertThat(ownership(true, new DefaultOperationListener(), true).mayClose())
                .as("The only combination that closes: the flag is set, the lifecycle ran, and"
                        + " no listener claims the connection.")
                .isTrue();
    }

    @Test
    void testMayClose_nullListener_treatedAsNotNoOp()
    {
        final ConnectionOwnership ownership =
                new ConnectionOwnership(() -> true, () -> null, () -> true);

        assertThat(ownership.mayClose())
                .as("A null listener is not the NO_OP listener, so it does not block a close.")
                .isTrue();
    }

    @Test
    void testMayClose_readsEveryInputFreshEachCall()
    {
        final boolean[] closeFlag = {false};
        final ConnectionOwnership ownership = new ConnectionOwnership(() -> closeFlag[0],
                () -> new DefaultOperationListener(), () -> true);

        assertThat(ownership.mayClose()).as("Initially the flag is false.").isFalse();
        closeFlag[0] = true;
        assertThat(ownership.mayClose()).as("The flag is re-read, not cached.").isTrue();
    }
}
