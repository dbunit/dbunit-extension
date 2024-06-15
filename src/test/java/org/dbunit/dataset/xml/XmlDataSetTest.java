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
package org.dbunit.dataset.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Feb 17, 2002
 */
public class XmlDataSetTest extends AbstractDataSetTest
{

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        final Reader in =
                new FileReader(TestUtils.getFile("xml/dataSetTest.xml"));
        return new XmlDataSet(in);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        final InputStream in = new FileInputStream(
                TestUtils.getFile("xml/xmlDataSetDuplicateTest.xml"));
        return new XmlDataSet(in);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        final InputStream in = new FileInputStream(TestUtils
                .getFile("xml/xmlDataSetDuplicateMultipleCaseTest.xml"));
        return new XmlDataSet(in);
    }

    @Test
    void testWrite() throws Exception
    {
        final IDataSet expectedDataSet = createDataSet();
        final File tempFile = File.createTempFile("dataSetTest", ".xml");
        try
        {
            final OutputStream out = new FileOutputStream(tempFile);

            try
            {
                // write dataset in temp file
                XmlDataSet.write(expectedDataSet, out);

                // load new dataset from temp file
                final IDataSet actualDataSet =
                        new XmlDataSet(new FileReader(tempFile));

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
                out.close();
            }
        } finally
        {
            tempFile.delete();
        }
    }

    /**
     * Overridden from parent because XmlDataSet has different behaviour than
     * other datasets. It allows the occurrence of the same table multiple times
     * in arbitrary locations.
     *
     * @see org.dbunit.dataset.AbstractDataSetTest#testCreateDuplicateDataSet()
     */
    // @Override
    @Override
    @Test
    public void testCreateDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet = createDuplicateDataSet();
        final ITable[] tables = dataSet.getTables();
        assertThat(tables.length).isEqualTo(2);
        assertEquals("DUPLICATE_TABLE",
                tables[0].getTableMetaData().getTableName());
        assertThat(tables[0].getRowCount()).isEqualTo(3);
        assertEquals("EMPTY_TABLE",
                tables[1].getTableMetaData().getTableName());
        assertThat(tables[1].getRowCount()).isZero();
    }

    /**
     * Overridden from parent because XmlDataSet has different behaviour than
     * other datasets. It allows the occurrence of the same table multiple times
     * in arbitrary locations.
     *
     * @see org.dbunit.dataset.AbstractDataSetTest#testCreateMultipleCaseDuplicateDataSet()
     */
    // @Override
    @Override
    @Test
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet = createMultipleCaseDuplicateDataSet();
        final ITable[] tables = dataSet.getTables();
        assertThat(tables.length).isEqualTo(2);
        assertEquals("DUPLICATE_TABLE",
                tables[0].getTableMetaData().getTableName());
        assertThat(tables[0].getRowCount()).isEqualTo(3);
        assertEquals("EMPTY_TABLE",
                tables[1].getTableMetaData().getTableName());
        assertThat(tables[1].getRowCount()).isZero();
    }

    @Test
    void testCreate_$InTableName_Success() throws DataSetException
    {
        final String fileName = "/xml/dataSet$Test.xml";
        final String tableName = "TEST_TA$BLE";

        final InputStream inputStream =
                getClass().getResourceAsStream(fileName);
        final XmlDataSet dataSet = new XmlDataSet(inputStream);

        final ITable table = dataSet.getTable(tableName);

        // getTable() throws NoSuchTableException if not found
        // so these checks currently unnecessary but future proof
        assertThat(table).as("DataSet table is null.").isNotNull();

        final ITableMetaData tableMetaData = table.getTableMetaData();
        final String actualTableName = tableMetaData.getTableName();
        assertThat(actualTableName).as("Expected table name not found.")
                .isEqualTo(tableName);
    }
}
