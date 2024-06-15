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

import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Mar 27, 2002)
 */
class CaseInsensitiveDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new CaseInsensitiveDataSet(new XmlDataSet(
                TestUtils.getFileReader("xml/caseInsensitiveDataSetTest.xml")));
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void assertEqualsTableName(final String message,
            final String expected, final String actual)
    {
        assertEqualsIgnoreCase(message, expected, actual);
    }

    @Override
    public void testCreateDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

    @Override
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        // No op. This dataSet is only a wrapper for another dataSet which is
        // why duplicates cannot occur.
    }

}
