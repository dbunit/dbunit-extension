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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 16, 2002
 */
public class MockBatchStatement implements IBatchStatement, Verifiable
{
    private Integer _executeBatchCalls = 0;
    private Integer _expectedExecuteBatchCalls = 0;
    private Integer _clearBatchCalls = 0;
    private Integer _expectedClearBatchCalls = 0;
    private Integer _closeCalls = 0;
    private Integer _expectedCloseCalls = 0;
    private List<String> _batchStrings = new LinkedList<>();
    private List<String> _actualBatchStrings = new LinkedList<>();
    private int _addBatchCalls = 0;

    public MockBatchStatement()
    {
    }

    public void addExpectedBatchString(final String sql)
    {
        _batchStrings.add(sql);
    }

    public void addExpectedBatchStrings(final String[] sql)
    {
        _batchStrings.addAll(Arrays.asList(sql));
    }

    public void setExpectedExecuteBatchCalls(final int callsCount)
    {
        _expectedExecuteBatchCalls = callsCount;
    }

    public void setExpectedClearBatchCalls(final int callsCount)
    {
        _expectedClearBatchCalls = callsCount;
    }

    public void setExpectedCloseCalls(final int callsCount)
    {
        _expectedCloseCalls = callsCount;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Verifiable interface

    @Override
    public void verify()
    {
        verify(_executeBatchCalls, _expectedExecuteBatchCalls);
        verify(_clearBatchCalls, _expectedClearBatchCalls);
        verify(_closeCalls, _expectedCloseCalls);
        assertThat(_batchStrings).isEqualTo(_actualBatchStrings);
    }

    private void verify(final int count, final int expected)
    {
        if (!Objects.isNull(expected))
        {
            assertThat(count).isEqualTo(expected);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // IBatchStatement interface

    @Override
    public void addBatch(final String sql) throws SQLException
    {
        _actualBatchStrings.add(sql);
        _addBatchCalls++;
    }

    @Override
    public int executeBatch() throws SQLException
    {
        _executeBatchCalls++;
        return _addBatchCalls;
    }

    @Override
    public void clearBatch() throws SQLException
    {
        _clearBatchCalls++;
        _addBatchCalls = 0;
    }

    @Override
    public void close() throws SQLException
    {
        _closeCalls++;
    }
}
