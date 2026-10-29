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

import java.util.function.BooleanSupplier;

import org.dbunit.IDatabaseTester;

/**
 * Answers whether a tester is known to hand out one fixed connection from every
 * {@link IDatabaseTester#getConnection()} call. The connection a lifecycle resolved from such a
 * tester is not that lifecycle's to close: the tester or its owner holds it, and the next test
 * uses it again.
 *
 * <p>The check goes by the tester's {@link IDatabaseTester#isReusingConnection()}. A
 * {@link org.dbunit.DefaultDatabaseTester} always says {@code true}, and a
 * {@link org.dbunit.JdbcDatabaseTester}, a {@link org.dbunit.DataSourceDatabaseTester} or a
 * {@link org.dbunit.JndiDatabaseTester} does when it has a
 * {@link org.dbunit.database.CachingConnectionProvider}. Any other tester may open a new
 * connection on every call, as a tester without a provider does, and nothing else would close the
 * connection a lifecycle resolved from it. A tester of another kind that does return one fixed
 * connection says so by overriding {@link IDatabaseTester#isReusingConnection()}, or with
 * {@link org.dbunit.IOperationListener#NO_OP_OPERATION_LISTENER}, the established signal that
 * something else manages its connections, which
 * {@link org.dbunit.database.connection.ConnectionOwnership} honors on its own.
 *
 * <p>The classic lifecycle of {@link AnnotatedTestExecutor} closes the connection it resolves
 * itself, which the 3.5.0 lifecycle never did, unless this check says the tester hands out a
 * fixed one.
 *
 * @author Jeff Jensen
 * @since 3.6.0
 */
class FixedConnectionCheck implements BooleanSupplier
{
    private final IDatabaseTester tester;

    /**
     * Creates the check for one tester.
     *
     * @param tester The tester whose connections to judge; may be {@code null}.
     */
    FixedConnectionCheck(final IDatabaseTester tester)
    {
        this.tester = tester;
    }

    /**
     * Returns whether the tester is known to hand out one fixed connection from every call.
     *
     * @return {@code true} for a tester known to, {@code false} for any other tester and when
     *         there is none.
     */
    @Override
    public boolean getAsBoolean()
    {
        return tester != null && tester.isReusingConnection();
    }
}
