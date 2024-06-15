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
package org.dbunit.dataset.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @since Feb 22, 2003
 * @version $Revision$
 */
class XlsDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new XlsDataSet(TestUtils.getFile("xml/dataSetTest.xls"));
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return new XlsDataSet(
                TestUtils.getFile("xml/dataSetDuplicateTest.xls"));
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        throw new UnsupportedOperationException(
                "Excel does not support the same sheet name with different cases in one file");
    }

    @Override
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        // Not supported
    }

    @Test
    void testWrite() throws Exception
    {
        final IDataSet expectedDataSet = createDataSet();
        final File tempFile = File.createTempFile("xlsDataSetTest", ".xls");
        try
        {
            final OutputStream out = new FileOutputStream(tempFile);

            // write dataset in temp file
            try
            {
                XlsDataSet.write(expectedDataSet, out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final InputStream in = new FileInputStream(tempFile);
            try
            {
                final IDataSet actualDataSet = new XlsDataSet(in);

                // verify table count
                assertThat(actualDataSet.getTableNames()).as("table count")
                        .hasSameSizeAs(expectedDataSet.getTableNames());

                // verify each table
                final ITable[] expected =
                        DataSetUtils.getTables(expectedDataSet);
                final ITable[] actual = DataSetUtils.getTables(actualDataSet);
                assertThat(actual).as("table count").hasSameSizeAs(expected);
                for (int i = 0; i < expected.length; i++)
                {
                    final String expectedName =
                            expected[i].getTableMetaData().getTableName();
                    final String actualName =
                            actual[i].getTableMetaData().getTableName();
                    assertThat(actualName).as("table name")
                            .isEqualTo(expectedName);

                    assertThat(actual[i]).as("not same instance")
                            .isNotEqualTo(expected[i]);
                    Assertion.assertEquals(expected[i], actual[i]);
                }
            } finally
            {
                in.close();
            }
        } finally
        {
            tempFile.delete();
        }
    }

    @Test
    void testColumnNameWithSpace() throws Exception
    {
        final IDataSet dataSet = new XlsDataSet(
                TestUtils.getFileInputStream("xml/contactor.xls"));
        final ITable customerTable = dataSet.getTable("customer");
        final Column column = Columns.getColumn("name",
                customerTable.getTableMetaData().getColumns());
        assertThat(column).isNotNull();
    }

}
