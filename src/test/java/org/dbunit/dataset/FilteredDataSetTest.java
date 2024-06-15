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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileReader;

import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.XmlDataSet;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Feb 22, 2002
 */
class FilteredDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final IDataSet dataSet1 =
                new XmlDataSet(TestUtils.getFileReader("xml/dataSetTest.xml"));
        final IDataSet dataSet2 = new XmlDataSet(
                TestUtils.getFileReader("xml/filteredDataSetTest.xml"));

        final IDataSet dataSet = new CompositeDataSet(dataSet1, dataSet2);
        assertThat(dataSet.getTableNames()).as("count before filter")
                .hasSize(getExpectedNames().length + 1);
        return new FilteredDataSet(getExpectedNames(), dataSet);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet1 = new XmlDataSet(
                TestUtils.getFileReader("xml/xmlDataSetDuplicateTest.xml"));
        final IDataSet dataSet2 = new XmlDataSet(
                TestUtils.getFileReader("xml/filteredDataSetTest.xml"));

        assertThat(dataSet1.getTableNames()).hasSize(2);
        assertThat(dataSet2.getTableNames()).hasSize(1);

        final IDataSet dataSet =
                new CompositeDataSet(dataSet1, dataSet2, false);
        assertThat(dataSet.getTableNames()).as("count before filter")
                .hasSize(3);
        return new FilteredDataSet(getExpectedDuplicateNames(), dataSet);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        final String[] names = getExpectedDuplicateNames();
        names[0] = names[0].toLowerCase();

        return new FilteredDataSet(names, createDuplicateDataSet());
    }

    @Test
    void testGetFilteredTableNames() throws Exception
    {
        final String[] originalNames = getExpectedNames();
        final String expectedName = originalNames[0];
        final IDataSet dataSet = createDataSet();
        assertThat(dataSet.getTableNames()).as("original count")
                .hasSizeGreaterThan(1);

        final IDataSet filteredDataSet =
                new FilteredDataSet(new String[] {expectedName}, dataSet);
        assertThat(filteredDataSet.getTableNames()).as("filtered count")
                .hasSize(1);
        assertThat(filteredDataSet.getTableNames()[0]).as("filtered names")
                .isEqualTo(expectedName);
    }

    @Test
    void testGetFilteredTable() throws Exception
    {
        final String[] originalNames = getExpectedNames();
        final IDataSet filteredDataSet = new FilteredDataSet(
                new String[] {originalNames[0]}, createDataSet());

        for (int i = 0; i < originalNames.length; i++)
        {
            final String name = originalNames[i];
            if (i == 0)
            {
                assertThat(filteredDataSet.getTable(name).getTableMetaData()
                        .getTableName()).as("table " + i).isEqualTo(name);
            } else
            {
                assertThrows(NoSuchTableException.class,
                        () -> filteredDataSet.getTable(name),
                        "Should throw a NoSuchTableException");
            }
        }
    }

    @Test
    void testGetFilteredTableMetaData() throws Exception
    {
        final String[] originalNames = getExpectedNames();
        final IDataSet filteredDataSet = new FilteredDataSet(
                new String[] {originalNames[0]}, createDataSet());

        for (int i = 0; i < originalNames.length; i++)
        {
            final String name = originalNames[i];
            if (i == 0)
            {
                assertThat(
                        filteredDataSet.getTableMetaData(name).getTableName())
                                .as("table " + i).isEqualTo(name);
            } else
            {
                assertThrows(NoSuchTableException.class,
                        () -> filteredDataSet.getTableMetaData(name),
                        "Should throw a NoSuchTableException");
            }
        }
    }

    @Test
    void testCaseSensitivityInheritance() throws Exception
    {
        // Case sensitive check
        FileReader fileReader = TestUtils.getFileReader("xml/dataSetTest.xml");
        final IDataSet caseSensitive = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(fileReader);

        final FilteredDataSet caseSesitiveFilter =
                new FilteredDataSet(getExpectedNames(), caseSensitive);
        assertThat(caseSesitiveFilter.isCaseSensitiveTableNames())
                .as("case sensitive inheritance").isTrue();

        // Case insensitive check
        fileReader = TestUtils.getFileReader("xml/dataSetTest.xml");
        final IDataSet caseInsensitive = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(false).build(fileReader);

        final FilteredDataSet caseInsesitiveFilter =
                new FilteredDataSet(getExpectedNames(), caseInsensitive);
        assertThat(caseInsesitiveFilter.isCaseSensitiveTableNames())
                .as("case insensitive inheritance").isFalse();
    }
}
