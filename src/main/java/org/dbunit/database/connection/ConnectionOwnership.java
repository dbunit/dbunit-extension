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
 * <p>Three inputs, each read fresh at release time since the tester's listener and the
 * borrowing lifecycle's own state can both still change after this is constructed:
 *
 * <ol>
 * <li><b>{@code closeConnectionAfterTest}</b> - the {@code @DbUnitConfig} flag, {@code true} by
 * default. {@code false} means a real owner exists elsewhere - a
 * {@link org.dbunit.database.CachingConnectionProvider}, an externally-supplied fixed
 * connection - and it, not this lifecycle, closes the connection.</li>
 * <li><b>{@code testerListener}</b> - the tester's {@link IOperationListener}. When it is
 * (directly, or wrapped by a {@link ConnectionPreservingOperationListener})
 * {@link IOperationListener#NO_OP_OPERATION_LISTENER}, that is the established, pre-existing
 * signal that the connection is managed elsewhere and must not be closed.</li>
 * <li><b>{@code borrowingLifecycleRan}</b> - whether the borrowing lifecycle actually reached
 * the point of owning the connection. On the setup/teardown path a connection was actually
 * acquired; on the prep/expected path with an injected {@code @DbUnitTestCase}, its
 * {@code configureTest()} completed. When it did not - the injected instance threw before
 * {@code configureTest()}, so its own {@code postTest()}/{@code cleanupData()} never ran to
 * close the connection its way - closing here would strand that reused instance holding a
 * closed connection.</li>
 * </ol>
 *
 * <p>Truth table - "close" only when every input allows it:
 *
 * <pre>
 * closeConnectionAfterTest | listener unwraps to NO_OP | borrowing lifecycle ran | -&gt; decision
 * ------------------------- | ------------------------ | ----------------------- | -----------
 *          false            |            -             |            -            |    SKIP
 *          true             |           true           |            -            |    SKIP
 *          true             |          false           |          false          |    SKIP
 *          true             |          false           |          true           |    CLOSE
 * </pre>
 *
 * <p>{@link TestScopedConnection#release()} additionally skips a connection that is already
 * closed - an idempotency guard, not part of this decision: on the {@code @DbUnitExpected}
 * path the executor's borrowed connection and {@link org.dbunit.DefaultPrepAndExpectedTestCase}'s
 * are the same object, and whichever releases first closes it.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
public final class ConnectionOwnership
{
    private final BooleanSupplier closeConnectionAfterTest;
    private final Supplier<IOperationListener> testerListener;
    private final BooleanSupplier borrowingLifecycleRan;

    /**
     * Creates an ownership decision from its three inputs, each a supplier read fresh every
     * time {@link #mayClose()} is called.
     *
     * @param closeConnectionAfterTest Supplies the {@code @DbUnitConfig} flag.
     * @param testerListener Supplies the tester's current {@link IOperationListener}; may
     *            supply {@code null}, treated as "not the no-op listener".
     * @param borrowingLifecycleRan Supplies whether the borrowing lifecycle reached the point
     *            of owning the connection.
     */
    public ConnectionOwnership(final BooleanSupplier closeConnectionAfterTest,
            final Supplier<IOperationListener> testerListener,
            final BooleanSupplier borrowingLifecycleRan)
    {
        this.closeConnectionAfterTest = closeConnectionAfterTest;
        this.testerListener = testerListener;
        this.borrowingLifecycleRan = borrowingLifecycleRan;
    }

    /**
     * Returns whether the borrowing lifecycle may close the connection it borrowed.
     *
     * @return {@code true} only when the {@code closeConnectionAfterTest} flag is set, the
     *         lifecycle actually ran, and the tester's listener does not unwrap to the no-op
     *         listener.
     */
    public boolean mayClose()
    {
        return closeConnectionAfterTest.getAsBoolean() && borrowingLifecycleRan.getAsBoolean()
                && !ConnectionPreservingOperationListener.unwrapsToNoOp(testerListener.get());
    }
}
