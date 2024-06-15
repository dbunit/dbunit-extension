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

import java.io.FileReader;

import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 14, 2003
 */
public class LowerCaseDataSetTest extends AbstractDataSetDecoratorTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new LowerCaseDataSet(new FlatXmlDataSetBuilder()
                .build(new FileReader(FlatXmlDataSetTest.DATASET_FILE)));
    }

    @Override
    protected String[] getExpectedNames() throws Exception
    {
        return getExpectedLowerNames();
    }

    @Override
    protected String[] getExpectedDuplicateNames()
    {
        final String[] names = super.getExpectedDuplicateNames();
        for (int i = 0; i < names.length; i++)
        {
            names[i] = names[i].toLowerCase();
        }

        return names;
    }

}
