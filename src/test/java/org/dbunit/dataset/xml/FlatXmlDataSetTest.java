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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.Writer;

import org.dbunit.Assertion;
import org.dbunit.dataset.AbstractDataSetTest;
import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetUtils;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableIterator;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Manuel Laflamme
 * @version $Revision$
 * @since Mar 13, 2002
 */
public class FlatXmlDataSetTest extends AbstractDataSetTest
{
    public static final File DATASET_FILE =
            TestUtils.getFile("xml/flatXmlDataSetTest.xml");
    public static final File DUPLICATE_DATASET_FILE =
            TestUtils.getFile("xml/flatXmlDataSetDuplicateTest.xml");
    public static final File DUPLICATE_DATASET_MULTIPLE_CASE_FILE = TestUtils
            .getFile("xml/flatXmlDataSetDuplicateMultipleCaseTest.xml");

    private static final File FLAT_XML_TABLE =
            TestUtils.getFile("xml/flatXmlTableTest.xml");

    private static final File FLAT_XML_DTD_DIFFERENT_CASE_FILE =
            TestUtils.getFile("xml/flatXmlDataSetDtdDifferentCaseTest.xml");

    @Override
    protected IDataSet createDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder().build(DATASET_FILE);
    }

    @Override
    protected IDataSet createDuplicateDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder().build(DUPLICATE_DATASET_FILE);
    }

    @Override
    protected IDataSet createMultipleCaseDuplicateDataSet() throws Exception
    {
        return new FlatXmlDataSetBuilder()
                .build(DUPLICATE_DATASET_MULTIPLE_CASE_FILE);
    }

    @Test
    void testMissingColumnAndEnableDtdMetadata() throws Exception
    {
        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(true);
        final IDataSet dataSet = builder.build(FLAT_XML_TABLE);

        final ITable table = dataSet.getTable("MISSING_VALUES");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(3);
    }

    @Test
    void testMissingColumnAndDisableDtdMetadata() throws Exception
    {
        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(false);
        final IDataSet dataSet = builder.build(FLAT_XML_TABLE);

        final ITable table = dataSet.getTable("MISSING_VALUES");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(2);
    }

    @Test
    void testMissingColumnAndDisableDtdMetadataEnableSensing() throws Exception
    {
        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(false);
        builder.setColumnSensing(true);
        final IDataSet dataSet = builder.build(FLAT_XML_TABLE);

        final ITable table = dataSet.getTable("MISSING_VALUES_SENSING");

        final Column[] columns = table.getTableMetaData().getColumns();
        assertThat(columns).as("column count").hasSize(3);
        assertThat(columns[0].getColumnName()).isEqualTo("COLUMN0");
        assertThat(columns[1].getColumnName()).isEqualTo("COLUMN3");
        assertThat(columns[2].getColumnName()).isEqualTo("COLUMN1");
        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(table.getValue(0, "COLUMN0")).isEqualTo("row 0 col 0");
        assertThat(table.getValue(0, "COLUMN3")).isEqualTo("row 0 col 3");
        assertThat(table.getValue(1, "COLUMN0")).isEqualTo("row 1 col 0");
        assertThat(table.getValue(1, "COLUMN1")).isEqualTo("row 1 col 1");
        assertThat(table.getValue(2, "COLUMN3")).isEqualTo("row 2 col 3");
    }

    @Test
    void testWrite() throws Exception
    {
        final IDataSet expectedDataSet = createDataSet();
        final File tempFile = File.createTempFile("flatXmlDataSetTest", ".xml");
        try
        {
            final Writer out = new FileWriter(tempFile);

            // write dataset in temp file
            try
            {
                FlatXmlDataSet.write(expectedDataSet, out);
            } finally
            {
                out.close();
            }

            // load new dataset from temp file
            final FileReader in = new FileReader(tempFile);
            try
            {
                final IDataSet actualDataSet =
                        new FlatXmlDataSetBuilder().build(in);

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
                            .isNotSameAs(expected[i]);
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
    void testReadFlatXmlWithDifferentCaseInDtd() throws Exception
    {
        // The creation of such a dataset should work
        final IDataSet ds = new FlatXmlDataSetBuilder()
                .build(FLAT_XML_DTD_DIFFERENT_CASE_FILE);
        assertThat(ds.getTableNames()).hasSize(1);
        assertThat(ds.getTableNames()[0]).isEqualTo("emp");
    }

    @Test
    void testCreateMultipleCaseDuplicateDataSet_CaseSensitive() throws Exception
    {
        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(false);
        builder.setColumnSensing(false);
        // Create a FlatXmlDataSet having caseSensitivity=true
        builder.setCaseSensitiveTableNames(true);
        final IDataSet dataSet =
                builder.build(DUPLICATE_DATASET_MULTIPLE_CASE_FILE);

        final ITable[] tables = dataSet.getTables();
        assertThat(tables).hasSize(3);
        assertThat(tables[0].getTableMetaData().getTableName())
                .isEqualTo("DUPLICATE_TABLE");
        assertThat(tables[1].getTableMetaData().getTableName())
                .isEqualTo("EMPTY_TABLE");
        assertThat(tables[2].getTableMetaData().getTableName())
                .isEqualTo("duplicate_TABLE");
    }

    /**
     * Overridden from parent because FlatXml has different behaviour than other
     * datasets. It allows the occurrence of the same table multiple times in
     * arbitrary locations.
     * 
     * @see org.dbunit.dataset.AbstractDataSetTest#testCreateDuplicateDataSet()
     */
    @Override
    @Test
    public void testCreateDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet = createDuplicateDataSet();
        final ITable[] tables = dataSet.getTables();
        assertThat(tables).hasSize(2);
        assertThat(tables[0].getTableMetaData().getTableName())
                .isEqualTo("DUPLICATE_TABLE");
        assertThat(tables[0].getRowCount()).isEqualTo(3);
        assertThat(tables[1].getTableMetaData().getTableName())
                .isEqualTo("EMPTY_TABLE");
        assertThat(tables[1].getRowCount()).isZero();
    }

    /**
     * Overridden from parent because FlatXml has different behaviour than other
     * datasets. It allows the occurrence of the same table multiple times in
     * arbitrary locations.
     * 
     * @see org.dbunit.dataset.AbstractDataSetTest#testCreateMultipleCaseDuplicateDataSet()
     */
    @Override
    @Test
    public void testCreateMultipleCaseDuplicateDataSet() throws Exception
    {
        final IDataSet dataSet = createMultipleCaseDuplicateDataSet();
        final ITable[] tables = dataSet.getTables();
        assertThat(tables).hasSize(2);
        assertThat(tables[0].getTableMetaData().getTableName())
                .isEqualTo("DUPLICATE_TABLE");
        assertThat(tables[0].getRowCount()).isEqualTo(3);
        assertThat(tables[1].getTableMetaData().getTableName())
                .isEqualTo("EMPTY_TABLE");
        assertThat(tables[1].getRowCount()).isZero();
    }

    @Test
    void testCreateDuplicateDataSetWithVaryingColumnsAndColumnSensing()
            throws Exception
    {
        final String xmlString = "<dataset>"
                + "<MISSING_VALUES_SENSING COLUMN0='row 0 col 0' COLUMN3='row 0 col 3'/>"
                + "<MISSING_VALUES         COLUMN0='row 1 col 0' COLUMN2='row 1 col 2'/>"
                + "<MISSING_VALUES_SENSING COLUMN0='row 1 col 0' COLUMN1='row 1 col 1'/>"
                + "</dataset>";

        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(false);
        builder.setColumnSensing(true);
        final IDataSet dataSet = builder.build(new StringReader(xmlString));
        final ITable[] tables = dataSet.getTables();
        assertThat(tables).hasSize(2);

        final ITableMetaData meta1 = tables[0].getTableMetaData();
        assertThat(meta1.getTableName()).isEqualTo("MISSING_VALUES_SENSING");
        assertThat(meta1.getColumns()).hasSize(3);
        assertThat(meta1.getColumns()[0].getColumnName()).isEqualTo("COLUMN0");
        assertThat(meta1.getColumns()[1].getColumnName()).isEqualTo("COLUMN3");
        assertThat(meta1.getColumns()[2].getColumnName()).isEqualTo("COLUMN1");
        assertThat(tables[0].getRowCount()).isEqualTo(2);
        assertThat(tables[0].getValue(0, "COLUMN0")).isEqualTo("row 0 col 0");
        assertThat(tables[0].getValue(0, "COLUMN3")).isEqualTo("row 0 col 3");
        assertThat(tables[0].getValue(0, "COLUMN1")).isNull();
        assertThat(tables[0].getValue(1, "COLUMN0")).isEqualTo("row 1 col 0");
        assertThat(tables[0].getValue(1, "COLUMN3")).isNull();
        assertThat(tables[0].getValue(1, "COLUMN1")).isEqualTo("row 1 col 1");

        assertThat(tables[1].getTableMetaData().getTableName())
                .isEqualTo("MISSING_VALUES");
        assertThat(tables[1].getRowCount()).isEqualTo(1);
    }

    @Test
    void testCreateDataSetWithVaryingColumnCasingAndColumnSensing()
            throws Exception
    {
        final String xmlContent = "<dataset>"
                + "<CASED_COLUMNS COLUMN0='row 0 col 0' COLUMN1='row 0 col 1' />"
                + "<CASED_COLUMNS column0='row 1 col 0' COLUMN1='row 1 col 1' />"
                + "</dataset>";

        final FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        builder.setDtdMetadata(false);
        builder.setColumnSensing(true);
        final IDataSet dataSet = builder.build(new StringReader(xmlContent));

        final ITableIterator tables = dataSet.iterator();

        // there is only one table in the dataset
        assertTrue(tables.next());
        final ITable table = tables.getTable();
        assertFalse(tables.next());

        final ITableMetaData tableMetaData = table.getTableMetaData();
        assertThat(tableMetaData.getColumns()).hasSize(2);
        assertThat(table.getRowCount()).isEqualTo(2);

        // first row has standard values
        assertThat(table.getValue(0, "COLUMN0")).isEqualTo("row 0 col 0");
        assertThat(table.getValue(0, "COLUMN1")).isEqualTo("row 0 col 1");
        // second row should have proper values, no nulls
        assertThat(table.getValue(1, "COLUMN0")).isEqualTo("row 1 col 0");
        assertThat(table.getValue(1, "COLUMN1")).isEqualTo("row 1 col 1");
    }
}
