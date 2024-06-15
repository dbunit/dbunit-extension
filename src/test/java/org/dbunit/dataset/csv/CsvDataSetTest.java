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
package org.dbunit.dataset.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.dbunit.Assertion;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.FileHelper;
import org.junit.jupiter.api.Test;

/**
 * @author Lenny Marks (lenny@aps.org)
 * @version $Revision$
 * @since Sep 12, 2004 (pre 2.3)
 */
class CsvDataSetTest
{
    protected static final File DATASET_DIR = TestUtils.getFile("csv/orders");

    @Test
    void testNullColumns() throws DataSetException
    {
        final File csvDir = DATASET_DIR;

        final CsvDataSet dataSet = new CsvDataSet(csvDir);

        final ITable table = dataSet.getTable("orders");

        assertThat(table.getValue(4, "description")).isNull();

    }

    @Test
    void testWrite() throws Exception
    {

        final IDataSet expectedDataSet = new CsvDataSet(DATASET_DIR);

        final File tempDir = createTmpDir();
        try
        {
            // modified this test from FlatXmlDataSetTest
            CsvDataSetWriter.write(expectedDataSet, tempDir);

            final File tableOrderingFile =
                    new File(tempDir, CsvDataSet.TABLE_ORDERING_FILE);
            assertThat(tableOrderingFile).exists();

            final IDataSet actualDataSet = new CsvDataSet(tempDir);

            // verify table count
            assertThat(actualDataSet.getTableNames()).as("table count")
                    .hasSameSizeAs(expectedDataSet.getTableNames());

            // verify each table
            final ITable[] expected = DataSetUtils.getTables(expectedDataSet);
            final ITable[] actual = DataSetUtils.getTables(actualDataSet);
            assertThat(actual).as("table count").hasSameSizeAs(expected);
            for (int i = 0; i < expected.length; i++)
            {
                final String expectedName =
                        expected[i].getTableMetaData().getTableName();
                final String actualName =
                        actual[i].getTableMetaData().getTableName();
                assertThat(actualName).as("table name").isEqualTo(expectedName);

                assertThat(actual[i]).as("not same instance")
                        .isNotEqualTo(expected[i]);
                Assertion.assertEquals(expected[i], actual[i]);
            }

        } finally
        {
            FileHelper.deleteDirectory(tempDir, true);

        }

        // assertFalse("temporary directory was not deleted", tempDir.exists());
    }

    private File createTmpDir() throws IOException
    {
        final File tmpFile = File.createTempFile("CsvDataSetTest", "-csv");
        final String fullPath = tmpFile.getAbsolutePath();
        tmpFile.delete();

        final File tmpDir = new File(fullPath);
        if (!tmpDir.mkdir())
        {
            throw new IOException("Failed to create tmpDir: " + fullPath);
        }

        return tmpDir;
    }

}
