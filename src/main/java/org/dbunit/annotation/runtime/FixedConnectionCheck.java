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

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.DefaultDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.JndiDatabaseTester;

/**
 * Answers whether a tester is known to hand out one fixed connection from every
 * {@link IDatabaseTester#getConnection()} call. The connection a lifecycle resolved from such a
 * tester is not that lifecycle's to close: the tester or its owner holds it, and the next test
 * uses it again.
 *
 * <p>A {@link DefaultDatabaseTester} is known to, as it returns the connection its constructor
 * received from every call, or the one its {@link org.dbunit.database.CachingConnectionProvider}
 * holds, which the provider replaces once that connection closes. A
 * {@link JdbcDatabaseTester}, a {@link DataSourceDatabaseTester} or a
 * {@link JndiDatabaseTester} is when it reports that it is reusing its connection, which it does
 * when it was built with a {@link org.dbunit.database.CachingConnectionProvider}. Any other
 * tester is not known to: it may open a new connection on every call, as a tester built without
 * a provider does, and nothing else would close the connection a lifecycle resolved from it.
 * A tester of another kind that does return one fixed connection says so with
 * {@link org.dbunit.IOperationListener#NO_OP_OPERATION_LISTENER}, the established signal that its
 * connections are managed elsewhere, which {@link org.dbunit.database.connection.ConnectionOwnership}
 * honors on its own.
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
        if (tester instanceof DefaultDatabaseTester)
        {
            return true;
        }
        if (tester instanceof JdbcDatabaseTester)
        {
            final JdbcDatabaseTester jdbcTester = (JdbcDatabaseTester) tester;
            return jdbcTester.isReusingConnection();
        }
        if (tester instanceof DataSourceDatabaseTester)
        {
            final DataSourceDatabaseTester dataSourceTester = (DataSourceDatabaseTester) tester;
            return dataSourceTester.isReusingConnection();
        }
        if (tester instanceof JndiDatabaseTester)
        {
            final JndiDatabaseTester jndiTester = (JndiDatabaseTester) tester;
            return jndiTester.isReusingConnection();
        }
        return false;
    }
}
