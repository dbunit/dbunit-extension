/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
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

package org.dbunit.database.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.database.IDatabaseConnection;

/**
 * Mock implementation of {@link IStatementFactory} for use in unit tests.
 * Tracks the number of statement creation calls and delegates to a configured
 * {@link IBatchStatement}.
 *
 * <p>Use {@link #forSingleBatch(String...)} to create a fully-wired factory
 * expecting exactly one batch execution of the given SQL statements, reducing
 * per-test setup boilerplate.
 *
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public class MockStatementFactory implements IStatementFactory
{
    private IBatchStatement _batchStatement = null;
    // private IPreparedBatchStatement _preparedBatchStatement = null;
    private Integer _expectedCreateStatementCalls;
    private Integer _expectedCreatePreparedStatementCalls;
    private AtomicInteger _createStatementCalls = new AtomicInteger();
    private AtomicInteger _createPreparedStatementCalls = new AtomicInteger();

    /**
     * Creates a {@link MockStatementFactory} pre-wired with a {@link MockBatchStatement}
     * expecting exactly the given SQL statements in a single batch execution.
     * Equivalent to manually creating and configuring both a {@link MockBatchStatement}
     * and a {@link MockStatementFactory}, but in one call.
     *
     * <p>Call {@link #getBatchStatement()} on the returned factory to access the
     * underlying {@link MockBatchStatement} for additional assertions such as
     * {@link MockBatchStatement#getCapturedSql()}.
     *
     * @param expectedSql the SQL strings expected to be batched, in order
     * @return a fully configured factory ready for use in a test
     */
    public static MockStatementFactory forSingleBatch(final String... expectedSql)
    {
        final MockBatchStatement statement = new MockBatchStatement();
        statement.addExpectedBatchStrings(expectedSql);
        statement.setExpectedExecuteBatchCalls(1);
        statement.setExpectedClearBatchCalls(1);
        statement.setExpectedCloseCalls(1);

        final MockStatementFactory factory = new MockStatementFactory();
        factory.setExpectedCreatePreparedStatementCalls(1);
        factory.setupStatement(statement);
        return factory;
    }

    /**
     * Returns the {@link MockBatchStatement} configured via {@link #setupStatement(IBatchStatement)}.
     * Useful after using {@link #forSingleBatch(String...)} to access the underlying
     * statement for pattern-based assertions via {@link MockBatchStatement#getCapturedSql()}.
     *
     * @return the configured batch statement, or {@code null} if none was set
     */
    public MockBatchStatement getBatchStatement()
    {
        return (MockBatchStatement) _batchStatement;
    }

    public void setupStatement(final IBatchStatement batchStatement)
    {
        _batchStatement = batchStatement;
    }

    // public void setupPreparedStatement(IPreparedBatchStatement
    // preparedBatchStatement)
    // {
    // _preparedBatchStatement = preparedBatchStatement;
    // }

    public void setExpectedCreateStatementCalls(final int callsCount)
    {
        _expectedCreateStatementCalls = callsCount;
    }

    public void setExpectedCreatePreparedStatementCalls(final int callsCount)
    {
        _expectedCreatePreparedStatementCalls = callsCount;
    }

    public void verify()
    {
        if (!Objects.isNull(_expectedCreateStatementCalls))
        {
            assertThat(_createStatementCalls.get())
                    .isEqualTo(_expectedCreateStatementCalls);
        }
        if (!Objects.isNull(_expectedCreatePreparedStatementCalls))
        {
            assertThat(_createPreparedStatementCalls.get())
                    .isEqualTo(_expectedCreatePreparedStatementCalls);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // IStatementFactory interface

    @Override
    public IBatchStatement createBatchStatement(
            final IDatabaseConnection connection) throws SQLException
    {
        _createStatementCalls.incrementAndGet();
        return _batchStatement;
    }

    @Override
    public IPreparedBatchStatement createPreparedBatchStatement(
            final String sql, final IDatabaseConnection connection)
            throws SQLException
    {
        _createPreparedStatementCalls.incrementAndGet();
        return new BatchStatementDecorator(sql, _batchStatement);
    }
}
