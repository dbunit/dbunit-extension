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

import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.dbunit.ConnectionPreservingOperationListener;
import org.dbunit.IOperationListener;
import org.dbunit.database.IDatabaseConnection;

/**
 * The {@link IOperationListener} {@link AnnotatedTestExecutor} installs on the tester for every
 * annotation-driven test. A {@link ConnectionPreservingOperationListener} that adds the
 * executor's own two pieces of {@code connectionRetrieved} work on top of the inherited
 * connection shield: after forwarding to {@code delegate} - the tester's own listener, and the
 * documented place for it to configure the connection's {@code DatabaseConfig} (e.g. to enable
 * the row count check or set a custom {@code RowCounter}) - it applies {@code @DbUnitProperty}
 * values, letting them override whatever {@code delegate} set, then offers the connection to
 * {@code onFirstConnectionRetrieved} for the row count check baseline capture (a no-op after
 * the first call). Reading {@code DatabaseConfig} last this way means the row count check
 * always sees {@code delegate}'s and {@code @DbUnitProperty}'s configuration already applied,
 * never a stale value from before either ran. The {@code operationSetUpFinished}/
 * {@code operationTearDownFinished} shield - and its caveat that suppressing the callback
 * suppresses the delegate's non-close work in it too - is entirely
 * {@link ConnectionPreservingOperationListener}'s.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
final class ExecutorOperationListener extends ConnectionPreservingOperationListener
{
    private final Properties properties;
    private final Consumer<IDatabaseConnection> onFirstConnectionRetrieved;

    /**
     * Creates the listener.
     *
     * @param properties The {@code @DbUnitProperty} values to apply to each retrieved
     *            connection's {@code DatabaseConfig}.
     * @param protectedConnection Supplies the one connection the executor holds past
     *            {@code onSetup()} and must not have closed by the delegate; re-read on each
     *            callback, may return {@code null} until it is resolved.
     * @param delegate The tester's own listener to forward every callback to.
     * @param onFirstConnectionRetrieved Offered each retrieved connection, after the delegate
     *            and property application have run; a no-op after the first call.
     */
    ExecutorOperationListener(final Properties properties,
            final Supplier<IDatabaseConnection> protectedConnection,
            final IOperationListener delegate,
            final Consumer<IDatabaseConnection> onFirstConnectionRetrieved)
    {
        super(delegate, protectedConnection);
        this.properties = properties;
        this.onFirstConnectionRetrieved = onFirstConnectionRetrieved;
    }

    @Override
    public void connectionRetrieved(final IDatabaseConnection connection)
    {
        super.connectionRetrieved(connection);
        AnnotatedTestExecutor.applyProperties(connection, properties);
        onFirstConnectionRetrieved.accept(connection);
    }
}
