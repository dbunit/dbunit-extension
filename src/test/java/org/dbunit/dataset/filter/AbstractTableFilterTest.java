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

import static org.assertj.core.api.Assertions.assertThat;

import org.dbunit.dataset.AbstractTest;
import org.dbunit.dataset.CompositeDataSet;
import org.dbunit.dataset.DefaultDataSet;
import org.dbunit.dataset.DefaultTable;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;

/**
 * @author Manuel Laflamme
 * @since Mar 9, 2003
 * @version $Revision$
 */
public abstract class AbstractTableFilterTest extends AbstractTest
{

    protected IDataSet createDataSet() throws Exception
    {
        final IDataSet dataSet1 =
                new XmlDataSet(TestUtils.getFileReader("xml/dataSetTest.xml"));
        final IDataSet dataSet2 =
                new DefaultDataSet(new DefaultTable(getExtraTableName()));

        final IDataSet dataSet = new CompositeDataSet(dataSet1, dataSet2);
        assertThat(dataSet.getTableNames()).as("count before filter")
                .hasSize(getExpectedNames().length + 1);
        return dataSet;
    }

    protected IDataSet createDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet1 = new XmlDataSet(
                TestUtils.getFileReader("xml/xmlDataSetDuplicateTest.xml"));
        final IDataSet dataSet2 =
                new DefaultDataSet(new DefaultTable(getExtraTableName()));

        final IDataSet dataSet =
                new CompositeDataSet(dataSet1, dataSet2, false);
        assertThat(dataSet.getTableNames()).as("count before filter")
                .hasSize(getExpectedDuplicateNames().length + 1);
        return dataSet;
    }

    public abstract void testAccept_withFilter_acceptsExpectedTables()
            throws Exception;

    public abstract void testIsCaseInsensitiveValidName_withMixedCaseName_acceptsAsValid()
            throws Exception;

    public abstract void testIsValidNameAndInvalid_withInvalidName_rejectsName()
            throws Exception;

    public abstract void testGetTableNames_withFilter_returnsFilteredTableNames()
            throws Exception;

    public abstract void testGetCaseInsensitiveTableNames_withLowercaseNames_returnsMatchingTables()
            throws Exception;

    public abstract void testGetReverseTableNames_withFilter_returnsFilteredTablesInReverseOrder()
            throws Exception;

    public abstract void testGetTableNamesAndTableNotInDecoratedDataSet_withMissingTable_excludesMissingTable()
            throws Exception;

    public abstract void testIterator_withFilter_iteratesFilteredTables()
            throws Exception;

    public abstract void testCaseInsensitiveIterator_withMixedCaseFilter_iteratesMatchingTables()
            throws Exception;

    public abstract void testReverseIterator_withFilter_iteratesFilteredTablesInReverse()
            throws Exception;

    public abstract void testIteratorAndTableNotInDecoratedDataSet_withMissingTable_iteratesAvailableTables()
            throws Exception;
}
