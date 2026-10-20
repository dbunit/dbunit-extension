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

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.dbunit.ConnectionPreservingOperationListener;
import org.dbunit.IOperationListener;

/**
 * Decides one question, the same way for every caller: may whatever is driving a test's dbUnit
 * lifecycle close the connection that lifecycle borrowed, once the test is over? A
 * {@link TestScopedConnection} consults its {@code ConnectionOwnership} in
 * {@link TestScopedConnection#release()}.
 *
 * <p>Two inputs, each read fresh at release time since the tester's listener and the
 * test case's flag can both still change after this is constructed:
 *
 * <ol>
 * <li><b>{@code closeConnectionAfterTest}</b> - the flag of the test case that holds the
 * connection, such as {@link org.dbunit.DefaultPrepAndExpectedTestCase}'s, {@code true} by
 * default. {@code false} means a real owner exists elsewhere - a
 * {@link org.dbunit.database.CachingConnectionProvider}, an externally-supplied fixed
 * connection - and it, not this lifecycle, closes the connection.</li>
 * <li><b>{@code testerListener}</b> - the tester's {@link IOperationListener}. When it is
 * (directly, or wrapped by a {@link ConnectionPreservingOperationListener})
 * {@link IOperationListener#NO_OP_OPERATION_LISTENER}, that is the established, pre-existing
 * signal that the connection is managed elsewhere and must not be closed. A blanket
 * {@link ConnectionPreservingOperationListener} layer, which keeps every connection from a
 * close, says the same - see
 * {@link ConnectionPreservingOperationListener#leavesEveryConnectionOpen(IOperationListener)}.</li>
 * </ol>
 *
 * <p>Truth table - "close" only when every input allows it:
 *
 * <pre>
 * closeConnectionAfterTest | listener leaves all open  | -&gt; decision
 * ------------------------- | ------------------------ | -----------
 *          false            |            -             |    SKIP
 *          true             |           true           |    SKIP
 *          true             |          false           |    CLOSE
 * </pre>
 *
 * <p>{@link #isLeftOpenByConfiguration()} answers whether either input leaves the connection
 * to another owner, for a caller that needs to know.
 *
 * <p>{@link TestScopedConnection#release()} additionally skips a connection that is already
 * closed - an idempotency guard, not part of this decision: for an annotation-driven test the
 * executor's borrowed connection and {@link org.dbunit.DefaultPrepAndExpectedTestCase}'s
 * are the same object, and whichever releases first closes it.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public class ConnectionOwnership
{
    private final BooleanSupplier closeConnectionAfterTest;
    private final Supplier<IOperationListener> testerListener;

    /**
     * Creates an ownership decision from its two inputs, each a supplier read fresh every
     * time {@link #mayClose()} or {@link #isLeftOpenByConfiguration()} is called.
     *
     * @param closeConnectionAfterTest Supplies the test case's flag; {@code true} where the
     *            lifecycle has no test case flag to ask.
     * @param testerListener Supplies the tester's current {@link IOperationListener}; may
     *            supply {@code null}, treated as a listener that does not leave every connection
     *            open.
     */
    public ConnectionOwnership(final BooleanSupplier closeConnectionAfterTest,
            final Supplier<IOperationListener> testerListener)
    {
        this.closeConnectionAfterTest = closeConnectionAfterTest;
        this.testerListener = testerListener;
    }

    /**
     * Returns whether the borrowing lifecycle may close the connection it borrowed.
     *
     * @return {@code true} only when the {@code closeConnectionAfterTest} flag is set and the
     *         tester's listener does not leave every connection open.
     */
    public boolean mayClose()
    {
        return closeConnectionAfterTest.getAsBoolean()
                && !ConnectionPreservingOperationListener
                        .leavesEveryConnectionOpen(testerListener.get());
    }

    /**
     * Returns whether the configuration leaves the connection open for another owner to
     * close: the {@code closeConnectionAfterTest} flag is {@code false}, or the tester's
     * listener leaves every connection open - it is (or wraps)
     * {@link IOperationListener#NO_OP_OPERATION_LISTENER}, or has a blanket
     * {@link ConnectionPreservingOperationListener} layer. That is the SKIP rows of the truth
     * table.
     *
     * @return {@code true} when the flag is not set or the tester's listener leaves every
     *         connection open.
     */
    public boolean isLeftOpenByConfiguration()
    {
        return !closeConnectionAfterTest.getAsBoolean()
                || ConnectionPreservingOperationListener
                        .leavesEveryConnectionOpen(testerListener.get());
    }
}
