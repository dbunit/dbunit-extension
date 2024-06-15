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
package org.dbunit.dataset;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * @author Manuel Laflamme
 * @since Apr 12, 2003
 * @version $Revision$
 */
public class MockDataSet extends AbstractDataSet
{
    private final List<ITable> _tableList = new ArrayList<>();

    public void addTable(final ITable table)
    {
        _tableList.add(table);
    }

    public void addEmptyTable(final String tableName)
    {
        _tableList.add(new DefaultTable(tableName));
    }

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSet class

    @Override
    protected ITableIterator createIterator(final boolean reversed)
            throws DataSetException
    {
        final ITable[] tables = _tableList.toArray(new ITable[0]);
        return new DefaultTableIterator(tables, reversed);
    }

    public void verify()
    {
        for (final Iterator<ITable> it = _tableList.iterator(); it.hasNext();)
        {
            final ITable table = it.next();
            if (table instanceof Mock)
            {
                Mockito.verify(table).getTableMetaData();
            }
        }
    }
}
