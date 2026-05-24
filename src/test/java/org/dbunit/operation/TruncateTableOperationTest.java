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

package org.dbunit.operation;

/**
 * Unit tests for {@link TruncateTableOperation} using mock objects.
 *
 * @author Manuel Laflamme
 * @since Apr 13, 2003
 * @version $Revision$
 */
class TruncateTableOperationTest extends DeleteAllOperationTest
{
    @Override
    protected DatabaseOperation getDeleteAllOperation()
    {
        return new TruncateTableOperation();
    }

    @Override
    protected String getExpectedStament(final String tableName)
    {
        return "truncate table " + tableName;
    }
}
