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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since 1.0 (Feb 22, 2002)
 */
class CompositeDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final IDataSet dataSet1 = new XmlDataSet(
                TestUtils.getFileReader("xml/compositeDataSetTest1.xml"));
        assertThat(dataSet1.getTableNames()).as("count before combine (1)")
                .hasSizeLessThan(getExpectedNames().length);

        final IDataSet dataSet2 = new XmlDataSet(
                TestUtils.getFileReader("xml/compositeDataSetTest2.xml"));
        assertThat(dataSet2.getTableNames()).as("count before combine (2)")
                .hasSizeLessThan(getExpectedNames().length);

        return new CompositeDataSet(dataSet1, dataSet2);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return createCompositeDataSet(false, false);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        return createCompositeDataSet(false, true);
    }

    @Test
    void testCombineTables() throws Exception
    {
        final CompositeDataSet combinedDataSet =
                createCompositeDataSet(true, false);
        final String[] tableNames = combinedDataSet.getTableNames();
        assertThat(tableNames).as("table count combined").hasSize(2);
        assertThat(tableNames[0]).isEqualTo("DUPLICATE_TABLE");
        assertThat(tableNames[1]).isEqualTo("EMPTY_TABLE");
    }

    private CompositeDataSet createCompositeDataSet(final boolean combined,
            final boolean multipleCase)
            throws DataSetException, FileNotFoundException, IOException
    {
        final IDataSet dataSet1 = new FlatXmlDataSetBuilder().build(TestUtils
                .getFileReader("xml/compositeDataSetDuplicateTest1.xml"));
        assertThat(dataSet1.getTableNames()).as("count before combine (1)")
                .hasSizeLessThan(getExpectedDuplicateNames().length);

        IDataSet dataSet2 = new FlatXmlDataSetBuilder().build(TestUtils
                .getFileReader("xml/compositeDataSetDuplicateTest2.xml"));
        assertThat(dataSet2.getTableNames()).as("count before combine (2)")
                .hasSizeLessThan(getExpectedDuplicateNames().length);

        if (multipleCase)
        {
            dataSet2 = new LowerCaseDataSet(dataSet2);
        }

        final CompositeDataSet dataSet =
                new CompositeDataSet(dataSet1, dataSet2, combined);
        return dataSet;
    }

}
