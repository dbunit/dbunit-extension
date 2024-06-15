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
package org.dbunit.dataset.filter;

import org.dbunit.dataset.AbstractTableIteratorTest;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.MockDataSet;

/**
 * @author Manuel Laflamme
 * @since Apr 6, 2003
 * @version $Revision$
 */
public class SequenceTableIteratorTest extends AbstractTableIteratorTest
{

    @Override
    protected ITableIterator getIterator() throws Exception
    {
        final String[] expectedNames = getExpectedNames();
        final MockDataSet dataSet = new MockDataSet();
        for (int i = 0; i < expectedNames.length; i++)
        {
            final String tableName = expectedNames[i];
            dataSet.addEmptyTable(tableName);
        }

        return new SequenceTableIterator(expectedNames, dataSet);
    }

    @Override
    protected ITableIterator getEmptyIterator() throws Exception
    {
        return new SequenceTableIterator(new String[0], new MockDataSet());
    }
}
