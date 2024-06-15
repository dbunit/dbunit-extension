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
package org.dbunit.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DefaultTableMetaData;
import org.dbunit.dataset.ITableMetaData;

/**
 * @author Manuel Laflamme
 * @since Apr 12, 2003
 * @version $Revision$
 */
public class MockResultSetTable implements IResultSetTable
{
    private final AtomicInteger _closeCalls = new AtomicInteger();
    private int expectedCloseCalls;
    private ITableMetaData _metaData;

    public void setupTableMetaData(final String tableName)
    {
        _metaData = new DefaultTableMetaData(tableName, new Column[0]);
    }

    public void setExpectedCloseCalls(final int callsCount)
    {
        expectedCloseCalls = callsCount;
    }

    public void verify()
    {
        assertThat(_closeCalls.get()).isEqualTo(expectedCloseCalls);
    }

    ////////////////////////////////////////////////////////////////////////////
    // IResultSetTable interface

    @Override
    public Object getValue(final int row, final String column)
            throws DataSetException
    {
        return null;
    }

    @Override
    public int getRowCount()
    {
        return 0;
    }

    @Override
    public ITableMetaData getTableMetaData()
    {
        return _metaData;
    }

    @Override
    public void close() throws DataSetException
    {
        _closeCalls.incrementAndGet();
    }
}
