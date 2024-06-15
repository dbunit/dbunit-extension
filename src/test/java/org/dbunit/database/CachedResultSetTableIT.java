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

import org.dbunit.DatabaseEnvironment;
import org.dbunit.dataset.AbstractTableTest;
import org.dbunit.dataset.ITable;
import org.dbunit.operation.DatabaseOperation;

/**
 * @author Manuel Laflamme
 * @since Apr 11, 2003
 * @version $Revision$
 */
public class CachedResultSetTableIT extends AbstractTableTest
{

    @Override
    protected ITable createTable() throws Exception
    {
        final DatabaseEnvironment env = DatabaseEnvironment.getInstance();
        final IDatabaseConnection connection = env.getConnection();

        DatabaseOperation.CLEAN_INSERT.execute(connection,
                env.getInitDataSet());

        final String selectStatement =
                "select * from TEST_TABLE order by COLUMN0";
        return new CachedResultSetTable(new ForwardOnlyResultSetTable(
                "TEST_TABLE", selectStatement, connection));
    }

    @Override
    protected String convertString(final String str) throws Exception
    {
        return DatabaseEnvironment.getInstance().convertString(str);
    }

    @Override
    public void testGetMissingValue() throws Exception
    {
        // Do not test this!
    }
}
