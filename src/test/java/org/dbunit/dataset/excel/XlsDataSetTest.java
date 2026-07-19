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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.Columns;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

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
    public void testCreateMultipleCaseDuplicateDataSet_withDuplicateCaseVariantNames_throwsAmbiguousTableNameException() throws Exception
    {
        // Not supported
    }

    @Test
    void testWrite_withValidDataSet_writesAndReadsBackEquivalentData() throws Exception
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
    @EnabledOnOs(OS.WINDOWS)
    void testConstructor_withFile_closesUnderlyingFileStream() throws Exception
    {
        // Windows-only: proving stream closure via deletability relies on Windows' mandatory
        // file locking, where an open FileInputStream blocks File.delete(). On Linux/macOS,
        // unlink() succeeds regardless of open file descriptors, so this same assertion would
        // pass even with the leak still present -- it would not be a meaningful regression guard
        // there.
        final File sourceFile = TestUtils.getFile("xml/dataSetTest.xls");
        final File tempCopy = File.createTempFile("xlsDataSetTest", ".xls");
        try
        {
            Files.copy(sourceFile.toPath(), tempCopy.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

            new XlsDataSet(tempCopy);

            assertThat(tempCopy.delete())
                    .as("the file should be deletable immediately after construction, "
                            + "proving the internal FileInputStream was closed rather than leaked.")
                    .isTrue();
        } finally
        {
            tempCopy.delete();
        }
    }

    @Test
    void testColumnNameWithSpace_withSpaceInColumnName_returnsColumn() throws Exception
    {
        final IDataSet dataSet = new XlsDataSet(
                TestUtils.getFileInputStream("xml/contactor.xls"));
        final ITable customerTable = dataSet.getTable("customer");
        final Column column = Columns.getColumn("name",
                customerTable.getTableMetaData().getColumns());
        assertThat(column).isNotNull();
    }

}
