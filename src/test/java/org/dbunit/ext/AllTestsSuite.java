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
package org.dbunit.ext;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * @author Manuel Laflamme
 * @since Aug 13, 2003
 * @version $Revision$
 */
@Suite
@SelectClasses({org.dbunit.ext.db2.AllTestsSuite.class,
        org.dbunit.ext.mckoi.AllTestsSuite.class,
        org.dbunit.ext.mssql.AllTestsSuite.class,
        org.dbunit.ext.mysql.AllTestsSuite.class,
        org.dbunit.ext.oracle.AllTestsSuite.class,
        org.dbunit.ext.hsqldb.AllTestsSuite.class,
        org.dbunit.ext.h2.AllTestsSuite.class,
        org.dbunit.ext.postgresql.AllTestsSuite.class,})
public class AllTestsSuite
{
}
