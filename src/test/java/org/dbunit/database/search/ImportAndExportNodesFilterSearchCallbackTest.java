/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2005, DbUnit.org
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

import org.dbunit.database.IDatabaseConnection;

/**
 * @author Felipe Leme (dbunit@felipeal.net)
 * @version $Revision$
 * @since Aug 28, 2005
 */
public class ImportAndExportNodesFilterSearchCallbackTest
        extends ImportNodesFilterSearchCallbackTest
{

    @Override
    protected String[][] getExpectedOutput()
    {
        final int size = getInput().length;
        final String[][] result = new String[size][];
        final String[] allResults = super.getExpectedOutput()[1];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = allResults;
        }
        return result;
    }

    @Override
    protected AbstractMetaDataBasedSearchCallback getCallback(
            final IDatabaseConnection connection)
    {
        return new ImportedAndExportedKeysSearchCallback(connection);
    }

}
