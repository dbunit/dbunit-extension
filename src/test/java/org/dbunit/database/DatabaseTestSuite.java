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

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 */
@Suite
@SelectClasses({org.dbunit.database.statement.AllTestsSuite.class,
        CachedResultSetTableIT.class, DatabaseConfigTest.class,
        DatabaseConnectionIT.class, DatabaseDataSetIT.class,
        DatabaseSequenceFilterTest.class, DatabaseTableIteratorTest.class,
        DatabaseTableMetaDataIT.class, ForwardOnlyResultSetTableIT.class,
        QueryDataSetIT.class, PrimaryKeyFilteredTableWrapperTest.class,
        JdbcDatabaseTesterConnectionIT.class,
        DefaultDatabaseTesterConnectionIT.class,
        ResultSetTableMetaDataIT.class})
public class DatabaseTestSuite
{
}
