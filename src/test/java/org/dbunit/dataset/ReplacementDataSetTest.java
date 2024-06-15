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

import java.io.FileReader;

import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.dataset.xml.FlatXmlDataSetTest;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Mar 17, 2003
 * @version $Revision$
 */
class ReplacementDataSetTest extends AbstractDataSetDecoratorTest
{

    ////////////////////////////////////////////////////////////////////////////
    // AbstractDataSetTest class

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new ReplacementDataSet(new FlatXmlDataSetBuilder()
                .build(new FileReader(FlatXmlDataSetTest.DATASET_FILE)));
    }

    @Test
    void testConstructor_DataSetHasCaseSensitive_ReplacementSetHasCaseSensitive()
            throws Exception
    {
        final FileReader xmlReader =
                new FileReader(FlatXmlDataSetTest.DATASET_FILE);
        final FlatXmlDataSet flatDataSet = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(xmlReader);
        final ReplacementDataSet dataSet = new ReplacementDataSet(flatDataSet);
        assertThat(dataSet.isCaseSensitiveTableNames()).isTrue();

    }

    @Test
    void testConstructor_DifferentCaseTableNames_CaseSensitiveMatch()
            throws Exception
    {
        final FileReader fileReader = TestUtils
                .getFileReader("/xml/replacementDataSetCaseSensitive.xml");
        final IDataSet originalDataSet = new FlatXmlDataSetBuilder()
                .setCaseSensitiveTableNames(true).build(fileReader);
        assertCaseSensitiveTables(originalDataSet);

        final IDataSet replacementDataSet =
                new ReplacementDataSet(originalDataSet);
        assertCaseSensitiveTables(replacementDataSet);
    }

    private void assertCaseSensitiveTables(final IDataSet dataSet)
            throws DataSetException
    {
        final ITable[] tables = dataSet.getTables();
        assertThat(tables).as(
                "Should be 2 tables with case-sensitive table names; 1 without.")
                .hasSize(2);

        final String tableName0 = tables[0].getTableMetaData().getTableName();
        final String tableName1 = tables[1].getTableMetaData().getTableName();

        assertThat(tableName0).isEqualTo("TEST_TABLE");
        assertThat(tableName1).isEqualTo("test_table");
        assertThat(tables[0].getValue(0, "COLUMN0")).isEqualTo("row 0 col 0");
        assertThat(tables[1].getValue(0, "COLUMN0")).isEqualTo("row 1 col 0");
    }
}
