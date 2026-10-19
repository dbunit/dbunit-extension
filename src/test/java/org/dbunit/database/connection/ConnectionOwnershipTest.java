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
            final IOperationListener listener)
    {
        return new ConnectionOwnership(() -> closeConnectionAfterTest, () -> listener);
    }

    @Test
    void testMayClose_closeConnectionAfterTestFalse_returnsFalse()
    {
        assertThat(ownership(false, new DefaultOperationListener()).mayClose())
                .as("closeConnectionAfterTest=false: a real owner closes it elsewhere.")
                .isFalse();
    }

    @Test
    void testMayClose_listenerUnwrapsToNoOp_returnsFalse()
    {
        assertThat(ownership(true, IOperationListener.NO_OP_OPERATION_LISTENER).mayClose())
                .as("A NO_OP listener is the signal the connection is managed elsewhere.")
                .isFalse();
    }

    @Test
    void testMayClose_noOpListenerBehindAWrapper_returnsFalse()
    {
        final IOperationListener wrapped = new ConnectionPreservingOperationListener(
                IOperationListener.NO_OP_OPERATION_LISTENER);

        assertThat(ownership(true, wrapped).mayClose())
                .as("A NO_OP listener still counts when wrapped by a"
                        + " ConnectionPreservingOperationListener.")
                .isFalse();
    }

    @Test
    void testMayClose_flagSetAndPlainListener_returnsTrue()
    {
        assertThat(ownership(true, new DefaultOperationListener()).mayClose())
                .as("The only combination that closes: the flag is set, and no listener claims"
                        + " the connection.")
                .isTrue();
    }

    @Test
    void testMayClose_nullListener_treatedAsNotNoOp()
    {
        final ConnectionOwnership ownership =
                new ConnectionOwnership(() -> true, () -> null);

        assertThat(ownership.mayClose())
                .as("A null listener is not the NO_OP listener, so it does not block a close.")
                .isTrue();
    }

    @Test
    void testMayClose_inputsChangeBetweenCalls_readsEveryInputFreshEachCall()
    {
        final boolean[] closeFlag = {false};
        final ConnectionOwnership ownership = new ConnectionOwnership(() -> closeFlag[0],
                () -> new DefaultOperationListener());

        assertThat(ownership.mayClose()).as("Initially the flag is false.").isFalse();
        closeFlag[0] = true;
        assertThat(ownership.mayClose()).as("The flag is re-read, not cached.").isTrue();
    }

    @Test
    void testIsLeftOpenByConfiguration_closeConnectionAfterTestFalse_returnsTrue()
    {
        assertThat(ownership(false, new DefaultOperationListener())
                .isLeftOpenByConfiguration())
                        .as("closeConnectionAfterTest=false leaves the connection to a real"
                                + " owner.")
                        .isTrue();
    }

    @Test
    void testIsLeftOpenByConfiguration_listenerUnwrapsToNoOp_returnsTrue()
    {
        assertThat(ownership(true, IOperationListener.NO_OP_OPERATION_LISTENER)
                .isLeftOpenByConfiguration())
                        .as("A NO_OP listener is the signal the connection is managed"
                                + " elsewhere.")
                        .isTrue();
    }

    @Test
    void testIsLeftOpenByConfiguration_noOpListenerBehindAWrapper_returnsTrue()
    {
        final IOperationListener wrapped = new ConnectionPreservingOperationListener(
                IOperationListener.NO_OP_OPERATION_LISTENER);

        assertThat(ownership(true, wrapped).isLeftOpenByConfiguration())
                .as("A NO_OP listener still counts when wrapped by a"
                        + " ConnectionPreservingOperationListener.")
                .isTrue();
    }

    @Test
    void testMayClose_blanketPreservingListener_returnsFalse()
    {
        final IOperationListener blanket =
                new ConnectionPreservingOperationListener(new DefaultOperationListener());

        assertThat(ownership(true, blanket).mayClose())
                .as("A blanket ConnectionPreservingOperationListener says the caller owns every"
                        + " connection, so the lifecycle must not close the one it borrowed.")
                .isFalse();
    }

    @Test
    void testIsLeftOpenByConfiguration_blanketPreservingListener_returnsTrue()
    {
        final IOperationListener blanket =
                new ConnectionPreservingOperationListener(new DefaultOperationListener());

        assertThat(ownership(true, blanket).isLeftOpenByConfiguration())
                .as("A blanket ConnectionPreservingOperationListener leaves the connection to"
                        + " its real owner, as the NO_OP listener does.")
                .isTrue();
    }

    @Test
    void testIsLeftOpenByConfiguration_flagSetAndPlainListener_returnsFalse()
    {
        assertThat(ownership(true, new DefaultOperationListener())
                .isLeftOpenByConfiguration())
                        .as("Nothing in the configuration claims the connection.")
                        .isFalse();
    }

    @Test
    void testIsLeftOpenByConfiguration_nullListener_treatedAsNotNoOp()
    {
        final ConnectionOwnership ownership =
                new ConnectionOwnership(() -> true, () -> null);

        assertThat(ownership.isLeftOpenByConfiguration())
                .as("A null listener is not the NO_OP listener.").isFalse();
    }

    @Test
    void testIsLeftOpenByConfiguration_flagChangesBetweenCalls_answersFromTheCurrentFlag()
    {
        final boolean[] closeFlag = {true};
        final ConnectionOwnership ownership = new ConnectionOwnership(() -> closeFlag[0],
                () -> new DefaultOperationListener());

        assertThat(ownership.isLeftOpenByConfiguration()).as("Initially the flag is set.")
                .isFalse();
        closeFlag[0] = false;
        assertThat(ownership.isLeftOpenByConfiguration())
                .as("The flag is re-read, not cached.").isTrue();
    }
}
