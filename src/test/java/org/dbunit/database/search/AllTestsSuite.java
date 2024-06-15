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

package org.dbunit.database.search;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 28, 2005
 */
@Suite
@SelectClasses({ForeignKeyRelationshipEdgeTest.class,
        ImportAndExportNodesFilterSearchCallbackTest.class,
        ImportNodesFilterSearchCallbackTest.class,
        ImportAndExportKeysSearchCallbackOwnFileTest.class,
        ImportedKeysFilteredByPKsCyclicTest.class,
        ImportedKeysFilteredByPKsSingleTest.class,
        ImportedKeysFilteredByPKsTest.class,
        ImportedAndExportedKeysFilteredByPKsCyclicTest.class,
        ImportedAndExportedKeysFilteredByPKsSingleTest.class,
        ImportedAndExportedKeysFilteredByPKsTest.class,
        TablesDependencyHelperTest.class})
public class AllTestsSuite
{
}
